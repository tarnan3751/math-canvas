package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.event.Event;
import javafx.scene.control.ListView;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.junit.jupiter.api.Test;

/** The history panel lists evaluated inputs, newest first, and recalls one into the cell. */
class HistoryPanelTest extends WorksheetHarness {

  @SuppressWarnings("unchecked")
  private ListView<String> list() {
    return (ListView<String>) stage.getScene().lookup("#history-list");
  }

  @Test
  void anInputIsRecalledByAClickOrByEnter() throws Exception {
    enter("2 + 3");
    waitForOutput(0, "5");
    enter("7 * 6");
    waitForOutput(1, "42");
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-history")).fire());

    assertThat(get(() -> list().getItems())).containsExactly("7 * 6", "2 + 3");
    screenshot("worksheet-history.png");
    fx(
        () -> {
          list().getSelectionModel().select(1);
          Event.fireEvent(
              list(),
              new MouseEvent(
                  MouseEvent.MOUSE_CLICKED,
                  0,
                  0,
                  0,
                  0,
                  MouseButton.PRIMARY,
                  1,
                  false,
                  false,
                  false,
                  false,
                  true,
                  false,
                  false,
                  true,
                  false,
                  true,
                  null));
        });
    assertThat(input(2)).isEqualTo("2 + 3");
    assertThat(get(() -> stage.getScene().getFocusOwner() == window.cellViews().get(2).field()))
        .isTrue();

    fx(
        () -> {
          list().getSelectionModel().select(0);
          Event.fireEvent(
              list(),
              new KeyEvent(
                  KeyEvent.KEY_PRESSED, "", "", KeyCode.ENTER, false, false, false, false));
        });
    assertThat(input(2)).isEqualTo("7 * 6");
    press(KeyCode.ENTER);
    waitForOutput(2, "42");
  }
}
