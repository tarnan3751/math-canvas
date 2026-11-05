package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

/**
 * Keyboard reach and names for assistive technology: Tab moves through the window in reading order,
 * the navigation rail, the app bar, then the cells, then the canvas beside them when it is shown,
 * then the side panels, and back to the rail; a result is read as its ASCII print, a keypad key as
 * what it inserts.
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
    fx(() -> stage.getScene().lookup("#new").requestFocus());
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

    // the rail, then the app bar (Undo and Redo disabled with nothing to undo, Stop hidden)
    List<String> toolbar =
        List.of(
            "new",
            "toggle-worksheet",
            "toggle-canvas",
            "toggle-keypad",
            "toggle-history",
            "toggle-inspector",
            "toggle-unit-circle",
            "help",
            "settings",
            "toggle-steps",
            "run-all");
    assertThat(seen.subList(0, toolbar.size())).isEqualTo(toolbar);
    assertThat(seen.get(toolbar.size())).isEqualTo("cell");
    assertThat(seen.get(toolbar.size() + 1)).isEqualTo("key-inv");
    int last = seen.indexOf("key-data");
    assertThat(last).isPositive();
    assertThat(seen.subList(toolbar.size() + 1, last + 1)).allMatch(id -> id.startsWith("key-"));
    assertThat(seen.get(last + 1)).as("back to the rail").isEqualTo("new");
    // a second cycle goes the same way: the keypad's last key is not accepted in the cell
    int second = last + 1;
    assertThat(seen.subList(second, second + last + 1)).isEqualTo(seen.subList(0, last + 1));
    assertThat(input(0)).isEmpty();
    assertThat(get(() -> window.dataEditor())).isNull();
  }

  @Test
  void theCanvasComesAfterTheCells() throws Exception {
    fx(() -> window.canvasShownProperty().set(true));
    List<String> seen = tabs(220);

    // at 1280 px showing the canvas turned the side panels off: the cell, then the canvas
    int cell = seen.indexOf("cell");
    assertThat(cell).isPositive();
    assertThat(seen.get(cell + 1)).isEqualTo("canvas-tool-select");
    assertThat(seen).doesNotContain("key-inv");
    int entry = seen.indexOf("algebra-entry");
    int plane = seen.indexOf("canvas");
    assertThat(entry).isGreaterThan(cell);
    assertThat(plane).isGreaterThan(entry);
    // then back to the rail, and round again
    int rail = seen.subList(plane, seen.size()).indexOf("new") + plane;
    assertThat(rail).isGreaterThan(plane);
    int again = seen.subList(rail, seen.size()).indexOf("canvas-tool-select") + rail;
    assertThat(again).isGreaterThan(rail);
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
        .isEqualTo("Working");
  }
}
