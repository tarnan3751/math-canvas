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
 * Regression ({@code docs/language.md} §14.25): exact fits on exact data with their {@code r²} and
 * {@code r}, decimal data, the linearised fits, residuals and predictions, the narration of the
 * normal equations with the linear algebra's steps, and the errors.
 */
class RegressionTest {

  private static Outcome.Value run(String script) {
    Session s = new Session(Environment.EMPTY.withSettings(Settings.DEFAULT));
    return (Outcome.Value) s.run(Parser.parseScript(script, s.names())).getLast();
  }

  @Test
  void polynomialFitsAreExact() {
    assertThat(shown("linreg([1, 2, 3], [1, 2, 2])"))
        .isEqualTo("[y = x/2 + 2/3, r² = 3/4, r = √3/2]");
    assertThat(shown("linreg([[1, 1], [2, 2], [3, 2]])"))
        .isEqualTo("[y = x/2 + 2/3, r² = 3/4, r = √3/2]");
    assertThat(shown("linreg([1, 2, 3], [3, 2, 2])"))
        .isEqualTo("[y = -x/2 + 10/3, r² = 3/4, r = -√3/2]");
    assertThat(shown("quadreg([0, 1, 2, 3], [1, 2, 5, 10])")).isEqualTo("[y = x² + 1, r² = 1]");
    assertThat(shown("cubicreg([0, 1, 2, 3, 4], [0, 1, 8, 27, 64])")).isEqualTo("[y = x³, r² = 1]");
    assertThat(shown("polyreg([0, 1, 2], [1, 3, 7], 2)")).isEqualTo("[y = x² + x + 1, r² = 1]");
    assertThat(shown("linreg([1, 2, 3], [5, 5, 5])"))
        .isEqualTo("[y = 5, r² = undefined, r = undefined]");
  }

  @Test
  void decimalDataIsFittedExactlyAndRoundedOnce() {
    // Anscombe's first set (Anscombe 1973)
    assertThat(
            shown(
                "numeric(linreg([10, 8, 13, 9, 11, 14, 6, 4, 12, 7, 5], [8.04, 6.95, 7.58, 8.81,"
                    + " 8.33, 9.96, 7.24, 4.26, 10.84, 4.82, 5.68]), 4)"))
        .isEqualTo("[y = 0.5001x + 3.0, r² = 0.6665, r = 0.8164]");
  }

  @Test
  void linearisedFitsAreNumeric() {
    assertThat(shown("numeric(expreg([0, 1, 2, 3], [2, 6, 18, 54]), 10)"))
        .isEqualTo("[y = 2.0·3.0^x, r² = 1.0]");
    assertThat(shown("numeric(powreg([1, 2, 3, 4], [3, 12, 27, 48]), 10)"))
        .isEqualTo("[y = 3.0x^(2.0), r² = 1.0]");
    assertThat(shown("numeric(lnreg([1, 2, 4, 8], [1, 2, 3, 4]), 10)"))
        .isEqualTo("[y = 1.442695041 log(x) + 1.0, r² = 1.0]");
    assertThat(run("expreg([0, 1, 2, 3], [2, 6, 18, 54])").numeric()).isTrue();
    assertThat(run("linreg([1, 2, 3], [1, 2, 2])").numeric()).isFalse();
  }

  @Test
  void residualsAndPredictions() {
    assertThat(shown("residuals([1, 2, 3], [1, 2, 2], linreg([1, 2, 3], [1, 2, 2]))"))
        .isEqualTo("⟨-1/6, 1/3, -1/6⟩");
    assertThat(shown("predict(linreg([1, 2, 3], [1, 2, 2]), 4)")).isEqualTo("8/3");
    assertThat(shown("predict(y = 2x + 1, t)")).isEqualTo("2t + 1");
    assertThat(shown("nth(linreg([1, 2, 3], [1, 2, 2]), 1)")).isEqualTo("y = x/2 + 2/3");
  }

  @Test
  void theNormalEquationsAreNarrated() {
    Outcome.Value v = run("steps(linreg([1, 2, 3], [1, 2, 2]))");
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    List<String> texts =
        Narration.steps(new Evaluator.Result(v.value(), v.trace()), Context.DEFAULT).stream()
            .map(Step::text)
            .toList();
    assertThat(texts.getFirst())
        .isEqualTo(
            "Fit y = a x + b by least squares to the 3 points: the normal equations XᵀX·β = Xᵀy"
                + " are linsolve([[14, 6], [6, 3]], ⟨11, 5⟩)");
    assertThat(texts).contains("Write the augmented matrix [A | b]: [[14, 6, 11], [6, 3, 5]]");
    assertThat(texts)
        .anyMatch(t -> t.startsWith("The fitted equation, with r² = 1 - SSres/SStot = 3/4"));
  }

  @Test
  void wrongDataIsAnError() {
    assertThat(error("linreg([1, 1, 1], [1, 2, 3])"))
        .isEqualTo("linreg needs at least 2 distinct x values");
    assertThat(error("quadreg([1, 2], [1, 2])"))
        .isEqualTo("quadreg needs at least 3 distinct x values");
    assertThat(error("polyreg([1, 2], [1, 2], 11)"))
        .isEqualTo("polyreg's degree must be an integer from 1 to 10");
    assertThat(error("expreg([1, 2], [1, -2])")).isEqualTo("expreg needs positive y values");
    assertThat(error("lnreg([0, 2], [1, 2])")).isEqualTo("lnreg needs positive x values");
    assertThat(error("linreg([1, 2], [1, 2, 3])"))
        .isEqualTo("linreg needs two lists of the same length or a matrix of two columns");
    assertThat(error("predict(3, 4)"))
        .isEqualTo("predict needs a fit: a relation y = … or the list a fit gives");
  }
}
