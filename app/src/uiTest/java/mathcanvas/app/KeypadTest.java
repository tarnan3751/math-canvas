package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.control.Button;
import javafx.scene.control.IndexRange;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

/** The keypad inserts the language's text at the caret of the current cell. */
class KeypadTest extends WorksheetHarness {

  private void key(String id) throws Exception {
    fx(() -> ((Button) stage.getScene().lookup("#key-" + id)).fire());
  }

  @Test
  void keysInsertAtTheCaretAndTemplatesSelectTheirPlaceholder() throws Exception {
    key("2");
    key("pi");
    key("plus");
    key("sqrt");
    key("8");
    assertThat(input(0)).isEqualTo("2pi+sqrt(8)");
    // a call leaves the caret between its parentheses
    assertThat(get(() -> window.cellViews().getFirst().field().getCaretPosition())).isEqualTo(10);
    screenshot("worksheet-keypad.png");
    press(KeyCode.END);
    press(KeyCode.ENTER);
    waitForOutput(0, "2√2 + 2π");

    key("sum");
    assertThat(input(1)).isEqualTo("sum(f, k, 1, n)");
    IndexRange selected = get(() -> window.cellViews().get(1).field().getSelection());
    assertThat(selected.getStart()).isEqualTo(4);
    assertThat(selected.getEnd()).isEqualTo(5);
    type("k^2");
    assertThat(input(1)).isEqualTo("sum(k^2, k, 1, n)");
  }

  @Test
  void theShiftTogglesGiveTheInverseAndHyperbolicFunctions() throws Exception {
    fx(() -> ((ToggleButton) stage.getScene().lookup("#key-inv")).fire());
    assertThat(get(() -> ((Button) stage.getScene().lookup("#key-sin")).getText()))
        .isEqualTo("asin");
    fx(() -> ((ToggleButton) stage.getScene().lookup("#key-hyp")).fire());
    key("sin");
    assertThat(input(0)).isEqualTo("asinh()");
  }

  @Test
  void theKeypadCanBeHidden() throws Exception {
    assertThat(get(() -> stage.getScene().lookup("#keypad").isVisible())).isTrue();
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-keypad")).fire());
    assertThat(get(() -> stage.getScene().lookup("#keypad").isVisible())).isFalse();
  }

  @Test
  void tabMovesToTheKeypadAndAcceptsItsFocusedKey() throws Exception {
    fx(() -> window.cellViews().getFirst().field().requestFocus());
    press(KeyCode.TAB);
    assertThat(get(() -> stage.getScene().getFocusOwner().getId())).isEqualTo("key-inv");
    fx(() -> stage.getScene().lookup("#key-pi").requestFocus());
    fx(() -> window.cellViews().getFirst().field().requestFocus());
    press(KeyCode.TAB);
    assertThat(input(0)).isEqualTo("pi");
  }
}
