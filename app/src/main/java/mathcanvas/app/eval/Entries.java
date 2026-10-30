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
import mathcanvas.canvas.model.ImplicitCurve;
import mathcanvas.canvas.model.InequalityRegion;
import mathcanvas.canvas.model.ObjectId;
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
 *   <li>on a slider's row, a number (or {@code a = number}): the slider's value, an exact decimal.
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
          : new Read(new Command.AddObject(make), null);
    } catch (EvaluationException x) {
      return new Read(null, x.getMessage());
    }
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
    return new Read(new Command.AddObject(make, name, name.isEmpty()), null);
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
