package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import mathcanvas.app.document.DocumentSettings;
import mathcanvas.app.document.Language;
import mathcanvas.app.document.Preferences;
import mathcanvas.app.json.JsonIo;
import mathcanvas.app.persist.SettingsFile;
import mathcanvas.app.text.Messages;
import org.junit.jupiter.api.Test;

/**
 * The language setting: a start with French chosen shows the French toolbar, and the root bundle's
 * texts for every key the French scaffold does not translate.
 */
class LocalizationTest extends WorksheetHarness {

  @Test
  void frenchShowsTheTranslatedToolbarAndFallsBackElsewhere() throws Exception {
    Path french = newDirectory();
    Files.createDirectories(french);
    Preferences prefs = Preferences.DEFAULT;
    Files.writeString(
        french.resolve(SettingsFile.NAME),
        JsonIo.text(
            SettingsFile.encode(
                new SettingsFile.Stored(
                    DocumentSettings.DEFAULT,
                    new Preferences(
                        prefs.theme(),
                        prefs.showSteps(),
                        prefs.keypadShown(),
                        prefs.canvasShown(),
                        prefs.exportFolder(),
                        prefs.checkForUpdates(),
                        Language.FRENCH,
                        true)))));
    Main other = new Main();
    Stage[] second = new Stage[1];
    try {
      fx(
          () -> {
            second[0] = new Stage();
            other.start(second[0], french);
          });
      assertThat(get(() -> ((Button) second[0].getScene().lookup("#undo")).getText()))
          .isEqualTo("Annuler");
      assertThat(get(() -> ((Button) second[0].getScene().lookup("#help")).getText()))
          .isEqualTo("Aide");
      // the scaffold translates the rail, the app bar and the menus; the rest falls back to English
      assertThat(get(() -> ((TextArea) second[0].getScene().lookup(".cell-input")).getPromptText()))
          .isEqualTo(Messages.text("cell.prompt"));
      screenshot(get(() -> second[0].getScene()), "localization-fr.png");
    } finally {
      fx(
          () -> {
            other.stop();
            second[0].close();
            Messages.setLocale(Locale.ROOT);
          });
    }
  }
}
