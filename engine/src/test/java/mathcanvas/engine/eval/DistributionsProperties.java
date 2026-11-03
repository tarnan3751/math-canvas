package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.shown;
import static mathcanvas.engine.eval.Run.value;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.RealNumeric;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;
import org.hipparchus.distribution.continuous.ChiSquaredDistribution;
import org.hipparchus.distribution.continuous.FDistribution;
import org.hipparchus.distribution.continuous.NormalDistribution;
import org.hipparchus.distribution.continuous.TDistribution;

/**
 * The distributions' soundness: the binomial probabilities sum to exactly 1 and the Poisson
 * cumulative function is the sum of its probabilities; the whole normal line has probability 1;
 * each quantile inverts its cumulative function; and the closed-form densities agree with
 * Hipparchus's, an independent implementation.
 */
class DistributionsProperties {

  private static double numeric(String script) {
    Expr v = value("numeric(" + script + ", 30)");
    assertThat(v).as(script).isInstanceOf(Num.class);
    return ((RealNumeric) ((Num) v).value()).toDouble();
  }

  private static void close(String what, double got, double want, double relative) {
    assertThat(Math.abs(got - want))
        .as("%s: %s against %s", what, got, want)
        .isLessThanOrEqualTo(relative * Math.max(1, Math.abs(want)));
  }

  @Property(tries = 40)
  void binomialProbabilitiesSumToOne(
      @ForAll @IntRange(min = 0, max = 30) int n,
      @ForAll @IntRange(min = 0, max = 12) int a,
      @ForAll @IntRange(min = 1, max = 12) int b) {
    String p = Math.min(a, b) + "/" + b;
    List<String> terms = new ArrayList<>();
    for (int k = 0; k <= n; k++) {
      terms.add("binompdf(" + n + ", " + p + ", " + k + ")");
    }
    assertThat(shown(String.join(" + ", terms))).as("n = %d, p = %s", n, p).isEqualTo("1");
  }

  @Property(tries = 40)
  void poissonCumulativeIsTheSumOfItsProbabilities(
      @ForAll @IntRange(min = 1, max = 20) int a,
      @ForAll @IntRange(min = 1, max = 5) int b,
      @ForAll @IntRange(min = 0, max = 15) int k) {
    String lambda = a + "/" + b;
    List<String> terms = new ArrayList<>();
    for (int j = 0; j <= k; j++) {
      terms.add("poissonpdf(" + lambda + ", " + j + ")");
    }
    assertThat(shown("poissoncdf(" + lambda + ", " + k + ") - (" + String.join(" + ", terms) + ")"))
        .isEqualTo("0");
  }

  @Property(tries = 60)
  void quantilesInvertTheCumulativeFunctions(
      @ForAll @IntRange(min = -5000, max = 5000) int thousandths,
      @ForAll @IntRange(min = 1, max = 30) int df,
      @ForAll @IntRange(min = 1, max = 30) int df2) {
    double x = thousandths / 1000.0;
    String at = Double.toString(x);
    double z = numeric("invnorm(normalcdf(-inf, " + at + "))");
    assertThat(Math.abs(z - x)).as("normal at %s", x).isLessThanOrEqualTo(1e-10);
    double t = numeric("invt(tcdf(-inf, " + at + ", " + df + "), " + df + ")");
    close("t at " + x, t, x, 1e-9);
    // χ² and F from the probability side: a point where the cumulative function is flat (a tiny
    // probability) is no test of the quantile
    double q = 0.001 + 0.998 * (thousandths + 5000) / 10000.0;
    String p = Double.toString(q);
    assertThat(Math.abs(numeric("chi2cdf(0, invchi2(" + p + ", " + df + "), " + df + ")") - q))
        .as("χ² at %s", q)
        .isLessThanOrEqualTo(1e-12);
    assertThat(
            Math.abs(
                numeric(
                        "fcdf(0, invf("
                            + p
                            + ", "
                            + df
                            + ", "
                            + df2
                            + "), "
                            + df
                            + ", "
                            + df2
                            + ")")
                    - q))
        .as("F at %s", q)
        .isLessThanOrEqualTo(1e-12);
  }

  @Property(tries = 10)
  void theWholeNormalLineHasProbabilityOne(@ForAll @IntRange(min = 1, max = 5) int sigma) {
    close("normal", numeric("normalcdf(-inf, inf, 3, " + sigma + ")"), 1.0, 1e-15);
  }

  @Property(tries = 80)
  void closedDensitiesAgreeWithHipparchus(
      @ForAll @IntRange(min = -4000, max = 4000) int thousandths,
      @ForAll @IntRange(min = 1, max = 30) int df,
      @ForAll @IntRange(min = 1, max = 30) int df2) {
    double x = thousandths / 1000.0;
    String at = thousandths + "/1000";
    close(
        "normal at " + x,
        numeric("normalpdf(" + at + ", 1/2, 3/2)"),
        new NormalDistribution(0.5, 1.5).density(x),
        1e-12);
    close(
        "t at " + x,
        numeric("tpdf(" + at + ", " + df + ")"),
        new TDistribution(df).density(x),
        1e-12);
    if (x > 0) {
      close(
          "χ² at " + x,
          numeric("chi2pdf(" + at + ", " + df + ")"),
          new ChiSquaredDistribution(df).density(x),
          1e-12);
      close(
          "F at " + x,
          numeric("fpdf(" + at + ", " + df + ", " + df2 + ")"),
          new FDistribution(df, df2).density(x),
          1e-12);
    }
  }
}
