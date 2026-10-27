package mathcanvas.engine.print;

import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.parse.Statement;
import org.junit.jupiter.api.Test;

class EchoAndCaretTest {

  private static Statement.Definition definition(String source) {
    return (Statement.Definition) Parser.parseScript(source, Names.NONE).statements().getFirst();
  }

  @Test
  void aDefinitionEchoesItsHeadAndItsBodyAsParsed() {
    assertThat(UnicodePrinter.INSTANCE.echo(definition("f(x) := x^2 + 1")))
        .isEqualTo("f(x) := x² + 1");
    assertThat(AsciiPrinter.INSTANCE.echo(definition("f(x, y) := x y")))
        .isEqualTo("f(x, y) := x*y");
    assertThat(UnicodePrinter.INSTANCE.echo(definition("a := 2 + 3"))).isEqualTo("a := 2 + 3");
    assertThat(UnicodePrinter.INSTANCE.echo(definition("g := t -> t^2"))).isEqualTo("g := t → t²");
  }

  @Test
  void theCaretSitsUnderTheColumnWithTheTabsOfTheLineCopied() {
    assertThat(Caret.under("1 + ", 5)).isEqualTo("    ^");
    assertThat(Caret.under("x", 1)).isEqualTo("^");
    assertThat(Caret.under("\tab", 4)).isEqualTo("\t  ^");
    assertThat(Caret.under("π + ", 3)).isEqualTo("  ^");
    assertThat(Caret.under("ab", 5)).isEqualTo("    ^");
  }
}
