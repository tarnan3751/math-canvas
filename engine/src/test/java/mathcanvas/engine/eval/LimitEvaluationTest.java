package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The limit command and the lim notation through a session. */
class LimitEvaluationTest {

  @Test
  void theNotationAndTheCommandAgree() {
    assertThat(Run.shown("lim[x→0] sin(x)/x")).isEqualTo("1");
    assertThat(Run.shown("limit(sin(x)/x, x, 0)")).isEqualTo("1");
    assertThat(Run.shown("lim[x → 0+] 1/x")).isEqualTo("∞");
    assertThat(Run.shown("lim[x → 0-] 1/x")).isEqualTo("-∞");
    assertThat(Run.shown("limit(1/x, x, 0)")).isEqualTo("undefined");
    assertThat(Run.shown("lim[x → ∞] (1 + 1/x)^x")).isEqualTo("e");
    assertThat(Run.shown("lim[x -> -inf] e^x")).isEqualTo("0");
    assertThat(Run.shown("2 + lim[x→0] sin(x)/x")).isEqualTo("3");
  }

  @Test
  void theVariableIsBoundAndThePointEvaluated() {
    assertThat(Run.shown("x := 3\nlimit(x^2, x, 2)")).isEqualTo("4");
    assertThat(Run.shown("a := 5\nlimit(x + a, x, 1)")).isEqualTo("6");
    assertThat(Run.shown("p := 2\nlimit(x^2, x, p)")).isEqualTo("4");
    assertThat(Run.shown("f(t) := limit(t x, x, 2)\nf(3)")).isEqualTo("6");
    assertThat(Run.shown("limit(limit(x y, x, 2), y, 3)")).isEqualTo("6");
  }

  @Test
  void theAssumptionsOfThePointHold() {
    assertThat(Run.shown("assume(b > 0)\nlimit(e^(-b x), x, inf)")).isEqualTo("0");
    assertThat(Run.shown("limit(e^(-b x), x, inf)")).isEqualTo("lim[x → ∞] e^(-b x)");
    // inside the body the variable's own facts do not hold
    assertThat(Run.shown("assume(x > 0)\nlimit(abs(x)/x, x, 0)")).isEqualTo("undefined");
  }

  @Test
  void whatNoMethodDecidesStays() {
    assertThat(Run.shown("limit(x!, x, 1/2)")).isEqualTo("lim[x → 1/2] x!");
  }

  @Test
  void aSideAtInfinityIsAnError() {
    assertThat(Run.error("limit(e^x, x, inf, left)")).isEqualTo("a limit at ∞ or -∞ has no side");
  }

  @Test
  void theStepsAreTheDerivation() {
    // the body is simplified first, its steps before the limit's
    assertThat(NarrationTest.narrated("steps(lim[x → 1] (x^2 - 1)/(x - 1))"))
        .containsExactly(
            "Cancel the common factor x - 1: (x² - 1)/(x - 1) = x + 1 | lim[x → 1] (x + 1)",
            "By substitution, x + 1 → 2 | 2");
    // the numeric probe records an estimate, never a value
    assertThat(NarrationTest.narrated("steps(limit(mod(x, 1)/x, x, 0, right))"))
        .containsExactly(
            "No method finds lim[x → 0+] (x mod 1)/x. Approximate values suggest 1.0, to 12"
                + " digits, but that is not a proof, so the limit is left as it is | lim[x → 0+]"
                + " (x mod 1)/x");
    assertThat(NarrationTest.narrated("steps(limit(x!, x, 1/2))")).hasSize(1);
  }

  @Test
  void theBodyIsSimplifiedFirst() {
    assertThat(Run.shown("limit(sin(x)^2 + cos(x)^2, x, inf)")).isEqualTo("1");
    assertThat(Run.shown("assume(x > 0)\nlimit(sqrt(x^2)/x, x, 0, left)")).isEqualTo("-1");
  }
}
