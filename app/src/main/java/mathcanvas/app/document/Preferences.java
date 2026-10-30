package mathcanvas.app.document;

import java.util.Objects;

/**
 * The app's preferences: what belongs to the person, not to the document. They live outside {@link
 * DocumentState} and the undo history, so an undo never changes them, and are saved beside the
 * document settings in {@code settings.json}.
 *
 * @param theme the colour theme
 * @param showSteps whether every evaluation is traced, so each value shows its steps
 * @param keypadShown whether the keypad is shown
 */
public record Preferences(Theme theme, boolean showSteps, boolean keypadShown) {

  /** The system theme, no steps, the keypad shown. */
  public static final Preferences DEFAULT = new Preferences(Theme.SYSTEM, false, true);

  /**
   * Checks the field.
   *
   * @throws NullPointerException if the theme is null
   */
  public Preferences {
    Objects.requireNonNull(theme, "theme");
  }
}
