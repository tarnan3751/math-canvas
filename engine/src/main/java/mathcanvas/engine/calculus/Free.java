package mathcanvas.engine.calculus;

import mathcanvas.engine.builtin.Builtins;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Piecewise;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Operands;

/**
 * Whether an expression is free of a variable: its value does not depend on it. A query over the
 * tree, not a rewrite. A binder whose variable is {@code x} hides {@code x} in its body (a lambda,
 * Σ, Π, a limit, a definite integral), while its bounds and point are outside that scope. A
 * derivative's variable is free in its value ({@code d/dx x²} is {@code 2x}), so a derivative is
 * free when its expression is; an indefinite integral in {@code x} is never free of {@code x}. A
 * command left as a call with a variable of its own (a held {@code solve}) is free only when {@code
 * x} occurs nowhere in it.
 */
final class Free {

  private Free() {}

  /**
   * Whether {@code e} is free of {@code x}.
   *
   * @param e the expression
   * @param x the variable
   * @return true if the value of {@code e} does not depend on {@code x}
   */
  static boolean of(Expr e, Symbol x) {
    return switch (e) {
      case Num _, Constant _, Bool _, mathcanvas.engine.expr.Unit _ -> true;
      case Symbol s -> !s.equals(x);
      case Sum _,
          Product _,
          Power _,
          Relation _,
          ListExpr _,
          Vector _,
          SetExpr _,
          Matrix _,
          Interval _,
          Piecewise _ ->
          operands(e, x);
      case Apply a ->
          Builtins.lookup(a.head().name()).map(b -> b.binder().isPresent()).orElse(false)
              ? !occurs(a, x)
              : operands(a, x);
      case Lambda l -> l.params().contains(x) || of(l.body(), x);
      case Derivative d -> of(d.expr(), x);
      case Integral i ->
          i.isDefinite()
              ? of(i.lower(), x) && of(i.upper(), x) && (i.var().equals(x) || of(i.integrand(), x))
              : !i.var().equals(x) && of(i.integrand(), x);
      case Limit l -> of(l.point(), x) && (l.var().equals(x) || of(l.expr(), x));
      case BigSum s -> of(s.from(), x) && of(s.to(), x) && (s.var().equals(x) || of(s.body(), x));
      case BigProduct p ->
          of(p.from(), x) && of(p.to(), x) && (p.var().equals(x) || of(p.body(), x));
    };
  }

  private static boolean operands(Expr e, Symbol x) {
    for (Expr o : Operands.of(e)) {
      if (!of(o, x)) {
        return false;
      }
    }
    return true;
  }

  /** Whether {@code x} occurs anywhere in {@code e}, bound or free. */
  private static boolean occurs(Expr e, Symbol x) {
    if (e.equals(x)) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (occurs(o, x)) {
        return true;
      }
    }
    return false;
  }
}
