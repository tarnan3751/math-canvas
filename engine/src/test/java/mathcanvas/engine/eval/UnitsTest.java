package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.ParseException;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.AsciiPrinter;
import mathcanvas.engine.print.UnicodePrinter;
import org.junit.jupiter.api.Test;

/** Units in the language (§3.6, §14.26): the token, quantities as values, and their printing. */
class UnitsTest {

  private static Expr parsed(String text) {
    return Canonical.of(Parser.parseExpression(text, Names.NONE));
  }

  @Test
  void aQuantityIsAMagnitudeTimesUnits() {
    assertThat(shown("3 _m")).isEqualTo("3 _m");
    assertThat(shown("5 _km/_h")).isEqualTo("5 _km/_h");
    assertThat(shown("25/18 _m/_s")).isEqualTo("25/18 _m/_s");
    assertThat(shown("9.8 _m/_s^2")).isEqualTo("9.8 _m/_s²");
    assertThat(shown("3 _N * 2 _m")).isEqualTo("6 _N _m");
    assertThat(shown("_J/(_kg _K)")).isEqualTo("_J/(_kg _K)");
    assertThat(shown("5/_s")).isEqualTo("5/_s");
    assertThat(shown("(x + 1) _m")).isEqualTo("(x + 1) _m");
    assertThat(shown("-3 _kg")).isEqualTo("-3 _kg");
    assertThat(shown("3_m")).isEqualTo("3 _m");
    assertThat(shown("2 _kohm")).isEqualTo("2 _kΩ");
    assertThat(shown("3 _μm")).isEqualTo("3 _µm");
  }

  @Test
  void unitsArePositiveConstants() {
    assertThat(shown("_m * _m")).isEqualTo("_m²");
    assertThat(shown("3 _m + 2 _m")).isEqualTo("5 _m");
    assertThat(shown("_m/_m")).isEqualTo("1");
    assertThat(shown("sqrt(4 _m^2)")).isEqualTo("2 _m");
    assertThat(shown("abs(-3 _m)")).isEqualTo("3 _m");
    assertThat(shown("diff(3 _m x^2, x)")).isEqualTo("6x _m");
    assertThat(shown("expand((x + 1) _m)")).isEqualTo("x _m + _m");
  }

  @Test
  void quantitiesPrintAndReadBack() {
    for (String text :
        new String[] {
          "25/18 _m/_s",
          "x _m",
          "(x + 3) _m",
          "_J/(_kg _K)",
          "5/_s",
          "-3 _kg",
          "2 _kohm",
          "3 _um",
          "1/_s^2",
          "-_m",
          "-_m/_s",
          "-2/_s",
          "1/_s",
          "_m/_s"
        }) {
      Expr e = parsed(text);
      assertThat(parsed(UnicodePrinter.INSTANCE.display(e))).as(text).isEqualTo(e);
      assertThat(parsed(AsciiPrinter.INSTANCE.display(e))).as(text).isEqualTo(e);
    }
    assertThat(AsciiPrinter.INSTANCE.display(parsed("25/18 _m/_s"))).isEqualTo("25/18*_m/_s");
    assertThat(AsciiPrinter.INSTANCE.display(parsed("2 _kΩ"))).isEqualTo("2*_kohm");
    // a unit part with no unit above the line divides (§14.26)
    assertThat(UnicodePrinter.INSTANCE.display(parsed("1/_s^2"))).isEqualTo("1/_s²");
    assertThat(UnicodePrinter.INSTANCE.display(parsed("-_m"))).isEqualTo("-_m");
    assertThat(UnicodePrinter.INSTANCE.display(parsed("-_m/_s"))).isEqualTo("-_m/_s");
    assertThat(AsciiPrinter.INSTANCE.display(parsed("-_m"))).isEqualTo("-_m");
  }

  @Test
  void quantitiesInLatexAreUprightOnTheLine() {
    mathcanvas.engine.print.LatexPrinter latex = mathcanvas.engine.print.LatexPrinter.INSTANCE;
    assertThat(latex.display(parsed("25/18 _m/_s")))
        .isEqualTo("\\frac{25}{18}\\,\\mathrm{m}/\\mathrm{s}");
    assertThat(latex.display(parsed("5 _km/_h"))).isEqualTo("5\\,\\mathrm{km}/\\mathrm{h}");
    assertThat(latex.display(parsed("x _J/(_kg _K)")))
        .isEqualTo("x\\,\\mathrm{J}/(\\mathrm{kg}\\,\\mathrm{K})");
    assertThat(latex.display(parsed("5/_s"))).isEqualTo("5/\\mathrm{s}");
    assertThat(latex.display(parsed("-_m"))).isEqualTo("-\\mathrm{m}");
    assertThat(latex.display(parsed("6 _N _m"))).isEqualTo("6\\,\\mathrm{N}\\,\\mathrm{m}");
  }

  @Test
  void aQuantityDoesNotCompile() {
    assertThatThrownBy(
            () ->
                mathcanvas.engine.compile.Compiled.compile(
                    Canonical.of(parsed("3 _m x")),
                    java.util.List.of(mathcanvas.engine.expr.Symbol.of("x")),
                    Settings.DEFAULT))
        .isInstanceOf(mathcanvas.engine.compile.UnsupportedException.class);
  }

  @Test
  void theTokenAndItsErrors() {
    assertThatThrownBy(() -> parsed("_tmp"))
        .isInstanceOf(ParseException.class)
        .hasMessageContaining("unknown unit `_tmp`");
    assertThatThrownBy(() -> parsed("_ 2"))
        .isInstanceOf(ParseException.class)
        .hasMessageContaining("a unit name must follow `_`, as in `3 _m`");
    assertThatThrownBy(() -> Parser.parseScript("_m := 3", Names.NONE))
        .isInstanceOf(ParseException.class)
        .hasMessageContaining("a unit is built in, so it cannot be redefined");
    // x_m is a name, not x times a unit
    assertThat(shown("x_m")).isEqualTo("xₘ");
  }
}
