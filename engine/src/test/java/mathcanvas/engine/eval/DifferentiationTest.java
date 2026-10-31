package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Trace;
import org.junit.jupiter.api.Test;

/** The diff command and the d/dx notation through a session. */
class DifferentiationTest {

  @Test
  void theNotationAndTheCommandDifferentiate() {
    assertThat(Run.shown("d/dx (x^2 sin x)")).isEqualTo("x² cos(x) + 2x sin(x)");
    assertThat(Run.shown("diff(x^2 sin(x), x)")).isEqualTo("x² cos(x) + 2x sin(x)");
    assertThat(Run.shown("d/dx x^x")).isEqualTo("x^x·(log(x) + 1)");
    assertThat(Run.shown("d^2/dx^2 sin x")).isEqualTo("-sin(x)");
    assertThat(Run.shown("d/dx x^2 + 1")).isEqualTo("2x + 1");
    assertThat(Run.shown("n := 2; diff(x^3, x, n)")).isEqualTo("6x");
  }

  @Test
  void whatCannotBeDifferentiatedStays() {
    assertThat(Run.shown("diff(factorial(x), x, 2)")).isEqualTo("d²/dx² x!");
    assertThat(Run.shown("d/dx integrate(x t!, t)")).isEqualTo("d/dx ∫ x t! dt");
  }

  @Test
  void theAssumptionsApplyToTheResult() {
    assertThat(Run.shown("assume(x > 0)\ndiff(abs(x), x)")).isEqualTo("1");
    assertThat(Run.shown("diff(abs(x), x)")).isEqualTo("sign(x)");
  }

  @Test
  void theVariableIsFreeInTheResult() {
    assertThat(Run.shown("f(x) := diff(x^3, x)\nf(2)")).isEqualTo("12");
    assertThat(Run.shown("subs(diff(x^2, x), x = 3)")).isEqualTo("6");
    assertThat(Run.shown("f(y) := diff(x^2 + y, x)\nf(x)")).isEqualTo("2x");
    assertThat(Run.shown("x := 3\ndiff(x^2, x)")).isEqualTo("6");
    // The value replaces the variable, never a caller's x that arrived through another value.
    assertThat(Run.shown("f(x, y) := diff(x^2 y, x)\nf(2, x)")).isEqualTo("4x");
    assertThat(Run.shown("f(x, y) := diff(x y, x)\nf(2, x)")).isEqualTo("x");
    assertThat(Run.shown("subs(diff(x^2 y, x), x = 2, y = x)")).isEqualTo("4x");
    // A fresh name that would merge with a free x of the result stays.
    assertThat(Run.shown("f(y) := diff(x y + x^2, x)\nf(x)")).isEqualTo("x + 2x₁");
    // A number put in for the variable reads as radians in every mode.
    assertThat(Run.shown("f(x) := diff(sin x, x)\nf(30)", Run.DEGREES)).isEqualTo("cos(30)");
    // Inside a binder of the same name the variable has no value.
    assertThat(Run.shown("x := 3\ndiff(diff(x^3, x), x)")).isEqualTo("18");
  }

  @Test
  void theResultIsFoldedOnceWithoutConvertingAnAngleAgain() {
    // log(2.0) from the exponential rule is evaluated, as evaluation would.
    assertThat(Run.value("diff(2.0^x, x)")).isEqualTo(Run.value("2.0^x log(2.0)"));
    // asin(1/2) was converted to degrees when the scope was evaluated, and only then.
    assertThat(Run.value("diff(x asin(1/2), x)", Run.DEGREES))
        .isEqualTo(Run.value("asin(1/2)", Run.DEGREES));
    assertThat(Run.error("diff(x^x, x, 40)")).startsWith("differentiation did not finish");
  }

  @Test
  void stepsCarryTheDerivativeThenTheSimplifier() {
    Outcome.Value v = (Outcome.Value) Run.last("steps(d/dx e^x log(10 x))");
    Trace t = v.trace();
    List<String> sets = t.rewrites().stream().map(Rewrite::ruleSet).distinct().toList();
    assertThat(sets.getFirst()).isEqualTo("diff");
    assertThat(sets).contains("exp-log");
    assertThat(t.result()).isEqualTo(v.value());
  }

  @Test
  void aNestedDerivativeIsNotNarrated() {
    Outcome.Value v = (Outcome.Value) Run.last("steps(1 + d/dx x^2)");
    assertThat(v.trace().rewrites()).extracting(Rewrite::ruleSet).doesNotContain("diff");
  }
}
