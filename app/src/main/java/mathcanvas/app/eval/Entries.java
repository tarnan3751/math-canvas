package mathcanvas.app.eval;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import mathcanvas.app.document.Command;
import mathcanvas.app.text.Messages;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ImplicitCurve;
import mathcanvas.canvas.model.InequalityRegion;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Parametrisation;
import mathcanvas.canvas.model.Path;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Sample;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.EvaluationException;
import mathcanvas.engine.eval.Evaluator;
import mathcanvas.engine.eval.NumericForm;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.parse.ParseException;
import mathcanvas.engine.parse.Parser;

/**
 * Reads the text of an algebra panel row into the command that makes it so, on the worker. The text
 * is parsed with the user functions of the environment after the last cell, so {@code f(x)} is a
 * call when a cell defines {@code f}. An object's expressions are stored as parsed, made canonical
 * at the default context ({@link Canonical#DEFAULT_CONTEXT}), never evaluated, so a stored
 * expression depends neither on the worksheet's values nor on the document's precision.
 *
 * <ul>
 *   <li>{@code y = e}, {@code e} free of {@code y}: a function graph of {@code e} in {@code x};
 *   <li>any other equation: an implicit curve in {@code x} and {@code y};
 *   <li>{@code <}, {@code ≤}, {@code >}, {@code ≥}: an inequality region;
 *   <li>{@code [x, y]} (or {@code ⟨x, y⟩}, or {@code A = [x, y]} naming a new point): a free point,
 *       its coordinates evaluated;
 *   <li>on a slider's row, a number (or {@code a = number}): the slider's value, an exact decimal;
 *   <li>on the row of a point on a path ({@link #onPath}), a number (or {@code name = number}): its
 *       parameter on the path, the coordinate entry.
 * </ul>
 */
final class Entries {

  private static final Symbol X = ImplicitCurve.X;
  private static final Symbol Y = ImplicitCurve.Y;

  /**
   * What an entry asks for.
   *
   * @param command the command, or null when the entry is refused
   * @param error why it is refused, or null
   */
  record Read(Command command, String error) {

    static Read refused(String key, Object... args) {
      return new Read(null, Messages.text(key, args));
    }
  }

  private Entries() {}

  /**
   * Reads an entry.
   *
   * @param text the row's text
   * @param target the object the row shows, or empty for a new object
   * @param env the environment after the last cell, for the user functions and the numbers
   * @param cancellation the request to stop
   * @return the command, or why there is none
   */
  static Read read(
      String text, Optional<CanvasObject> target, Environment env, Cancellation cancellation) {
    Expr e;
    try {
      e = Parser.parseExpression(text, env.names());
    } catch (ParseException x) {
      return new Read(null, x.getMessage());
    }
    try {
      if (target.isPresent() && target.get() instanceof Slider s) {
        return slider(s, e, env, cancellation);
      }
      if (e instanceof Vector || e instanceof Relation r && isNamedPoint(r)) {
        return point(e, target, env, cancellation);
      }
      if (!(e instanceof Relation r)) {
        return Read.refused("entry.notObject");
      }
      Expr stored = Canonical.of(r, Canonical.DEFAULT_CONTEXT, cancellation);
      if (!(stored instanceof Relation c)) {
        return Read.refused("entry.notObject");
      }
      Style style = target.map(CanvasObject::style).orElse(Style.DEFAULT);
      Function<ObjectId, CanvasObject> make;
      if (c.op() == Relation.Op.EQ
          && c.left().equals(Y)
          && !ReadNames.of(c.right()).contains(Y.name())) {
        make = id -> new FunctionGraph(id, style, c.right(), X, FunctionGraph.ALL);
      } else if (c.op() == Relation.Op.EQ) {
        make = id -> new ImplicitCurve(id, style, c);
      } else if (Set.of(Relation.Op.LT, Relation.Op.LE, Relation.Op.GT, Relation.Op.GE)
          .contains(c.op())) {
        make = id -> new InequalityRegion(id, style, c);
      } else {
        return Read.refused("entry.notObject");
      }
      return target.isPresent()
          ? new Read(new Command.ReplaceObject(make.apply(target.get().id())), null)
          : new Read(Command.AddObject.inTurn(make), null);
    } catch (EvaluationException x) {
      return new Read(null, x.getMessage());
    }
  }

  /**
   * Reads the coordinate entry of a point on a path: a number, or {@code name = number} whatever
   * the name (the path's variable, {@code x = 2}, reads naturally), evaluated in the environment to
   * a real number, the point's new parameter. The point lands where the path is at that parameter,
   * found with the sampler. An expression that reads a defined name ({@code a + h}, of sliders or
   * of the worksheet's definitions) places the point at that expression, which it then follows; a
   * number places a point that was at an expression at a parameter of its own again. An expression
   * may not read a point's name: the canvas has no edge from that point, so the point would follow
   * it one recompute late.
   *
   * @param text the text
   * @param point the point
   * @param path the path's geometry as the point was read
   * @param env the environment after the last cell
   * @param points the names of the canvas's points
   * @param sampler evaluates the path at the parameter
   * @param cancellation the request to stop
   * @return the move, or why there is none
   */
  static Read onPath(
      String text,
      PointOnPath point,
      Geometry path,
      Environment env,
      Set<String> points,
      Sampler sampler,
      Cancellation cancellation) {
    Expr e;
    try {
      e = Parser.parseExpression(text, env.names());
    } catch (ParseException x) {
      return new Read(null, x.getMessage());
    }
    if (e instanceof Relation r && r.op() == Relation.Op.EQ && r.left() instanceof Symbol) {
      e = r.right();
    }
    double t;
    try {
      Expr value =
          NumericForm.numeric(
              Evaluator.evaluate(e, env, false, cancellation).value(), 17, cancellation);
      if (!(value instanceof Num n)
          || !(n.value() instanceof RealNumeric x)
          || !Double.isFinite(x.toDouble())) {
        return Read.refused("entry.parameter");
      }
      if (readsName(e, points)) {
        return Read.refused("entry.atPoint");
      }
      if (readsDefinition(e, env)) {
        return new Read(
            new Command.ReplaceObject(point.withAt(Optional.of(Canonical.of(e)))), null);
      }
      t = x.toDouble();
    } catch (EvaluationException x) {
      return new Read(null, x.getMessage());
    }
    if (!(path instanceof Geometry.Curve c
        && c.path() instanceof Path.Parametrised q
        && q.kind() == point.kind()
        && q.contains(t))) {
      return Read.refused("entry.offPath");
    }
    Sample at =
        switch (q.parametrisation()) {
          case Parametrisation.Linear l -> l.at(t);
          case Parametrisation.Expressed x -> sampler.point(x, t);
        };
    if (!at.isFinite()) {
      return Read.refused("entry.offPath");
    }
    if (point.at().isPresent()) {
      return new Read(
          new Command.ReplaceObject(
              point.withAt(Optional.empty()).withParameter(q.kind(), t).landedAt(at.x(), at.y())),
          null);
    }
    return new Read(new Command.MovePoint(point.id(), at.x(), at.y(), t), null);
  }

  /** Whether an expression reads one of the names: a query over the tree. */
  private static boolean readsName(Expr e, Set<String> names) {
    if (e instanceof Symbol s && names.contains(s.name())) {
      return true;
    }
    for (Expr op : Operands.of(e)) {
      if (readsName(op, names)) {
        return true;
      }
    }
    return false;
  }

  /** Whether an expression reads a name the environment defines: a query over the tree. */
  private static boolean readsDefinition(Expr e, Environment env) {
    if (e instanceof Symbol s && env.definition(s.name()).isPresent()) {
      return true;
    }
    for (Expr op : Operands.of(e)) {
      if (readsDefinition(op, env)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isNamedPoint(Relation r) {
    return r.op() == Relation.Op.EQ && r.left() instanceof Symbol && r.right() instanceof Vector;
  }

  private static Read point(
      Expr e, Optional<CanvasObject> target, Environment env, Cancellation cancellation) {
    Optional<String> name = Optional.empty();
    Expr coordinates = e;
    if (e instanceof Relation r) {
      name = Optional.of(((Symbol) r.left()).name());
      coordinates = r.right();
      if (CanvasBindings.isReserved(name.get())) {
        return Read.refused("name.reserved", name.get());
      }
    }
    if (!(coordinates instanceof Vector v) || v.items().size() != 2) {
      return Read.refused("entry.point");
    }
    double[] at = new double[2];
    for (int k = 0; k < 2; k++) {
      Expr value =
          NumericForm.numeric(
              Evaluator.evaluate(v.items().get(k), env, false, cancellation).value(),
              17,
              cancellation);
      if (!(value instanceof Num n)
          || !(n.value() instanceof RealNumeric x)
          || !Double.isFinite(x.toDouble())) {
        return Read.refused("entry.point");
      }
      at[k] = x.toDouble();
    }
    if (target.isPresent()) {
      return target.get() instanceof FreePoint p
          ? new Read(new Command.MovePoint(p.id(), at[0], at[1]), null)
          : Read.refused("entry.notThisKind");
    }
    Function<ObjectId, CanvasObject> make = id -> new FreePoint(id, Style.DEFAULT, at[0], at[1]);
    // an unnamed point takes the next free name when its command runs, not now
    return new Read(new Command.AddObject(make, name, name.isEmpty(), true), null);
  }

  private static Read slider(Slider s, Expr e, Environment env, Cancellation cancellation) {
    Expr value = e;
    if (e instanceof Relation r
        && r.op() == Relation.Op.EQ
        && r.left() instanceof Symbol name
        && name.name().equals(s.variable())) {
      value = r.right();
    }
    Optional<BigDecimal> exact =
        CanvasBindings.decimal(
            Evaluator.evaluate(value, env.without(s.variable()), false, cancellation).value());
    if (exact.isEmpty()) {
      return Read.refused("entry.sliderValue");
    }
    return new Read(new Command.SetSlider(s.id(), exact.get()), null);
  }
}
