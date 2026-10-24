package mathcanvas.engine.simplify;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;
import mathcanvas.engine.testing.NumericEvaluator;
import mathcanvas.engine.testing.Soundness;
import mathcanvas.engine.testing.TerminationProbe;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Reporter;
import net.jqwik.api.statistics.Statistics;

/**
 * The checks every rule set's properties run: soundness and definedness at points within the
 * assumptions, and termination, for a list of sets combined as the simplifier combines them.
 */
final class SoundnessChecks {

  /** The points compared per expression. */
  static final int POINTS = 6;

  /** The precision of the second comparison of a point that disagrees at 34 digits. */
  static final MathContext RECHECK = new MathContext(100);

  private static final BigDecimal TOLERANCE = new BigDecimal("1e-20");
  private static final BigDecimal FLOOR = new BigDecimal("1e-25");

  /** The most rewrites one generated expression may take; the cap is 10,000. */
  static final int MAX_REWRITES = 200;

  /**
   * The most ill-conditioned points a property run may skip: one in this many of the points it
   * compared (5%), so that a rule that passed only by skipping would fail.
   */
  static final int ILL_CONDITIONED_DIVISOR = 20;

  /** The points compared and the ill-conditioned points skipped in the current property run. */
  private static int comparedPoints;

  private static int illConditionedPoints;

  private SoundnessChecks() {}

  /** Starts a property run's tally of compared and ill-conditioned points. */
  static void begin() {
    comparedPoints = 0;
    illConditionedPoints = 0;
  }

  /**
   * Reports the ill-conditioned points skipped since {@link #begin} with the points compared and
   * the rate, and asserts that they are at most one in {@link #ILL_CONDITIONED_DIVISOR} of them,
   * compared exactly ({@code skipped · 20 ≤ compared}).
   *
   * @param reporter the property's reporter
   */
  static void end(Reporter reporter) {
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

  /** The tally since {@link #begin}: the points compared and the ill-conditioned ones skipped. */
  static int[] tally() {
    return new int[] {comparedPoints, illConditionedPoints};
  }

  /**
   * Simplifies {@code e} with {@code sets} under {@code a} and checks that the result agrees with
   * {@code e} wherever both are defined, and is defined wherever {@code e} has an exact value. Only
   * an exact value decides definedness: at 34 digits {@code log(e)} need not be exactly 1, so
   * {@code log(e^2, log e)} or {@code log(log(e^b))} at {@code b = 1} can look defined where they
   * are not. Each point is compared on its own; one whose values overflow the comparison ({@code
   * ArithmeticException} from {@link Soundness#check}, an exponential of an exponential) is
   * skipped.
   */
  static void check(List<RuleSet> sets, Expr e, Assumptions a, Random random) {
    Expr before = Canonical.of(e);
    Expr after = Simplifier.simplify(before, sets, Context.DEFAULT.withAssumptions(a)).expr();
    List<Map<Symbol, Numeric>> points =
        Soundness.points(ExprArbitraries.SYMBOLS, a, POINTS, random);
    int compared = 0;
    for (Map<Symbol, Numeric> point : points) {
      try {
        compared += Soundness.check(before, after, List.of(point));
      } catch (ArithmeticException overflow) {
        Statistics.label("overflowed points").collect(true);
      } catch (AssertionError disagreement) {
        switch (recheck(before, after, point)) {
          case AGREE -> {
            Statistics.label("rechecked at 100 digits").collect(true);
            compared++;
          }
          case ON_A_CUT -> Statistics.label("within rounding of a cut or a zero").collect(true);
          case ILL_CONDITIONED -> {
            Statistics.label("ill-conditioned").collect(true);
            illConditionedPoints++;
          }
          case OVERFLOW -> Statistics.label("overflowed points").collect(true);
          case DISAGREE -> throw disagreement;
        }
      }
      Optional<Numeric> value = NumericEvaluator.eval(before, point, Soundness.PRECISION);
      if (value.isPresent() && value.get().isExact()) {
        assertThat(defined(after, point))
            .as("%s is defined at %s but %s is not", before, point, after)
            .isTrue();
        Statistics.label("definedness checked").collect(true);
      }
    }
    comparedPoints += compared;
    Statistics.label("rewritten").collect(!after.equals(before));
    Statistics.label("compared").collect(compared > 0);
  }

  /** The outcome of checking a disagreeing point again. */
  private enum Recheck {
    AGREE,
    ON_A_CUT,
    ILL_CONDITIONED,
    OVERFLOW,
    DISAGREE
  }

  /**
   * Checks a point where {@code before} and {@code after} disagree at 34 digits again at {@link
   * #RECHECK} digits, within the exact tolerance. A point where either side takes a principal value
   * within rounding of its branch cut, a pole or a zero ({@link NumericEvaluator#evalAwayFromCuts})
   * is no evidence either way: the true value lies on the cut and the rounding noise picks the
   * side. Elsewhere the higher precision removes the digits an evaluation loses to cancellation. A
   * point where they still disagree, and where either side disagrees with itself between 34 and 100
   * digits, is ill-conditioned: its digits are lost to cancellation beyond what 100 digits recover,
   * so it is no evidence either; a wrong rule at a well-conditioned point still disagrees.
   */
  private static Recheck recheck(Expr before, Expr after, Map<Symbol, Numeric> point) {
    Optional<Numeric> a = NumericEvaluator.evalAwayFromCuts(before, point, RECHECK);
    Optional<Numeric> b = NumericEvaluator.evalAwayFromCuts(after, point, RECHECK);
    if (a.isEmpty() || b.isEmpty()) {
      return Recheck.ON_A_CUT;
    }
    try {
      if (close(a.get(), b.get())) {
        return Recheck.AGREE;
      }
      return selfAgrees(before, a.get(), point) && selfAgrees(after, b.get(), point)
          ? Recheck.DISAGREE
          : Recheck.ILL_CONDITIONED;
    } catch (ArithmeticException overflow) {
      return Recheck.OVERFLOW;
    }
  }

  /** Whether two values agree within the exact tolerance, at {@link #RECHECK} digits. */
  private static boolean close(Numeric a, Numeric b) {
    BigDecimal difference = magnitude(a.subtract(b, RECHECK));
    BigDecimal scale = magnitude(a).max(magnitude(b));
    return difference.compareTo(TOLERANCE.multiply(scale, RECHECK).add(FLOOR, RECHECK)) <= 0;
  }

  /** Whether {@code e}'s value at 34 digits agrees with {@code fine}, its value at 100. */
  private static boolean selfAgrees(Expr e, Numeric fine, Map<Symbol, Numeric> point) {
    Optional<Numeric> coarse = NumericEvaluator.eval(e, point, Soundness.PRECISION);
    return coarse.isEmpty() || close(coarse.get(), fine);
  }

  private static BigDecimal magnitude(Numeric v) {
    return v.modulus(RECHECK).toReal(RECHECK).value().abs();
  }

  /**
   * Whether {@code e} is defined at {@code point}: its value exists, exactly or, since the numeric
   * evaluator refuses exact powers of more than 4,000 bits, with the point's values made inexact.
   */
  private static boolean defined(Expr e, Map<Symbol, Numeric> point) {
    if (NumericEvaluator.eval(e, point, Soundness.PRECISION).isPresent()) {
      return true;
    }
    Map<Symbol, Numeric> inexact = new LinkedHashMap<>();
    point.forEach((s, v) -> inexact.put(s, v.toReal(Soundness.PRECISION)));
    return NumericEvaluator.eval(e, inexact, Soundness.PRECISION).isPresent();
  }

  /**
   * Runs the termination probe over {@code count} generated expressions and returns the most
   * rewrites one took.
   */
  static int terminates(
      List<RuleSet> sets, Arbitrary<Expr> exprs, Assumptions a, Random random, int count) {
    List<Expr> sample = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      sample.add(exprs.generator(1000).next(random).value());
    }
    List<Rule> rules = new ArrayList<>();
    for (RuleSet set : sets) {
      rules.addAll(set.rules());
    }
    TerminationProbe.Report report =
        TerminationProbe.run(
            new RuleSet(Simplifier.COMBINED, rules), sample, Context.DEFAULT.withAssumptions(a));
    assertThat(report.maxRewrites()).as("rewrites of %s", report.worst()).isLessThan(MAX_REWRITES);
    Statistics.label("most rewrites").collect(report.maxRewrites());
    return report.maxRewrites();
  }
}
