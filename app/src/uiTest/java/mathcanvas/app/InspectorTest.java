package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import mathcanvas.app.ui.InspectorPanel;
import mathcanvas.render.MathView;
import org.junit.jupiter.api.Test;

/** The variables inspector lists the names defined, their definitions and the assumptions. */
class InspectorTest extends WorksheetHarness {

  private InspectorPanel inspector() {
    return (InspectorPanel) stage.getScene().lookup("#inspector");
  }

  @Test
  void namesAreListedInsertedAndCleared() throws Exception {
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-inspector")).fire());
    enter("f(x) := x^2 + 1");
    enter("a := 3");
    enter("assume(x > 0)");
    waitUntil(30, () -> inspector().names().size() == 2 && !inspector().assumptions().isEmpty());

    assertThat(get(() -> inspector().names())).containsExactly("a", "f");
    assertThat(get(() -> inspector().assumptions())).containsExactly("x > 0");
    assertThat(get(() -> stage.getScene().lookup("#inspector-f").lookup(".math-view")))
        .isInstanceOf(MathView.class);
    screenshot("worksheet-inspector.png");

    fx(
        () -> {
          Node row = stage.getScene().lookup("#inspector-f");
          Event.fireEvent(
              row,
              new MouseEvent(
                  MouseEvent.MOUSE_CLICKED,
                  0,
                  0,
                  0,
                  0,
                  MouseButton.PRIMARY,
                  2,
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
    assertThat(input(3)).isEqualTo("f");

    fx(() -> ((Button) stage.getScene().lookup("#clear-f")).fire());
    waitUntil(30, () -> inspector().names().size() == 1);
    assertThat(get(() -> inspector().names())).containsExactly("a");
    // the cell being typed in keeps its text; the clear runs as a cell after it
    assertThat(input(3)).isEqualTo("f");
    assertThat(input(4)).isEqualTo("clear(f)");
  }
}
