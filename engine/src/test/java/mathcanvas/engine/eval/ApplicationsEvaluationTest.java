package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

/**
 * The applications of integration and the optimisation helpers ({@code docs/language.md} §14.15).
 */
class ApplicationsEvaluationTest {

  private static List<String> steps(String statement) {
    Session s = new Session(Environment.EMPTY);
    Outcome.Value v = (Outcome.Value) s.run(Parser.parseScript(statement, s.names())).getLast();
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    return v.trace().rewrites().stream().map(Rewrite::rule).toList();
  }

  /** The value of a script's last statement, whose steps, traced, must also chain. */
  private static String shown(String script) {
    int last = script.lastIndexOf('\n') + 1;
    steps(script.substring(0, last) + "steps(" + script.substring(last) + ")");
    return Run.shown(script);
  }

  @Test
  void arcLength() {
    assertThat(shown("arclength(x^(3/2), x, 0, 1)")).isEqualTo("13√13/27 - 8/27");
    assertThat(shown("arclength([cos(t), sin(t)], t, 0, pi)")).isEqualTo("π");
    assertThat(shown("arclength([t, 2t], t, 0, 1)")).isEqualTo("√5");
    assertThat(shown("assume(a > 0)\narclength(a x, x, 0, 1)")).isEqualTo("√(a² + 1)");
    // no closed form: the integral it set up, which numeric evaluates
    assertThat(shown("arclength(x^2, x, 0, 1)")).isEqualTo("∫[0, 1] √(4x² + 1) dx");
    assertThat(shown("numeric(arclength(sin(x), x, 0, pi))"))
        .isEqualTo("3.820197789027712017904762082171443");
    assertThat(steps("steps(arclength(x^(3/2), x, 0, 1))"))
        .startsWith("arclength.formula", "arclength.integrand");
    assertThat(error("arclength(x, x, 1, 0)")).isEqualTo("arclength needs a ≤ b");
    assertThat(shown("arclength(x, x, 1, 1)")).isEqualTo("0");
  }
}
