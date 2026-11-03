package mathcanvas.engine.provider;

import java.util.Objects;

/**
 * A numeric value of a probability distribution, or of one of the special functions the
 * distributions are built on, in {@code double}: a density, the probability of an interval, a
 * quantile, or a special function's value. A result is correct when it is that value to working
 * precision, or when its status says why there is none. It is normalised by turning {@code -0.0}
 * into {@code 0.0}, giving the status {@link DistributionValue.Status#NOT_FINITE} to a NaN or an
 * infinity, and clamping a probability into {@code [0, 1]} and a density to {@code 0} or above. The
 * arrays are copied in, so the record compares them by identity: it is a transient input, never a
 * key.
 *
 * @param law the distribution, or the special function
 * @param kind which value
 * @param parameters the law's parameters, in the order {@link Law} names them
 * @param points the point: {@code [x]} for {@link Kind#DENSITY} and {@link Kind#VALUE}, {@code [a,
 *     b]} for {@link Kind#PROBABILITY}, {@code [p]} for {@link Kind#INVERSE} and {@link
 *     Kind#UPPER_INVERSE}
 */
public record Distribute(Law law, Kind kind, double[] parameters, double[] points)
    implements Operation<DistributionValue> {

  /** The distributions, with their parameters, and the special functions. */
  public enum Law {
    /** The normal distribution: the mean {@code μ} and the standard deviation {@code σ > 0}. */
    NORMAL(2, true),
    /** Student's t distribution: the degrees of freedom {@code ν > 0}. */
    STUDENT_T(1, true),
    /** The χ² distribution: the degrees of freedom {@code k > 0}. */
    CHI_SQUARED(1, true),
    /** The F distribution: the degrees of freedom {@code d1 > 0} and {@code d2 > 0}. */
    F(2, true),
    /** The exponential distribution: the rate {@code λ > 0}. */
    EXPONENTIAL(1, true),
    /** The uniform distribution on {@code [a, b]}, {@code a < b}. */
    UNIFORM(2, true),
    /** The binomial distribution: the trials {@code n}, an integer, and {@code 0 ≤ p ≤ 1}. */
    BINOMIAL(2, false),
    /** The Poisson distribution: the mean {@code λ > 0}. */
    POISSON(1, false),
    /** The geometric distribution on {@code 1, 2, …}: {@code 0 < p ≤ 1}. */
    GEOMETRIC(1, false),
    /**
     * The hypergeometric distribution: the population {@code N}, its successes {@code K} and the
     * draws {@code n}, integers with {@code 0 ≤ K ≤ N} and {@code 0 ≤ n ≤ N}.
     */
    HYPERGEOMETRIC(3, false),
    /** The error function {@code erf(x)}. */
    ERF(0, true),
    /** The complementary error function {@code erfc(x) = 1 − erf(x)}. */
    ERFC(0, true),
    /** The gamma function {@code Γ(x)}, {@code x} not a pole. */
    GAMMA(0, true),
    /** The beta function {@code B(x, b) = Γ(x)Γ(b)/Γ(x + b)}: the second argument {@code b}. */
    BETA(1, true);

    private final int parameters;
    private final boolean continuous;

    Law(int parameters, boolean continuous) {
      this.parameters = parameters;
      this.continuous = continuous;
    }

    /**
     * The number of parameters.
     *
     * @return the count
     */
    public int parameters() {
      return parameters;
    }

    /**
     * Whether the law is a continuous distribution or a special function, rather than a discrete
     * distribution on the integers.
     *
     * @return true unless it is discrete
     */
    public boolean continuous() {
      return continuous;
    }

    /**
     * Whether the law is a special function, which has only {@link Kind#VALUE}.
     *
     * @return true for {@code ERF}, {@code ERFC}, {@code GAMMA} and {@code BETA}
     */
    public boolean special() {
      return ordinal() >= ERF.ordinal();
    }
  }

  /** Which value of the law. */
  public enum Kind {
    /** The density at {@code x}; for a discrete law the probability of {@code x}, an integer. */
    DENSITY,
    /**
     * The probability of the interval: {@code P(a < X ≤ b)}, or {@code P(a ≤ X ≤ b)} for a discrete
     * law; {@code a ≤ b}, either infinite.
     */
    PROBABILITY,
    /** The least {@code x} with {@code P(X ≤ x) ≥ p}, for a continuous distribution. */
    INVERSE,
    /**
     * The {@code x} with {@code P(X > x) = p}, the quantile of the upper tail {@code p}, for a
     * continuous distribution: a quantile above the median asked from its tail, which a lower
     * probability {@code 1 − p} in {@code double} would round.
     */
    UPPER_INVERSE,
    /** A special function's value at {@code x}. */
    VALUE
  }

  /**
   * Checks and copies the operands.
   *
   * @throws IllegalArgumentException if the parameters are not the law's or outside its domain, the
   *     kind is not the law's, or the points are not the kind's
   */
  public Distribute {
    Objects.requireNonNull(law, "law");
    Objects.requireNonNull(kind, "kind");
    parameters = parameters.clone();
    points = points.clone();
    if (parameters.length != law.parameters()) {
      throw new IllegalArgumentException(law + " takes " + law.parameters() + " parameters");
    }
    for (double p : parameters) {
      if (!Double.isFinite(p)) {
        throw new IllegalArgumentException("the parameters must be finite");
      }
    }
    if (law.special() != (kind == Kind.VALUE)) {
      throw new IllegalArgumentException(kind + " is not a value of " + law);
    }
    if ((kind == Kind.INVERSE || kind == Kind.UPPER_INVERSE) && !law.continuous()) {
      throw new IllegalArgumentException("a discrete law has no inverse here");
    }
    checkDomain(law, parameters);
    switch (kind) {
      case DENSITY, VALUE -> {
        if (points.length != 1 || !Double.isFinite(points[0])) {
          throw new IllegalArgumentException("one finite point");
        }
      }
      case PROBABILITY -> {
        if (points.length != 2 || Double.isNaN(points[0]) || Double.isNaN(points[1])) {
          throw new IllegalArgumentException("an interval of two points");
        }
        if (points[0] > points[1]) {
          throw new IllegalArgumentException("the interval's lower end is above its upper end");
        }
      }
      case INVERSE, UPPER_INVERSE -> {
        if (points.length != 1 || !(points[0] >= 0 && points[0] <= 1)) {
          throw new IllegalArgumentException("a probability in [0, 1]");
        }
      }
    }
  }

  private static void checkDomain(Law law, double[] p) {
    boolean ok =
        switch (law) {
          case NORMAL -> p[1] > 0;
          case STUDENT_T, CHI_SQUARED, EXPONENTIAL, POISSON -> p[0] > 0;
          case F -> p[0] > 0 && p[1] > 0;
          case UNIFORM -> p[0] < p[1];
          case BINOMIAL -> integer(p[0]) && p[0] >= 0 && p[1] >= 0 && p[1] <= 1;
          case GEOMETRIC -> p[0] > 0 && p[0] <= 1;
          case HYPERGEOMETRIC ->
              integer(p[0])
                  && integer(p[1])
                  && integer(p[2])
                  && p[1] >= 0
                  && p[1] <= p[0]
                  && p[2] >= 0
                  && p[2] <= p[0];
          case ERF, ERFC, GAMMA, BETA -> true;
        };
    if (!ok) {
      throw new IllegalArgumentException("the parameters are outside the domain of " + law);
    }
  }

  private static boolean integer(double x) {
    return x == Math.rint(x) && Math.abs(x) <= Integer.MAX_VALUE;
  }

  @Override
  public Class<DistributionValue> outputType() {
    return DistributionValue.class;
  }

  @Override
  public DistributionValue normalise(DistributionValue result) {
    if (result.status() != DistributionValue.Status.OK) {
      return DistributionValue.failed(result.status());
    }
    double v = result.value();
    if (!Double.isFinite(v)) {
      return DistributionValue.failed(DistributionValue.Status.NOT_FINITE);
    }
    switch (kind) {
      case PROBABILITY -> v = Math.min(1.0, Math.max(0.0, v));
      case DENSITY -> v = Math.max(0.0, v);
      default -> {}
    }
    // -0.0 is 0
    return new DistributionValue(DistributionValue.Status.OK, v + 0.0);
  }
}
