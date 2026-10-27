package mathcanvas.engine.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Numeric;
import net.jqwik.api.Reporter;

/**
 * The second look every soundness property takes at a point where two expressions disagree at 34
 * digits ({@link Soundness#check}), and the tally of a property run's compared and ill-conditioned
 * points with its cap.
 */
public final class Recheck {

  /** The precision of the second comparison. */
  public static final MathContext PRECISION = new MathContext(100);

  private static final BigDecimal TOLERANCE = new BigDecimal("1e-20");
  private static final BigDecimal FLOOR = new BigDecimal("1e-25");

  /**
   * The most ill-conditioned points a property run may skip: one in this many of the points it
   * compared (5%), so that a property that passed only by skipping would fail.
   */
  public static final int ILL_CONDITIONED_DIVISOR = 20;

  /** The points compared and the ill-conditioned points skipped in the current property run. */
  private static int comparedPoints;

  private static int illConditionedPoints;

  private Recheck() {}

  /** The outcome of checking a disagreeing point again. */
  public enum Outcome {
    /** They agree at {@link #PRECISION}: the disagreement was rounding. */
    AGREE,
    /** Either side is empty away from cuts: a cut, a pole or a zero within rounding. */
    ON_A_CUT,
    /** They still disagree, and either side disagrees with itself between 34 and 100 digits. */
    ILL_CONDITIONED,
    /** A value overflows the comparison. */
    OVERFLOW,
    /** They disagree at a well-conditioned point. */
    DISAGREE
  }

  /**
   * Checks a point where {@code before} and {@code after} disagree at 34 digits again at {@link
   * #PRECISION}, within the exact tolerance. A point where either side takes a principal value
   * within rounding of its branch cut, a pole or a zero ({@link NumericEvaluator#evalAwayFromCuts})
   * is no evidence either way: the true value lies on the cut and the rounding noise picks the
   * side. Elsewhere the higher precision removes the digits an evaluation loses to cancellation or
   * to an ill-conditioned function ({@code asin} of a value one ulp below 1 is off by {@code
   * √(2ε)}). A point where they still disagree, and where either side disagrees with itself between
   * 34 and 100 digits, is ill-conditioned: its digits are lost beyond what 100 digits recover, so
   * it is no evidence either; a wrong rewrite at a well-conditioned point still disagrees.
   *
   * @param before the input
   * @param after the result
   * @param point the point
   * @return the outcome
   */
  public static Outcome of(Expr before, Expr after, Map<Symbol, Numeric> point) {
    Optional<Numeric> a = NumericEvaluator.evalAwayFromCuts(before, point, PRECISION);
    Optional<Numeric> b = NumericEvaluator.evalAwayFromCuts(after, point, PRECISION);
    if (a.isEmpty() || b.isEmpty()) {
      return Outcome.ON_A_CUT;
    }
    try {
      if (close(a.get(), b.get())) {
        return Outcome.AGREE;
      }
      return selfAgrees(before, a.get(), point) && selfAgrees(after, b.get(), point)
          ? Outcome.DISAGREE
          : Outcome.ILL_CONDITIONED;
    } catch (ArithmeticException overflow) {
      return Outcome.OVERFLOW;
    }
  }

  /** Starts a property run's tally of compared and ill-conditioned points. */
  public static void begin() {
    comparedPoints = 0;
    illConditionedPoints = 0;
  }

  /**
   * Counts points compared in the current run.
   *
   * @param points how many
   */
  public static void compared(int points) {
    comparedPoints += points;
  }

  /** Counts an ill-conditioned point skipped in the current run. */
  public static void illConditioned() {
    illConditionedPoints++;
  }

  /**
   * Reports the ill-conditioned points skipped since {@link #begin} with the points compared and
   * the rate, and asserts that they are at most one in {@link #ILL_CONDITIONED_DIVISOR} of them,
   * compared exactly ({@code skipped · 20 ≤ compared}).
   *
   * @param reporter the property's reporter
   */
  public static void end(Reporter reporter) {
    long skipped = illConditionedPoints;
    long compared = comparedPoints;
    String rate =
        compared == 0
            ? "no points compared"
            : BigDecimal.valueOf(100 * skipped)
                    .divide(BigDecimal.valueOf(compared), 4, RoundingMode.HALF_EVEN)
                    .toPlainString()
                + "%";
    reporter.publishValue(
        "ill-conditioned points skipped", skipped + " of " + compared + " compared (" + rate + ")");
    assertThat(skipped * ILL_CONDITIONED_DIVISOR)
        .as("ill-conditioned points skipped: %d of %d compared", skipped, compared)
        .isLessThanOrEqualTo(compared);
  }

  /**
   * The tally since {@link #begin}.
   *
   * @return the points compared and the ill-conditioned ones skipped
   */
  public static int[] tally() {
    return new int[] {comparedPoints, illConditionedPoints};
  }

  /** Whether two values agree within the exact tolerance, at {@link #PRECISION}. */
  private static boolean close(Numeric a, Numeric b) {
    BigDecimal difference = magnitude(a.subtract(b, PRECISION));
    BigDecimal scale = magnitude(a).max(magnitude(b));
    return difference.compareTo(TOLERANCE.multiply(scale, PRECISION).add(FLOOR, PRECISION)) <= 0;
  }

  /** Whether {@code e}'s value at 34 digits agrees with {@code fine}, its value at 100. */
  private static boolean selfAgrees(Expr e, Numeric fine, Map<Symbol, Numeric> point) {
    Optional<Numeric> coarse = NumericEvaluator.eval(e, point, Soundness.PRECISION);
    return coarse.isEmpty() || close(coarse.get(), fine);
  }

  private static BigDecimal magnitude(Numeric v) {
    return v.modulus(PRECISION).toReal(PRECISION).value().abs();
  }
}
