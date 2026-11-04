package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

/**
 * A program typed over several lines of a cell (docs/language.md §19): Shift+Enter starts a line,
 * Enter runs the cell; its printed values come before its value, a function shows its head, and an
 * error inside a block points at its own line with the caret.
 */
class ProgramCellTest extends WorksheetHarness {

  /** Types lines into the current cell, Shift+Enter between them, and runs it with Enter. */
  private void enterLines(String... lines) throws Exception {
    for (int k = 0; k < lines.length; k++) {
      if (k > 0) {
        press(KeyCode.ENTER, false, true, false);
      }
      type(lines[k]);
    }
    press(KeyCode.ENTER);
  }

  @Test
  void aMultiLineProgramPrintsItsOutputAndItsValue() throws Exception {
    enterLines(
        "function fact(n)", "  r := 1", "  for k in 1..n do r := r k end", "  return r", "end");
    waitForOutput(0, "function fact(n)");
    assertThat(input(0)).contains("\n  r := 1\n");
    enterLines("for k in 1..4", "  print(fact(k))", "end", "fact(10)");
    waitForOutput(1, "1", "2", "6", "24", "3628800");
    enterLines("s := 0", "for k in 1..2 do s := s + k; [1] + [1, 2] end");
    waitUntil(
        30,
        () -> window.cellViews().size() > 2 && window.cellViews().get(2).outputTexts().size() == 2);
    assertThat(outputs(2).get(0)).isEqualTo("s := 0");
    assertThat(outputs(2).get(1)).startsWith("cannot add vectors of lengths 1 and 2");
    assertThat(outputs(2).get(1))
        .contains("\nfor k in 1..2 do s := s + k; [1] + [1, 2] end\n")
        .endsWith("^");
    screenshot("worksheet-program.png");
  }
}
