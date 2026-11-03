package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.shown;
import static mathcanvas.engine.eval.Run.value;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.stat.numeric.DistributionMath;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;

/**
 * The soundness of the {@code special} rule set and of the error functions' derivatives: the exact
 * values the rules give against Hipparchus's {@code double} functions, and the derivatives against
 * central difference quotients.
 */
class SpecialFunctionsProperties {

  private static double numeric(String script) {
    Expr v = value("numeric(" + script + ", 30)");
    assertThat(v).as(script).isInstanceOf(Num.class);
    return ((RealNumeric) ((Num) v).value()).toDouble();
  }

  private static void close(double got, double want, double relative, String what) {
    assertThat(Math.abs(got - want))
        .as("%s: %s against %s", what, got, want)
        .isLessThanOrEqualTo(relative * Math.max(1, Math.abs(want)));
  }

  /** {@code Γ} at the integers and half-integers in {@code (−40, 40)}, away from the poles. */
  @Property(tries = 200)
  void gammaValuesAreRight(@ForAll @IntRange(min = -79, max = 79) int twice) {
    double x = twice / 2.0;
    if (x <= 0 && x == Math.rint(x)) {
      assertThat(shown("gamma(" + twice + "/2)")).isEqualTo("undefined");
      return;
    }
    String script = "gamma(" + twice + "/2)";
    assertThat(shown(script)).as(script).doesNotContain("gamma");
    close(numeric(script), DistributionMath.gamma(x), 1e-13, script);
  }

  /** {@code B(a, b)} at positive integers and half-integers. */
  @Property(tries = 200)
  void betaValuesAreRight(
      @ForAll @IntRange(min = 1, max = 40) int a, @ForAll @IntRange(min = 1, max = 40) int b) {
    String script = "beta(" + a + "/2, " + b + "/2)";
    assertThat(shown(script)).as(script).doesNotContain("beta");
    close(numeric(script), DistributionMath.beta(a / 2.0, b / 2.0), 1e-13, script);
  }

  /** The symmetries of {@code erf} and {@code erfc} at rationals of either sign. */
  @Property(tries = 200)
  void errorFunctionSymmetriesHold(@ForAll @IntRange(min = -400, max = 400) int n) {
    String x = n + "/100";
    close(numeric("erf(" + x + ")"), DistributionMath.erf(n / 100.0), 1e-15, "erf " + x);
    close(numeric("erfc(" + x + ")"), DistributionMath.erfc(n / 100.0), 1e-15, "erfc " + x);
  }

  /** {@code d/dx erf(u)} and {@code erfc(u)} against central difference quotients. */
  @Property(tries = 100)
  void errorFunctionDerivativesAreRight(
      @ForAll @DoubleRange(min = -2, max = 2) double at,
      @ForAll @IntRange(min = 1, max = 3) int k) {
    for (String f : new String[] {"erf", "erfc"}) {
      String u = k + "x^2 - x";
      String point = new BigDecimal(at).toPlainString();
      double derivative = numeric("subs(diff(" + f + "(" + u + "), x), x = " + point + ")");
      double h = 1e-6;
      double plus = special(f, k * Math.pow(at + h, 2) - (at + h));
      double minus = special(f, k * Math.pow(at - h, 2) - (at - h));
      close(derivative, (plus - minus) / (2 * h), 1e-6, f + " at " + at);
    }
  }

  private static double special(String f, double u) {
    return f.equals("erf") ? DistributionMath.erf(u) : DistributionMath.erfc(u);
  }
}
