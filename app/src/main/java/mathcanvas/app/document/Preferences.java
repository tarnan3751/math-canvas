package mathcanvas.app.document;

import java.util.Objects;

/**
 * The app's preferences: what belongs to the person, not to the document. They live outside {@link
 * DocumentState} and the undo history, so an undo never changes them, and are saved beside the
 * document settings in {@code settings.json}.
 *
 * @param theme the colour theme
 */
public record Preferences(Theme theme) {

  /** The system theme. */
  public static final Preferences DEFAULT = new Preferences(Theme.SYSTEM);

  /**
   * Checks the field.
   *
   * @throws NullPointerException if the theme is null
   */
  public Preferences {
    Objects.requireNonNull(theme, "theme");
  }
}
