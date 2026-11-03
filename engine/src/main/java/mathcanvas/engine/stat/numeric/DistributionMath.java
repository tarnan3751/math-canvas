package mathcanvas.engine.stat.numeric;

import mathcanvas.engine.provider.Distribute.Law;
import org.hipparchus.analysis.UnivariateFunction;
import org.hipparchus.analysis.solvers.AllowedSolution;
import org.hipparchus.analysis.solvers.BracketingNthOrderBrentSolver;
import org.hipparchus.distribution.continuous.FDistribution;
import org.hipparchus.distribution.continuous.NormalDistribution;
import org.hipparchus.distribution.continuous.TDistribution;
import org.hipparchus.distribution.discrete.BinomialDistribution;
import org.hipparchus.distribution.discrete.GeometricDistribution;
import org.hipparchus.distribution.discrete.HypergeometricDistribution;
import org.hipparchus.distribution.discrete.PoissonDistribution;
import org.hipparchus.exception.LocalizedCoreFormats;
import org.hipparchus.exception.MathIllegalStateException;
import org.hipparchus.special.Beta;
import org.hipparchus.special.Erf;
import org.hipparchus.special.Gamma;
import org.hipparchus.util.ContinuedFraction;

/**
 * The values of the distributions and the special functions in {@code double}, shared by the {@link
 * HipparchusDistributions} provider and the compiled evaluators. Every function takes parameters
 * already in the law's domain ({@link mathcanvas.engine.provider.Distribute} checks them) and may
 * return NaN or an infinity, which its caller reports.
 *
 * <p>The densities are Hipparchus's, but for χ², whose closed form is taken in logarithms
 * (Hipparchus's loses {@code k = 1} near 0), and for the value at 0 of the χ² and F densities,
 * which is finite only for {@code k ≥ 2} ({@code d1 ≥ 2}) and taken as NaN below. The probabilities
 * are computed from the tail that keeps their digits: {@code P(X ≤ x)} and {@code P(X > x)} each
 * directly through the special functions (the normal's by {@code erfc}, Student's t and F by the
 * regularised incomplete beta function, χ² by the regularised incomplete gamma functions), and an
 * interval above the median as the difference of its upper tails, so {@code P(X > 6)} of a standard
 * normal is not lost in {@code 1 − 0.999999999}; the continued fractions of the incomplete beta and
 * gamma functions run to a relative {@value #EPSILON} (Hipparchus's default, {@code 1e-14}, leaves
 * the last digits of a quantile wrong). A quantile is found on the tail that holds its digits, the
 * lower one for {@code q ≤ 1/2} and the upper one, at {@code 1 − q}, above: the normal's as {@code
 * −√2·erfc⁻¹(2q)} (or its mirror), with {@code erfc⁻¹} refined by Newton's method on {@code erfc},
 * whose relative accuracy holds in the tail where Hipparchus's {@code erfcInv(y) = erfInv(1 − y)}
 * loses it; those of t, χ² and F by a Brent search to a relative accuracy in {@code x} on that
 * tail. Every one is then checked, {@code |F(x) − q| ≤} {@value #QUANTILE_CHECK}{@code ·min(q, 1 −
 * q)}, and a failed check is a {@link MathIllegalStateException}, which the provider reports as no
 * convergence. The geometric law counts trials ({@code 1, 2, …}), where Hipparchus counts failures.
 * A geometric law with {@code p = 1} is the trial 1 for certain.
 */
public final class DistributionMath {

  /**
   * The relative accuracy of the continued fractions of the incomplete beta and gamma functions.
   */
  public static final double EPSILON = 1e-15;

  /** The relative gap {@code |F(x) − q|/min(q, 1 − q)} a quantile {@code x} is checked to. */
  public static final double QUANTILE_CHECK = 1e-12;

  /**
   * The relative accuracy in {@code x} of the search for a quantile: two units in the last place.
   */
  private static final double SEARCH_ACCURACY = 2 * Math.ulp(1.0);

  /**
   * How far past Hipparchus's crossover the incomplete beta function's continued fraction is run on
   * the side whose first parameter is the smaller.
   */
  private static final double CROSSOVER = 2;

  /** The ratio of the incomplete beta function's parameters from which the crossover moves. */
  private static final double SKEW = 10_000;

  /** The evaluations the search for a quantile may make. */
  private static final int SEARCH_EVALUATIONS = 1000;

  private static final double SQRT2 = Math.sqrt(2);

  /** The standard normal's parameters. */
  private static final double[] STANDARD = {0, 1};

  private DistributionMath() {}

  /**
   * The density at {@code x}, or the probability of {@code x} for a discrete law (0 off the
   * integers).
   *
   * @param law a distribution
   * @param p its parameters
   * @param x the point
   * @return the density
   */
  public static double density(Law law, double[] p, double x) {
    return switch (law) {
      case NORMAL -> new NormalDistribution(p[0], p[1]).density(x);
      case STUDENT_T -> new TDistribution(p[0]).density(x);
      case CHI_SQUARED -> x == 0 ? atZero(p[0], 0.5) : x < 0 ? 0.0 : chiSquaredDensity(p[0], x);
      case F -> x == 0 ? atZero(p[0], 1.0) : x < 0 ? 0.0 : new FDistribution(p[0], p[1]).density(x);
      case EXPONENTIAL -> x < 0 ? 0.0 : p[0] * Math.exp(-p[0] * x);
      case UNIFORM -> x < p[0] || x > p[1] ? 0.0 : 1.0 / (p[1] - p[0]);
      case BINOMIAL ->
          integer(x) ? new BinomialDistribution((int) p[0], p[1]).probability((int) x) : 0.0;
      case POISSON -> integer(x) && x >= 0 ? new PoissonDistribution(p[0]).probability((int) x) : 0;
      case GEOMETRIC ->
          !integer(x) || x < 1
              ? 0.0
              : p[0] == 1
                  ? (x == 1 ? 1.0 : 0.0)
                  : new GeometricDistribution(p[0]).probability((int) x - 1);
      case HYPERGEOMETRIC ->
          integer(x)
              ? new HypergeometricDistribution((int) p[0], (int) p[1], (int) p[2])
                  .probability((int) x)
              : 0.0;
      case ERF, ERFC, GAMMA, BETA -> throw new IllegalArgumentException(law + " has no density");
    };
  }

  /**
   * The χ² density {@code x^(k/2 − 1) e^(−x/2)/(2^(k/2) Γ(k/2))} at {@code x > 0}, in logarithms:
   * Hipparchus's loses {@code k = 1} near 0 ({@code 0.0} at {@code 1e-20}, against {@code 3.99e9}).
   */
  private static double chiSquaredDensity(double k, double x) {
    double h = k / 2;
    return Math.exp((h - 1) * Math.log(x) - x / 2 - h * Math.log(2) - Gamma.logGamma(h));
  }

  /** The χ² or F density at 0: finite only from 2 degrees of freedom, {@code at2} at 2. */
  private static double atZero(double k, double at2) {
    return k > 2 ? 0.0 : k == 2 ? at2 : Double.NaN;
  }

  /**
   * {@code P(a < X ≤ b)} of a continuous law, {@code P(a ≤ X ≤ b)} of a discrete one, for {@code a
   * ≤ b}, either infinite.
   *
   * @param law a distribution
   * @param p its parameters
   * @param a the lower end
   * @param b the upper end
   * @return the probability
   */
  public static double probability(Law law, double[] p, double a, double b) {
    if (!law.continuous()) {
      double lo = Math.ceil(a);
      double hi = Math.floor(b);
      if (lo > hi) {
        return 0.0;
      }
      return discreteAtMost(law, p, hi) - discreteAtMost(law, p, lo - 1);
    }
    if (a == b) {
      return 0.0;
    }
    // above the median, the difference of the upper tails keeps the digits
    if (lower(law, p, a) > 0.5) {
      return upper(law, p, a) - upper(law, p, b);
    }
    return lower(law, p, b) - lower(law, p, a);
  }

  private static double discreteAtMost(Law law, double[] p, double k) {
    if (k == Double.NEGATIVE_INFINITY) {
      return 0.0;
    }
    int at = (int) Math.max(Integer.MIN_VALUE + 1.0, Math.min(Integer.MAX_VALUE - 1.0, k));
    return switch (law) {
      case BINOMIAL -> new BinomialDistribution((int) p[0], p[1]).cumulativeProbability(at);
      case POISSON -> at < 0 ? 0.0 : new PoissonDistribution(p[0]).cumulativeProbability(at);
      case GEOMETRIC ->
          at < 1
              ? 0.0
              : p[0] == 1 ? 1.0 : new GeometricDistribution(p[0]).cumulativeProbability(at - 1);
      case HYPERGEOMETRIC ->
          new HypergeometricDistribution((int) p[0], (int) p[1], (int) p[2])
              .cumulativeProbability(at);
      default -> throw new IllegalArgumentException(law + " is not discrete");
    };
  }

  /**
   * {@code P(X ≤ x)} of a continuous law.
   *
   * @param law a continuous distribution
   * @param p its parameters
   * @param x the point, possibly infinite
   * @return the probability
   */
  public static double lower(Law law, double[] p, double x) {
    if (x == Double.NEGATIVE_INFINITY) {
      return 0.0;
    }
    if (x == Double.POSITIVE_INFINITY) {
      return 1.0;
    }
    return switch (law) {
      case NORMAL -> 0.5 * Erf.erfc(-(x - p[0]) / (p[1] * Math.sqrt(2)));
      case STUDENT_T -> x < 0 ? studentUpper(p[0], -x) : 1 - studentUpper(p[0], x);
      case CHI_SQUARED ->
          x <= 0 ? 0.0 : Gamma.regularizedGammaP(p[0] / 2, x / 2, EPSILON, Integer.MAX_VALUE);
      case F -> x <= 0 ? 0.0 : fisher(p, x, false);
      case EXPONENTIAL -> x <= 0 ? 0.0 : -Math.expm1(-p[0] * x);
      case UNIFORM -> x <= p[0] ? 0.0 : x >= p[1] ? 1.0 : (x - p[0]) / (p[1] - p[0]);
      default -> throw new IllegalArgumentException(law + " is not continuous");
    };
  }

  /**
   * {@code P(X > x)} of a continuous law.
   *
   * @param law a continuous distribution
   * @param p its parameters
   * @param x the point, possibly infinite
   * @return the probability
   */
  public static double upper(Law law, double[] p, double x) {
    if (x == Double.NEGATIVE_INFINITY) {
      return 1.0;
    }
    if (x == Double.POSITIVE_INFINITY) {
      return 0.0;
    }
    return switch (law) {
      case NORMAL -> 0.5 * Erf.erfc((x - p[0]) / (p[1] * Math.sqrt(2)));
      case STUDENT_T -> x >= 0 ? studentUpper(p[0], x) : 1 - studentUpper(p[0], -x);
      case CHI_SQUARED ->
          x <= 0 ? 1.0 : Gamma.regularizedGammaQ(p[0] / 2, x / 2, EPSILON, Integer.MAX_VALUE);
      case F -> x <= 0 ? 1.0 : fisher(p, x, true);
      case EXPONENTIAL -> x <= 0 ? 1.0 : Math.exp(-p[0] * x);
      case UNIFORM -> x <= p[0] ? 1.0 : x >= p[1] ? 0.0 : (p[1] - x) / (p[1] - p[0]);
      default -> throw new IllegalArgumentException(law + " is not continuous");
    };
  }

  /**
   * {@code P(T > t)} for {@code t ≥ 0}: half the regularised beta {@code I_{ν/(ν+t²)}(ν/2, 1/2)},
   * its argument and the complement {@code t²/(ν + t²)} each computed directly.
   */
  private static double studentUpper(double nu, double t) {
    double t2 = t * t;
    if (Double.isInfinite(t2)) {
      return 0.0;
    }
    double d = nu + t2;
    return 0.5 * regularizedBeta(nu / d, t2 / d, nu / 2, 0.5);
  }

  /**
   * {@code P(X ≤ x)}, or {@code P(X > x)} for the upper tail, of F with {@code d1, d2} degrees of
   * freedom at {@code x > 0}: {@code I_{d1 x/(d1 x + d2)}(d1/2, d2/2)}, the argument and its
   * complement {@code d2/(d1 x + d2)} each computed directly.
   */
  private static double fisher(double[] p, double x, boolean upper) {
    double scaled = p[0] * x;
    if (Double.isInfinite(scaled)) {
      return upper ? 0.0 : 1.0;
    }
    double d = scaled + p[1];
    double lo = scaled / d;
    double hi = p[1] / d;
    return upper
        ? regularizedBeta(hi, lo, p[1] / 2, p[0] / 2)
        : regularizedBeta(lo, hi, p[0] / 2, p[1] / 2);
  }

  /**
   * The regularised incomplete beta function {@code I_x(a, b)}, given {@code x} and its complement
   * {@code y = 1 − x} each computed directly, so that an argument near 1 keeps the digits {@code 1
   * − x} would lose at large {@code a} (Student's t at large degrees of freedom). It is the
   * continued fraction of Hipparchus's {@code Beta.regularizedBeta} (DLMF 8.17.22), its prefactor's
   * logarithms taken from whichever of {@code x} and {@code y} is the smaller. Where one parameter
   * is at most 1 and the other at least {@value #SKEW} times it (Student's t at large degrees of
   * freedom is {@code I(ν/2, 1/2)}), the fraction is run on the side whose first parameter is the
   * small one further than Hipparchus's crossover {@code (a + 1)/(a + b + 2)} (to {@value
   * #CROSSOVER} times it), where the other side's fraction has lost digits.
   */
  private static double regularizedBeta(double x, double y, double a, double b) {
    if (x <= 0) {
      return 0.0;
    }
    if (y <= 0) {
      return 1.0;
    }
    double direct = (a + 1) / (a + b + 2);
    double complement = (b + 1) / (a + b + 2);
    boolean flip =
        b <= 1 && a >= SKEW * b
            ? y < CROSSOVER * complement
            : a <= 1 && b >= SKEW * a ? x >= CROSSOVER * direct : x > direct;
    return flip ? 1 - betaFraction(y, x, b, a) : betaFraction(x, y, a, b);
  }

  /** {@code I_x(a, b)} by its continued fraction, which converges fast below the crossover. */
  private static double betaFraction(double x, double y, double a, double b) {
    ContinuedFraction fraction =
        new ContinuedFraction() {
          @Override
          protected double getB(int n, double z) {
            if (n % 2 == 0) {
              double m = n / 2.0;
              return m * (b - m) * z / ((a + 2 * m - 1) * (a + 2 * m));
            }
            double m = (n - 1) / 2.0;
            return -((a + m) * (a + b + m) * z) / ((a + 2 * m) * (a + 2 * m + 1));
          }

          @Override
          protected double getA(int n, double z) {
            return 1.0;
          }
        };
    double logX = x < 0.5 ? Math.log(x) : Math.log1p(-y);
    double logY = y < 0.5 ? Math.log(y) : Math.log1p(-x);
    return Math.exp(a * logX + b * logY - Math.log(a) - Beta.logBeta(a, b))
        / fraction.evaluate(x, EPSILON, Integer.MAX_VALUE);
  }

  /**
   * The quantile of a tail of a continuous law: the {@code x} with {@code P(X ≤ x) = tail}, or with
   * {@code P(X > x) = tail} for the upper one. The caller gives the smaller tail, formed exactly,
   * so that a quantile above the median keeps the digits {@code 1 − q} in {@code double} would
   * lose.
   *
   * @param law a continuous distribution
   * @param p its parameters
   * @param tail the probability of the tail, in {@code [0, 1]}
   * @param upper whether the tail is the upper one
   * @return the quantile, infinite at an end of an unbounded support
   * @throws MathIllegalStateException if the tail is below {@code Double.MIN_NORMAL}, where it has
   *     lost digits, or the quantile found fails its check
   */
  public static double inverse(Law law, double[] p, double tail, boolean upper) {
    if (tail == 0 || tail == 1) {
      return upper == (tail == 0) ? top(law, p) : bottom(law, p);
    }
    if (tail < Double.MIN_NORMAL) {
      // a subnormal tail has lost digits of its own: no quantile is found to the accuracy
      throw new MathIllegalStateException(LocalizedCoreFormats.CONVERGENCE_FAILED);
    }
    return switch (law) {
      // the standard quantile is checked, before μ + σz rounds it
      case NORMAL ->
          p[0]
              + p[1]
                  * checked(
                      law, STANDARD, tail, upper, (upper ? SQRT2 : -SQRT2) * erfcInverse(2 * tail));
      case STUDENT_T, CHI_SQUARED, F -> checked(law, p, tail, upper, search(law, p, tail, upper));
      case EXPONENTIAL -> (upper ? -Math.log(tail) : -Math.log1p(-tail)) / p[0];
      case UNIFORM -> upper ? p[1] - tail * (p[1] - p[0]) : p[0] + tail * (p[1] - p[0]);
      default -> throw new IllegalArgumentException(law + " has no inverse here");
    };
  }

  /** The lower end of a continuous law's support. */
  private static double bottom(Law law, double[] p) {
    return switch (law) {
      case NORMAL, STUDENT_T -> Double.NEGATIVE_INFINITY;
      case UNIFORM -> p[0];
      default -> 0.0;
    };
  }

  /** The upper end of a continuous law's support. */
  private static double top(Law law, double[] p) {
    return law == Law.UNIFORM ? p[1] : Double.POSITIVE_INFINITY;
  }

  /**
   * {@code erfc⁻¹(y)} for {@code 0 < y ≤ 1}: from Hipparchus's value, or from the asymptotic {@code
   * erfc(x) ≈ e^(−x²)/(x√π)} where {@code 1 − y} has lost the digits, refined by Newton's method on
   * {@code erfc}.
   */
  private static double erfcInverse(double y) {
    double x;
    if (y > 1e-8) {
      x = Erf.erfcInv(y);
    } else {
      double t = Math.sqrt(-Math.log(y));
      x = Math.sqrt(-Math.log(y * Math.sqrt(Math.PI) * t));
    }
    for (int i = 0; i < 100; i++) {
      double slope = 2 / Math.sqrt(Math.PI) * Math.exp(-x * x);
      if (slope == 0) {
        break;
      }
      double step = (Erf.erfc(x) - y) / slope;
      x += step;
      if (Math.abs(step) <= Math.ulp(x)) {
        break;
      }
    }
    return x;
  }

  /**
   * The quantile of t, χ² or F at a tail: the root of {@code P(X ≤ x) − tail}, or of {@code tail −
   * P(X > x)} for the upper one, bracketed by stepping out from 0 (t) or 1 (χ², F) in doubling
   * steps and found by Brent's method to {@link #SEARCH_ACCURACY} relative.
   */
  private static double search(Law law, double[] p, double tail, boolean upper) {
    UnivariateFunction g = x -> upper ? tail - upper(law, p, x) : lower(law, p, x) - tail;
    boolean real = law == Law.STUDENT_T;
    double b = real ? 0.0 : 1.0;
    double gb = g.value(b);
    boolean up = gb < 0;
    double a = b;
    while (gb != 0 && gb < 0 == up) {
      a = b;
      b = real && b == 0 ? (up ? 1.0 : -1.0) : up || real ? 2 * b : b / 2;
      if (Double.isInfinite(b)) {
        return b;
      }
      gb = g.value(b);
    }
    if (gb == 0) {
      return b;
    }
    return new BracketingNthOrderBrentSolver(SEARCH_ACCURACY, Double.MIN_NORMAL, 0.0, 5)
        .solve(SEARCH_EVALUATIONS, g, Math.min(a, b), Math.max(a, b), AllowedSolution.ANY_SIDE);
  }

  /** A quantile {@code x} of a tail, checked against that tail. */
  private static double checked(Law law, double[] p, double tail, boolean upper, double x) {
    if (Double.isInfinite(x)) {
      return x;
    }
    double gap = (upper ? upper(law, p, x) : lower(law, p, x)) - tail;
    if (!(Math.abs(gap) <= QUANTILE_CHECK * Math.min(tail, 1 - tail))) {
      throw new MathIllegalStateException(LocalizedCoreFormats.CONVERGENCE_FAILED);
    }
    return x;
  }

  /**
   * A special function's value: NaN at a pole of {@code Γ} or {@code B}.
   *
   * @param law {@code ERF}, {@code ERFC}, {@code GAMMA} or {@code BETA}
   * @param p the parameters: {@code BETA}'s second argument
   * @param x the argument
   * @return the value
   */
  public static double value(Law law, double[] p, double x) {
    return switch (law) {
      case ERF -> erf(x);
      case ERFC -> erfc(x);
      case GAMMA -> gamma(x);
      case BETA -> beta(x, p[0]);
      default -> throw new IllegalArgumentException(law + " is not a special function");
    };
  }

  /**
   * Whether a special function's value is not zero, so that a computed 0 is an underflow: {@code
   * erf} but at 0, {@code erfc} and {@code Γ} always, {@code B(a, b)} but where only {@code a + b}
   * is a pole of {@code Γ}.
   *
   * @param law {@code ERF}, {@code ERFC}, {@code GAMMA} or {@code BETA}
   * @param p the parameters: {@code BETA}'s second argument
   * @param x the argument
   * @return whether the value is not zero
   */
  public static boolean nonZero(Law law, double[] p, double x) {
    return switch (law) {
      case ERF -> x != 0;
      case BETA -> !(x + p[0] <= 0 && integer(x + p[0]));
      default -> true;
    };
  }

  /**
   * The error function.
   *
   * @param x the argument
   * @return {@code erf(x)}
   */
  public static double erf(double x) {
    return Erf.erf(x);
  }

  /**
   * The complementary error function.
   *
   * @param x the argument
   * @return {@code erfc(x)}
   */
  public static double erfc(double x) {
    return Erf.erfc(x);
  }

  /**
   * The gamma function: NaN at its poles, the integers 0 and below.
   *
   * @param x the argument
   * @return {@code Γ(x)}
   */
  public static double gamma(double x) {
    if (x <= 0 && integer(x)) {
      return Double.NaN;
    }
    return Gamma.gamma(x);
  }

  /**
   * The beta function {@code Γ(a)Γ(b)/Γ(a + b)}: NaN where {@code a} or {@code b} is a pole of
   * {@code Γ}, 0 where only {@code a + b} is; through {@code log B} for positive arguments, where
   * the gamma values alone would overflow.
   *
   * @param a the first argument
   * @param b the second argument
   * @return {@code B(a, b)}
   */
  public static double beta(double a, double b) {
    if (a <= 0 && integer(a) || b <= 0 && integer(b)) {
      return Double.NaN;
    }
    if (a > 0 && b > 0) {
      return Math.exp(Beta.logBeta(a, b));
    }
    if (a + b <= 0 && integer(a + b)) {
      return 0.0;
    }
    return Gamma.gamma(a) * Gamma.gamma(b) / Gamma.gamma(a + b);
  }

  private static boolean integer(double x) {
    return x == Math.rint(x) && !Double.isInfinite(x);
  }
}
