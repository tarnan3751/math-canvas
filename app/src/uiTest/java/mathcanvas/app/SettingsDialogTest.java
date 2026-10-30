package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Spinner;
import javafx.scene.input.KeyCode;
import mathcanvas.app.document.NumberFormat;
import mathcanvas.app.document.Theme;
import mathcanvas.app.ui.SettingsDialog;
import mathcanvas.engine.eval.AngleMode;
import org.junit.jupiter.api.Test;

/** The settings dialog round trip: open, change, OK, see the change, undo it, reset. */
class SettingsDialogTest extends WorksheetHarness {

  private DialogPane pane() {
    return window.settingsDialog().getDialogPane();
  }

  @SuppressWarnings("unchecked")
  private <T> ChoiceBox<T> choice(String id) {
    return (ChoiceBox<T>) pane().lookup("#" + id);
  }

  @Test
  void aNumberFormatShowsResultsAsDecimals() throws Exception {
    enter("1/2");
    waitForOutput(0, "1/2");
    click("settings");
    waitUntil(10, () -> window.settingsDialog() != null && window.settingsDialog().isShowing());
    fx(() -> this.<NumberFormat>choice("settings-format").setValue(NumberFormat.DECIMAL));
    assertThat(get(() -> pane().lookup("#settings-digits").isDisabled())).isFalse();
    fx(() -> this.<AngleMode>choice("settings-angle").setValue(AngleMode.DEGREES));
    fx(() -> ((CheckBox) pane().lookup("#settings-keypad")).setSelected(false));
    screenshot(window.settingsDialog().getDialogPane().getScene(), "worksheet-settings.png");
    fx(() -> ((Button) pane().lookupButton(ButtonType.OK)).fire());

    waitForOutput(0, "0.5");
    assertThat(get(() -> stage.getScene().lookup("#keypad").isVisible())).isFalse();
    enter("sin(30)");
    waitForOutput(1, "0.5");
    waitUntil(
        10,
        () ->
            Files.exists(directory.resolve("settings.json"))
                && Files.readString(directory.resolve("settings.json")).contains("decimal"));

    // the document settings are undone as one step; the preferences stay
    fx(() -> window.cellViews().get(1).field().requestFocus());
    click("undo");
    click("undo");
    click("undo");
    waitForOutput(0, "1/2");
    assertThat(get(() -> stage.getScene().lookup("#keypad").isVisible())).isFalse();
  }

  @Test
  void resetPutsTheDefaultsBackAndCancelKeepsEverything() throws Exception {
    click("settings");
    waitUntil(10, () -> window.settingsDialog() != null && window.settingsDialog().isShowing());
    fx(() -> this.<Theme>choice("settings-theme").setValue(Theme.DARK));
    fx(
        () -> {
          @SuppressWarnings("unchecked")
          Spinner<Integer> p = (Spinner<Integer>) pane().lookup("#settings-precision");
          p.getValueFactory().setValue(50);
        });
    fx(() -> ((Button) pane().lookupButton(SettingsDialog.RESET)).fire());
    assertThat(get(() -> window.settingsDialog().chosenSettings().precision())).isEqualTo(34);
    assertThat(get(() -> window.settingsDialog().chosenPreferences().theme()))
        .isEqualTo(Theme.SYSTEM);
    assertThat(get(() -> window.settingsDialog().isShowing())).isTrue();
    fx(() -> ((Button) pane().lookupButton(ButtonType.CANCEL)).fire());
    assertThat(get(() -> window.themeProperty().get())).isEqualTo(Theme.SYSTEM);
    press(KeyCode.ESCAPE);
  }
}
