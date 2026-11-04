package mathcanvas.app.ui;

import java.util.Optional;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import javafx.util.StringConverter;
import mathcanvas.app.document.DocumentSettings;
import mathcanvas.app.document.Language;
import mathcanvas.app.document.NumberFormat;
import mathcanvas.app.document.Preferences;
import mathcanvas.app.document.Theme;
import mathcanvas.app.text.Messages;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Settings;

/**
 * The settings dialog: the document settings (precision, angle mode, number format and its digits),
 * which apply through the document's command so an undo reverts them, and the preferences (theme,
 * steps, keypad). Reset puts the defaults in the controls. It is shown without blocking; OK
 * applies, Cancel discards. Touched only on the FX thread.
 */
public final class SettingsDialog extends Dialog<ButtonType> {

  /** The reset button's type. */
  public static final ButtonType RESET =
      new ButtonType(Messages.text("settings.reset"), ButtonBar.ButtonData.LEFT);

  private final Spinner<Integer> precision =
      new Spinner<>(Settings.MIN_PRECISION, Settings.MAX_PRECISION, 34);
  private final ChoiceBox<AngleMode> angle = new ChoiceBox<>();
  private final ChoiceBox<NumberFormat> format = new ChoiceBox<>();
  private final Spinner<Integer> digits = new Spinner<>(1, DocumentSettings.MAX_DIGITS, 10);
  private final ChoiceBox<Theme> theme = new ChoiceBox<>();
  private final CheckBox showSteps = new CheckBox(Messages.text("settings.showSteps"));
  private final CheckBox keypadShown = new CheckBox(Messages.text("settings.keypadShown"));
  // not a control of the dialog: kept as the dialog found it
  private final boolean canvasShown;
  private final Optional<String> exportFolder;
  private final boolean checkForUpdates;
  private final Language language;

  /**
   * A dialog showing the current settings.
   *
   * @param owner the window it belongs to
   * @param settings the document settings now
   * @param preferences the preferences now
   * @param apply told of the settings and preferences chosen, when OK closes the dialog
   */
  public SettingsDialog(
      Window owner, DocumentSettings settings, Preferences preferences, Applier apply) {
    this.canvasShown = preferences.canvasShown();
    this.exportFolder = preferences.exportFolder();
    this.checkForUpdates = preferences.checkForUpdates();
    this.language = preferences.language();
    initOwner(owner);
    setTitle(Messages.text("settings.title"));
    getDialogPane().getButtonTypes().addAll(RESET, ButtonType.OK, ButtonType.CANCEL);
    getDialogPane().setId("settings-dialog");
    if (owner != null && owner.getScene() != null) {
      getDialogPane().getStylesheets().addAll(owner.getScene().getStylesheets());
      getDialogPane().getStyleClass().addAll(owner.getScene().getRoot().getStyleClass());
    }

    precision.setEditable(true);
    precision.setId("settings-precision");
    angle.getItems().setAll(AngleMode.values());
    angle.setConverter(labels("angle."));
    angle.setId("settings-angle");
    format.getItems().setAll(NumberFormat.values());
    format.setConverter(labels("format."));
    format.setId("settings-format");
    digits.setEditable(true);
    digits.setId("settings-digits");
    digits
        .disableProperty()
        .bind(
            format
                .valueProperty()
                .map(f -> f != NumberFormat.DECIMAL && f != NumberFormat.SCIENTIFIC)
                .orElse(true));
    theme.getItems().setAll(Theme.values());
    theme.setConverter(labels("theme."));
    theme.setId("settings-theme");
    showSteps.setId("settings-steps");
    keypadShown.setId("settings-keypad");
    show(settings, preferences);

    GridPane grid = new GridPane();
    grid.getStyleClass().add("settings-grid");
    grid.setHgap(12);
    grid.setVgap(8);
    int row = 0;
    grid.addRow(row++, new Label(Messages.text("settings.precision")), precision);
    grid.addRow(row++, new Label(Messages.text("settings.angle")), angle);
    grid.addRow(row++, new Label(Messages.text("settings.format")), format);
    grid.addRow(row++, new Label(Messages.text("settings.digits")), digits);
    grid.addRow(row++, new Label(Messages.text("settings.theme")), theme);
    grid.add(showSteps, 1, row++);
    grid.add(keypadShown, 1, row);
    getDialogPane().setContent(grid);

    // Reset fills in the defaults and keeps the dialog open
    getDialogPane()
        .lookupButton(RESET)
        .addEventFilter(
            ActionEvent.ACTION,
            e -> {
              show(DocumentSettings.DEFAULT, Preferences.DEFAULT);
              e.consume();
            });
    setOnHidden(
        e -> {
          if (getResult() == ButtonType.OK) {
            commit(precision);
            commit(digits);
            apply.apply(chosenSettings(), chosenPreferences());
          }
        });
  }

  /** Receives what the dialog's OK chose. */
  @FunctionalInterface
  public interface Applier {

    /**
     * Applies the choice.
     *
     * @param settings the document settings
     * @param preferences the preferences
     */
    void apply(DocumentSettings settings, Preferences preferences);
  }

  /** Takes a typed spinner value, or keeps the last good one when the text is not a number. */
  private static void commit(Spinner<Integer> spinner) {
    try {
      spinner.commitValue();
    } catch (NumberFormatException e) {
      spinner.cancelEdit();
    }
  }

  private void show(DocumentSettings settings, Preferences preferences) {
    precision.getValueFactory().setValue(settings.precision());
    angle.setValue(settings.angleMode());
    format.setValue(settings.numberFormat());
    digits.getValueFactory().setValue(settings.numberDigits());
    theme.setValue(preferences.theme());
    showSteps.setSelected(preferences.showSteps());
    keypadShown.setSelected(preferences.keypadShown());
  }

  /**
   * The document settings the controls hold.
   *
   * @return the settings
   */
  public DocumentSettings chosenSettings() {
    return new DocumentSettings(
        precision.getValue(), angle.getValue(), format.getValue(), digits.getValue());
  }

  /**
   * The preferences the controls hold.
   *
   * @return the preferences
   */
  public Preferences chosenPreferences() {
    return new Preferences(
        theme.getValue(),
        showSteps.isSelected(),
        keypadShown.isSelected(),
        canvasShown,
        exportFolder,
        checkForUpdates,
        language);
  }

  private static <E extends Enum<E>> StringConverter<E> labels(String prefix) {
    return new StringConverter<>() {
      @Override
      public String toString(E e) {
        return e == null ? "" : Messages.text(prefix + e.name());
      }

      @Override
      public E fromString(String s) {
        throw new UnsupportedOperationException("a choice is never typed");
      }
    };
  }
}
