package mathcanvas.engine.number;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;
import mathcanvas.engine.Cancellation;

/**
 * Numeric integration at a requested precision by the double-exponential rules (H. Takahasi and M.
 * Mori, "Double exponential formulas for numerical integration", 1974): tanh-sinh on a finite
 * interval, exp-sinh on a half-infinite one and sinh-sinh on the whole line, in {@code BigDecimal}
 * end to end. {@code docs/reference/engine.md}, section <b>Numeric integration</b>, is the
 * reference.
 *
 * <p>The rule works at {@code digits + 10} digits. Level {@code k} samples the transformed
 * integrand at the step {@code 2^-k} in the transformed variable {@code t}, each level adding the
 * odd multiples of its step to the nodes before it, walking outward from {@code t = 0} on each
 * side: toward a finite end until two terms in a row are negligible at the working precision or the
 * transform's reach is passed, toward an infinite end to the reach or a sample beyond the tower's
 * range after vanished terms, so a bump beyond a stretch of vanished terms is reached. A node near
 * a finite end is computed exactly, as the end plus or minus its distance, and sampled at the
 * digits that carries, so an endpoint singularity is seen at its true distance. A level's error
 * estimate is its difference from the level before; a value is accepted from level {@link
 * #MIN_LEVEL} on (from {@link #MIN_LEVEL_INFINITE} toward an infinite end) when that difference has
 * been below {@code 10^-(digits+2)} of the larger of the value and {@code 10^-6} of the integral of
 * the absolute value on {@link #AGREEMENTS} levels in a row, and when the caller's {@link Check}
 * agrees. The rule fails, rather than give a number, when a sample inside the interval is
 * undefined; when a side reaches the reach with its terms not vanished and the integrand's size
 * times its span from the end, carried to the reach as a power of the span from the last two nodes,
 * above the target (a divergent integral, or one whose singularity or tail is beyond the
 * precision); when the levels do not agree by the level cap, {@link #maxLevel}; or when they agree
 * only on values the check refuses.
 *
 * <p><b>The limit.</b> The levels see the integrand only at their nodes, so a feature narrower than
 * the finest spacing reached can be missed: the levels then agree on the value without it ({@code
 * e^(-10^6 (x - 0.3)^2) + 1} on {@code [0, 1]} at 10 digits gave {@code 1.0} before the minimum
 * level and the check). The cross-check by a rule that adapts to the local error is the guard.
 */
public final class Quadrature {

  /** The digits carried beyond the digits asked for. */
  public static final int GUARD = 10;

  /**
   * The finest level for a number of digits, whose step is {@code 2^-maxLevel}: 5 plus the bit
   * length of the working digits, so 10 at 10 digits, 11 at 34, 12 at 100 and 15 at 1,000. Smooth
   * integrands were measured to be accepted at levels 6 and 7 at 10 digits, 7 to 9 at 34 and 8 to
   * 11 at 100 ({@code e^(-x^2)} on the line the last); a run to the cap is about 16,000 samples at
   * 34 digits.
   *
   * @param digits the digits asked for
   * @return the level
   */
  public static int maxLevel(int digits) {
    return 5 + (32 - Integer.numberOfLeadingZeros(digits + GUARD));
  }

  /** The context of the tail's extrapolation. */
  private static final MathContext LOGS = new MathContext(16, RoundingMode.HALF_EVEN);

  /**
   * The first level accepted on a finite interval: a feature narrower than the coarse nodes'
   * spacing is stepped over by the early levels, which then agree on the wrong value (a spike of
   * width 1e-3 inside {@code [0, 1]} at 10 digits, before this bound and the cross-check).
   */
  static final int MIN_LEVEL = 6;

  /** The first level accepted toward an infinite end, where no cross-check is made. */
  static final int MIN_LEVEL_INFINITE = 7;

  /** The successive level differences that must agree before a value is accepted. */
  static final int AGREEMENTS = 3;

  private Quadrature() {}

  /** The integrand: its value at a point, or null where it is undefined or not real. */
  @FunctionalInterface
  public interface Integrand {

    /**
     * The value at a point. An {@link ArithmeticException} is taken as a value beyond the tower's
     * range.
     *
     * @param x the point
     * @param mc the precision to evaluate at: the working precision, or more near a finite end of
     *     the interval, where {@code x} carries the digits that set it apart from the end
     * @return the value, or null where it is undefined or not real
     */
    Real at(Real x, MathContext mc);
  }

  /**
   * A bound: a finite real, or an infinity.
   *
   * @param value the finite value; null for an infinity
   * @param infinity 1 for {@code ∞}, -1 for {@code -∞}, 0 for a finite bound
   */
  public record Bound(Real value, int infinity) {

    /** {@code ∞}. */
    public static final Bound POSITIVE_INFINITY = new Bound(null, 1);

    /** {@code -∞}. */
    public static final Bound NEGATIVE_INFINITY = new Bound(null, -1);

    /**
     * Checks the bound.
     *
     * @throws IllegalArgumentException if it is neither finite nor an infinity
     */
    public Bound {
      if (infinity < -1 || infinity > 1 || (value == null) != (infinity != 0)) {
        throw new IllegalArgumentException("a bound is a finite real or an infinity");
      }
    }

    /**
     * A finite bound.
     *
     * @param value the value
     * @return the bound
     */
    public static Bound finite(Real value) {
      return new Bound(Objects.requireNonNull(value, "value"), 0);
    }

    /** Whether the bound is finite. */
    boolean isFinite() {
      return infinity == 0;
    }
  }

  /** The result of a quadrature. */
  public sealed interface Result {}

  /**
   * The value, rounded to the digits asked for.
   *
   * @param value the integral
   * @param error the error estimate: the difference of the last two levels
   * @param level the level the value was accepted at
   */
  public record Value(Real value, Real error, int level) implements Result {}

  /**
   * No value.
   *
   * @param reason why
   */
  public record None(Reason reason) implements Result {}

  /** Why a quadrature gave no value. */
  public enum Reason {
    /** A sample inside the interval was undefined or not real. */
    UNDEFINED,
    /**
     * The terms did not vanish at an end of the interval: the integral diverges, or the integrand
     * grows there too fast, or decays toward an infinite end too slowly, for the precision.
     */
    TAIL,
    /** The levels did not agree by {@link #maxLevel}. */
    UNSETTLED,
    /** The levels agreed, but on a value the cross-check refused, up to {@link #maxLevel}. */
    DISAGREE
  }

  /** Thrown inside the walk when the rule cannot give a value. */
  private static final class Failure extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final Reason reason;

    Failure(Reason reason) {
      super(null, null, false, false);
      this.reason = reason;
    }
  }

  /**
   * A cross-check of a value the levels agreed on, by an independent rule: a refusal makes the
   * levels go on.
   */
  @FunctionalInterface
  public interface Check {

    /** No cross-check: every agreed value stands. */
    Check NONE = (value, l1) -> true;

    /**
     * Whether a value stands.
     *
     * @param value the integral from the lower bound given to the upper one, at the working
     *     precision
     * @param l1 the integral of the integrand's absolute value
     * @return true when the check agrees
     */
    boolean agrees(Real value, Real l1);
  }

  /**
   * Integrates {@code f} from {@code a} to {@code b}, with no cross-check.
   *
   * @param f the integrand
   * @param a the lower bound
   * @param b the upper bound
   * @param digits the significant digits of the value, at least 1
   * @param cancellation the request to stop, checked at every level and every node
   * @return the value, or why there is none
   * @throws IllegalArgumentException if {@code digits} is below 1
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Result integrate(
      Integrand f, Bound a, Bound b, int digits, Cancellation cancellation) {
    return integrate(f, a, b, digits, Check.NONE, cancellation);
  }

  /**
   * Integrates {@code f} from {@code a} to {@code b}, each value the levels agree on cross-checked:
   * a refused value makes the levels go on, and at the cap the result is {@link Reason#DISAGREE}.
   *
   * @param f the integrand
   * @param a the lower bound
   * @param b the upper bound
   * @param digits the significant digits of the value, at least 1
   * @param check the cross-check
   * @param cancellation the request to stop, checked at every level and every node
   * @return the value, or why there is none
   * @throws IllegalArgumentException if {@code digits} is below 1
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Result integrate(
      Integrand f, Bound a, Bound b, int digits, Check check, Cancellation cancellation) {
    if (digits < 1) {
      throw new IllegalArgumentException("digits below 1: " + digits);
    }
    cancellation.check();
    int order = compare(a, b);
    if (order == 0) {
      return new Value(Real.of(BigDecimal.ZERO), Real.of(BigDecimal.ZERO), 0);
    }
    Bound lo = order < 0 ? a : b;
    Bound hi = order < 0 ? b : a;
    try {
      Check oriented = order < 0 ? check : (value, l1) -> check.agrees(value.negate(), l1);
      Value v = new Rule(f, lo, hi, digits, cancellation).run(oriented);
      return order < 0 ? v : new Value(v.value().negate(), v.error(), v.level());
    } catch (Failure failure) {
      return new None(failure.reason);
    }
  }

  /** The order of two bounds; two equal infinities are equal. */
  private static int compare(Bound a, Bound b) {
    if (a.isFinite() && b.isFinite()) {
      return a.value().value().compareTo(b.value().value());
    }
    int ia = a.isFinite() ? 0 : a.infinity();
    int ib = b.isFinite() ? 0 : b.infinity();
    return Integer.compare(ia, ib);
  }

  /** One quadrature over an interval with {@code lo < hi}. */
  private static final class Rule {
    private final Integrand f;
    private final Cancellation cancellation;
    private final BigDecimalMath math;
    private final MathContext w;
    private final int digits;
    private final BigDecimal halfPi;
    private final BigDecimal negligible;
    private final BigDecimal target;
    private final BigDecimal reach;
    // the transform: finite (tanh-sinh), half-infinite (exp-sinh) or the whole line (sinh-sinh)
    private final BigDecimal lo;
    private final BigDecimal hi;
    private final BigDecimal mid;
    private final BigDecimal half;
    private final BigDecimal end;
    private final int direction;
    private final Kind kind;

    private enum Kind {
      FINITE,
      HALF_LINE,
      LINE
    }

    /** The raw sums of {@code w f} and {@code |w f|} over every node so far. */
    private BigDecimal sum = BigDecimal.ZERO;

    private BigDecimal l1 = BigDecimal.ZERO;

    Rule(Integrand f, Bound lo, Bound hi, int digits, Cancellation cancellation) {
      this.f = f;
      this.cancellation = cancellation;
      this.math = new BigDecimalMath(cancellation);
      this.digits = digits;
      int wp = digits + GUARD;
      this.w = new MathContext(wp, RoundingMode.HALF_EVEN);
      this.halfPi = math.pi(BigDecimalMath.extend(w, 5)).divide(BigDecimal.TWO, w);
      this.negligible = BigDecimal.ONE.scaleByPowerOfTen(-wp);
      this.target = BigDecimal.ONE.scaleByPowerOfTen(-(digits + 2));
      // beyond u = 3wp + 20 every transform's weights, or its abscissas, are out of reach
      this.reach = BigDecimal.valueOf(3L * wp + 20);
      if (lo.isFinite() && hi.isFinite()) {
        kind = Kind.FINITE;
        this.lo = lo.value().value();
        this.hi = hi.value().value();
        mid = this.lo.add(this.hi, w).divide(BigDecimal.TWO, w);
        half = this.hi.subtract(this.lo, w).divide(BigDecimal.TWO, w);
        end = null;
        direction = 0;
      } else if (lo.isFinite() || hi.isFinite()) {
        kind = Kind.HALF_LINE;
        this.lo = null;
        this.hi = null;
        mid = null;
        half = null;
        end = lo.isFinite() ? lo.value().value() : hi.value().value();
        direction = lo.isFinite() ? 1 : -1;
      } else {
        kind = Kind.LINE;
        this.lo = null;
        this.hi = null;
        mid = null;
        half = null;
        end = null;
        direction = 0;
      }
    }

    Value run(Check check) {
      BigDecimal previous = null;
      int agreed = 0;
      boolean refused = false;
      int first = kind == Kind.FINITE ? MIN_LEVEL : MIN_LEVEL_INFINITE;
      int cap = Math.max(maxLevel(digits), first);
      for (int k = 0; k <= cap; k++) {
        cancellation.check();
        level(k);
        BigDecimal h = BigDecimal.ONE.divide(BigDecimal.TWO.pow(k));
        BigDecimal value = sum.multiply(h, w);
        if (previous != null) {
          BigDecimal error = value.subtract(previous, w).abs();
          BigDecimal scale = value.abs().max(l1.multiply(h).scaleByPowerOfTen(-6));
          agreed = error.compareTo(target.multiply(scale)) <= 0 ? agreed + 1 : 0;
          if (agreed >= AGREEMENTS && k >= first) {
            if (check.agrees(Real.of(value), Real.of(l1.multiply(h, w)))) {
              MathContext out = new MathContext(digits, RoundingMode.HALF_EVEN);
              return new Value(
                  Real.of(value.round(out)), Real.of(error.round(new MathContext(3))), k);
            }
            refused = true;
          }
        }
        previous = value;
      }
      throw new Failure(refused ? Reason.DISAGREE : Reason.UNSETTLED);
    }

    /** Adds the nodes of level {@code k}: every multiple of 1 at level 0, the odd ones after. */
    private void level(int k) {
      BigDecimal step = BigDecimal.ONE.divide(BigDecimal.TWO.pow(k));
      if (k == 0) {
        centre();
      }
      // toward an infinite end the walk goes on past vanished terms, to a bump beyond them
      Side right = new Side(kind != Kind.FINITE);
      Side left = new Side(kind == Kind.LINE);
      for (long m = 1; right.open || left.open; m += k == 0 ? 1 : 2) {
        cancellation.check();
        Node node = node(step.multiply(BigDecimal.valueOf(m)));
        if (node.beyond()) {
          // past the reach: a side still open must have vanished there
          right.end(node.rightSpan(), step);
          left.end(node.leftSpan(), step);
          return;
        }
        right.next(node.right(), node.rightWeight(), node.rightSpan());
        left.next(node.left(), node.leftWeight(), node.leftSpan());
      }
    }

    /** One side of a level's walk outward from {@code t = 0}. */
    private final class Side {
      private final boolean infinite;
      boolean open = true;
      private int quiet;
      private BigDecimal lastTerm;
      private BigDecimal area;
      private BigDecimal span;
      private BigDecimal previousArea;
      private BigDecimal previousSpan;

      /**
       * A side of the walk.
       *
       * @param infinite whether it goes toward an infinite end, where only the reach or a sample
       *     beyond the tower's range after vanished terms ends it
       */
      Side(boolean infinite) {
        this.infinite = infinite;
      }

      /** The node at the next {@code t}. */
      void next(BigDecimal x, BigDecimal weight, BigDecimal nodeSpan) {
        if (!open) {
          return;
        }
        BigDecimal value = sample(x, lastTerm);
        if (value == null) {
          // beyond the tower's range after a vanished term
          open = false;
          return;
        }
        BigDecimal term = value.multiply(weight, w);
        add(term);
        lastTerm = term;
        previousArea = area;
        previousSpan = span;
        area = value.abs().multiply(nodeSpan, w);
        span = nodeSpan;
        quiet = quiet(term) ? quiet + 1 : 0;
        open = infinite || quiet < 2;
      }

      /**
       * The side ends at the reach, at {@code endSpan}, while its terms have not vanished: the
       * integrand's size times its span from the end, carried from the last two nodes to {@code
       * endSpan} as a power of the span, must be below the target there, or the integral is taken
       * not to converge.
       */
      void end(BigDecimal endSpan, BigDecimal step) {
        if (open && area != null && area.signum() != 0) {
          BigDecimal bound = l1.multiply(step).multiply(target);
          boolean vanishes =
              previousArea == null || previousArea.signum() == 0
                  ? area.compareTo(bound) <= 0
                  : extrapolated(endSpan).compareTo(log(bound)) <= 0;
          if (!vanishes) {
            throw new Failure(Reason.TAIL);
          }
        }
        open = false;
      }

      /** The logarithm of the area at {@code endSpan}, by the power law of the last two nodes. */
      private BigDecimal extrapolated(BigDecimal endSpan) {
        BigDecimal power =
            log(area.divide(previousArea, LOGS)).divide(log(span.divide(previousSpan, LOGS)), LOGS);
        return log(area).add(power.multiply(log(endSpan.divide(span, LOGS)), LOGS), LOGS);
      }
    }

    private BigDecimal log(BigDecimal x) {
      return math.log(x, LOGS);
    }

    /** The node at {@code t = 0}, on level 0 only. */
    private void centre() {
      BigDecimal x;
      BigDecimal weight;
      switch (kind) {
        case FINITE -> {
          x = mid;
          weight = half.multiply(halfPi, w);
        }
        case HALF_LINE -> {
          x = end.add(BigDecimal.valueOf(direction), w);
          weight = halfPi;
        }
        case LINE -> {
          x = BigDecimal.ZERO;
          weight = halfPi;
        }
        default -> throw new IllegalStateException();
      }
      BigDecimal term = sample(x, null).multiply(weight, w);
      add(term);
    }

    /** The two nodes at {@code ±t}. */
    private Node node(BigDecimal t) {
      BigDecimal e = math.exp(t, w);
      BigDecimal inverse = BigDecimal.ONE.divide(e, w);
      BigDecimal sinh = e.subtract(inverse, w).divide(BigDecimal.TWO, w);
      BigDecimal cosh = e.add(inverse, w).divide(BigDecimal.TWO, w);
      BigDecimal u = halfPi.multiply(sinh, w);
      BigDecimal du = halfPi.multiply(cosh, w);
      boolean beyond = u.compareTo(reach) > 0;
      return switch (kind) {
        case FINITE -> {
          // tanh-sinh: the distance from each end, 2q/(1 + q) of the half-width, q = e^(-2u)
          BigDecimal q = math.exp(u.multiply(BigDecimal.TWO).negate(), w);
          BigDecimal onePlus = BigDecimal.ONE.add(q, w);
          BigDecimal distance = half.multiply(q.multiply(BigDecimal.TWO), w).divide(onePlus, w);
          BigDecimal weight =
              half.multiply(du, w)
                  .multiply(q.multiply(BigDecimal.valueOf(4)), w)
                  .divide(onePlus.multiply(onePlus, w), w);
          // exact: a node near an end carries the digits that tell it from the end
          yield new Node(
              hi.subtract(distance), weight, distance, lo.add(distance), weight, distance, beyond);
        }
        case HALF_LINE -> {
          // exp-sinh: x = end ± e^u, the weight du·e^u; at -t, e^-u
          BigDecimal big = math.exp(u, w);
          BigDecimal small = BigDecimal.ONE.divide(big, w);
          BigDecimal sign = BigDecimal.valueOf(direction);
          yield new Node(
              end.add(big.multiply(sign), w),
              du.multiply(big, w),
              big,
              end.add(small.multiply(sign)),
              du.multiply(small, w),
              small,
              beyond);
        }
        case LINE -> {
          // sinh-sinh: x = ±sinh u, the weight du·cosh u
          BigDecimal big = math.exp(u, w);
          BigDecimal small = BigDecimal.ONE.divide(big, w);
          BigDecimal x = big.subtract(small, w).divide(BigDecimal.TWO, w);
          BigDecimal weight = du.multiply(big.add(small, w), w).divide(BigDecimal.TWO, w);
          yield new Node(x, weight, x, x.negate(), weight, x, beyond);
        }
      };
    }

    private void add(BigDecimal term) {
      sum = sum.add(term, w);
      l1 = l1.add(term.abs(), w);
    }

    /**
     * The integrand at {@code x}: a failure when it is undefined, null when it is beyond the
     * tower's range after a vanished term (a failure before one).
     */
    private BigDecimal sample(BigDecimal x, BigDecimal last) {
      cancellation.check();
      Real value;
      try {
        // a node near an end is sampled with the digits it carries beyond the working ones
        value =
            f.at(
                Real.of(x),
                x.precision() > w.getPrecision()
                    ? new MathContext(x.precision(), RoundingMode.HALF_EVEN)
                    : w);
      } catch (ArithmeticException outOfRange) {
        if (last != null && last.abs().compareTo(l1.multiply(target)) <= 0) {
          return null;
        }
        throw new Failure(Reason.UNDEFINED);
      }
      if (value == null) {
        throw new Failure(Reason.UNDEFINED);
      }
      return value.value();
    }

    /** Whether a term is negligible at the working precision against the sums so far. */
    private boolean quiet(BigDecimal term) {
      return term.abs().compareTo(l1.multiply(negligible)) <= 0;
    }
  }

  /**
   * The abscissas and weights at {@code ±t}, and the span of each from its end of the interval (or
   * its distance from 0 toward an infinite end); {@code beyond} marks a node past the transform's
   * reach, which only ends the walk.
   */
  private record Node(
      BigDecimal right,
      BigDecimal rightWeight,
      BigDecimal rightSpan,
      BigDecimal left,
      BigDecimal leftWeight,
      BigDecimal leftSpan,
      boolean beyond) {}
}
