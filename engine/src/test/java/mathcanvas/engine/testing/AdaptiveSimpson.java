package mathcanvas.engine.testing;

import java.util.function.DoubleUnaryOperator;

/**
 * Adaptive Simpson quadrature in {@code double}, the test-only reference the definite-integral
 * properties compare with until the engine's own quadrature lands: each interval is halved until
 * the two Simpson estimates on its halves agree with the whole one within fifteen times its share
 * of the tolerance, or within rounding of their size, then Richardson's correction is added. The
 * tolerance is relative to the integral of {@code |f|}, estimated first by a composite rule, and at
 * most {@value #MAX_EVALUATIONS} evaluations are made.
 */
public final class AdaptiveSimpson {

  private AdaptiveSimpson() {}

  /** The deepest halving. */
  private static final int MAX_DEPTH = 40;

  /** The most evaluations of the integrand. */
  public static final int MAX_EVALUATIONS = 2_000_000;

  /**
   * An estimate.
   *
   * @param value the integral of {@code f}
   * @param magnitude the integral of {@code |f|}, its scale
   */
  public record Estimate(double value, double magnitude) {}

  /**
   * The integral of {@code f} over {@code [a, b]}.
   *
   * @param f the integrand, finite and smooth on the interval
   * @param a the lower end
   * @param b the upper end
   * @param relative the tolerance, relative to the integral of {@code |f|}
   * @return the estimate and the integral of {@code |f|}
   * @throws IllegalStateException past {@value #MAX_EVALUATIONS} evaluations
   */
  public static Estimate integrate(DoubleUnaryOperator f, double a, double b, double relative) {
    int n = 2_000;
    double h = (b - a) / n;
    double magnitude = 0;
    for (int k = 0; k <= n; k++) {
      double w = k == 0 || k == n ? 1 : k % 2 == 1 ? 4 : 2;
      magnitude += w * Math.abs(f.applyAsDouble(a + k * h));
    }
    magnitude = Math.abs(magnitude * h / 3);
    int[] count = {0};
    double fa = f.applyAsDouble(a);
    double fb = f.applyAsDouble(b);
    double fm = f.applyAsDouble((a + b) / 2);
    double tolerance = relative * Math.max(magnitude, Double.MIN_NORMAL);
    double value =
        step(f, a, b, fa, fm, fb, simpson(a, b, fa, fm, fb), tolerance, MAX_DEPTH, count);
    return new Estimate(value, magnitude);
  }

  private static double simpson(double a, double b, double fa, double fm, double fb) {
    return (b - a) / 6 * (fa + 4 * fm + fb);
  }

  private static double step(
      DoubleUnaryOperator f,
      double a,
      double b,
      double fa,
      double fm,
      double fb,
      double whole,
      double tolerance,
      int depth,
      int[] count) {
    if ((count[0] += 2) > MAX_EVALUATIONS) {
      throw new IllegalStateException("adaptive Simpson did not converge");
    }
    double m = (a + b) / 2;
    double flm = f.applyAsDouble((a + m) / 2);
    double frm = f.applyAsDouble((m + b) / 2);
    double left = simpson(a, m, fa, flm, fm);
    double right = simpson(m, b, fm, frm, fb);
    double delta = left + right - whole;
    if (depth <= 0
        || Math.abs(delta) <= 15 * tolerance
        || Math.abs(delta) <= 1e-15 * (Math.abs(left) + Math.abs(right))) {
      return left + right + delta / 15;
    }
    return step(f, a, m, fa, flm, fm, left, tolerance / 2, depth - 1, count)
        + step(f, m, b, fm, frm, fb, right, tolerance / 2, depth - 1, count);
  }
}
