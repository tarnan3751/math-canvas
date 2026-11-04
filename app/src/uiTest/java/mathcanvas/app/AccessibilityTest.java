package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

/**
 * Keyboard reach and names for assistive technology: Tab moves through the window in reading order,
 * the toolbar, then the cells, then the canvas beside them when it is shown, then the side panels,
 * and back to the toolbar; a result is read as its ASCII print, a keypad key as what it inserts.
 */
class AccessibilityTest extends WorksheetHarness {

  /** The focus owner's id, {@code cell} for a cell's field, or its class when it has none. */
  private String owner() throws Exception {
    return get(
        () -> {
          Node n = stage.getScene().getFocusOwner();
          if (n == null) {
            return "none";
          }
          if (window.cellViews().stream().anyMatch(v -> v.field() == n)) {
            return "cell";
          }
          return n.getId() != null ? n.getId() : n.getClass().getSimpleName();
        });
  }

  private List<String> tabs(int count) throws Exception {
    fx(() -> stage.getScene().lookup("#theme").requestFocus());
    List<String> seen = new ArrayList<>();
    for (int k = 0; k < count; k++) {
      seen.add(owner());
      press(KeyCode.TAB);
    }
    return seen;
  }

  @Test
  void tabMovesThroughTheWindowInReadingOrder() throws Exception {
    List<String> seen = tabs(160);

    List<String> toolbar =
        List.of(
            "theme",
            "toggle-keypad",
            "toggle-history",
            "toggle-inspector",
            "toggle-steps",
            "toggle-canvas",
            "toggle-space",
            "toggle-unit-circle",
            "settings",
            "help");
    assertThat(seen.subList(0, toolbar.size())).isEqualTo(toolbar);
    assertThat(seen.get(toolbar.size())).isEqualTo("cell");
    assertThat(seen.get(toolbar.size() + 1)).isEqualTo("key-inv");
    int last = seen.indexOf("key-data");
    assertThat(last).isPositive();
    assertThat(seen.subList(toolbar.size() + 1, last + 1)).allMatch(id -> id.startsWith("key-"));
    assertThat(seen.get(last + 1)).as("back to the toolbar").isEqualTo("theme");
    // a second cycle goes the same way: the keypad's last key is not accepted in the cell
    int second = last + 1;
    assertThat(seen.subList(second, second + last + 1)).isEqualTo(seen.subList(0, last + 1));
    assertThat(input(0)).isEmpty();
    assertThat(get(() -> window.dataEditor())).isNull();
  }

  @Test
  void theCanvasComesAfterTheCellsAndBeforeTheSidePanels() throws Exception {
    fx(() -> window.canvasShownProperty().set(true));
    List<String> seen = tabs(220);

    int cell = seen.indexOf("cell");
    assertThat(cell).isPositive();
    assertThat(seen.get(cell + 1)).isEqualTo("canvas-tool-select");
    int entry = seen.indexOf("algebra-entry");
    int plane = seen.indexOf("canvas");
    int keypad = seen.indexOf("key-inv");
    assertThat(entry).isGreaterThan(cell);
    assertThat(plane).isGreaterThan(entry);
    assertThat(keypad).isGreaterThan(plane);
    // and round again
    int again = seen.subList(keypad, seen.size()).indexOf("canvas-tool-select") + keypad;
    assertThat(again).isGreaterThan(keypad);
    assertThat(seen.get(again - 1)).isEqualTo("cell");
  }

  @Test
  void resultsAndKeysAreNamedForAssistiveTechnology() throws Exception {
    enter("(x + 1)^2 + sqrt(2)");
    waitForOutput(0, "√2 + (x + 1)²");
    assertThat(
            get(
                () ->
                    window.cellViews().getFirst().lookupAll("*").stream()
                        .map(Node::getAccessibleText)
                        .filter(t -> t != null)
                        .toList()))
        .contains("sqrt(2) + (x + 1)^2");
    assertThat(get(() -> stage.getScene().lookup("#key-sqrt").getAccessibleText()))
        .isEqualTo(
            get(
                () ->
                    ((javafx.scene.control.Button) stage.getScene().lookup("#key-sqrt"))
                        .getTooltip()
                        .getText()));
    assertThat(get(() -> stage.getScene().lookup("#busy").getAccessibleText()))
        .isEqualTo("Evaluating");
  }
}
