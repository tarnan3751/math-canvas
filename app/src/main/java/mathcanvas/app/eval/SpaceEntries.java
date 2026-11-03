package mathcanvas.app.eval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.app.document.Command;
import mathcanvas.canvas.model.CanvasObject;
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
 * and each part is parsed on its own; nothing is evaluated, the expressions are stored canonical as
 * {@link Entries} stores them.
 *
 * <ul>
 *   <li>{@code z = f(x, y)}, {@code f} free of {@code z}: a surface over {@code x} and {@code y},
 *       each in {@code [−5, 5]} unless a range is given; on a surface's row, its ranges are kept
 *       unless given, and its cells a side.
 * </ul>
 */
final class SpaceEntries {

  private static final Symbol Z = Symbol.of("z");

  private SpaceEntries() {}

  /**
   * Reads an entry of the 3D view.
   *
   * @param text the row's text
   * @param target the object the row shows, or empty for a new object
   * @param env the environment after the last cell, for the user functions
   * @param cancellation the request to stop
   * @return the command, or why there is none
   */
  static Entries.Read read(
      String text, Optional<CanvasObject> target, Environment env, Cancellation cancellation) {
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
    return Entries.Read.refused("entry.notSpaceObject");
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
    for (Symbol name : ranges.keySet()) {
      if (!name.equals(Surface.X) && !name.equals(Surface.Y)) {
        return Entries.Read.refused("entry.rangeOf", name.name(), "x, y");
      }
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
