package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.shape.Rectangle;
import mathcanvas.app.ui.CellEditor;
import org.junit.jupiter.api.Test;

/**
 * The input editor: brackets marked at the caret, a closing bracket typed over its twin,
 * Shift+Enter for a second line, and Up and Down moving between lines before cells.
 */
class CellEditorTest extends WorksheetHarness {

  private CellEditor editor(int cell) {
    return (CellEditor) window.cellViews().get(cell).field().getParent();
  }

  private List<String> marks(int cell) throws Exception {
    return get(
        () -> {
          Rectangle[] r = editor(cell).marks();
          return List.of(
              r[0].isVisible() ? String.join(" ", r[0].getStyleClass()) : "-",
              r[1].isVisible() ? String.join(" ", r[1].getStyleClass()) : "-");
        });
  }

  @Test
  void theBracketAtTheCaretAndItsPartnerAreMarked() throws Exception {
    type("sin((x + 1)");
    waitUntil(10, () -> editor(0).marks()[0].isVisible());
    assertThat(marks(0)).containsExactly("bracket-match", "bracket-match");
    assertThat(get(() -> editor(0).marks()[1].getX()))
        .isLessThan(get(() -> editor(0).marks()[0].getX()));
    screenshot("editor-brackets.png");

    fx(() -> window.cellViews().get(0).field().positionCaret(4));
    waitUntil(10, () -> !editor(0).marks()[1].isVisible());
    assertThat(marks(0)).containsExactly("bracket-unmatched", "-");
  }

  @Test
  void aClosingBracketIsTypedOverItsTwin() throws Exception {
    type("f()");
    fx(() -> window.cellViews().get(0).field().positionCaret(2));
    type(")");
    TextArea area = get(() -> window.cellViews().get(0).field());
    assertThat(get(() -> area.getText())).isEqualTo("f()");
    assertThat(get(() -> area.getCaretPosition())).isEqualTo(3);
  }

  @Test
  void shiftEnterStartsALineAndEnterEvaluatesTheWholeCell() throws Exception {
    type("a := 1");
    press(KeyCode.ENTER, false, true, false);
    type("a + 1");
    assertThat(input(0)).isEqualTo("a := 1\na + 1");

    // Up on the second line stays in the cell; on the first it would leave it
    press(KeyCode.UP);
    assertThat(get(() -> stage.getScene().getFocusOwner() == window.cellViews().get(0).field()))
        .isTrue();
    press(KeyCode.ENTER);
    waitForOutput(0, "a := 1", "2");
  }
}
