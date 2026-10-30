package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

/** The {@code f(x) = body} shortcut: offered with a hint, Enter defines, Shift+Enter does not. */
class DefinitionShortcutUiTest extends WorksheetHarness {

  private String hint(int cell) throws Exception {
    return get(() -> window.cellViews().get(cell).hintText());
  }

  @Test
  void enterDefinesAFreshFunction() throws Exception {
    type("f(x) = x^2 + 1");
    assertThat(hint(0)).startsWith("Define f?");
    screenshot("worksheet-define-hint.png");

    press(KeyCode.ENTER);
    waitForOutput(0, "f(x) := x² + 1");
    assertThat(input(0)).isEqualTo("f(x) := x^2 + 1");
    assertThat(hint(0)).isEmpty();
    enter("f(3)");
    waitForOutput(1, "10");

    // f is now defined: no offer, the equation is evaluated as written
    type("f(x) = 2");
    assertThat(hint(2)).isEmpty();
  }

  @Test
  void shiftEnterEvaluatesTheEquationAsWritten() throws Exception {
    type("g(t) = 2");
    assertThat(hint(0)).startsWith("Define g?");
    press(KeyCode.ENTER, false, true, false);
    waitUntil(
        30,
        () ->
            !window.cellViews().get(0).outputTexts().isEmpty()
                && !window.cellViews().get(0).outputTexts().getFirst().equals("…"));
    assertThat(input(0)).isEqualTo("g(t) = 2");
    assertThat(outputs(0).getFirst()).doesNotContain(":=");
  }
}
