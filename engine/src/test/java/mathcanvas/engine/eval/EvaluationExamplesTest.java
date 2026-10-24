package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.DEGREES;
import static mathcanvas.engine.eval.Run.canonical;
import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static mathcanvas.engine.eval.Run.value;
import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.Real;
import org.junit.jupiter.api.Test;

/** The evaluation examples of {@code docs/language.md} §17.3 and §14, one assertion each. */
class EvaluationExamplesTest {

  @Test
  void section17Point3() {
    assertThat(value("0.5")).isEqualTo(new Num(Real.of("0.5")));
    assertThat(shown("exact(0.5)")).isEqualTo("1/2");
    assertThat(shown("numeric(1/3)")).isEqualTo("0.3333333333333333333333333333333333");
    assertThat(shown("numeric(π, 50)"))
        .isEqualTo("3.1415926535897932384626433832795028841971693993751");
    // The angle is converted exactly, and the trig rule set gives the value.
    assertThat(shown("sin(30)", DEGREES)).isEqualTo("1/2");
    assertThat(value("2 < 3")).isEqualTo(Bool.TRUE);
    assertThat(shown("x < 3")).isEqualTo("x < 3");
    assertThat(value("2 ∈ ℤ")).isEqualTo(Bool.TRUE);
    assertThat(shown("{a, b, a}")).isEqualTo("{a, b}");
    assertThat(value("[1, 2] * [3, 4]")).isEqualTo(Num.of(11));
    assertThat(shown("[1, 0, 0] × [0, 1, 0]")).isEqualTo("⟨0, 0, 1⟩");
    // solve is a later brief's: it receives the symbol and returns its application.
    assertThat(value("x := 3; solve(x^2 = 4, x)")).isEqualTo(canonical("solve(x^2 = 4, x)"));
    assertThat(shown("a := 5; subs(x^2 + a, a = 2)")).isEqualTo("x² + 2");
    assertThat(shown("x^-1")).isEqualTo("1/x");
    assertThat(value("1 = 1.0")).isEqualTo(Bool.TRUE);
    assertThat(value("1/0")).isEqualTo(Constant.UNDEFINED);
    assertThat(value("undefined + 1")).isEqualTo(Constant.UNDEFINED);
  }

  @Test
  void section14() {
    assertThat(shown("1/3 + 1/6")).isEqualTo("1/2");
    assertThat(shown("0.5 + 1/3")).isEqualTo("0.8333333333333333333333333333333333");
    assertThat(shown("exact(0.1)")).isEqualTo("1/10");
    assertThat(shown("2π")).isEqualTo("2π");
    assertThat(value("a := 1; b := 2a; a := 3; b")).isEqualTo(Num.of(6));
    assertThat(error("a := b; b := a; a")).isEqualTo("a is defined in terms of itself");
    assertThat(value("x := 3; f(x) := x^2; f(2)")).isEqualTo(Num.of(4));
    assertThat(value("f(x) := x^2 + 1; f(2)")).isEqualTo(Num.of(5));
    assertThat(error("f(t) := t^2; g(x) := f(x); f := 3; g(2)")).isEqualTo("f is not a function");
    assertThat(value("a := 5; b := a + 1; subs(b, a = 2)")).isEqualTo(Num.of(6));
    assertThat(value("a := 5; subs(a + 1, a = 2)")).isEqualTo(Num.of(3));
    assertThat(error("i < 1")).isEqualTo("complex numbers are not ordered");
    assertThat(shown("sin(30°)", DEGREES)).isEqualTo("1/2");
    assertThat(shown("sin(π)", DEGREES)).isEqualTo("0");
    assertThat(shown("sin(15 + 30°)", DEGREES)).isEqualTo("sin(π/6 + 15)");
    assertThat(shown("asin(1/2)", DEGREES)).isEqualTo("30");
  }
}
