package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * The special functions ({@code docs/language.md} §12.1): the exact values of the {@code special}
 * rule set, the values at 0, the decimals through the distribute provider at most 16 digits, the
 * poles, and the derivatives of {@code erf} and {@code erfc}.
 */
class SpecialFunctionsTest {

  @Test
  void gammaIsExactAtIntegersAndHalfIntegers() {
    assertThat(shown("gamma(5)")).isEqualTo("24");
    assertThat(shown("gamma(1)")).isEqualTo("1");
    assertThat(shown("gamma(1/2)")).isEqualTo("√π");
    assertThat(shown("gamma(5/2)")).isEqualTo("3√π/4");
    assertThat(shown("gamma(-1/2)")).isEqualTo("-2√π");
    assertThat(shown("gamma(-3/2)")).isEqualTo("4√π/3");
    assertThat(shown("gamma(0)")).isEqualTo("undefined");
    assertThat(shown("gamma(-3)")).isEqualTo("undefined");
    assertThat(shown("gamma(1/3)")).isEqualTo("gamma(1/3)");
  }

  @Test
  void betaGoesThroughGamma() {
    assertThat(shown("beta(1/2, 1/2)")).isEqualTo("π");
    assertThat(shown("beta(2, 3)")).isEqualTo("1/12");
    assertThat(shown("beta(1/3, 2)")).isEqualTo("beta(1/3, 2)");
    // a + b past the bound of the gamma rules: the call stays rather than a gamma of it
    assertThat(shown("beta(6000, 6000)")).isEqualTo("beta(6000, 6000)");
    assertThat(shown("beta(5000, 5000)")).doesNotContain("beta").doesNotContain("gamma");
  }

  @Test
  void theErrorFunctionsAreOddAndHaveTheirLimits() {
    assertThat(shown("erf(0)")).isEqualTo("0");
    assertThat(shown("erfc(0)")).isEqualTo("1");
    assertThat(shown("erf(-x)")).isEqualTo("-erf(x)");
    assertThat(shown("erfc(-2)")).isEqualTo("-erfc(2) + 2");
    assertThat(shown("erf(inf)")).isEqualTo("1");
    assertThat(shown("erf(-inf)")).isEqualTo("-1");
    assertThat(shown("erfc(inf)")).isEqualTo("0");
    assertThat(shown("erf(1/3)")).isEqualTo("erf(1/3)");
  }

  @Test
  void decimalsAreDoubles() {
    assertThat(shown("erf(1.0)")).isEqualTo("0.8427007929497151");
    assertThat(shown("gamma(0.5)")).isEqualTo("1.772453850905516");
    assertThat(shown("numeric(erf(1), 5)")).isEqualTo("0.8427");
    assertThat(shown("numeric(gamma(1/3))")).isEqualTo("2.678938534707748");
    assertThat(shown("gamma(-2.0)")).isEqualTo("undefined");
    assertThat(shown("beta(2.0, 3)")).isEqualTo("0.08333333333333333");
    assertThat(error("gamma(200.0)"))
        .isEqualTo("`gamma`'s value is too large, or too close to zero, to approximate");
    // a non-zero value that underflows is beyond the range too; a true zero is a value
    assertThat(error("gamma(-200.5)"))
        .isEqualTo("`gamma`'s value is too large, or too close to zero, to approximate");
    assertThat(error("numeric(beta(6000, 6000))"))
        .isEqualTo("`beta`'s value is too large, or too close to zero, to approximate");
    assertThat(error("erfc(30.0)"))
        .isEqualTo("`erfc`'s value is too large, or too close to zero, to approximate");
    assertThat(shown("beta(0.5, -0.5)")).isEqualTo("0.0");
  }

  @Test
  void theErrorFunctionsDifferentiate() {
    assertThat(shown("diff(erf(x), x)")).isEqualTo("2 e^(-x²)/√π");
    assertThat(shown("diff(erfc(2x), x)")).isEqualTo("-4 e^(-4x²)/√π");
  }
}
