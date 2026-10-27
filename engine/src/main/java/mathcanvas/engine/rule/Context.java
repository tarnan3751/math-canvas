package mathcanvas.engine.rule;

import java.math.MathContext;
import java.util.Objects;
import mathcanvas.engine.Cancellation;
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
 * @param numerics the numeric values of closed expressions, for the operations that need them
 * @param cancellation the request to stop, checked at every loop that can run long
 */
public record Context(
    Assumptions assumptions,
    MathContext precision,
    boolean traceEnabled,
    int rewriteCap,
    Providers providers,
    Numerics numerics,
    Cancellation cancellation) {

  /**
   * No assumptions, 34 digits, no trace, 10,000 rewrites, the default providers, no numeric values,
   * never cancelled.
   */
  public static final Context DEFAULT =
      new Context(Assumptions.EMPTY, Canonical.DEFAULT_CONTEXT, false, 10_000);

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if the assumptions, the precision, the providers, the numerics or
   *     the cancellation are null
   * @throws IllegalArgumentException if the cap is below 1
   */
  public Context {
    Objects.requireNonNull(assumptions, "assumptions");
    Objects.requireNonNull(precision, "precision");
    Objects.requireNonNull(providers, "providers");
    Objects.requireNonNull(numerics, "numerics");
    Objects.requireNonNull(cancellation, "cancellation");
    if (rewriteCap < 1) {
      throw new IllegalArgumentException("the rewrite cap is at least 1: " + rewriteCap);
    }
  }

  /**
   * A context that is never cancelled ({@link Cancellation#NEVER}).
   *
   * @param assumptions the facts rule conditions consult
   * @param precision the context inexact arithmetic is rounded to
   * @param traceEnabled whether the rewrites are recorded
   * @param rewriteCap the most rewrites one call may make
   * @param providers the providers of the heavyweight operations
   * @param numerics the numeric values of closed expressions
   */
  public Context(
      Assumptions assumptions,
      MathContext precision,
      boolean traceEnabled,
      int rewriteCap,
      Providers providers,
      Numerics numerics) {
    this(assumptions, precision, traceEnabled, rewriteCap, providers, numerics, Cancellation.NEVER);
  }

  /**
   * A context without numeric values ({@link Numerics#NONE}).
   *
   * @param assumptions the facts rule conditions consult
   * @param precision the context inexact arithmetic is rounded to
   * @param traceEnabled whether the rewrites are recorded
   * @param rewriteCap the most rewrites one call may make
   * @param providers the providers of the heavyweight operations
   */
  public Context(
      Assumptions assumptions,
      MathContext precision,
      boolean traceEnabled,
      int rewriteCap,
      Providers providers) {
    this(assumptions, precision, traceEnabled, rewriteCap, providers, Numerics.NONE);
  }

  /**
   * A context with the default providers ({@link Providers#defaults()}) and no numeric values.
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
    return new Context(a, precision, traceEnabled, rewriteCap, providers, numerics, cancellation);
  }

  /**
   * Returns this context with the trace on or off.
   *
   * @param enabled whether to record rewrites
   * @return the context
   */
  public Context withTrace(boolean enabled) {
    return new Context(
        assumptions, precision, enabled, rewriteCap, providers, numerics, cancellation);
  }

  /**
   * Returns this context with another cap.
   *
   * @param cap the most rewrites one call may make
   * @return the context
   */
  public Context withRewriteCap(int cap) {
    return new Context(
        assumptions, precision, traceEnabled, cap, providers, numerics, cancellation);
  }

  /**
   * Returns this context with other providers.
   *
   * @param p the providers
   * @return the context
   */
  public Context withProviders(Providers p) {
    return new Context(assumptions, precision, traceEnabled, rewriteCap, p, numerics, cancellation);
  }

  /**
   * Returns this context with other numeric values.
   *
   * @param n the numerics
   * @return the context
   */
  public Context withNumerics(Numerics n) {
    return new Context(
        assumptions, precision, traceEnabled, rewriteCap, providers, n, cancellation);
  }

  /**
   * Returns this context with another request to stop.
   *
   * @param c the cancellation
   * @return the context
   */
  public Context withCancellation(Cancellation c) {
    return new Context(assumptions, precision, traceEnabled, rewriteCap, providers, numerics, c);
  }
}
