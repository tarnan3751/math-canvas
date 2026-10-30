package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.canonical;
import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static mathcanvas.engine.eval.Run.value;
import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.Real;
import org.junit.jupiter.api.Test;

class NumericExactTest {

  static final String PI_50 = "3.1415926535897932384626433832795028841971693993751";

  @Test
  void numericAtTheSessionPrecision() {
    assertThat(value("numeric(1/3)"))
        .isEqualTo(new Num(Real.of("0.3333333333333333333333333333333333")));
    assertThat(value("numeric(1/3)", Settings.DEFAULT.withPrecision(5)))
        .isEqualTo(new Num(Real.of("0.33333")));
  }

  @Test
  void numericAtAPrecisionAskedFor() {
    assertThat(value("numeric(π, 50)")).isEqualTo(new Num(Real.of(PI_50)));
    assertThat(value("numeric(e, 20)")).isEqualTo(new Num(Real.of("2.7182818284590452354")));
    assertThat(value("numeric(0.1 + 1/3, 50)"))
        .isEqualTo(new Num(Real.of("0.43333333333333333333333333333333333333333333333333")));
    assertThat(value("a := 1/3; numeric(a, 40) + numeric(a, 5)"))
        .isEqualTo(new Num(Real.of("0.6666633333333333333333333333333333")));
  }

  @Test
  void numericKeepsSymbolsAndSpecialValues() {
    assertThat(shown("numeric(x + 1/3)")).isEqualTo("x + 0.3333333333333333333333333333333333");
    assertThat(value("numeric(inf)")).isEqualTo(Constant.INFINITY);
    assertThat(value("numeric(1/0)")).isEqualTo(Constant.UNDEFINED);
    assertThat(value("numeric(2 < pi)")).isEqualTo(Bool.TRUE);
    assertThat(shown("numeric(sin(x) + 2)")).isEqualTo("sin(x) + 2.0");
    assertThat(shown("numeric(integrate(x^2/3, x, 0, 1/3), 3)")).isEqualTo("∫[0, 1/3] 0.333x² dx");
  }

  @Test
  void numericKeepsIntegerExponentsIndicesAndBounds() {
    assertThat(shown("numeric(x^2 + 1/3)")).isEqualTo("x² + 0.3333333333333333333333333333333333");
    assertThat(shown("numeric(sum(k/3, k, 1, n), 5)")).isEqualTo("∑[k = 1, n] 0.33333k");
    assertThat(shown("numeric(x^(1/2), 3)")).isEqualTo("x^(0.5)");
    // a root's integer degree stays exact, so a root of a number is evaluated
    assertThat(shown("numeric(root(5, 3), 10)")).isEqualTo("1.709975947");
    assertThat(shown("numeric(root(-5, 3), 10)")).isEqualTo("-1.709975947");
    assertThat(shown("numeric(root(x, 3))")).isEqualTo("root(x, 3)");
    assertThat(shown("numeric(root(-16, 4))")).isEqualTo("undefined");
    assertThat(shown("numeric(root(5, 3.0), 10)")).isEqualTo("root(5.0, 3.0)");
    // a root stays a root, never a power with an exact rational exponent
    assertThat(shown("numeric(root(x^2 + 1/3, 3), 5)")).isEqualTo("root(x² + 0.33333, 3)");
  }

  @Test
  void numericEvaluatesFunctionsAndConstants() {
    assertThat(shown("numeric(sqrt(2))")).isEqualTo("1.414213562373095048801688724209698");
    assertThat(shown("numeric(sin(1) + pi)")).isEqualTo("3.983063638397689745115145704909802");
    assertThat(shown("numeric([1/4, pi], 3)")).isEqualTo("⟨0.25, 3.14⟩");
  }

  @Test
  void theDigitsAreChecked() {
    String message = "the number of digits must be an integer from 1 to 10000";
    assertThat(error("numeric(pi, 0)")).isEqualTo(message);
    assertThat(error("numeric(pi, 10001)")).isEqualTo(message);
    assertThat(error("numeric(pi, 2.5)")).isEqualTo(message);
    assertThat(error("numeric(pi, n)")).isEqualTo(message);
  }

  @Test
  void exactTurnsDecimalsIntoRationals() {
    assertThat(value("exact(0.5)")).isEqualTo(canonical("1/2"));
    assertThat(value("exact(0.1)")).isEqualTo(canonical("1/10"));
    assertThat(value("exact(1.5e3)")).isEqualTo(Num.of(1500));
    assertThat(value("exact(0.25 + x)")).isEqualTo(canonical("x + 1/4"));
    assertThat(value("exact(2.5 + 0.5i)")).isEqualTo(canonical("5/2 + i/2"));
    assertThat(value("exact(1/3)")).isEqualTo(canonical("1/3"));
    assertThat(value("exact(numeric(1/8))")).isEqualTo(canonical("1/8"));
    assertThat(error("exact(1e-200000)")).isEqualTo("number out of range at this precision");
  }

  @Test
  void decimalsStayInexact() {
    assertThat(value("0.5")).isEqualTo(new Num(Real.of("0.5")));
    assertThat(value("0.5 + 1/3"))
        .isEqualTo(new Num(Real.of("0.8333333333333333333333333333333333")));
    assertThat(value("0.1 + 0.2")).isEqualTo(new Num(Real.of("0.3")));
    // a power of e or π with a decimal is numeric too, as exp(2.0) is; so a plotted e^x at a
    // decimal converts in degrees as the cell does
    assertThat(value("e^(2.0)")).isEqualTo(value("exp(2.0)"));
    assertThat(value("subs(e^x, x = 2.0)")).isEqualTo(value("exp(2.0)"));
    assertThat(Run.value("sin(e^x)", Run.DEGREES)).isNotInstanceOf(Num.class);
    Expr plotted =
        Substitution.apply(
            Run.value("sin(e^x)", Run.DEGREES),
            new java.util.TreeMap<>(java.util.Map.of("x", new Num(Real.of("2.0")))));
    assertThat(
            Evaluator.evaluate(plotted, Environment.EMPTY.withSettings(Run.DEGREES), false).value())
        .isEqualTo(Run.value("sin(exp(2.0))", Run.DEGREES));
  }
}
