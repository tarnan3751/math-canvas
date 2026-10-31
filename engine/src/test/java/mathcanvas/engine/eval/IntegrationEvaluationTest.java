package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
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

  @Test
  void theStepsAreTheIntegrationsThenTheSimplifiers() {
    Session s = new Session(Environment.EMPTY);
    Outcome.Value v =
        (Outcome.Value) s.run(Parser.parseScript("steps(∫ x e^x dx)", s.names())).getLast();
    List<String> rules = v.trace().rewrites().stream().map(Rewrite::rule).toList();
    assertThat(rules).containsExactly("by-parts");
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    // nested in a larger statement, the integration's steps are not shown
    Outcome.Value nested =
        (Outcome.Value) s.run(Parser.parseScript("steps(2 + ∫ x dx)", s.names())).getLast();
    assertThat(nested.trace().rewrites()).noneMatch(w -> w.ruleSet().equals("integrate"));
  }
}
