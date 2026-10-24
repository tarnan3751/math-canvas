package mathcanvas.engine.rule;

import java.math.MathContext;
import java.util.Objects;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.provider.Providers;

/**
 * What a rewrite runs under.
 *
 * @param assumptions the facts rule conditions consult
 * @param precision the context inexact arithmetic is rounded to, in the canonical form too
 * @param traceEnabled whether the rewrites are recorded
 * @param rewriteCap the most rewrites one call may make
 * @param providers the providers of the heavyweight operations (factor, gcd, …)
 */
public record Context(
    Assumptions assumptions,
    MathContext precision,
    boolean traceEnabled,
    int rewriteCap,
    Providers providers) {

  /** No assumptions, 34 digits, no trace, 10,000 rewrites, the default providers. */
  public static final Context DEFAULT =
      new Context(Assumptions.EMPTY, Canonical.DEFAULT_CONTEXT, false, 10_000);

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if the assumptions, the precision or the providers are null
   * @throws IllegalArgumentException if the cap is below 1
   */
  public Context {
    Objects.requireNonNull(assumptions, "assumptions");
    Objects.requireNonNull(precision, "precision");
    Objects.requireNonNull(providers, "providers");
    if (rewriteCap < 1) {
      throw new IllegalArgumentException("the rewrite cap is at least 1: " + rewriteCap);
    }
  }

  /**
   * A context with the default providers ({@link Providers#defaults()}).
   *
   * @param assumptions the facts rule conditions consult
   * @param precision the context inexact arithmetic is rounded to
   * @param traceEnabled whether the rewrites are recorded
   * @param rewriteCap the most rewrites one call may make
   */
  public Context(
      Assumptions assumptions, MathContext precision, boolean traceEnabled, int rewriteCap) {
    this(assumptions, precision, traceEnabled, rewriteCap, Providers.defaults());
  }

  /**
   * Returns this context with other assumptions.
   *
   * @param a the assumptions
   * @return the context
   */
  public Context withAssumptions(Assumptions a) {
    return new Context(a, precision, traceEnabled, rewriteCap, providers);
  }

  /**
   * Returns this context with the trace on or off.
   *
   * @param enabled whether to record rewrites
   * @return the context
   */
  public Context withTrace(boolean enabled) {
    return new Context(assumptions, precision, enabled, rewriteCap, providers);
  }

  /**
   * Returns this context with another cap.
   *
   * @param cap the most rewrites one call may make
   * @return the context
   */
  public Context withRewriteCap(int cap) {
    return new Context(assumptions, precision, traceEnabled, cap, providers);
  }

  /**
   * Returns this context with other providers.
   *
   * @param p the providers
   * @return the context
   */
  public Context withProviders(Providers p) {
    return new Context(assumptions, precision, traceEnabled, rewriteCap, p);
  }
}
