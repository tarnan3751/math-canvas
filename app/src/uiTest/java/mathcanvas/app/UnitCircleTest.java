package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.OptionalInt;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.DocumentSettings;
import mathcanvas.app.document.Theme;
import mathcanvas.app.ui.UnitCirclePanel;
import mathcanvas.engine.eval.AngleMode;
import org.junit.jupiter.api.Test;

/**
 * The unit circle view: opened from the toolbar, dragged to a special angle with exact values, an
 * angle typed, the angle mode followed, the keys, a call sent to a cell and evaluated, and both
 * themes in screenshots.
 */
class UnitCircleTest extends WorksheetHarness {

  private UnitCirclePanel panel() {
    return window.unitCircle();
  }

  private void openView() throws Exception {
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-unit-circle")).fire());
    waitUntil(10, () -> panel().isVisible() && panel().getHeight() > 0);
  }

  /** Fires a mouse event at the picture, at an angle on the circle, Alt held or not. */
  private void mouse(EventType<MouseEvent> type, double degrees, boolean alt) throws Exception {
    fx(
        () -> {
          Canvas canvas = (Canvas) stage.getScene().lookup("#unit-circle-canvas");
          double c = canvas.getWidth() / 2;
          double r = 0.32 * canvas.getWidth();
          double a = Math.toRadians(degrees);
          Point2D at = canvas.localToScene(c + r * Math.cos(a), c - r * Math.sin(a));
          Event.fireEvent(
              canvas,
              new MouseEvent(
                  type,
                  at.getX(),
                  at.getY(),
                  0,
                  0,
                  MouseButton.PRIMARY,
                  1,
                  false,
                  false,
                  alt,
                  false,
                  type != MouseEvent.MOUSE_RELEASED,
                  false,
                  false,
                  true,
                  false,
                  false,
                  null));
        });
  }

  private void dragTo(double degrees, boolean alt) throws Exception {
    mouse(MouseEvent.MOUSE_PRESSED, 80, alt);
    mouse(MouseEvent.MOUSE_DRAGGED, degrees, alt);
    mouse(MouseEvent.MOUSE_RELEASED, degrees, alt);
  }

  private void waitForValues(String... values) throws Exception {
    waitUntil(10, () -> panel().valueTexts().equals(List.of(values)));
  }

  private String shown() throws Exception {
    return get(() -> ((Label) stage.getScene().lookup("#unit-circle-shown")).getText());
  }

  private void angleMode(AngleMode mode) throws Exception {
    fx(
        () -> {
          DocumentSettings s = main.document().state().settings();
          main.document()
              .execute(
                  new Command.SetSettings(
                      new DocumentSettings(
                          s.precision(), mode, s.numberFormat(), s.numberDigits())));
        });
  }

  @Test
  void aDragSnapsToThirtyDegreesWithExactValues() throws Exception {
    assertThat(get(() -> panel().isVisible())).isFalse();
    openView();
    waitForValues("0", "1", "0");

    // while dragging, the values come from doubles; on release, from the engine
    mouse(MouseEvent.MOUSE_PRESSED, 80, false);
    mouse(MouseEvent.MOUSE_DRAGGED, 31.5, false);
    assertThat(get(() -> panel().special())).isEqualTo(OptionalInt.of(2));
    assertThat(get(() -> panel().valueTexts()))
        .containsExactly("0.5", "0.8660254038", "0.5773502692");
    mouse(MouseEvent.MOUSE_RELEASED, 31.5, false);
    waitForValues("1/2", "√3/2", "√3/3");
    assertThat(shown()).isEqualTo("θ = π/6 = 30°");
    screenshot("unit-circle-keypad.png");

    // Alt drags freely, to decimals at the document's digits
    dragTo(31.5, true);
    assertThat(get(() -> panel().special())).isEmpty();
    double a = get(() -> panel().angle());
    assertThat(Math.toDegrees(a)).isCloseTo(31.5, org.assertj.core.data.Offset.offset(1e-3));
    // the engine's decimals at the document's ten digits (trailing zeros dropped), settled
    waitUntil(10, () -> panel().showsEngineValues());
    List<String> decimals = get(() -> panel().valueTexts());
    double[] expected = {Math.sin(a), Math.cos(a), Math.tan(a)};
    for (int i = 0; i < 3; i++) {
      assertThat(decimals.get(i).replaceFirst("^0[.]", "").replace(".", ""))
          .hasSizeLessThanOrEqualTo(10);
      assertThat(Double.parseDouble(decimals.get(i))).isCloseTo(expected[i], within());
    }
    assertThat(shown()).isEqualTo("θ ≈ 0.5498 rad ≈ 31.5°");
    assertThat(get(() -> panel().sendButton().isDisable())).isTrue();

    // the keys: to the next special angle, then a degree
    fx(() -> stage.getScene().lookup("#unit-circle-canvas").requestFocus());
    press(KeyCode.UP);
    waitForValues("√2/2", "√2/2", "1");
    press(KeyCode.UP);
    press(KeyCode.UP);
    press(KeyCode.UP);
    waitForValues("1", "0", "undefined");
    press(KeyCode.DOWN, false, true, false);
    assertThat(Math.toDegrees(get(() -> panel().angle()))).isCloseTo(89, within());
    waitUntil(10, () -> panel().showsEngineValues());
    waitForValues("0.9998476952", "0.01745240644", "57.28996163");
  }

  @Test
  void anAngleIsTypedAndTheModeIsFollowed() throws Exception {
    openView();
    fx(() -> stage.getScene().lookup("#unit-circle-angle").requestFocus());
    type("π/4");
    press(KeyCode.ENTER);
    waitForValues("√2/2", "√2/2", "1");
    assertThat(shown()).isEqualTo("θ = π/4 = 45°");

    // in degrees the display leads with degrees, and a plain number typed is in degrees
    angleMode(AngleMode.DEGREES);
    waitUntil(10, () -> shown().equals("θ = 45° = π/4"));
    assertThat(
            get(() -> ((TextField) stage.getScene().lookup("#unit-circle-angle")).getPromptText()))
        .startsWith("Degrees");
    fx(() -> ((TextField) stage.getScene().lookup("#unit-circle-angle")).clear());
    type("120");
    press(KeyCode.ENTER);
    waitForValues("√3/2", "-1/2", "-√3");
    assertThat(shown()).isEqualTo("θ = 120° = 2π/3");
    angleMode(AngleMode.GRADIANS);
    waitUntil(10, () -> shown().equals("θ = 400/3 grad = 120° = 2π/3"));

    // a text that is no real number is refused beside the field
    fx(() -> ((TextField) stage.getScene().lookup("#unit-circle-angle")).clear());
    type("x");
    press(KeyCode.ENTER);
    waitUntil(10, () -> stage.getScene().lookup("#unit-circle-refused").isVisible());
    assertThat(get(() -> ((Label) stage.getScene().lookup("#unit-circle-refused")).getText()))
        .isEqualTo("type a real number");
    assertThat(shown()).isEqualTo("θ = 400/3 grad = 120° = 2π/3");

    // the refusal goes when the angle moves another way
    fx(() -> stage.getScene().lookup("#unit-circle-canvas").requestFocus());
    press(KeyCode.UP);
    assertThat(get(() -> stage.getScene().lookup("#unit-circle-refused").isVisible())).isFalse();

    // a decimal near a special angle is not it
    angleMode(AngleMode.RADIANS);
    fx(() -> stage.getScene().lookup("#unit-circle-angle").requestFocus());
    fx(() -> ((TextField) stage.getScene().lookup("#unit-circle-angle")).clear());
    type("0.52359877559");
    press(KeyCode.ENTER);
    waitUntil(10, () -> panel().showsEngineValues() && panel().special().isEmpty());
    assertThat(get(() -> panel().valueTexts()).getFirst()).isEqualTo("0.5");
    assertThat(shown()).startsWith("θ ≈ 0.5236 rad");
  }

  @Test
  void sineIsSentToTheCellAndEvaluates() throws Exception {
    openView();
    angleMode(AngleMode.DEGREES);
    fx(() -> window.cellViews().getFirst().field().requestFocus());
    type("2*");
    dragTo(45, false);
    waitForValues("√2/2", "√2/2", "1");
    fx(
        () -> {
          for (MenuItem item : panel().sendButton().getItems()) {
            if (item.getId().equals("unit-circle-send-sin")) {
              item.fire();
            }
          }
        });
    assertThat(input(0)).isEqualTo("2*sin(45)");
    assertThat(get(() -> stage.getScene().getFocusOwner()))
        .isSameAs(get(() -> window.cellViews().getFirst().field()));
    press(KeyCode.ENTER);
    waitForOutput(0, "√2");

    // one undo removes the call
    press(KeyCode.Z, true, false, false);
    press(KeyCode.Z, true, false, false);
    assertThat(input(0)).isEqualTo("2*");
  }

  @Test
  void bothThemes() throws Exception {
    // with the keypad hidden the picture takes the room it has
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-keypad")).fire());
    openView();
    dragTo(120, false);
    waitForValues("√3/2", "-1/2", "-√3");
    fx(() -> window.themeProperty().set(Theme.DARK));
    waitUntil(10, () -> stage.getScene().getRoot().getStyleClass().contains("dark"));
    screenshot("unit-circle-dark.png");
    fx(() -> window.themeProperty().set(Theme.LIGHT));
    dragTo(300, false);
    waitForValues("-√3/2", "1/2", "-√3");
    screenshot("unit-circle-light.png");
  }

  private static org.assertj.core.data.Offset<Double> within() {
    return org.assertj.core.data.Offset.offset(1e-9);
  }
}
