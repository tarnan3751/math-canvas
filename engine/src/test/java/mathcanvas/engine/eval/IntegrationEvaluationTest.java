package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@code integrate} and {@code ∫} in the evaluator ({@code docs/language.md} §14.13). */
class IntegrationEvaluationTest {

  @Test
  void theNotationAndTheCommandAreOne() {
    assertThat(shown("∫ x^2 dx")).isEqualTo("x³/3");
    assertThat(shown("integrate(x^2, x)")).isEqualTo("x³/3");
    assertThat(shown("∫∫ x y dx dy")).isEqualTo("x² y²/4");
  }

  @Test
  void anIndefiniteIntegralsVariableIsFreeInItsResult() {
    assertThat(shown("x := 3\n∫ x^2 dx")).isEqualTo("9");
    assertThat(shown("f(x) := ∫ x^2 dx\nf(2)")).isEqualTo("8/3");
    assertThat(shown("subs(∫ x^2 dx, x = 3)")).isEqualTo("9");
    assertThat(shown("f(y) := integrate(x y + x^2, x)\nf(x)")).isEqualTo("x x₁²/2 + x₁³/3");
  }

  @Test
  void whatCannotBeIntegratedStays() {
    assertThat(shown("∫ e^(x^2) dx")).isEqualTo("∫ e^(x²) dx");
    assertThat(shown("∫ x + x! dx")).isEqualTo("∫ x + x! dx");
    assertThat(shown("integrate(x^2, 2)")).isEqualTo("integrate(x², 2)");
  }
}
