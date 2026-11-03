package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.stage.Popup;
import mathcanvas.app.document.Theme;
import mathcanvas.app.ui.DataEditor;
import org.junit.jupiter.api.Test;

/**
 * The data editor (Phase 8c): a table typed with Enter adding rows and written as a list, a table
 * pasted with its headers and written as a matrix and named lists, reloaded from the cell, a long
 * one scrolled, Escape and undo.
 */
class DataEditorTest extends WorksheetHarness {

  private Popup popup() throws Exception {
    return get(() -> window.dataEditor() == null ? null : window.dataEditor().popup());
  }

  private Node onFx(String id) {
    return window.dataEditor().popup().getScene().lookup("#" + id);
  }

  private String field(String id) throws Exception {
    return get(() -> ((TextField) onFx(id)).getText());
  }

  private void openEditor() throws Exception {
    fx(() -> ((Button) stage.getScene().lookup("#key-data")).fire());
    waitUntil(10, () -> window.dataEditor() != null && window.dataEditor().popup().isShowing());
  }

  private void focus(String id) throws Exception {
    fx(() -> onFx(id).requestFocus());
  }

  private void paste(String text) throws Exception {
    fx(() -> Clipboard.getSystemClipboard().setContent(Map.of(DataFormat.PLAIN_TEXT, text)));
    press(KeyCode.V, true, false, false);
  }

  @Test
  void aColumnTypedWithEnterAddingRowsIsWrittenAsAListInOneUndoStep() throws Exception {
    fx(() -> window.cellViews().getFirst().field().requestFocus());
    openEditor();
    assertThat(get(() -> popup().getScene().getFocusOwner().getId())).isEqualTo("data-cell-0-0");
    assertThat(field("data-name")).isEqualTo("data");
    type("4");
    press(KeyCode.ENTER); // after the last row: a new row
    type("8");
    press(KeyCode.ENTER);
    type("15");
    assertThat(get(() -> popup().getScene().getFocusOwner().getId())).isEqualTo("data-cell-2-0");
    press(KeyCode.UP);
    type("9"); // the field's text selected on focus: replaced
    assertThat(get(() -> window.dataEditor().table().rows()))
        .containsExactly(java.util.List.of("4"), java.util.List.of("9"), java.util.List.of("15"));
    assertThat(input(0)).isEmpty();
    fx(() -> ((Button) onFx("data-done")).fire());
    assertThat(get(() -> popup().isShowing())).isFalse();
    assertThat(input(0)).isEqualTo("data := [4, 9, 15]");
    // one undo step, the focus back in the cell
    press(KeyCode.Z, true, false, false);
    assertThat(input(0)).isEmpty();
    press(KeyCode.Z, true, true, false);
    assertThat(input(0)).isEqualTo("data := [4, 9, 15]");
    press(KeyCode.ENTER);
    waitForOutput(0, "data := ⟨4, 9, 15⟩");
  }

  @Test
  void aPastedTableWithHeadersIsAMatrixAndNamedListsAndReloads() throws Exception {
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    fx(() -> window.cellViews().getFirst().field().requestFocus());
    openEditor();
    focus("data-header-0");
    paste("x\ty\n1\t2\n3\t5\n4\t4\n");
    assertThat(get(() -> ((Spinner<?>) onFx("data-columns")).getValue())).isEqualTo(2);
    assertThat(field("data-header-1")).isEqualTo("y");
    assertThat(field("data-cell-2-1")).isEqualTo("4");
    screenshot(get(() -> popup().getScene()), "data-editor.png");
    fx(() -> ((Button) onFx("data-done")).fire());
    String written = "data := [[1, 2], [3, 5], [4, 4]]\nx := [1, 3, 4]\ny := [2, 5, 4]";
    assertThat(input(0)).isEqualTo(written);

    // opened again on the cell, the table comes back; commas paste too, from the field pasted at
    openEditor();
    assertThat(field("data-header-0")).isEqualTo("x");
    assertThat(field("data-cell-1-1")).isEqualTo("5");
    focus("data-cell-2-0");
    paste("7, 1\n8, 0");
    fx(() -> ((TextField) onFx("data-name")).setText("pts"));
    fx(() -> ((Button) onFx("data-done")).fire());
    assertThat(input(0))
        .isEqualTo("pts := [[1, 2], [3, 5], [7, 1], [8, 0]]\nx := [1, 3, 7, 8]\ny := [2, 5, 1, 0]");

    // Escape leaves the cell as it was; a name that cannot be defined disables Done
    openEditor();
    fx(() -> ((TextField) onFx("data-name")).setText("sin"));
    assertThat(get(() -> onFx("data-done").isDisabled())).isTrue();
    press(KeyCode.ESCAPE);
    assertThat(get(() -> popup().isShowing())).isFalse();
    assertThat(input(0)).startsWith("pts := ");
  }

  @Test
  void aLongTableIsScrolledThroughTheFieldsItShows() throws Exception {
    fx(() -> window.cellViews().getFirst().field().requestFocus());
    openEditor();
    StringBuilder lines = new StringBuilder();
    for (int k = 1; k <= 40; k++) {
      lines.append(k).append(",").append(k * k).append('\n');
    }
    focus("data-cell-0-0");
    paste(lines.toString());
    // the grid shows the last rows pasted, the focus on the last
    assertThat(get(() -> window.dataEditor().firstShown())).isEqualTo(40 - DataEditor.VISIBLE);
    assertThat(field("data-cell-" + (DataEditor.VISIBLE - 1) + "-1")).isEqualTo("1600");
    press(KeyCode.ENTER);
    assertThat(get(() -> window.dataEditor().table().rows())).hasSize(41);
    assertThat(get(() -> window.dataEditor().firstShown())).isEqualTo(41 - DataEditor.VISIBLE);
    for (int k = 0; k < 15; k++) {
      press(KeyCode.UP);
    }
    assertThat(get(() -> window.dataEditor().firstShown())).isEqualTo(25);
    assertThat(field("data-cell-0-0")).isEqualTo("26");
    fx(() -> ((Button) onFx("data-done")).fire());
    assertThat(input(0)).startsWith("data := [[1, 1], [2, 4], ").endsWith("[40, 1600]]");
  }
}
