package mathcanvas.engine.simplify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

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
import mathcanvas.engine.testing.Recheck;
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

  /** The most rewrites one generated expression may take; the cap is 10,000. */
  static final int MAX_REWRITES = 200;

  private SoundnessChecks() {}

  /** Starts a property run's tally of compared and ill-conditioned points ({@link Recheck}). */
  static void begin() {
    Recheck.begin();
  }

  /**
   * Reports and caps the ill-conditioned points skipped since {@link #begin} ({@link Recheck#end}).
   *
   * @param reporter the property's reporter
   */
  static void end(Reporter reporter) {
    Recheck.end(reporter);
  }

  /** The tally since {@link #begin}: the points compared and the ill-conditioned ones skipped. */
  static int[] tally() {
    return Recheck.tally();
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
    check(sets, e, a, Soundness.points(ExprArbitraries.SYMBOLS, a, POINTS, random));
  }

  /**
   * As {@link #check(List, Expr, Assumptions, Random)}, at the given points, which must satisfy
   * {@code a}.
   */
  static void check(List<RuleSet> sets, Expr e, Assumptions a, List<Map<Symbol, Numeric>> points) {
    Expr before = Canonical.of(e);
    Expr after = Simplifier.simplify(before, sets, Context.DEFAULT.withAssumptions(a)).expr();
    int compared = 0;
    for (Map<Symbol, Numeric> point : points) {
      try {
        compared += Soundness.check(before, after, List.of(point));
      } catch (ArithmeticException overflow) {
        Statistics.label("overflowed points").collect(true);
      } catch (AssertionError disagreement) {
        switch (Recheck.of(before, after, point)) {
          case AGREE -> {
            Statistics.label("rechecked at 100 digits").collect(true);
            compared++;
          }
          case ON_A_CUT -> Statistics.label("within rounding of a cut or a zero").collect(true);
          case ILL_CONDITIONED -> {
            Statistics.label("ill-conditioned").collect(true);
            Recheck.illConditioned();
          }
          case OVERFLOW -> Statistics.label("overflowed points").collect(true);
          case DISAGREE -> throw disagreement;
        }
      }
      Optional<Numeric> value = NumericEvaluator.eval(before, point, Soundness.PRECISION);
      if (value.isPresent() && value.get().isExact()) {
        if (defined(after, point)) {
          Statistics.label("definedness checked").collect(true);
        } else if (beyondBounds(after, point)) {
          Statistics.label("beyond the evaluator's bounds").collect(true);
          Recheck.illConditioned();
        } else {
          fail("%s is defined at %s but %s is not", before, point, after);
        }
      }
    }
    Recheck.compared(compared);
    Statistics.label("rewritten").collect(!after.equals(before));
    Statistics.label("compared").collect(compared > 0);
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
   * Whether evaluating {@code e} at {@code point} stops at a bound of the numeric evaluator ({@link
   * NumericEvaluator#beyondBounds}) exactly and also with the point's values made inexact, so that
   * an inexact pass undefined where the exact one stopped at a bound decides: a magnitude past its
   * range is no evidence that {@code e} is undefined, so such a point is skipped as an
   * ill-conditioned one, under the same cap. {@code (2·a·b·y)^((1/2)^x)} at {@code a = 0}, {@code x
   * = -16} is {@code 0^65536 = 0}, while its rewrite {@code 2^((1/2)^x)·(a·b·y)^((1/2)^x)} holds
   * {@code 2^65536}. A side that is undefined, by a division by zero or a logarithm of zero, still
   * fails.
   */
  private static boolean beyondBounds(Expr e, Map<Symbol, Numeric> point) {
    if (!NumericEvaluator.beyondBounds(e, point, Soundness.PRECISION)) {
      return false;
    }
    Map<Symbol, Numeric> inexact = new LinkedHashMap<>();
    point.forEach((s, v) -> inexact.put(s, v.toReal(Soundness.PRECISION)));
    return NumericEvaluator.beyondBounds(e, inexact, Soundness.PRECISION);
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
