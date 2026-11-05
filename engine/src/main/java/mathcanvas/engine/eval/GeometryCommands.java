package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Evaluator.REWRITE_CAP;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.geometry.Shapes;
import mathcanvas.engine.geometry.Triangle;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.provider.Providers;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.units.Convert;

/**
 * The geometry and conversion commands of the evaluator: the shape formulas (§14.27), {@code
 * triangle} by the laws of sines and cosines, and {@code convert} between units (§14.26), evaluated
 * in a statement's {@link Evaluator.State}.
 */
final class GeometryCommands {

  private final Evaluator.State state;

  GeometryCommands(Evaluator.State state) {
    this.state = state;
  }

  /** The context of a geometry solver: the facts, the precision, the session's numerics. */
  private Context geometryContext(boolean traced) {
    return new Context(
        state.env().assumptions(),
        state.mc(),
        traced,
        REWRITE_CAP,
        Providers.defaults(),
        new SessionNumerics(state.env().settings().angleMode().halfTurn(), state.cancellation()),
        state.cancellation());
  }

  /** A shape formula, coordinate-geometry or conic function ({@link Shapes}). */
  Expr shape(Apply a, List<Expr> args) {
    try {
      Expr value = new Shapes(geometryContext(false)).apply(a.head().name(), args);
      return value == null ? state.canon(Apply.of(a.head(), args)) : value;
    } catch (IllegalArgumentException wrong) {
      throw new EvaluationException(wrong.getMessage(), a);
    }
  }

  /**
   * {@code triangle(p = v, …)}: each part's name held, its value evaluated; a plain number given
   * for an angle is in the session's unit, any other angle in radians. A decimal among the values
   * makes every part a decimal at the session's precision. A value with a symbol, or a comparison
   * that cannot be decided, leaves the call ({@link Triangle}).
   */
  Expr triangle(Apply a) {
    Map<String, Expr> given = new TreeMap<>();
    List<Expr> evaluated = new ArrayList<>();
    boolean symbolic = false;
    boolean inexact = false;
    int half = state.env().settings().angleMode().halfTurn();
    for (Expr arg : a.args()) {
      if (!(arg instanceof Relation r
          && r.op() == Relation.Op.EQ
          && r.left() instanceof Symbol name
          && List.of("a", "b", "c", "A", "B", "C").contains(name.name()))) {
        throw new EvaluationException(
            "`triangle` takes three parts such as a = 3 or C = 90°, from a b c A B C", arg);
      }
      if (given.containsKey(name.name())) {
        throw new EvaluationException("`triangle` takes each part once", arg);
      }
      Expr value = state.eval(r.right());
      evaluated.add(new Relation(Relation.Op.EQ, name, value));
      if (value instanceof Num n && !(n.value() instanceof RealNumeric)) {
        throw new EvaluationException("`triangle`'s parts must be real numbers", arg);
      }
      symbolic |= occursSymbol(value);
      inexact |= hasInexact(value);
      boolean angle = Character.isUpperCase(name.name().charAt(0));
      if (angle && half != 0 && value instanceof Num) {
        value = state.canon(Exprs.div(Product.of(value, Constant.PI), Num.of(half)));
      }
      given.put(name.name(), value);
    }
    Expr stays = state.canon(Apply.of(a.head(), evaluated));
    if (symbolic) {
      return stays;
    }
    Context ctx = geometryContext(state.traced() && a == state.root());
    Optional<Rewriter.Result> r;
    try {
      r = Triangle.solve(given, ctx);
    } catch (IllegalArgumentException wrong) {
      throw new EvaluationException(wrong.getMessage(), a);
    }
    if (r.isEmpty()) {
      return stays;
    }
    if (ctx.traceEnabled()) {
      state.commandTrace(r.get().trace());
    }
    Expr value = state.canon(r.get().expr());
    // decimal data give decimal parts, as solving a decimal polynomial does
    return inexact
        ? NumericForm.numeric(value, state.mc().getPrecision(), a, state.cancellation())
        : value;
  }

  /** Whether {@code e} holds an inexact number. */
  private static boolean hasInexact(Expr e) {
    Deque<Expr> todo = new ArrayDeque<>();
    todo.push(e);
    while (!todo.isEmpty()) {
      Expr n = todo.pop();
      if (n instanceof Num num && !num.value().isExact()) {
        return true;
      }
      for (Expr op : Operands.of(n)) {
        todo.push(op);
      }
    }
    return false;
  }

  /** Whether {@code e} holds a symbol. */
  private static boolean occursSymbol(Expr e) {
    Deque<Expr> todo = new ArrayDeque<>();
    todo.push(e);
    while (!todo.isEmpty()) {
      Expr n = todo.pop();
      if (n instanceof Symbol) {
        return true;
      }
      for (Expr op : Operands.of(n)) {
        todo.push(op);
      }
    }
    return false;
  }

  /**
   * {@code convert(q, unit)} (§14.26): the quantity in the target's units with exact factors
   * ({@link Convert}), its step kept when the call is the statement itself.
   */
  Expr convert(Apply a) {
    Expr q = state.eval(a.args().getFirst());
    Expr target = state.eval(a.args().get(1));
    state.dimension(q, a);
    try {
      return state.keepTrace(a, Convert.of(q, target, state.algebraContext(a, List.of(q, target))));
    } catch (Convert.ConvertException x) {
      throw new EvaluationException(x.getMessage(), a);
    }
  }
}
