package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.ParseException;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.AsciiPrinter;
import org.junit.jupiter.api.Test;

/** Base literals, integers shown in other bases, (§3.1, §14.27). */
class BasesTest {

  @Test
  void baseLiteralsAreIntegers() {
    assertThat(shown("0xff")).isEqualTo("255");
    assertThat(shown("0XFF + 1")).isEqualTo("256");
    assertThat(shown("0b1011")).isEqualTo("11");
    assertThat(shown("0o17")).isEqualTo("15");
    assertThat(shown("0xFFFF_FFFF")).isEqualTo("4294967295");
    assertThat(shown("0xff x")).isEqualTo("255x");
    assertThat(shown("0x + 1")).isEqualTo("1");
  }

  @Test
  void theLiteralsErrors() {
    for (String[] c :
        new String[][] {
          {"0b102", "`2` is not a binary digit"},
          {"0x1g", "`g` is not a hexadecimal digit"},
          {"0o8", "`8` is not an octal digit"},
          {"0x_ff", "a digit separator must stand between digits"},
          {"0xff_", "a digit separator must stand between digits"},
        }) {
      assertThatThrownBy(() -> Parser.parseExpression(c[0], Names.NONE))
          .as(c[0])
          .isInstanceOf(ParseException.class)
          .hasMessageContaining(c[1]);
    }
  }

  @Test
  void aBaseLiteralLongerThanItsCapIsOutOfRange() {
    String digits = "f".repeat(mathcanvas.engine.parse.Lexer.MAX_BASED_LENGTH - 2);
    assertThat(Parser.parseExpression("0x" + digits, Names.NONE)).isNotNull();
    assertThatThrownBy(() -> Parser.parseExpression("0x" + digits + "f", Names.NONE))
        .isInstanceOf(ParseException.class)
        .hasMessageContaining("number out of range");
  }

  @Test
  void aZeroBeforeANameThatWouldReadAsABaseLiteralKeepsItsSpace() {
    for (String text : new String[] {"0 x2", "0 b2", "0 o7", "0 X1", "0 b_1", "x -> 0 x2"}) {
      mathcanvas.engine.expr.Expr parsed = Parser.parseExpression(text, Names.NONE);
      for (mathcanvas.engine.print.Printer printer :
          new mathcanvas.engine.print.Printer[] {
            mathcanvas.engine.print.UnicodePrinter.INSTANCE, AsciiPrinter.INSTANCE
          }) {
        String printed = printer.print(parsed);
        assertThat(Parser.parseExpression(printed, Names.NONE)).as(printed).isEqualTo(parsed);
      }
    }
    assertThat(
            mathcanvas.engine.print.UnicodePrinter.INSTANCE.print(
                Parser.parseExpression("0 x2", Names.NONE)))
        .isEqualTo("0 x2");
    assertThat(
            mathcanvas.engine.print.UnicodePrinter.INSTANCE.print(
                Parser.parseExpression("0 x", Names.NONE)))
        .isEqualTo("0x");
  }

  @Test
  void anIntegerShownInABase() {
    assertThat(shown("hex(255)")).isEqualTo("0xff");
    assertThat(shown("bin(11)")).isEqualTo("0b1011");
    assertThat(shown("oct(15)")).isEqualTo("0o17");
    assertThat(shown("hex(-255)")).isEqualTo("-0xff");
    assertThat(shown("base(1295, 36)")).isEqualTo("zz₍₃₆₎");
    assertThat(shown("base(255, 10)")).isEqualTo("255");
    assertThat(shown("hex(0)")).isEqualTo("0x0");
    assertThat(shown("[hex(1), hex(255)]")).isEqualTo("⟨0x1, 0xff⟩");
    assertThat(shown("a := hex(255); a")).isEqualTo("0xff");
    assertThat(shown("hex(x)")).isEqualTo("hex(x)");
    assertThat(AsciiPrinter.INSTANCE.display(Run.value("base(1295, 36)")))
        .isEqualTo("base(1295, 36)");
    assertThat(AsciiPrinter.INSTANCE.display(Run.value("hex(255)"))).isEqualTo("0xff");
  }

  @Test
  void theBaseDoesNotPropagate() {
    assertThat(shown("hex(255) + 1")).isEqualTo("256");
    assertThat(shown("2 hex(3)")).isEqualTo("6");
    assertThat(shown("hex(255) = 255")).isEqualTo("true");
    assertThat(shown("{hex(1), 1}")).isEqualTo("{1}");
    assertThat(shown("sin(hex(0))")).isEqualTo("0");
    assertThat(shown("hex(hex(255))")).isEqualTo("0xff");
    assertThat(shown("bin(hex(5))")).isEqualTo("0b101");
    assertThat(Canonical.of(Run.value("hex(255)"))).isEqualTo(Run.value("hex(255)"));
  }

  @Test
  void digits() {
    assertThat(shown("digits(11, 2)")).isEqualTo("⟨1, 0, 1, 1⟩");
    assertThat(shown("digits(255, 16)")).isEqualTo("⟨15, 15⟩");
    assertThat(shown("digits(0, 10)")).isEqualTo("⟨0⟩");
  }

  @Test
  void theErrors() {
    assertThat(error("hex(1/2)")).isEqualTo("hex needs an integer");
    assertThat(error("base(10, 37)")).isEqualTo("the base must be an integer from 2 to 36");
    assertThat(error("digits(-1, 2)")).isEqualTo("digits needs a non-negative integer");
    assertThat(error("hex(3 _m)")).isEqualTo("hex needs a number, not a quantity in m");
  }
}
