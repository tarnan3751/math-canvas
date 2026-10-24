package mathcanvas.engine.rule;

import java.math.MathContext;
import java.util.Objects;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Canonical;

/**
 * What a rewrite runs under.
 *
 * @param assumptions the facts rule conditions consult
 * @param precision the context inexact arithmetic is rounded to, in the canonical form too
 * @param traceEnabled whether the rewrites are recorded
 * @param rewriteCap the most rewrites one call may make
 */
public record Context(
    Assumptions assumptions, MathContext precision, boolean traceEnabled, int rewriteCap) {

  /** No assumptions, 34 digits, no trace, 10,000 rewrites. */
  public static final Context DEFAULT =
      new Context(Assumptions.EMPTY, Canonical.DEFAULT_CONTEXT, false, 10_000);

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if the assumptions or the precision is null
   * @throws IllegalArgumentException if the cap is below 1
   */
  public Context {
    Objects.requireNonNull(assumptions, "assumptions");
    Objects.requireNonNull(precision, "precision");
    if (rewriteCap < 1) {
      throw new IllegalArgumentException("the rewrite cap is at least 1: " + rewriteCap);
    }
  }

  /**
   * Returns this context with other assumptions.
   *
   * @param a the assumptions
   * @return the context
   */
  public Context withAssumptions(Assumptions a) {
    return new Context(a, precision, traceEnabled, rewriteCap);
  }

  /**
   * Returns this context with the trace on or off.
   *
   * @param enabled whether to record rewrites
   * @return the context
   */
  public Context withTrace(boolean enabled) {
    return new Context(assumptions, precision, enabled, rewriteCap);
  }

  /**
   * Returns this context with another cap.
   *
   * @param cap the most rewrites one call may make
   * @return the context
   */
  public Context withRewriteCap(int cap) {
    return new Context(assumptions, precision, traceEnabled, cap);
  }
}
