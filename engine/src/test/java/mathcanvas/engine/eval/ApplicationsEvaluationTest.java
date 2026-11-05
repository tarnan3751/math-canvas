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
    assertThat(shown("arclength(x^3, x, 0, 1)")).isEqualTo("∫[0, 1] √(9x⁴ + 1) dx");
    // a parabola's, by the tangent substitution
    assertThat(shown("arclength(x^2, x, 0, 1)")).isEqualTo("√5/2 + log(√5 + 2)/4");
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
    // a pole is a sign change: each piece is improper, and here one diverges, so the area does
    assertThat(shown("area(1/x, x, -1, 1)")).isEqualTo("undefined");
    assertThat(steps("steps(area(1/x, x, -1, 1))"))
        .containsExactly("area.formula", "area.diverges");
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
  void volumesOfRevolution() {
    assertThat(shown("volume(sqrt(x), x, 0, 4)")).isEqualTo("8π");
    assertThat(shown("volume(sqrt(1 - x^2), x, -1, 1)")).isEqualTo("4π/3");
    assertThat(shown("volume(x, x^2, x, 0, 1)")).isEqualTo("2π/15");
    assertThat(shown("assume(h > 0)\nvolume(r, x, 0, h)")).isEqualTo("πh r²");
    assertThat(shown("shellvolume(x^2, x, 0, 2)")).isEqualTo("8π");
    assertThat(error("shellvolume(x, x, -1, 1)")).isEqualTo("shellvolume needs a ≥ 0");
    assertThat(error("volume(x, x, 1, 0)")).isEqualTo("volume needs a ≤ b");
    assertThat(shown("volume(x, -x, x, 0, 1)")).isEqualTo("0");
    assertThat(shown("volume(x^2 - 2, 1, x, -2, 2)")).isEqualTo("π·(-16√3/5 + 164/15)");
    assertThat(shown("shellvolume(1, x, 0, 1)")).isEqualTo("π");
    // a sign of a that cannot be decided leaves the call
    assertThat(shown("shellvolume(x, x, a, a + 1)")).isEqualTo("shellvolume(x, x, a, a + 1)");
    assertThat(shown("volume(2x, x^2, x, 0, 2)")).isEqualTo("64π/15");
    assertThat(shown("x := 3\nvolume(x, x^2, x, 0, 1)")).isEqualTo("2π/15");
    assertThat(steps("steps(volume(x, x^2, x, 0, 1))"))
        .containsExactly("volume.washers", "volume.whole", "volume.value");
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

  @Test
  void theClassificationNamesOnlyTheKindsFound() {
    String by = "By the second derivative, or the sign of the derivative on either side: ";
    assertThat(NarrationTest.narrated("steps(extrema(x^3 - 3x, x))"))
        .containsExactly(
            "The derivative 3x² - 3 is 0 at {-1, 1} | {-1, 1}",
            by + "the minima are {[1, -2]} and the maxima are {[-1, 2]} | {[-1, 2], [1, -2]}");
    assertThat(NarrationTest.narrated("steps(extrema(x^3, x))"))
        .containsExactly(
            "The derivative 3x² is 0 at {0} | {0}",
            by
                + "there are no minima and no maxima, and the critical points that are neither are"
                + " {0} | {}");
    assertThat(NarrationTest.narrated("steps(extrema(x^2, x))").getLast())
        .isEqualTo(by + "the minima are {[0, 0]} and there are no maxima | {[0, 0]}");
  }

  @Test
  void extremaAreClassified() {
    assertThat(shown("extrema(x^3 - 3x, x)")).isEqualTo("{[-1, 2], [1, -2]}");
    // the first-derivative test where the second derivative vanishes; an inflection is excluded
    assertThat(shown("extrema(x^4, x)")).isEqualTo("{[0, 0]}");
    assertThat(shown("extrema(x^3, x)")).isEqualTo("{}");
    assertThat(shown("extrema(x^4 - 2x^2, x)")).isEqualTo("{[-1, -1], [0, 0], [1, -1]}");
    assertThat(shown("extrema(x + 1/x, x)")).isEqualTo("{[-1, -2], [1, 2]}");
    assertThat(shown("extrema(1/x, x)")).isEqualTo("{}");
    // not provably complete: the call stays with its note
    assertThat(shown("extrema(sin(x), x)")).isEqualTo("extrema(sin(x), x)");
    assertThat(shown("extrema(x^2 + a x, x)")).isEqualTo("extrema(a x + x², x)");
    assertThat(steps("steps(extrema(x^3 - 3x, x))"))
        .containsExactly("extrema.critical", "extrema.classify");
    assertThat(steps("steps(extrema(sin(x), x))")).containsExactly("extrema.none");
    // no critical point: no extrema; f′ ≡ 0: every point is critical, so the call stays
    assertThat(shown("extrema(x, x)")).isEqualTo("{}");
    assertThat(steps("steps(extrema(x, x))")).containsExactly("extrema.no-critical");
    assertThat(shown("extrema(5, x)")).isEqualTo("extrema(5, x)");
    // the first-derivative test does not step across a pole of f′ = (x − 1)³/(x − 1/2)
    assertThat(shown("extrema((x - 1/2)^3/3 - 3(x - 1/2)^2/4 + 3(x - 1/2)/4 - log(x - 1/2)/8, x)"))
        .isEqualTo("{[1, log(2)/8 + 11/48]}");
  }

  @Test
  void globalExtremaOnAnInterval() {
    assertThat(shown("maximize(x^3 - 3x, x, -3, 3)")).isEqualTo("[3, 18]");
    assertThat(shown("minimize(x^2 - 2x, x, 0, 3)")).isEqualTo("[1, -1]");
    assertThat(shown("maximize(x + 1/x, x, 1/2, 3)")).isEqualTo("[3, 10/3]");
    assertThat(shown("minimize(x + 1/x, x, 1/2, 3)")).isEqualTo("[1, 2]");
    // a tie goes to the leftmost point
    assertThat(shown("maximize(x^2, x, -1, 1)")).isEqualTo("[-1, 1]");
    assertThat(shown("maximize(x^4 - 2x^2, x, -2, 2)")).isEqualTo("[-2, 8]");
    assertThat(shown("minimize(x^3, x, 2, 2)")).isEqualTo("[2, 8]");
    // not continuous on the interval, or not provably complete: the call stays
    assertThat(shown("maximize(1/x, x, -1, 1)")).isEqualTo("maximize(1/x, x, -1, 1)");
    assertThat(shown("maximize(sin(x), x, 0, 3)")).isEqualTo("maximize(sin(x), x, 0, 3)");
    assertThat(steps("steps(maximize(x^3 - 3x, x, -3, 3))"))
        .containsExactly("maximize.candidates", "maximize.value");
    assertThat(error("maximize(x, x, 1, 0)")).isEqualTo("maximize needs a ≤ b");
  }
}
