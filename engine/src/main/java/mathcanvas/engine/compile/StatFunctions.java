package mathcanvas.engine.compile;

import mathcanvas.engine.provider.Distribute.Law;
import mathcanvas.engine.stat.numeric.DistributionMath;
import org.hipparchus.exception.MathRuntimeException;

/**
 * The special functions, the densities and the cumulative functions of the statistics in {@code
 * double}, for the compiled evaluators: {@link DistributionMath}, the provider's own functions,
 * with the arguments the language takes ({@code normalpdf(x[, μ[, σ]])}, {@code tcdf(a, b, ν)}, …).
 * A NaN argument, a parameter outside its law's domain, or an iteration of Hipparchus that does not
 * converge gives NaN; a cumulative function of bounds in the wrong order is negative, as the
 * engine's is.
 */
final class StatFunctions {

  private StatFunctions() {}

  /** A function of the compiled arguments. */
  @FunctionalInterface
  interface Fn {
    /**
     * The value.
     *
     * @param v the arguments' values
     * @return the value, NaN where there is none
     */
    double apply(double[] v);
  }

  /**
   * The function of a builtin, or null when it is not one of these.
   *
   * @param name the builtin's name
   * @return the function
   */
  static Fn of(String name) {
    return switch (name) {
      case "erf" -> v -> DistributionMath.erf(v[0]);
      case "erfc" -> v -> DistributionMath.erfc(v[0]);
      case "gamma" -> v -> DistributionMath.gamma(v[0]);
      case "beta" -> v -> DistributionMath.beta(v[0], v[1]);
      case "normalpdf" -> v -> density(Law.NORMAL, v[0], normal(v, 1));
      case "tpdf" -> v -> density(Law.STUDENT_T, v[0], new double[] {v[1]});
      case "chi2pdf" -> v -> density(Law.CHI_SQUARED, v[0], new double[] {v[1]});
      case "fpdf" -> v -> density(Law.F, v[0], new double[] {v[1], v[2]});
      case "exppdf" -> v -> density(Law.EXPONENTIAL, v[0], new double[] {v[1]});
      case "unifpdf" -> v -> density(Law.UNIFORM, v[0], new double[] {v[1], v[2]});
      case "normalcdf" -> v -> probability(Law.NORMAL, v[0], v[1], normal(v, 2));
      case "tcdf" -> v -> probability(Law.STUDENT_T, v[0], v[1], new double[] {v[2]});
      case "chi2cdf" -> v -> probability(Law.CHI_SQUARED, v[0], v[1], new double[] {v[2]});
      case "fcdf" -> v -> probability(Law.F, v[0], v[1], new double[] {v[2], v[3]});
      case "expcdf" -> v -> probability(Law.EXPONENTIAL, v[0], v[1], new double[] {v[2]});
      case "unifcdf" -> v -> probability(Law.UNIFORM, v[0], v[1], new double[] {v[2], v[3]});
      default -> null;
    };
  }

  /**
   * The normal's {@code μ} and {@code σ} from the arguments after {@code skip}, 0 and 1 by default.
   */
  private static double[] normal(double[] v, int skip) {
    return new double[] {v.length > skip ? v[skip] : 0.0, v.length > skip + 1 ? v[skip + 1] : 1.0};
  }

  private static boolean inDomain(Law law, double[] p) {
    for (double x : p) {
      if (!Double.isFinite(x)) {
        return false;
      }
    }
    return switch (law) {
      case NORMAL -> p[1] > 0;
      case STUDENT_T, CHI_SQUARED, EXPONENTIAL -> p[0] > 0;
      case F -> p[0] > 0 && p[1] > 0;
      case UNIFORM -> p[0] < p[1];
      default -> false;
    };
  }

  private static double density(Law law, double x, double[] p) {
    if (Double.isNaN(x) || !inDomain(law, p)) {
      return Double.NaN;
    }
    if (Double.isInfinite(x)) {
      return 0.0;
    }
    try {
      return DistributionMath.density(law, p, x);
    } catch (MathRuntimeException failed) {
      return Double.NaN;
    }
  }

  private static double probability(Law law, double a, double b, double[] p) {
    if (Double.isNaN(a) || Double.isNaN(b) || !inDomain(law, p)) {
      return Double.NaN;
    }
    try {
      return a <= b
          ? DistributionMath.probability(law, p, a, b)
          : -DistributionMath.probability(law, p, b, a);
    } catch (MathRuntimeException failed) {
      return Double.NaN;
    }
  }
}
