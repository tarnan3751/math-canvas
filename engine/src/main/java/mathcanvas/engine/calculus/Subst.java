package mathcanvas.engine.calculus;

import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.simplify.Simplifier;

/**
 * The value of an expression with a value put in for a variable: every free occurrence replaced,
 * the result canonical. A binder of the variable hides it in its body. A derivative or an
 * indefinite integral in the variable has no value at a point that substitution can give (the value
 * of {@code d/dx f} at {@code x = a} is not {@code d/da f(a)}), so it makes the substitution fail.
 */
final class Subst {

  private Subst() {}

  /**
   * Returns {@code e} with {@code v} for {@code x}, canonical at {@code mc}.
   *
   * @param e the expression
   * @param x the variable
   * @param v its value
   * @param mc the precision of the canonical form
   * @return the result, or empty when {@code e} holds a derivative or an indefinite integral in
   *     {@code x} that depends on it
   */
  static Optional<Expr> of(Expr e, Symbol x, Expr v, MathContext mc) {
    Expr out = replace(e, x, v);
    return out == null ? Optional.empty() : Optional.of(Canonical.of(out, mc));
  }

  /**
   * The value of {@code e} at {@code x = v}: the substitution, the builtin functions of numbers in
   * it evaluated through the context's numerics ({@link mathcanvas.engine.rule.Numerics#exact}),
   * and the standard simplifier.
   *
   * @param e the expression
   * @param x the variable
   * @param v its value
   * @param ctx the context
   * @return the simplified value, or empty when the substitution fails
   */
  static Optional<Expr> value(Expr e, Symbol x, Expr v, Context ctx) {
    return of(e, x, v, ctx.precision())
        .map(s -> ctx.numerics().exact(s, ctx.precision()))
        .map(
            s ->
                Simplifier.simplify(Canonical.of(s, ctx.precision()), ctx.withTrace(false)).expr());
  }

  private static Expr replace(Expr e, Symbol x, Expr v) {
    if (e.equals(x)) {
      return v;
    }
    if (Free.of(e, x)) {
      return e;
    }
    if (e instanceof Derivative d && d.var().equals(x)
        || e instanceof Integral i && i.var().equals(x) && !i.isDefinite()) {
      return null;
    }
    if (e instanceof Lambda l && l.params().contains(x)) {
      return e;
    }
    List<Expr> ops = Operands.of(e);
    List<Expr> out = new ArrayList<>(ops.size());
    for (int i = 0; i < ops.size(); i++) {
      if (Operands.isBound(e, i)) {
        out.add(ops.get(i));
        continue;
      }
      if (i == 0 && binds(e, x)) {
        out.add(ops.get(i));
        continue;
      }
      Expr r = replace(ops.get(i), x, v);
      if (r == null) {
        return null;
      }
      out.add(r);
    }
    return Operands.rebuild(e, out);
  }

  /** Whether {@code e} is a binder whose body (operand 0) binds {@code x}. */
  private static boolean binds(Expr e, Symbol x) {
    List<Expr> ops = Operands.of(e);
    return ops.size() > 1 && Operands.isBound(e, 1) && ops.get(1).equals(x);
  }
}
