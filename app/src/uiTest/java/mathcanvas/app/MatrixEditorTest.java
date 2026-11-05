package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.stage.Popup;
import mathcanvas.app.document.Theme;
import mathcanvas.app.ui.ThemeBinding;
import org.junit.jupiter.api.Test;

/**
 * The matrix editor: a matrix built by keyboard alone and evaluated, a literal edited in place, a
 * ragged one padded, a vector, undo and cancel, and the popup in both themes.
 */
class MatrixEditorTest extends WorksheetHarness {

  private Popup popup() throws Exception {
    return get(() -> window.matrixEditor() == null ? null : window.matrixEditor().popup());
  }

  private Node inPopup(String id) throws Exception {
    return get(() -> popup().getScene().lookup("#" + id));
  }

  /** A node of the popup, looked up on the FX thread. */
  private Node onFx(String id) {
    return window.matrixEditor().popup().getScene().lookup("#" + id);
  }

  private String cell(int r, int c) throws Exception {
    return get(
        () -> ((TextField) popup().getScene().lookup("#matrix-cell-" + r + "-" + c)).getText());
  }

  private void openWith(String key) throws Exception {
    fx(() -> ((Button) stage.getScene().lookup("#key-" + key)).fire());
    waitUntil(10, () -> window.matrixEditor() != null && window.matrixEditor().popup().isShowing());
  }

  /** Puts text in the first cell with the caret at an index, as typing would. */
  private void cellText(String text, int caret) throws Exception {
    fx(() -> window.cellViews().getFirst().field().requestFocus());
    type(text);
    fx(() -> window.cellViews().getFirst().field().positionCaret(caret));
  }

  @Test
  void aMatrixIsBuiltByKeyboardAloneAndEvaluates() throws Exception {
    // the keypad's focused key is the matrix key: Tab in the cell accepts it
    fx(() -> stage.getScene().lookup("#key-matrix").requestFocus());
    fx(() -> window.cellViews().getFirst().field().requestFocus());
    press(KeyCode.TAB);
    waitUntil(10, () -> window.matrixEditor() != null && window.matrixEditor().popup().isShowing());
    assertThat(get(() -> popup().getScene().getFocusOwner().getId())).isEqualTo("matrix-cell-0-0");

    // the keys typed in the window reach the popup's focused field
    type("1");
    press(KeyCode.TAB);
    type("2");
    press(KeyCode.DOWN);
    type("4");
    press(KeyCode.LEFT); // after the 4: stays in the field
    press(KeyCode.HOME);
    press(KeyCode.LEFT); // at the start: back to the cell before
    type("3");
    assertThat(cell(1, 0)).isEqualTo("3");
    assertThat(input(0)).isEmpty();
    press(KeyCode.ENTER);

    assertThat(get(() -> popup().isShowing())).isFalse();
    assertThat(input(0)).isEqualTo("[[1, 2], [3, 4]]");
    assertThat(get(() -> stage.getScene().getFocusOwner()))
        .isSameAs(get(() -> window.cellViews().getFirst().field()));
    assertThat(get(() -> window.cellViews().getFirst().field().getCaretPosition())).isEqualTo(16);
    type("^2");
    press(KeyCode.ENTER);
    waitForOutput(0, "[[7, 10], [15, 22]]");
    screenshot("worksheet-matrix.png");
  }

  @Test
  void aLiteralIsEditedInPlaceAndUndoneInOneStep() throws Exception {
    cellText("2*[[1, 2], [3, 4]] + 1", 5);
    openWith("matrix");
    assertThat(cell(0, 0)).isEqualTo("1");
    assertThat(cell(1, 1)).isEqualTo("4");
    assertThat(get(() -> ((Button) inPopup("matrix-insert")).getText())).isEqualTo("Replace");

    fx(() -> ((Spinner<?>) onFx("matrix-rows")).increment());
    assertThat(cell(2, 1)).isEmpty();
    type("x");
    press(KeyCode.ENTER);
    assertThat(input(0)).isEqualTo("2*[[x, 2], [3, 4], [0, 0]] + 1");

    press(KeyCode.Z, true, false, false);
    assertThat(input(0)).isEqualTo("2*[[1, 2], [3, 4]] + 1");
  }

  @Test
  void raggedRowsArePaddedAndEscapeCancels() throws Exception {
    cellText("[[1, 2, 3], [4]]", 13);
    openWith("vector"); // the literal under the caret decides the shape
    assertThat(cell(1, 0)).isEqualTo("4");
    assertThat(cell(1, 2)).isEmpty();
    assertThat(get(() -> ((Spinner<?>) inPopup("matrix-columns")).getValue())).isEqualTo(3);
    press(KeyCode.ESCAPE);
    assertThat(get(() -> popup().isShowing())).isFalse();
    assertThat(input(0)).isEqualTo("[[1, 2, 3], [4]]");

    // the window losing the focus hides it too, with nothing changed
    openWith("matrix");
    SimpleBooleanProperty focused = new SimpleBooleanProperty(true);
    fx(() -> window.matrixEditor().hideOnFocusLoss(focused));
    fx(() -> focused.set(false));
    assertThat(get(() -> popup().isShowing())).isFalse();
    assertThat(input(0)).isEqualTo("[[1, 2, 3], [4]]");

    openWith("matrix");
    fx(() -> ((Button) onFx("matrix-insert")).fire());
    assertThat(input(0)).isEqualTo("[[1, 2, 3], [4, 0, 0]]");
  }

  @Test
  void theVectorKeyBuildsAColumn() throws Exception {
    cellText("v := ", 5);
    openWith("vector");
    assertThat(inPopup("matrix-columns")).isNull();
    type("a");
    press(KeyCode.TAB);
    type("b");
    press(KeyCode.TAB);
    type("c");
    press(KeyCode.ENTER);
    assertThat(input(0)).isEqualTo("v := [a, b, c]");
    press(KeyCode.Z, true, false, false);
    assertThat(input(0)).isEqualTo("v := ");
    press(KeyCode.Z, true, true, false);
    assertThat(input(0)).isEqualTo("v := [a, b, c]");
    press(KeyCode.ENTER);
    waitForOutput(0, "v := ⟨a, b, c⟩");
  }

  @Test
  void thePopupFollowsTheTheme() throws Exception {
    cellText("[[a, b], [c, d]]", 3);
    chooseTheme(Theme.LIGHT);
    openWith("matrix");
    WritableImage light = screenshot(popup().getScene(), "matrix-editor-light.png");
    assertThat(get(() -> inPopup("matrix-editor").getStyleClass().contains(ThemeBinding.DARK)))
        .isFalse();
    press(KeyCode.ESCAPE);

    chooseTheme(Theme.DARK);
    openWith("matrix");
    WritableImage dark = screenshot(popup().getScene(), "matrix-editor-dark.png");
    assertThat(get(() -> inPopup("matrix-editor").getStyleClass().contains(ThemeBinding.DARK)))
        .isTrue();
    // inside the editor's top padding: its corners are rounded, so a corner pixel is outside it
    int x = (int) light.getWidth() / 2;
    assertThat(brightness(dark.getPixelReader().getArgb(x, 10)))
        .isLessThan(brightness(light.getPixelReader().getArgb(x, 10)));
    fx(() -> ((Button) onFx("matrix-insert")).fire());
    screenshot("worksheet-matrix-dark.png");
  }
}
