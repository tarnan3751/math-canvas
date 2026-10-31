package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The taylor command through a session. */
class TaylorEvaluationTest {

  @Test
  void aNumericOrderGivesThePolynomialInTheDisplayOrder() {
    assertThat(Run.shown("taylor(e^x, x, 0, 4)")).isEqualTo("x⁴/24 + x³/6 + x²/2 + x + 1");
    assertThat(Run.shown("taylor(sin(x), x, 0, 7)")).isEqualTo("-x⁷/5040 + x⁵/120 - x³/6 + x");
    assertThat(Run.shown("taylor(log(x), x, 1, 3)")).isEqualTo("x - (x - 1)²/2 + (x - 1)³/3 - 1");
    assertThat(Run.shown("taylor(e^x, x, 1, 2)")).isEqualTo("e + e·(x - 1) + e·(x - 1)²/2");
    assertThat(Run.shown("taylor(x^3, x, 0, 0)")).isEqualTo("0");
    assertThat(Run.shown("n := 3; taylor(1/(1 - x), x, 0, n)")).isEqualTo("x³ + x² + x + 1");
  }

  @Test
  void aSymbolicOrderStays() {
    assertThat(Run.shown("taylor(e^x, x, 0, n)")).isEqualTo("taylor(e^x, x, 0, n)");
  }

  @Test
  void theVariableIsFreeInThePolynomial() {
    assertThat(Run.shown("T(x) := taylor(e^x, x, 0, 3)\nT(1)")).isEqualTo("8/3");
    assertThat(Run.shown("x := 2\ntaylor(x^2, x, 1, 1)")).isEqualTo("3");
  }

  @Test
  void theErrorsNameTheOrder() {
    assertThat(Run.error("taylor(e^x, x, 0, 1/2)"))
        .isEqualTo("taylor's order must be a non-negative integer");
    assertThat(Run.error("taylor(e^x, x, 0, -1)"))
        .isEqualTo("taylor's order must be a non-negative integer");
    assertThat(Run.error("taylor(e^x, x, inf, 2)")).isEqualTo("taylor's point must be finite");
    assertThat(Run.error("taylor(sqrt(x), x, 0, 2)"))
        .isEqualTo("the derivative of order 1 could not be shown continuous at x = 0");
    // |x| is defined at 0 but not differentiable there, whatever the derivative rules say
    assertThat(Run.error("taylor(abs(x), x, 0, 1)"))
        .isEqualTo("the derivative of order 1 could not be shown continuous at x = 0");
    // the check is conservative: x^(5/2) is twice differentiable at 0, but not shown so
    assertThat(Run.error("taylor(x^(5/2), x, 0, 2)"))
        .isEqualTo("the derivative of order 1 could not be shown continuous at x = 0");
    assertThat(Run.error("taylor(sin(x)/x, x, 0, 2)"))
        .isEqualTo("the derivative of order 0 is undefined at x = 0");
    assertThat(Run.error("taylor(floor(x), x, 1, 2)"))
        .isEqualTo("the derivative of order 1 could not be shown continuous at x = 1");
    assertThat(Run.shown("taylor(floor(x), x, 1/2, 2)")).isEqualTo("0");
  }

  @Test
  void anglesAreRadians() {
    assertThat(Run.shown("taylor(sin(x), x, 0, 3)", Run.DEGREES)).isEqualTo("-x³/6 + x");
  }

  @Test
  void theStepsListTheDerivativesAtThePoint() {
    assertThat(NarrationTest.narrated("steps(taylor(cos(x), x, 0, 2))"))
        .containsExactly(
            "The derivatives at x = 0 are 1 (order 0), 0 (order 1), -1 (order 2); the Taylor"
                + " polynomial is -x²/2 + 1 | -x²/2 + 1");
  }
}
