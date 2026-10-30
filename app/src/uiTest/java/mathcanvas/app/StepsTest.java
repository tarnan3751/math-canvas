package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import mathcanvas.render.MathView;
import org.junit.jupiter.api.Test;

/** A traced value shows its narration steps in an expander under it. */
class StepsTest extends WorksheetHarness {

  private TitledPane steps(int cell) {
    return (TitledPane) window.cellViews().get(cell).lookup(".cell-steps");
  }

  @Test
  void stepsOfAStepsCallAreShownInAnExpander() throws Exception {
    enter("steps(sqrt(8))");
    waitUntil(30, () -> steps(0) != null);

    assertThat(get(() -> steps(0).isExpanded())).isFalse();
    assertThat(get(() -> steps(0).getText())).startsWith("Steps (");
    fx(() -> steps(0).setExpanded(true));
    assertThat(get(() -> steps(0).getContent().lookupAll(".step-math").size())).isPositive();
    assertThat(get(() -> steps(0).getContent().lookup(".step-math"))).isInstanceOf(MathView.class);
    screenshot("worksheet-steps.png");

    // a plain value has no expander until the global toggle traces every evaluation
    enter("expand((x + 1)^3)");
    waitForOutput(1, "x³ + 3x² + 3x + 1");
    assertThat(get(() -> steps(1))).isNull();
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-steps")).fire());
    enter("expand((x + 1)^3)");
    waitUntil(30, () -> steps(2) != null);
  }
}
