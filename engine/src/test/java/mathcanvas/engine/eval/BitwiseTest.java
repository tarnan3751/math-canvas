package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.compile.Compiled;
import mathcanvas.engine.expr.Symbol;
import org.junit.jupiter.api.Test;

/** The bitwise functions (§14.27), exact and compiled. */
class BitwiseTest {

  @Test
  void onIntegersAsBigInteger() {
    assertThat(shown("bitand(12, 10)")).isEqualTo("8");
    assertThat(shown("bitor(12, 10)")).isEqualTo("14");
    assertThat(shown("bitxor(12, 10)")).isEqualTo("6");
    assertThat(shown("bitnot(5)")).isEqualTo("-6");
    assertThat(shown("bitand(-1, 0xff)")).isEqualTo("255");
    assertThat(shown("shiftleft(1, 100)")).isEqualTo("1267650600228229401496703205376");
    assertThat(shown("shiftright(-16, 2)")).isEqualTo("-4");
    assertThat(shown("bitcount(255)")).isEqualTo("8");
    assertThat(shown("bit(5, 0)")).isEqualTo("1");
    assertThat(shown("bit(5, 1)")).isEqualTo("0");
    assertThat(shown("bit(-1, 1000)")).isEqualTo("1");
    assertThat(shown("hex(bitand(0xf0, 0x3c))")).isEqualTo("0x30");
    assertThat(shown("bitand(x, 3)")).isEqualTo("bitand(x, 3)");
  }

  @Test
  void theErrors() {
    assertThat(error("bitand(1.5, 2)")).isEqualTo("`bitand` needs integers");
    assertThat(error("bitor(1/2, 2)")).isEqualTo("`bitor` needs integers");
    assertThat(error("bitcount(-1)")).isEqualTo("`bitcount` needs a non-negative integer");
    assertThat(error("shiftleft(1, -1)"))
        .isEqualTo("`shiftleft`'s shift must be an integer from 0 to 1048576");
    assertThat(error("bit(1, -1)")).isEqualTo("`bit`'s index must be an integer from 0 to 1048576");
  }

  @Test
  void theCompiledFormsAgreeOnIntegersAndAreNaNElsewhere() {
    Symbol x = Symbol.of("x");
    Compiled and = Compiled.compile(Run.value("bitand(x, 12)"), List.of(x), Settings.DEFAULT);
    assertThat(and.eval(10)).isEqualTo(8.0);
    assertThat(and.eval(-1)).isEqualTo(12.0);
    assertThat(and.eval(1.5)).isNaN();
    assertThat(Compiled.compile(Run.value("shiftleft(x, 3)"), List.of(x), Settings.DEFAULT).eval(5))
        .isEqualTo(40.0);
    assertThat(
            Compiled.compile(Run.value("shiftright(x, 1)"), List.of(x), Settings.DEFAULT).eval(-7))
        .isEqualTo(-4.0);
    assertThat(Compiled.compile(Run.value("bitnot(x)"), List.of(x), Settings.DEFAULT).eval(5))
        .isEqualTo(-6.0);
    assertThat(Compiled.compile(Run.value("bitcount(x)"), List.of(x), Settings.DEFAULT).eval(255))
        .isEqualTo(8.0);
    assertThat(Compiled.compile(Run.value("bit(x, 2)"), List.of(x), Settings.DEFAULT).eval(4))
        .isEqualTo(1.0);
  }
}
