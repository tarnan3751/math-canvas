package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Step;
import org.junit.jupiter.api.Test;

/**
 * The hypothesis tests ({@code docs/language.md} §14.25): the exact statistic and degrees of
 * freedom, the numeric {@code p}-value for each alternative, the words read as written, the
 * narration, and the errors.
 */
class HypothesisTestsTest {

  private static Outcome.Value run(String script) {
    Session s = new Session(Environment.EMPTY.withSettings(Settings.DEFAULT));
    return (Outcome.Value) s.run(Parser.parseScript(script, s.names())).getLast();
  }

  private static List<String> steps(String script) {
    Outcome.Value v = run("steps(" + script + ")");
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    return Narration.steps(new Evaluator.Result(v.value(), v.trace()), Context.DEFAULT).stream()
        .map(Step::text)
        .toList();
  }

  @Test
  void oneSampleTests() {
    assertThat(shown("ttest([4, 5, 6, 7, 8], 5)"))
        .isEqualTo("[t = √2, df = 4, p = 0.2301996410804988]");
    assertThat(shown("ttest([4, 5, 6, 7, 8], 5, greater)"))
        .isEqualTo("[t = √2, df = 4, p = 0.1150998205402494]");
    assertThat(shown("ttest(6, 2, 25, 5)"))
        .isEqualTo("[t = 5/2, df = 24, p = 0.01965417511657875]");
    assertThat(shown("ztest([4, 5, 6, 7, 8], 2, 5)"))
        .isEqualTo("[z = √5/2, p = 0.2635524772829726]");
    assertThat(shown("ztest(6, 2, 25, 5, less)")).isEqualTo("[z = 5/2, p = 0.9937903346742238]");
    assertThat(shown("ttest([4.0, 5, 6, 7, 8], 5)"))
        .isEqualTo("[t = 1.414213562373095048801688724209698, df = 4, p = 0.2301996410804988]");
    assertThat(run("ttest([4, 5, 6, 7, 8], 5)").numeric()).isTrue();
  }

  @Test
  void twoSampleAndPairedTests() {
    assertThat(shown("ttest2([1, 2, 3, 4], [3, 4, 5, 7])"))
        .isEqualTo("[t = -9√165/55, df = 363/65, p = 0.0837069647040257]");
    assertThat(shown("ttest2([1, 2, 3, 4], [3, 4, 5, 7], pooled)"))
        .isEqualTo("[t = -9√165/55, df = 6, p = 0.08026132621897218]");
    assertThat(shown("ttest2([1, 2, 3, 4], [3, 4, 5, 7], pooled, less)"))
        .isEqualTo("[t = -9√165/55, df = 6, p = 0.04013066310948609]");
    assertThat(shown("pairedttest([1, 2, 3, 4], [2, 2, 5, 7])"))
        .isEqualTo("[t = -3√15/5, df = 3, p = 0.102728078858399]");
    assertThat(shown("pairedttest([[1, 2], [2, 2], [3, 5], [4, 7]], greater)"))
        .isEqualTo("[t = -3√15/5, df = 3, p = 0.9486359605708005]");
  }

  @Test
  void proportionTests() {
    assertThat(shown("proptest(60, 100, 1/2)")).isEqualTo("[z = 2, p = 0.04550026389635833]");
    assertThat(shown("proptest(60, 100, 0.5, greater)"))
        .isEqualTo("[z = 2.0, p = 0.02275013194817917]");
    assertThat(shown("proptest2(60, 100, 45, 100)"))
        .isEqualTo("[z = 10√798/133, p = 0.03367206885634577]");
  }

  @Test
  void chiSquaredAndAnova() {
    assertThat(shown("chi2gof([20, 30, 50], [25, 25, 50])"))
        .isEqualTo("[χ² = 2, df = 2, p = 0.3678794411714422]");
    assertThat(shown("chi2test([[10, 20], [30, 40]])"))
        .isEqualTo("[χ² = 50/63, df = 1, p = 0.3729984836134871]");
    assertThat(shown("anova([1, 2, 3], [4, 5, 6], [7, 8, 9])"))
        .isEqualTo("[F = 27, df1 = 2, df2 = 6, p = 0.001000000000000001]");
  }

  @Test
  void theWordsAreReadAsWritten() {
    assertThat(shown("two := 3; ttest([4, 5, 6, 7, 8], 5, two)"))
        .isEqualTo("[t = √2, df = 4, p = 0.2301996410804988]");
    assertThat(shown("ttest([4, 5, 6, 7, 8], m)")).isEqualTo("ttest(⟨4, 5, 6, 7, 8⟩, m)");
  }

  @Test
  void theNarrationStatesTheTest() {
    assertThat(steps("ttest([4, 5, 6, 7, 8], 5)"))
        .containsExactly(
            "The hypotheses H₀ and H₁, two-sided: [μ = 5, μ ≠ 5]",
            "From the data, n = 5, x̄ = 6 and s = √10/2: ttest(6, √10/2, 5, 5)",
            "The statistic t = (x̄ - μ₀)/(s/√n), with x̄ = 6, μ₀ = 5, s = √10/2 and n = 5: [t = √2]",
            "The degrees of freedom: [t = √2, df = 4]",
            "The approximate p-value, the probability of a statistic at least as far from 0 on"
                + " either side: [t = √2, df = 4, p = 0.2301996410804988]");
    assertThat(steps("chi2test([[10, 20], [30, 40]])"))
        .contains(
            "The expected counts if the rows and the columns are independent, row total × column"
                + " total / grand total: [[12, 18], [28, 42]]");
    assertThat(steps("ttest2([1, 2, 3, 4], [3, 4, 5, 7])"))
        .contains("The degrees of freedom by Welch–Satterthwaite: [t = -9√165/55, df = 363/65]");
  }

  @Test
  void wrongArgumentsAreErrors() {
    assertThat(error("ttest([1, 1, 1], 0)")).isEqualTo("`ttest` needs s > 0");
    assertThat(error("proptest(120, 100, 1/2)"))
        .isEqualTo("`proptest` needs an integer x with 0 ≤ x ≤ n");
    assertThat(error("chi2gof([20, 30], [0, 50])"))
        .isEqualTo("`chi2gof` needs positive expected counts");
    assertThat(error("ttest([4, 5, 6], 5, sideways)"))
        .isEqualTo("`ttest`'s alternative must be two, less or greater");
    assertThat(error("ztest([4, 5, 6], 2, 5, pooled)"))
        .isEqualTo("`ztest` has no `pooled` option: `pooled` belongs to `ttest2` and `tinterval2`");
    assertThat(error("chi2test([1, 2])"))
        .isEqualTo("`chi2test` needs a matrix of at least 2 rows and 2 columns");
    assertThat(error("anova([1], [2])")).isEqualTo("`anova` needs more values than groups");
    // the summary form needs the data form's minimum
    assertThat(error("ttest(6, 2, 1, 5)")).isEqualTo("`ttest` needs at least 2 values");
    assertThat(shown("ztest(6, 2, 1, 5)")).startsWith("[z = 1/2, p = ");
    assertThat(error("chi2gof([10, 20], [15, 20])"))
        .isEqualTo("`chi2gof` needs expected counts that sum to the observed total: 35 against 30");
    // the totals agree within a relative 1e-3: rounded textbook counts pass
    assertThat(shown("chi2gof([30, 40, 30], [33.33, 33.33, 33.33])")).startsWith("[χ² = ");
    assertThat(error("chi2gof([30, 40, 30], [33.0, 33.0, 33.0])"))
        .isEqualTo(
            "`chi2gof` needs expected counts that sum to the observed total: 99.0 against 100.0");
  }
}
