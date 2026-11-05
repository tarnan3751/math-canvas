package mathcanvas.app.document;

import java.util.Objects;
import java.util.Optional;

/**
 * The app's preferences: what belongs to the person, not to the document. They live outside {@link
 * DocumentState} and the undo history, so an undo never changes them, and are saved beside the
 * document settings in {@code settings.json}.
 *
 * @param theme the colour theme
 * @param showSteps whether every evaluation is traced, so each value shows its steps
 * @param keypadShown whether the keypad is shown
 * @param canvasShown whether the canvas is shown beside the worksheet
 * @param exportFolder the folder the canvas was last exported to, where the next export's file
 *     chooser opens; empty before the first export
 * @param checkForUpdates whether the app asks GitHub for a newer release when it starts
 * @param language the language of the app's texts, read when the app starts
 * @param welcomeShown whether the welcome worksheet was opened on a first launch, so it is not
 *     opened again
 */
public record Preferences(
    Theme theme,
    boolean showSteps,
    boolean keypadShown,
    boolean canvasShown,
    Optional<String> exportFolder,
    boolean checkForUpdates,
    Language language,
    boolean welcomeShown) {

  /**
   * The system theme, no steps, the keypad shown, the canvas hidden, no export folder yet, no
   * update check, the system's language, the welcome not yet shown.
   */
  public static final Preferences DEFAULT =
      new Preferences(Theme.SYSTEM, false, true, false, Optional.empty(), false, Language.SYSTEM);

  /**
   * Preferences of a person who has not been shown the welcome worksheet.
   *
   * @param theme the colour theme
   * @param showSteps whether every evaluation is traced
   * @param keypadShown whether the keypad is shown
   * @param canvasShown whether the canvas is shown beside the worksheet
   * @param exportFolder the folder the canvas was last exported to
   * @param checkForUpdates whether the app asks GitHub for a newer release when it starts
   * @param language the language of the app's texts
   */
  public Preferences(
      Theme theme,
      boolean showSteps,
      boolean keypadShown,
      boolean canvasShown,
      Optional<String> exportFolder,
      boolean checkForUpdates,
      Language language) {
    this(
        theme, showSteps, keypadShown, canvasShown, exportFolder, checkForUpdates, language, false);
  }

  /**
   * These preferences with the welcome worksheet shown, and the canvas beside it.
   *
   * @return the preferences
   */
  public Preferences withWelcomeShown() {
    return new Preferences(
        theme, showSteps, keypadShown, true, exportFolder, checkForUpdates, language, true);
  }

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if the theme, the export folder or the language is null
   */
  public Preferences {
    Objects.requireNonNull(theme, "theme");
    Objects.requireNonNull(exportFolder, "exportFolder");
    Objects.requireNonNull(language, "language");
  }
}
