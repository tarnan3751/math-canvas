package mathcanvas.engine.simplify;

import static mathcanvas.engine.simplify.RuleCases.facts;
import static mathcanvas.engine.testing.Inputs.canonical;
import static mathcanvas.engine.testing.Inputs.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Outcome;
import mathcanvas.engine.eval.Session;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Context;
import org.junit.jupiter.api.Test;

/**
 * PLAN's acceptance pairs, where assumptions change results, and the textbook examples of the
 * brief, through the standard list and through a session.
 */
class AcceptancePairsTest {

  private static Expr simplify(String e, String... relations) {
    return Simplifier.simplify(parse(e), Context.DEFAULT.withAssumptions(facts(relations))).expr();
  }

  /** The last value of a script run in a new session, in Unicode display form. */
  private static String shown(String script) {
    return shown(script, AngleMode.RADIANS);
  }

  /** The last value of a script run in a new session in an angle mode, in Unicode display form. */
  private static String shown(String script, AngleMode mode) {
    Session s = new Session();
    s.setAngleMode(mode);
    List<Outcome> outcomes = s.run(Parser.parseScript(script, s.names()));
    Outcome last = outcomes.getLast();
    assertThat(last).as(script).isInstanceOf(Outcome.Value.class);
    return UnicodePrinter.INSTANCE.display(((Outcome.Value) last).value());
  }

  @Test
  void theSquareRootOfASquare() {
    assertThat(simplify("sqrt(x^2)")).isEqualTo(canonical("|x|"));
    assertThat(simplify("sqrt(x^2)", "x >= 0")).isEqualTo(canonical("x"));
    assertThat(simplify("sqrt(x^2)", "x ∈ ℂ")).isEqualTo(canonical("sqrt(x^2)"));
    assertThat(shown("sqrt(x^2)")).isEqualTo("|x|");
    assertThat(shown("assume(x >= 0); sqrt(x^2)")).isEqualTo("x");
  }

  @Test
  void theLogarithmOfAProductSplitsOnlyUnderPositivity() {
    assertThat(simplify("log(a*b)")).isEqualTo(canonical("log(a*b)"));
    assertThat(simplify("log(a*b)", "a > 0", "b > 0")).isEqualTo(canonical("log(a) + log(b)"));
    assertThat(simplify("log(2 a b c)", "a > 0", "b > 0", "c > 0"))
        .isEqualTo(canonical("log(2) + log(a) + log(b) + log(c)"));
    // One positive factor is enough: its argument is 0, so the principal logs add.
    assertThat(simplify("log(a*b)", "a > 0")).isEqualTo(canonical("log(a) + log(b)"));
    assertThat(simplify("log(a*b)", "a < 0", "b < 0")).isEqualTo(canonical("log(a*b)"));
    assertThat(shown("log(a b)")).isEqualTo("log(a b)");
    assertThat(shown("assume(a > 0, b > 0); log(a b)")).isEqualTo("log(a) + log(b)");
  }

  @Test
  void textbookRadicals() {
    assertThat(shown("sqrt(8)")).isEqualTo("2√2");
    assertThat(shown("sqrt(8/9)")).isEqualTo("2√2/3");
    assertThat(shown("12^(1/2)")).isEqualTo("2√3");
    assertThat(shown("54^(1/3)")).isEqualTo("3·2^(1/3)");
    assertThat(shown("1/sqrt(2)")).isEqualTo("√2/2");
    assertThat(shown("3/sqrt(5)")).isEqualTo("3√5/5");
    assertThat(shown("sqrt(5 + 2 sqrt(6))")).isEqualTo("√2 + √3");
    assertThat(shown("sqrt(2) sqrt(3)")).isEqualTo("√6");
    assertThat(shown("cbrt(-16)")).isEqualTo("-2·2^(1/3)");
  }

  @Test
  void textbookLogarithms() {
    assertThat(shown("log(8, 2)")).isEqualTo("3");
    assertThat(shown("log10(1000)")).isEqualTo("3");
    assertThat(shown("log(1/4, 2)")).isEqualTo("-2");
    assertThat(shown("ln(e^3)")).isEqualTo("3");
    assertThat(shown("exp(2x)")).isEqualTo("e^(2x)");
    assertThat(shown("log(x^2)")).isEqualTo("2 log(|x|)");
    assertThat(shown("assume(x > 0); e^(2 log(x))")).isEqualTo("x²");
  }

  @Test
  void theAngleModeTableOfTheLanguage() {
    // PLAN §6 and docs/language.md §14.2.
    for (AngleMode mode : AngleMode.values()) {
      assertThat(shown("sin(30°)", mode)).as("%s", mode).isEqualTo("1/2");
      assertThat(shown("sin(pi)", mode)).as("%s", mode).isEqualTo("0");
    }
    assertThat(shown("sin(30)", AngleMode.DEGREES)).isEqualTo("1/2");
    assertThat(shown("sin(30)")).isEqualTo("sin(30)");
    assertThat(shown("sin(15 + 30°)", AngleMode.DEGREES)).isEqualTo("sin(π/6 + 15)");
    assertThat(shown("asin(1/2)")).isEqualTo("π/6");
    assertThat(shown("asin(1/2)", AngleMode.DEGREES)).isEqualTo("30");
    assertThat(shown("acos(-1/2)", AngleMode.DEGREES)).isEqualTo("120");
    assertThat(shown("sin(50)", AngleMode.GRADIANS)).isEqualTo("√2/2");
  }

  @Test
  void assumptionsDecideTheInverseOfASine() {
    assertThat(simplify("asin(sin(x))")).isEqualTo(canonical("asin(sin(x))"));
    assertThat(simplify("asin(sin(x))", "x >= -1", "x <= 1")).isEqualTo(canonical("x"));
    assertThat(simplify("asin(sin(x))", "x >= 0", "x <= 2")).isEqualTo(canonical("asin(sin(x))"));
    assertThat(shown("assume(x >= -1, x <= 1); asin(sin(x))")).isEqualTo("x");
    assertThat(simplify("cos(pi n)")).isEqualTo(canonical("cos(pi n)"));
    assertThat(simplify("cos(pi n)", "n ∈ ℤ")).isEqualTo(canonical("(-1)^n"));
    assertThat(simplify("cos(pi n)", "mod(n, 2) = 0")).isEqualTo(canonical("1"));
  }

  @Test
  void textbookTrigonometry() {
    assertThat(shown("cos(pi/4)")).isEqualTo("√2/2");
    assertThat(shown("tan(pi/3)")).isEqualTo("√3");
    assertThat(shown("tan(pi/2)")).isEqualTo("undefined");
    assertThat(shown("sin(x)^2 + cos(x)^2")).isEqualTo("1");
    assertThat(shown("1 + tan(x)^2")).isEqualTo("sec(x)²");
    assertThat(shown("sin(x)/cos(x)")).isEqualTo("tan(x)");
    assertThat(shown("2 sin(x) cos(x)")).isEqualTo("sin(2x)");
    assertThat(shown("cos(x)^2 - sin(x)^2")).isEqualTo("cos(2x)");
    assertThat(shown("sin(-x) + sin(x)")).isEqualTo("0");
    assertThat(shown("cos(pi - x)")).isEqualTo("-cos(x)");
    assertThat(shown("sqrt(1 - sin(x)^2)")).isEqualTo("|cos(x)|");
    assertThat(shown("sin(acos(x))")).isEqualTo("√(-x² + 1)");
  }
}
