package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.print.AsciiPrinter;
import mathcanvas.engine.print.LatexPrinter;
import mathcanvas.engine.print.UnicodePrinter;
import org.junit.jupiter.api.Test;

/** The prime notation: a user function's derivative at a point, its errors and its printing. */
class PrimeNotationTest {

  @Test
  void aUserFunctionsDerivativeAtAPoint() {
    assertThat(shown("f(x) := x^3; f'(2)")).isEqualTo("12");
    assertThat(shown("f(x) := x^3; f''(x)")).isEqualTo("6x");
    assertThat(shown("f(t) := sin(t); f′(π)")).isEqualTo("-1");
    // the argument is evaluated first, and the parameter's name does not capture it
    assertThat(shown("f(x) := x^2; f'(x + 1)")).isEqualTo("2(x + 1)");
    assertThat(shown("f(t) := t^2 + a t; f'(a)")).isEqualTo("3a");
  }

  @Test
  void errors() {
    assertThat(Run.error("y'(x)")).isEqualTo("y is not a function");
    assertThat(Run.error("f(x, z) := x z; f'(1)"))
        .isEqualTo("the prime notation needs a function of one argument: write diff");
    assertThat(Run.error("f(x) := x; f'(1, 2)")).isEqualTo("f' takes 1 argument");
  }

  @Test
  void primedNamesPrint() {
    Apply y = Apply.of(Symbol.of("y''"), Symbol.of("x"));
    assertThat(AsciiPrinter.INSTANCE.print(y)).isEqualTo("y''(x)");
    assertThat(UnicodePrinter.INSTANCE.print(y)).isEqualTo("y′′(x)");
    assertThat(LatexPrinter.INSTANCE.print(y)).isEqualTo("y''(x)");
  }
}
