package mathcanvas.engine.simplify;

import static mathcanvas.engine.simplify.RuleCases.canonical;
import static mathcanvas.engine.simplify.RuleCases.parse;
import static mathcanvas.engine.simplify.Rules.template;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.MathContext;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;
import mathcanvas.engine.testing.NumericEvaluator;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Reporter;
import org.junit.jupiter.api.Test;

/**
 * The skip policy of the soundness checks: a point whose input is noise magnified around a true
 * zero is skipped, an ill-conditioned point is skipped and counted against the cap, and a wrong
 * rule still fails. The cases that collect statistics run as jqwik examples or properties.
 */
class SoundnessChecksTest {

  /**
   * {@code log(log e, …)} is exactly 0, computed as noise near 10^-34; its fourth root near 10^-9.
   */
  private static final String ROOT_OF_NOISE = "root(log(log(e), (2^b)^(1/3)), 4)";

  @Test
  void theSimplificationIsZero() {
    assertThat(Simplifier.simplify(parse(ROOT_OF_NOISE), Context.DEFAULT).expr())
        .isEqualTo(canonical("0"));
  }

  @Test
  void theRecheckEvaluationRefusesARootOfAZeroWithinRounding() {
    // At b = -2 the noise survives 100 digits: the root is near 10^-25, where the answer is 0.
    Expr e = canonical(ROOT_OF_NOISE);
    Map<Symbol, Numeric> point = Map.of(Symbol.of("b"), Int.of(-2));
    assertThat(NumericEvaluator.eval(e, point, new MathContext(100))).isPresent();
    assertThat(NumericEvaluator.evalAwayFromCuts(e, point, new MathContext(100))).isEmpty();
  }

  @Property(tries = 50)
  void aCorrectSimplificationOfARootOfNoiseIsNotAFailure(@ForAll Random random) {
    SoundnessChecks.check(Simplifier.standard(), parse(ROOT_OF_NOISE), Assumptions.EMPTY, random);
  }

  /** A sound rule of the test's own: sin(u)² + cos(u)² = 1. */
  private static final RuleSet UNIT =
      new RuleSet(
          "unit",
          List.of(
              Rule.of(
                  "pythagorean",
                  Pattern.parse("sin(u_)^2 + cos(u_)^2 + rest___"),
                  template("1 + rest___"))));

  @Example
  void anIllConditionedPointIsSkippedAndCounted() {
    // sin(u)² + cos(u)² - 1 is rounding noise, at 34 digits non-zero for u = 2 and at 100 digits
    // for u = 3; times 10^120 neither evaluation has a correct digit, while the rule's 0 is
    // exact.
    SoundnessChecks.begin();
    SoundnessChecks.check(
        List.of(UNIT),
        parse("10^120 (sin(2)^2 + cos(2)^2 - 1) + 10^120 (sin(3)^2 + cos(3)^2 - 1)"),
        Assumptions.EMPTY,
        new Random(1));
    int[] tally = SoundnessChecks.tally();
    assertThat(tally[1]).as("ill-conditioned").isEqualTo(SoundnessChecks.POINTS);
    Recording reporter = new Recording();
    assertThatThrownBy(() -> SoundnessChecks.end(reporter)).isInstanceOf(AssertionError.class);
    assertThat(reporter.values)
        .containsEntry("ill-conditioned points skipped", "6 of 0 compared (no points compared)");
  }

  @Example
  void aWrongRuleAtAWellConditionedPointStillFails() {
    RuleSet wrong =
        new RuleSet(
            "wrong",
            List.of(Rule.of("sine-is-cosine", Pattern.parse("sin(u_)"), template("cos(u_)"))));
    SoundnessChecks.begin();
    assertThatThrownBy(
            () ->
                SoundnessChecks.check(
                    List.of(wrong), parse("sin(x)"), Assumptions.EMPTY, new Random(1)))
        .isInstanceOf(AssertionError.class);
  }

  @Example
  void aSoundRuleCountsItsPointsAndPassesTheCap() {
    SoundnessChecks.begin();
    SoundnessChecks.check(
        List.of(UNIT), parse("sin(x)^2 + cos(x)^2"), Assumptions.EMPTY, new Random(1));
    assertThat(SoundnessChecks.tally()).containsExactly(SoundnessChecks.POINTS, 0);
    Recording reporter = new Recording();
    SoundnessChecks.end(reporter);
    assertThat(reporter.values)
        .containsEntry("ill-conditioned points skipped", "0 of 6 compared (0.0000%)");
  }

  /** A reporter that keeps the values published to it. */
  private static final class Recording implements Reporter {
    final Map<String, String> values = new TreeMap<>();

    @Override
    public void publishValue(String key, String value) {
      values.put(key, value);
    }

    @Override
    public void publishReport(String key, Object object) {
      values.put(key, String.valueOf(object));
    }

    @Override
    public void publishReports(String key, Map<String, Object> objects) {
      values.put(key, String.valueOf(objects));
    }
  }
}
