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

  @Test
  void areaSplitsAtTheCrossings() {
    assertThat(shown("area(x^2 - 1, x, -2, 2)")).isEqualTo("4");
    assertThat(shown("area(x, x^2, x, 0, 1)")).isEqualTo("1/6");
    assertThat(shown("area(1/x - 1, x, 1/2, 2)")).isEqualTo("1/2");
    assertThat(shown("area(x^2, 2x, x, 0, 2)")).isEqualTo("4/3");
    assertThat(shown("area(x^3 - 2x, x, -2, 2)")).isEqualTo("4");
    assertThat(steps("steps(area(x^2 - 1, x, -2, 2))"))
        .containsExactly("area.formula", "area.split", "area.value");
    // crossings that are not known exactly: the integral of the absolute value, with its note
    assertThat(shown("area(sin(x), x, 0, 3)")).isEqualTo("∫[0, 3] |sin(x)| dx");
    assertThat(shown("numeric(area(sin(x), x, 0, 3), 10)")).isEqualTo("1.989992497");
    assertThat(shown("area(x^5 - x - 1, x, 0, 2)")).isEqualTo("∫[0, 2] |x⁵ - x - 1| dx");
    assertThat(steps("steps(area(sin(x), x, 0, 3))")).startsWith("area.crossings");
    // a pole is a sign change: each piece is improper and stays
    assertThat(shown("area(1/x, x, -1, 1)")).isEqualTo("|∫[-1, 0] 1/x dx| + |∫[0, 1] 1/x dx|");
    assertThat(steps("steps(area(1/x, x, -1, 1))")).containsExactly("area.formula", "area.split");
    // a constant difference has no crossing; one that may be zero goes to integrate
    assertThat(shown("area(2, x, 0, 1)")).isEqualTo("2");
    assertThat(shown("area(t, x, 0, 1)")).isEqualTo("|t|");
    // a number distributed over a sum, as for a definite integral's value
    assertThat(shown("area(x^2 - 2, x, -2, 2)")).isEqualTo("16√2/3 - 8/3");
    assertThat(steps("steps(area(x^2 - 2, x, -2, 2))"))
        .containsExactly("area.formula", "area.split", "area.value", "distribute");
    // the value step only when the sum changes
    assertThat(steps("steps(area(x^2 - 1, x, 1, 2))"))
        .containsExactly("area.formula", "area.whole", "area.value");
  }

  @Test
  void theBoundsOfAnArea() {
    assertThat(shown("area(x, x, 2, 2)")).isEqualTo("0");
    assertThat(error("area(x, x, 1, 0)")).isEqualTo("area needs a ≤ b");
    assertThat(shown("area(x^2, x, a, b)")).isEqualTo("area(x², x, a, b)");
    assertThat(steps("steps(area(x^2, x, a, b))")).containsExactly("area.none");
  }

  @Test
  void theVariableOfAnAreaIsBound() {
    assertThat(shown("x := 3\narea(x, x^2, x, 0, 1)")).isEqualTo("1/6");
    assertThat(shown("f(y) := area(x y, x, 0, 1)\nf(2)")).isEqualTo("1");
  }
}
