package mathcanvas.app.eval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mathcanvas.app.document.Command;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.GradientArrow;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParametricSurface;
import mathcanvas.canvas.model.SpaceCurve;
import mathcanvas.canvas.model.SpaceObject;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Surface;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.parse.ParseException;
import mathcanvas.engine.parse.Parser;

/**
 * Reads the text of an algebra panel row of the 3D view (docs/reference/app.md, **Evaluation**): an
 * object in space, then optional ranges of its variables, each after a comma at the top level,
 * {@code name in [a, b]} ({@code ∈}). The text is split at those commas here, not by the grammar,
 * and each part is parsed on its own, as are the three arguments of {@code grad(…)}, which the
 * grammar would refuse (its {@code grad} takes one); nothing is evaluated, the expressions are
 * stored canonical as {@link Entries} stores them.
 *
 * <ul>
 *   <li>{@code z = f(x, y)}, {@code f} free of {@code z}: a surface over {@code x} and {@code y},
 *       each in {@code [−5, 5]} unless a range is given; on a surface's row, its ranges are kept
 *       unless given, and its cells a side;
 *   <li>{@code [x, y, z]} (or {@code ⟨x, y, z⟩}) reading {@code u} or {@code v}: a parametric
 *       surface, {@code u} in {@code [0, 2π]} and {@code v} in {@code [0, π]} unless given; reading
 *       {@code t} and neither: a space curve, {@code t} in {@code [0, 2π]} unless given;
 *   <li>{@code grad(z = f(x, y), x₀, y₀)}: the gradient of the surface {@code z = f} at {@code (x₀,
 *       y₀)}, on the canvas's surface of that body when there is one, else on a new surface added
 *       with it in one step; on a gradient's row, {@code [x₀, y₀]} moves its point.
 * </ul>
 */
final class SpaceEntries {

  private static final Symbol Z = Symbol.of("z");
  private static final Symbol GRAD = Symbol.of("grad");

  private SpaceEntries() {}

  /**
   * Reads an entry of the 3D view.
   *
   * @param text the row's text
   * @param target the object the row shows, or empty for a new object
   * @param canvas the canvas, whose surfaces a gradient is taken on
   * @param env the environment after the last cell, for the user functions
   * @param cancellation the request to stop
   * @return the command, or why there is none
   */
  static Entries.Read read(
      String text,
      Optional<CanvasObject> target,
      CanvasState canvas,
      Environment env,
      Cancellation cancellation) {
    // grad(z = f, x₀, y₀) is split here too: the grammar gives grad one argument
    String trimmed = text.strip();
    if (trimmed.startsWith(GRAD.name() + "(") && trimmed.endsWith(")") && target.isEmpty()) {
      List<String> args = parts(trimmed.substring(GRAD.name().length() + 1, trimmed.length() - 1));
      if (args.size() != 3) {
        return Entries.Read.refused("entry.grad");
      }
      List<Expr> in = new ArrayList<>();
      for (String arg : args) {
        try {
          in.add(Parser.parseExpression(arg, env.names()));
        } catch (ParseException x) {
          return new Entries.Read(null, x.getMessage());
        }
      }
      if (!(in.getFirst() instanceof Relation r
          && r.op() == Relation.Op.EQ
          && r.left().equals(Z))) {
        return Entries.Read.refused("entry.grad");
      }
      return gradient(r.right(), in.get(1), in.get(2), canvas, cancellation);
    }
    List<String> parts = parts(text);
    List<Expr> parsed = new ArrayList<>();
    for (String part : parts) {
      try {
        parsed.add(Parser.parseExpression(part, env.names()));
      } catch (ParseException x) {
        return new Entries.Read(null, x.getMessage());
      }
    }
    Map<Symbol, Interval> ranges = new HashMap<>();
    for (Expr clause : parsed.subList(1, parsed.size())) {
      if (!(clause instanceof Relation r
          && r.op() == Relation.Op.IN
          && r.left() instanceof Symbol name
          && r.right() instanceof Vector v
          && v.items().size() == 2)) {
        return Entries.Read.refused("entry.range");
      }
      if (ranges.containsKey(name)) {
        return Entries.Read.refused("entry.rangeTwice", name.name());
      }
      ranges.put(
          name,
          Interval.of(
              Canonical.of(v.items().get(0), Canonical.DEFAULT_CONTEXT, cancellation),
              Canonical.of(v.items().get(1), Canonical.DEFAULT_CONTEXT, cancellation),
              true,
              true));
    }
    Expr first = parsed.getFirst();
    Style style = target.map(CanvasObject::style).orElse(Style.DEFAULT);
    if (first instanceof Relation r
        && r.op() == Relation.Op.EQ
        && r.left().equals(Z)
        && !ReadNames.of(r.right()).contains(Z.name())) {
      return surface(r.right(), ranges, target, style, cancellation);
    }
    if (first instanceof Vector v3 && v3.items().size() == 3) {
      return vector(v3, ranges, target, style, cancellation);
    }
    if (first instanceof Vector v2
        && v2.items().size() == 2
        && target.isPresent()
        && target.get() instanceof GradientArrow g
        && ranges.isEmpty()) {
      return new Entries.Read(
          new Command.ReplaceObject(
              g.at(
                  canonical(v2.items().get(0), cancellation),
                  canonical(v2.items().get(1), cancellation))),
          null);
    }
    return Entries.Read.refused("entry.notSpaceObject");
  }

  private static Expr canonical(Expr e, Cancellation cancellation) {
    return Canonical.of(e, Canonical.DEFAULT_CONTEXT, cancellation);
  }

  /**
   * A 3-vector: a parametric surface when it reads {@code u} or {@code v}, else a curve in {@code
   * t}.
   */
  private static Entries.Read vector(
      Vector v,
      Map<Symbol, Interval> ranges,
      Optional<CanvasObject> target,
      Style style,
      Cancellation cancellation) {
    Set<String> names = new HashSet<>();
    for (Expr e : v.items()) {
      names.addAll(ReadNames.of(e));
    }
    Expr x = canonical(v.items().get(0), cancellation);
    Expr y = canonical(v.items().get(1), cancellation);
    Expr z = canonical(v.items().get(2), cancellation);
    boolean surface =
        names.contains(ParametricSurface.U.name()) || names.contains(ParametricSurface.V.name());
    if (surface) {
      Optional<ParametricSurface> was =
          target.filter(ParametricSurface.class::isInstance).map(ParametricSurface.class::cast);
      if (target.isPresent() && was.isEmpty()) {
        return Entries.Read.refused("entry.invalid");
      }
      Optional<Entries.Read> wrong = only(ranges, "u, v", ParametricSurface.U, ParametricSurface.V);
      if (wrong.isPresent()) {
        return wrong.get();
      }
      Interval u =
          ranges.getOrDefault(
              ParametricSurface.U,
              was.map(ParametricSurface::uDomain).orElse(ParametricSurface.U_DOMAIN));
      Interval w =
          ranges.getOrDefault(
              ParametricSurface.V,
              was.map(ParametricSurface::vDomain).orElse(ParametricSurface.V_DOMAIN));
      int n = was.map(ParametricSurface::n).orElse(Surface.DEFAULT_N);
      return was.isPresent()
          ? new Entries.Read(
              new Command.ReplaceObject(
                  new ParametricSurface(was.get().id(), style, x, y, z, u, w, n)),
              null)
          : new Entries.Read(
              Command.AddObject.inTurn(id -> new ParametricSurface(id, style, x, y, z, u, w, n)),
              null);
    }
    if (!names.contains(SpaceCurve.T.name())) {
      return Entries.Read.refused("entry.spaceVector");
    }
    Optional<SpaceCurve> was =
        target.filter(SpaceCurve.class::isInstance).map(SpaceCurve.class::cast);
    if (target.isPresent() && was.isEmpty()) {
      return Entries.Read.refused("entry.invalid");
    }
    Optional<Entries.Read> wrong = only(ranges, "t", SpaceCurve.T);
    if (wrong.isPresent()) {
      return wrong.get();
    }
    Interval t =
        ranges.getOrDefault(
            SpaceCurve.T, was.map(SpaceCurve::domain).orElse(ParametricSurface.U_DOMAIN));
    return was.isPresent()
        ? new Entries.Read(
            new Command.ReplaceObject(new SpaceCurve(was.get().id(), style, x, y, z, t)), null)
        : new Entries.Read(
            Command.AddObject.inTurn(id -> new SpaceCurve(id, style, x, y, z, t)), null);
  }

  /** A refusal when a range is of a name that is not one of the object's variables. */
  private static Optional<Entries.Read> only(
      Map<Symbol, Interval> ranges, String list, Symbol... variables) {
    for (Symbol name : ranges.keySet()) {
      if (!List.of(variables).contains(name)) {
        return Optional.of(Entries.Read.refused("entry.rangeOf", name.name(), list));
      }
    }
    return Optional.empty();
  }

  /**
   * The gradient of {@code z = body} at a point: on the canvas's surface of that body (its body
   * made canonical as an entry's is) when there is one, else on a new surface over the default
   * square added before it in one step.
   */
  private static Entries.Read gradient(
      Expr body, Expr x0, Expr y0, CanvasState canvas, Cancellation cancellation) {
    if (ReadNames.of(body).contains(Z.name())) {
      return Entries.Read.refused("entry.notSpaceObject");
    }
    Expr stored = canonical(body, cancellation);
    Expr x = canonical(x0, cancellation);
    Expr y = canonical(y0, cancellation);
    for (CanvasObject o : canvas.objects()) {
      if (o instanceof Surface s && s.body().equals(stored)) {
        return new Entries.Read(
            Command.AddObject.inTurn(id -> new GradientArrow(id, Style.DEFAULT, s.id(), x, y)),
            null);
      }
    }
    // the surface is added first, so the gradient's id follows it in the same step
    return new Entries.Read(
        new Command.Sequence(
            List.of(
                Command.AddObject.inTurn(
                    id ->
                        new Surface(
                            id,
                            Style.DEFAULT,
                            stored,
                            Surface.DEFAULT_DOMAIN,
                            Surface.DEFAULT_DOMAIN,
                            Surface.DEFAULT_N)),
                Command.AddObject.inTurn(
                    id ->
                        new GradientArrow(id, Style.DEFAULT, new ObjectId(id.value() - 1), x, y)))),
        null);
  }

  private static Entries.Read surface(
      Expr body,
      Map<Symbol, Interval> ranges,
      Optional<CanvasObject> target,
      Style style,
      Cancellation cancellation) {
    Optional<Surface> was = target.filter(Surface.class::isInstance).map(Surface.class::cast);
    if (target.isPresent() && was.isEmpty()) {
      return Entries.Read.refused("entry.invalid");
    }
    Optional<Entries.Read> wrong = only(ranges, "x, y", Surface.X, Surface.Y);
    if (wrong.isPresent()) {
      return wrong.get();
    }
    Expr stored = Canonical.of(body, Canonical.DEFAULT_CONTEXT, cancellation);
    Interval x =
        ranges.getOrDefault(Surface.X, was.map(Surface::xDomain).orElse(Surface.DEFAULT_DOMAIN));
    Interval y =
        ranges.getOrDefault(Surface.Y, was.map(Surface::yDomain).orElse(Surface.DEFAULT_DOMAIN));
    int n = was.map(Surface::n).orElse(Surface.DEFAULT_N);
    return was.isPresent()
        ? new Entries.Read(
            new Command.ReplaceObject(new Surface(was.get().id(), style, stored, x, y, n)), null)
        : new Entries.Read(
            Command.AddObject.inTurn(id -> new Surface(id, style, stored, x, y, n)), null);
  }

  /** Whether an object is read here, on its row. */
  static boolean reads(CanvasObject o) {
    return o instanceof SpaceObject;
  }

  /** The text split at its commas outside every bracket. */
  static List<String> parts(String text) {
    List<String> out = new ArrayList<>();
    int depth = 0;
    int start = 0;
    for (int k = 0; k < text.length(); k++) {
      switch (text.charAt(k)) {
        case '(', '[', '{', '⟨' -> depth++;
        case ')', ']', '}', '⟩' -> depth--;
        case ',' -> {
          if (depth == 0) {
            out.add(text.substring(start, k));
            start = k + 1;
          }
        }
        default -> {}
      }
    }
    out.add(text.substring(start));
    return out;
  }
}
