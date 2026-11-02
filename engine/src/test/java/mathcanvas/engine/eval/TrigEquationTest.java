package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

/**
 * Trigonometric equations in the language: general solutions in the angle mode, the integer
 * parameter and its note, its freshness, and each step of the trigonometric path.
 */
class TrigEquationTest {

  @Test
  void basicEquationsHaveTheirGeneralSolutions() {
    assertThat(shown("solve(sin(x) = 1/2, x)")).isEqualTo("{2πk + π/6, 2πk + 5π/6}");
    assertThat(shown("solve(sin(x) = -1/2, x)")).isEqualTo("{2πk + 7π/6, 2πk + 11π/6}");
    assertThat(shown("solve(cos(x) = 1/2, x)")).isEqualTo("{2πk + π/3, 2πk + 5π/3}");
    assertThat(shown("solve(tan(x) = 1, x)")).isEqualTo("{πk + π/4}");
    assertThat(shown("solve(cot(x) = 0, x)")).isEqualTo("{πk + π/2}");
    assertThat(shown("solve(sec(x) = 2, x)")).isEqualTo("{2πk + π/3, 2πk + 5π/3}");
    assertThat(shown("solve(csc(x) = 2, x)")).isEqualTo("{2πk + π/6, 2πk + 5π/6}");
    assertThat(shown("solve(sin(x) = 1/3, x)")).isEqualTo("{2πk + asin(1/3), 2πk + π - asin(1/3)}");
  }

  @Test
  void familiesMergeAndOutOfRangeValuesHaveNoSolution() {
    assertThat(shown("solve(sin(x) = 0, x)")).isEqualTo("{πk}");
    assertThat(shown("solve(cos(x) = 0, x)")).isEqualTo("{πk + π/2}");
    assertThat(shown("solve(cos(x) = 1, x)")).isEqualTo("{2πk}");
    assertThat(shown("solve(sin(x) = 2, x)")).isEqualTo("{}");
    assertThat(shown("solve(sec(x) = 1/2, x)")).isEqualTo("{}");
    // a complex root of the polynomial in sin(x) is dropped: the solutions are real
    assertThat(shown("solve(sin(x)^2 + 1 = 0, x)")).isEqualTo("{}");
  }

  @Test
  void linearArgumentsScaleThePeriod() {
    assertThat(shown("solve(sin(2x) = 1/2, x)")).isEqualTo("{πk + π/12, πk + 5π/12}");
    assertThat(shown("solve(cos(x/2 + π/3) = 0, x)")).isEqualTo("{2πk + π/3}");
    assertThat(shown("solve(tan(x) = a, x)")).isEqualTo("{πk + atan(a)}");
  }

  @Test
  void eachStepOfThePath() {
    assertThat(steps("solve(2cos(x)^2 + cos(x) - 1 = 0, x)"))
        .containsExactly("trig-factor", "trig-basic", "trig-general", "roots");
    assertThat(shown("solve(2cos(x)^2 + cos(x) - 1 = 0, x)"))
        .isEqualTo("{2πk + π, 2πk + π/3, 2πk + 5π/3}");
    // already in sin(x) and cos(x): no rewriting step
    assertThat(steps("solve(sin(x) + cos(x) = 1, x)"))
        .containsExactly("trig-auxiliary-angle", "trig-basic", "trig-general", "roots");
    assertThat(shown("solve(sin(x) + cos(x) = 1, x)")).isEqualTo("{2πk, 2πk + π/2}");
    assertThat(shown("solve(√3 sin(x) + cos(x) = 1, x)")).isEqualTo("{2πk, 2πk + 2π/3}");
    assertThat(steps("solve(sin(2x) = sin(x), x)"))
        .containsExactly("trig-to-sin-cos", "trig-factor", "trig-basic", "trig-general", "roots");
    assertThat(shown("solve(sin(2x) = sin(x), x)")).isEqualTo("{πk, 2πk + π/3, 2πk + 5π/3}");
    assertThat(steps("solve(cos(2x) = sin(x), x)"))
        .containsExactly(
            "trig-to-sin-cos", "trig-pythagorean", "trig-basic", "trig-general", "roots");
    assertThat(shown("solve(cos(2x) = sin(x), x)"))
        .isEqualTo("{2πk + π/6, 2πk + 5π/6, 2πk + 3π/2}");
    assertThat(shown("solve(sin(x)^2 - 3sin(x)cos(x) + 2cos(x)^2 = 0, x)"))
        .isEqualTo("{πk + π/4, πk + asin(2√5/5)}");
    assertThat(steps("solve(sin(x)cos(x) + sin(x) + cos(x) = 1, x)")).contains("trig-half-angle");
    assertThat(shown("solve(sin(x)cos(x) + sin(x) + cos(x) = 1, x)")).isEqualTo("{2πk, 2πk + π/2}");
  }

  @Test
  void aSolutionWhereTheEquationIsUndefinedIsRemoved() {
    assertThat(steps("solve(tan(x) + sec(x) = 0, x)")).contains("trig-exclude");
    assertThat(shown("solve(tan(x) + sec(x) = 0, x)")).isEqualTo("{}");
    assertThat(shown("solve(tan(x) cos(x) = 1, x)")).isEqualTo("{}");
    assertThat(shown("solve(tan(x) = sin(x), x)")).isEqualTo("{πk}");
  }

  @Test
  void aDenominatorTheRewritingCancelsStillExcludes() {
    // 2 sin(x) cos(x)/sin(x) is 2 cos(x) after the rewriting, but sin(x) = 0 stays excluded
    assertThat(shown("solve(sin(2x)/sin(x) = 2, x)")).isEqualTo("{}");
    assertThat(shown("solve(sin(x)/sin(2x) = 1/2, x)")).isEqualTo("{}");
    assertThat(shown("solve(sin(4x)/sin(2x) = 2, x)")).isEqualTo("{}");
    assertThat(shown("solve(sin(2x)/sin(x) = 1, x)")).isEqualTo("{2πk + π/3, 2πk + 5π/3}");
    assertThat(shown("solve(cot(2x) = tan(x), x)")).isEqualTo("{πk + π/6, πk + 5π/6}");
  }

  @Test
  void decimalValuesAnswerWithDecimals() {
    assertThat(shown("solve(sin(x) = 1.0, x)"))
        .isEqualTo("{2πk + 1.570796326794896619231321691639751}");
    assertThat(shown("solve(cos(x) = -1.0, x)"))
        .isEqualTo("{2πk + 3.141592653589793238462643383279503}");
    assertThat(shown("solve(sin(x) = 0.5, x)", Run.DEGREES))
        .isEqualTo("{360k + 30.0, 360k + 150.0}");
    // a converted decimal angle is rounded once, at the precision: 90.0, not 89.999…98
    assertThat(shown("solve(sin(x) = 1.0, x)", Run.DEGREES)).isEqualTo("{360k + 90.0}");
    assertThat(shown("solve(tan(x) = 1.0, x)", Run.DEGREES)).isEqualTo("{180k + 45.0}");
    assertThat(shown("solve(sin(x) = 1.0, x)", Run.GRADIANS)).isEqualTo("{400k + 100.0}");
  }

  @Test
  void inDegreesTheSolutionsAreInDegrees() {
    assertThat(shown("solve(sin(x) = 1/2, x)", Run.DEGREES)).isEqualTo("{360k + 30, 360k + 150}");
    assertThat(shown("solve(tan(x) = 1, x)", Run.DEGREES)).isEqualTo("{180k + 45}");
    assertThat(shown("solve(cos(2x) = cos(x), x)", Run.DEGREES))
        .isEqualTo("{360k, 360k + 120, 360k + 240}");
    assertThat(shown("solve(sin(x + 30) = 1/2, x)", Run.DEGREES)).isEqualTo("{360k, 360k + 120}");
    assertThat(shown("solve(sin(x) = 1/2, x)", Run.GRADIANS))
        .isEqualTo("{400k + 100/3, 400k + 500/3}");
    // an argument holding π is read in radians, as everywhere in degrees
    assertThat(shown("solve(sin(x + π/3) = 1/2, x)", Run.DEGREES))
        .isEqualTo("{2πk + π/2, 2πk + 11π/6}");
    assertThat(shown("subs(solve(sin(x) = 1/2, x), k = 0)", Run.DEGREES)).isEqualTo("{30, 150}");
  }

  @Test
  void theParameterIsNotedAndAssumedAnInteger() {
    Session s = new Session(Environment.EMPTY);
    List<Outcome> out =
        s.run(Parser.parseScript("solve(sin(x) = 1/2, x); simplify(cos(2π k))", s.names()));
    Outcome.Value v = (Outcome.Value) out.getFirst();
    assertThat(display(v.parameters())).isEqualTo("{k ∈ ℤ}");
    assertThat(display(((Outcome.Value) out.getLast()).value())).isEqualTo("1");
    assertThat(display(s.environment().assumptions().relations())).isEqualTo("{k ∈ ℤ}");
    // a value with no general solution has no parameters
    assertThat(((Outcome.Value) Run.last("solve(x^2 = 1, x)")).parameters().items()).isEmpty();
  }

  @Test
  void theParameterIsFresh() {
    assertThat(shown("k := 3; solve(sin(x) = 0, x)")).isEqualTo("{πk₁}");
    assertThat(shown("assume(k > 0); solve(sin(x) = 0, x)")).isEqualTo("{πk₁}");
    assertThat(shown("solve(sin(x) = 0, x); solve(cos(x) = 0, x)")).isEqualTo("{πk + π/2}");
    assertThat(shown("solve(tan(x) = k, x)")).isEqualTo("{πk₁ + atan(k)}");
    assertThat(shown("solve(sin(k) = 0, k)")).isEqualTo("{πk₁}");
  }

  @Test
  void aFrameValueOfAnInputKIsTheInputs() {
    // the equation mentions k: the parameter is k₁, and the value is the input's
    assertThat(shown("subs(solve(sin(x) = k, x), k = 1/2)")).isEqualTo("{2πk₁ + π/6, 2πk₁ + 5π/6}");
    assertThat(shown("f(k) := solve(sin(x) = k, x); f(1/2)"))
        .isEqualTo("{2πk₁ + π/6, 2πk₁ + 5π/6}");
  }

  @Test
  void twoSolvesInOneStatementHaveTwoParameters() {
    Outcome.Value v = (Outcome.Value) Run.last("[solve(sin(x) = 0, x), solve(cos(x) = 0, x)]");
    assertThat(display(v.value())).isEqualTo("[{πk}, {πk₁ + π/2}]");
    assertThat(display(v.parameters())).isEqualTo("{k ∈ ℤ, k₁ ∈ ℤ}");
  }

  @Test
  void aValueOfTheParameterInTheFrameIsPutIn() {
    assertThat(shown("subs(solve(sin(x) = 1/2, x), k = 0)")).isEqualTo("{π/6, 5π/6}");
    assertThat(shown("f(k) := solve(sin(x) = 1, x); f(1)")).isEqualTo("{5π/2}");
    assertThat(shown("k := 3; f(k) := solve(sin(x) = 1, x); f(1)")).isEqualTo("{5π/2}");
    assertThat(
            ((Outcome.Value) Run.last("subs(solve(sin(x) = 1/2, x), k = 0)")).parameters().items())
        .isEmpty();
  }

  @Test
  void whatThePathDoesNotTakeGoesToTheScan() {
    assertThat(shown("solve(sin(x) = a, x)")).isEqualTo("solve(sin(x) = a, x)");
    assertThat(shown("solve(sin(x^2) = 0, x)")).isEqualTo("solve(sin(x²) = 0, x)");
    assertThat(shown("solve(sin(x) = x/2, x)")).startsWith("{-1.895494267");
    assertThat(shown("solve(sin(x) + sin(5x) = 0, x)")).isEqualTo("solve(sin(x) + sin(5x) = 0, x)");
    assertThat(shown("solve(sin(5x) = 0, x)")).isEqualTo("{πk/5}");
  }

  @Test
  void numericLeavesTheParameter() {
    assertThat(shown("numeric(solve(tan(x) = 1, x), 5)")).isEqualTo("{3.1416k + 0.7854}");
  }

  private static List<String> steps(String call) {
    Session s = new Session(Environment.EMPTY);
    Outcome o = s.run(Parser.parseScript("steps(" + call + ")", s.names())).getLast();
    Outcome.Value v = (Outcome.Value) o;
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    ResourceBundle narration =
        ResourceBundle.getBundle("mathcanvas.engine.rule.narration", Locale.ROOT);
    for (Rewrite w : v.trace().rewrites()) {
      assertThat(narration.containsKey(w.ruleSet() + "." + w.rule())).as(w.rule()).isTrue();
    }
    return v.trace().rewrites().stream()
        .filter(r -> r.ruleSet().equals("solve"))
        .map(Rewrite::rule)
        .toList();
  }

  private static String display(Expr e) {
    return UnicodePrinter.INSTANCE.display(e);
  }
}
