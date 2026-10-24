package mathcanvas.engine.calculus;

import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;
import mathcanvas.engine.simplify.Simplifier;

/** The calculus operations: differentiation. */
public final class Calculus {

  private Calculus() {}

  /**
   * The {@code n}-th derivative of {@code e} in {@code x}: {@link RuleSets#DIFF} applied to {@code
   * Derivative(e, x, n)}, then the standard simplifier once. What cannot be differentiated stays a
   * {@code Derivative} in the result. With tracing on, the trace is the differentiation's rewrites
   * followed by the simplifier's ({@link Trace#concat}).
   *
   * @param e the expression
   * @param x the variable
   * @param n the order, at least 1
   * @param context the assumptions, precision, trace switch and cap, for each of the two runs
   * @return the canonical result and its trace
   * @throws IllegalArgumentException if {@code n} is below 1
   * @throws mathcanvas.engine.rule.RewriteLimitException if either run needs more rewrites than the
   *     cap
   */
  public static Rewriter.Result differentiate(Expr e, Symbol x, int n, Context context) {
    Rewriter.Result d = Rewriter.rewrite(Derivative.of(e, x, n), RuleSets.DIFF, context);
    Rewriter.Result s = Simplifier.simplify(d.expr(), context);
    Trace trace = context.traceEnabled() ? Trace.concat(d.trace(), s.trace()) : Trace.NONE;
    return new Rewriter.Result(s.expr(), trace);
  }
}
