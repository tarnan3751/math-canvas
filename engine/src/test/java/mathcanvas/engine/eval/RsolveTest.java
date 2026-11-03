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
 * {@code rsolve} ({@code docs/language.md} §14.24) through the evaluator: each class, conditions,
 * the constants, what stays, the errors and the steps.
 */
class RsolveTest {

  private static Outcome.Value run(String script) {
    Session s = new Session(Environment.EMPTY);
    return (Outcome.Value) s.run(Parser.parseScript(script, s.names())).getLast();
  }

  private static List<String> steps(String script) {
    Outcome.Value v = run(script);
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    return Narration.steps(new Evaluator.Result(v.value(), v.trace()), Context.DEFAULT).stream()
        .map(Step::text)
        .toList();
  }

  @Test
  void constantCoefficients() {
    assertThat(shown("rsolve(a(n + 1) = 2 a(n) + 1, a(n), a(0) = 1)"))
        .isEqualTo("a(n) = 2·2^n - 1");
    assertThat(shown("rsolve(a(n + 2) = -a(n), a(n))"))
        .isEqualTo("a(n) = C₁ cos(πn/2) + C₂ sin(πn/2)");
    assertThat(shown("rsolve(a(n + 2) - 4 a(n + 1) + 4 a(n) = 0, a(n))"))
        .isEqualTo("a(n) = C₂ n·2^n + C₁·2^n");
    assertThat(shown("rsolve(a(n + 2) - 2a(n + 1) + 2a(n) = 0, a(n), a(0) = 1, a(1) = 1)"))
        .isEqualTo("a(n) = 2^(n/2) cos(πn/4)");
    assertThat(shown("rsolve(a(n + 1) = 2 a(n) + 2^n, a(n))")).isEqualTo("a(n) = C₁·2^n + n·2^n/2");
    assertThat(shown("rsolve(a(n + 1) = 3 a(n), a(n), a(2) = 9)")).isEqualTo("a(n) = 3^n");
  }

  @Test
  void theConstantsAreNoted() {
    Outcome.Value v = run("rsolve(a(n + 1) = 2 a(n) + 1, a(n))");
    assertThat(v.parameters().items()).hasSize(1);
    assertThat(shown("subs(rsolve(a(n + 1) = 2 a(n), a(n)), C_1 = 5)")).isEqualTo("a(n) = 5·2^n");
    // a C_1 of the user's is a name of the recurrence, not a constant the conditions fix
    assertThat(shown("rsolve(a(n + 1) = 2a(n) + C_1, a(n), a(0) = 1)"))
        .isEqualTo("a(n) = -C₁ + (C₁ + 1)·2^n");
    assertThat(run("rsolve(a(n + 1) = 2a(n) + C_1, a(n), a(0) = 1)").parameters().items())
        .isEmpty();
  }

  @Test
  void variableCoefficients() {
    assertThat(shown("rsolve(a(n + 1) = (n + 1) a(n), a(n), a(0) = 1)")).isEqualTo("a(n) = n!");
    assertThat(shown("rsolve(a(n + 1) = a(n) + n, a(n), a(0) = 0)")).isEqualTo("a(n) = n²/2 - n/2");
  }

  @Test
  void whatStays() {
    assertThat(shown("rsolve(a(n + 1) = a(n)^2, a(n))"))
        .isEqualTo("rsolve(a(n + 1) = a(n)², a(n))");
    assertThat(shown("rsolve(a(2n) = a(n), a(n))")).isEqualTo("rsolve(a(2n) = a(n), a(n))");
    assertThat(shown("rsolve(a(n + 1) = a(n) + a(3), a(n))"))
        .isEqualTo("rsolve(a(n + 1) = a(3) + a(n), a(n))");
    assertThat(steps("steps(rsolve(a(2n) = a(n), a(n)))"))
        .containsExactly("No method applies to rsolve(a(2n) = a(n), a(n))");
    assertThat(shown("rsolve(a(n + 2) = a(n + 1) + 2a(n), a(n), a(0) = 1, a(1) = 1, a(2) = 7)"))
        .isEqualTo("{}");
    assertThat(error("rsolve(a(n) = 3, a(n))"))
        .isEqualTo(
            "rsolve needs a recurrence: a(n) with at least two shifts, as a(n + 1) and a(n)");
    assertThat(error("rsolve(a(n + 1) = a(n), a(n), a(1/2) = 1)"))
        .isEqualTo("rsolve's conditions are a(k) = v at integers k");
  }

  @Test
  void theSteps() {
    assertThat(steps("steps(rsolve(a(n + 1) = 2 a(n) + 1, a(n), a(0) = 1))"))
        .containsExactly(
            "The recurrence is linear with constant coefficients; its characteristic equation: r"
                + " - 2 = 0",
            "The roots {2} give the general solution of the homogeneous recurrence: a(n) = C₁·2^n",
            "Undetermined coefficients give the particular solution -1: a(n) = C₁·2^n - 1",
            "The initial conditions fix the constants: a(n) = 2·2^n - 1");
    assertThat(steps("steps(rsolve(a(n + 1) = a(n)^2, a(n)))"))
        .containsExactly("No method applies to rsolve(a(n + 1) = a(n)², a(n))");
  }
}
