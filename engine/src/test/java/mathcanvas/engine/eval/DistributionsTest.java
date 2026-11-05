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
 * The probability distributions ({@code docs/language.md} §14.25): the exact cases, the closed
 * densities, the values the provider gives with their mark and step, the bound past which a
 * discrete law stays, the call left for a symbol, and the errors.
 */
class DistributionsTest {

  private static Outcome.Value run(String script) {
    Session s = new Session(Environment.EMPTY.withSettings(Settings.DEFAULT));
    return (Outcome.Value) s.run(Parser.parseScript(script, s.names())).getLast();
  }

  @Test
  void discreteLawsAreExactWhereRational() {
    assertThat(shown("binompdf(10, 1/2, 3)")).isEqualTo("15/128");
    assertThat(shown("binomcdf(10, 1/2, 3)")).isEqualTo("11/64");
    assertThat(shown("binompdf(10, 1/2, 11)")).isEqualTo("0");
    assertThat(shown("binomcdf(10, 1/2, 11)")).isEqualTo("1");
    assertThat(shown("poissonpdf(2, 3)")).isEqualTo("4/(3 e²)");
    assertThat(shown("poissoncdf(2, 3)")).isEqualTo("19/(3 e²)");
    assertThat(shown("geometpdf(1/4, 3)")).isEqualTo("9/64");
    assertThat(shown("geometcdf(1/4, 2)")).isEqualTo("7/16");
    assertThat(shown("hypergeompdf(10, 4, 3, 2)")).isEqualTo("3/10");
    assertThat(shown("hypergeomcdf(10, 4, 3, 1)")).isEqualTo("2/3");
  }

  @Test
  void densitiesHaveClosedForms() {
    assertThat(shown("normalpdf(0)")).isEqualTo("√2/(2√π)");
    assertThat(shown("normalpdf(x)")).isEqualTo("√2 e^(-x²/2)/(2√π)");
    assertThat(shown("normalpdf(x, 1, 2)")).isEqualTo("√2 e^(-(x - 1)²/8)/(4√π)");
    assertThat(shown("tpdf(0, 1)")).isEqualTo("1/π");
    assertThat(shown("tpdf(x, 3)")).isEqualTo("6√3/(π·(x² + 3)²)");
    assertThat(shown("chi2pdf(2, 2)")).isEqualTo("1/(2 e)");
    assertThat(shown("chi2pdf(0, 2)")).isEqualTo("1/2");
    assertThat(shown("chi2pdf(0, 3)")).isEqualTo("0");
    assertThat(shown("chi2pdf(0, 1)")).isEqualTo("undefined");
    assertThat(shown("chi2pdf(-1, 3)")).isEqualTo("0");
    assertThat(shown("fpdf(1, 2, 2)")).isEqualTo("1/4");
    assertThat(shown("exppdf(1, 2)")).isEqualTo("2/e²");
    assertThat(shown("exppdf(-1, 2)")).isEqualTo("0");
    assertThat(shown("unifpdf(1, 0, 4)")).isEqualTo("1/4");
    assertThat(shown("expcdf(0, 1, 2)")).isEqualTo("-1/e² + 1");
    assertThat(shown("expcdf(-inf, inf, 2)")).isEqualTo("1");
    assertThat(shown("unifcdf(-1, 1, 0, 4)")).isEqualTo("1/4");
    // a decimal point: the closed form at the precision
    assertThat(shown("normalpdf(1.0)")).isEqualTo("0.2419707245191433497978301929355607");
    assertThat(shown("tpdf(1.0, 10)")).isEqualTo("0.2303619892291386471078692684488572");
  }

  @Test
  void theNormalTChiSquaredAndFAreNumeric() {
    Outcome.Value v = run("normalcdf(-1.96, 1.96)");
    assertThat(v.numeric()).isTrue();
    assertThat(shown("normalcdf(-1.96, 1.96)")).isEqualTo("0.9500042097035593");
    assertThat(shown("numeric(normalcdf(-1.96, 1.96), 4)")).isEqualTo("0.95");
    assertThat(shown("normalcdf(1, -1)")).isEqualTo("-0.6826894921370861");
    assertThat(shown("normalcdf(-inf, inf)")).isEqualTo("1.0");
    assertThat(shown("invnorm(0.975)")).isEqualTo("1.959963984540054");
    assertThat(shown("invnorm(0)")).isEqualTo("-∞");
    assertThat(shown("invt(0.975, 10)")).isEqualTo("2.228138851986274");
    assertThat(shown("invchi2(0.95, 3)")).isEqualTo("7.814727903251183");
    assertThat(shown("invf(0.95, 5, 10)")).isEqualTo("3.325834530413011");
    assertThat(shown("tcdf(-2, 2, 5)")).isEqualTo("0.8980605211701416");
    assertThat(run("binompdf(10, 1/2, 3)").numeric()).isFalse();
    assertThat(run("binompdf(10, 0.5, 3)").numeric()).isTrue();
  }

  @Test
  void theStepSaysTheValueIsNumeric() {
    Outcome.Value v = run("steps(normalcdf(-1, 1))");
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    List<Step> steps = Narration.steps(new Evaluator.Result(v.value(), v.trace()), Context.DEFAULT);
    assertThat(steps)
        .extracting(Step::text)
        .containsExactly("Approximate value, shown to at most 16 digits");
  }

  /**
   * A closed density that keeps a gamma or beta with no exact value is the provider's, marked and
   * with its step, not a double widened to the precision; one whose values are exact is at the
   * precision. The references are mpmath's.
   */
  @Test
  void aDensityWithAnInexactGammaIsTheProvidersValue() {
    for (String[] c :
        new String[][] {
          {"chi2pdf(2.0, 3.5)", "0.2001384184560116"},
          {"tpdf(1.0, 2.5)", "0.2007959606709201"},
          {"fpdf(1.0, 3.5, 4)", "0.3606866474071874"},
          {"numeric(chi2pdf(2, 7/2))", "0.2001384184560116"}
        }) {
      Outcome.Value v = run("steps(" + c[0] + ")");
      assertThat(shown(c[0])).as(c[0]).isEqualTo(c[1]);
      assertThat(v.numeric()).as(c[0]).isTrue();
      assertThat(Narration.steps(new Evaluator.Result(v.value(), v.trace()), Context.DEFAULT))
          .extracting(Step::text)
          .as(c[0])
          .containsExactly("Approximate value, shown to at most 16 digits");
    }
    // exact gamma and beta values: every digit at the precision, unmarked
    assertThat(shown("numeric(chi2pdf(2, 3))")).isEqualTo("0.2075537487102973516701341247206687");
    assertThat(shown("numeric(fpdf(1, 3, 4))")).isEqualTo("0.3435500312753232824597453123161356");
    assertThat(shown("numeric(tpdf(1, 3))")).isEqualTo("0.2067483357831720185667474368673635");
    assertThat(run("numeric(tpdf(1, 3))").numeric()).isFalse();
  }

  @Test
  void aLargeDiscreteLawStaysUntilNumeric() {
    assertThat(shown("binompdf(3000, 1/2, 1500)")).isEqualTo("binompdf(3000, 1/2, 1500)");
    assertThat(shown("numeric(binompdf(3000, 1/2, 1500), 5)")).isEqualTo("0.014566");
    assertThat(run("binompdf(3000, 1/2, 1500)").numeric()).isFalse();
    // under numeric, through the provider: marked, with its step, also through a definition
    Outcome.Value v = run("steps(numeric(binompdf(3000, 1/2, 1500)))");
    assertThat(v.numeric()).isTrue();
    assertThat(Narration.steps(new Evaluator.Result(v.value(), v.trace()), Context.DEFAULT))
        .extracting(Step::text)
        .containsExactly("Approximate value, shown to at most 16 digits");
    assertThat(run("v := binompdf(3000, 1/2, 1500); numeric(v)").numeric()).isTrue();
    assertThat(run("numeric(binompdf(3000, 1/2, 1500) + 1)").numeric()).isTrue();
    assertThat(error("numeric(binompdf(10^12, 1/2, 3))"))
        .isEqualTo(
            "binompdf's numeric value needs n ≤ 2147483647, the int range of the distribution"
                + " provider");
  }

  /**
   * A quantile above the median is asked of its upper tail, formed exactly: {@code 1 − q} in {@code
   * double} would carry {@code q}'s rounding, amplified by {@code 1/(1 − q)}. The references are
   * mpmath's at 50 digits.
   */
  @Test
  void anUpperQuantileKeepsTheDigitsOfItsTail() {
    close("invf(1 - 10^(-12), 3, 7)", 7966.5510382581691689, 1e-13);
    close("invchi2(1 - 10^(-12), 1)", 50.844127911818155546, 1e-13);
    close("invnorm(1 - 10^(-10))", 6.3613409024040561991, 1e-13);
    close("invt(1 - 10^(-20), 3)", 4795275.7204689731896, 1e-13);
    close("invnorm(1 - 10^(-300))", 37.047096299361199237, 1e-13);
    close("invt(0.975, 100000)", 1.9599877075346096386, 1e-13);
    close("invt(0.975, 10^6)", 1.9599663568141070353, 1e-9);
  }

  @Test
  void aTailBeyondTheDoubleRange() {
    // a subnormal tail stays with its note; one that rounds to 0 is an error naming the tail
    assertThat(shown("invnorm(1e-320)")).isEqualTo("invnorm(1.0e-320)");
    assertThat(error("invnorm(10^(-400))")).isEqualTo("invnorm's tail is beyond the double range");
    assertThat(error("invt(1 - 10^(-400), 3)")).isEqualTo("invt's tail is beyond the double range");
  }

  private static void close(String script, double want, double relative) {
    double got = Double.parseDouble(shown(script));
    assertThat(Math.abs(got - want)).as(script).isLessThanOrEqualTo(relative * Math.abs(want));
  }

  @Test
  void aQuantileTheProviderCannotFindLeavesTheCall() {
    // a subnormal probability: the search's check fails, and the call stays with its note
    Outcome.Value v = run("steps(invt(1e-320, 3))");
    assertThat(v.numeric()).isFalse();
    assertThat(shown("invt(1e-320, 3)")).isEqualTo("invt(1.0e-320, 3)");
    assertThat(Narration.steps(new Evaluator.Result(v.value(), v.trace()), Context.DEFAULT))
        .extracting(Step::text)
        .containsExactly(
            "No approximate value reaches the needed accuracy, so the function is left as it is");
  }

  @Test
  void aRationalPoissonCumulativeIsOneRational() {
    assertThat(shown("poissoncdf(7/3, 2)")).isEqualTo("109/(18 e^(7/3))");
    // two thousand terms in one rational
    assertThat(shown("numeric(poissoncdf(5, 2000), 10)")).isEqualTo("1.0");
  }

  @Test
  void theGeometricLawAtPOneIsTheFirstTrial() {
    assertThat(shown("geometpdf(1, 1)")).isEqualTo("1");
    assertThat(shown("geometpdf(1, 2)")).isEqualTo("0");
    assertThat(shown("geometcdf(1, 3)")).isEqualTo("1");
    assertThat(shown("geometpdf(1.0, 1)")).isEqualTo("1.0");
    assertThat(shown("geometpdf(1.0, 2)")).isEqualTo("0.0");
    assertThat(shown("geometcdf(1.0, 3)")).isEqualTo("1.0");
  }

  @Test
  void aSymbolLeavesTheCall() {
    assertThat(shown("binompdf(3, p, 1)")).isEqualTo("binompdf(3, p, 1)");
    assertThat(shown("chi2pdf(x, 3)")).isEqualTo("chi2pdf(x, 3)");
    assertThat(shown("normalcdf(-inf, x)")).isEqualTo("normalcdf(-∞, x)");
    assertThat(shown("tpdf(x, n)")).isEqualTo("tpdf(x, n)");
  }

  @Test
  void parametersOutsideTheirDomainAreErrors() {
    assertThat(error("normalpdf(0, 0, 0)")).isEqualTo("normalpdf needs σ > 0");
    assertThat(error("tpdf(1, 0)")).isEqualTo("tpdf needs df > 0");
    assertThat(error("binompdf(10, 2, 3)")).isEqualTo("binompdf needs 0 ≤ p ≤ 1");
    assertThat(error("binompdf(2.5, 1/2, 1)")).isEqualTo("binompdf needs a non-negative integer n");
    assertThat(error("binompdf(10, 1/2, 1/2)")).isEqualTo("binompdf needs an integer k");
    assertThat(error("geometpdf(0, 1)")).isEqualTo("geometpdf needs 0 < p ≤ 1");
    assertThat(error("hypergeompdf(5, 6, 1, 0)"))
        .isEqualTo("hypergeompdf needs integers 0 ≤ K ≤ N and 0 ≤ n ≤ N");
    assertThat(error("invnorm(2)")).isEqualTo("invnorm needs 0 ≤ p ≤ 1");
    assertThat(error("unifpdf(1, 2, 2)")).isEqualTo("unifpdf needs a < b");
    assertThat(error("normalcdf(i, 1)")).isEqualTo("normalcdf needs a real bound");
  }
}
