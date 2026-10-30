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
 */
public record Preferences(
    Theme theme,
    boolean showSteps,
    boolean keypadShown,
    boolean canvasShown,
    Optional<String> exportFolder) {

  /** The system theme, no steps, the keypad shown, the canvas hidden, no export folder yet. */
  public static final Preferences DEFAULT =
      new Preferences(Theme.SYSTEM, false, true, false, Optional.empty());

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if the theme or the export folder is null
   */
  public Preferences {
    Objects.requireNonNull(theme, "theme");
    Objects.requireNonNull(exportFolder, "exportFolder");
  }
}
