package mathcanvas.app.document;

import java.util.Locale;

/** The language of the app's texts: the system's, or one chosen from the bundles there are. */
public enum Language {
  /** The platform's default locale's, English when there is no bundle for it. */
  SYSTEM,
  /** English, the root bundle. */
  ENGLISH,
  /** French. */
  FRENCH;

  /**
   * The locale whose bundle the app's texts are read from. English is the root locale, so that a
   * French system's default never stands in for it.
   *
   * @return the locale
   */
  public Locale locale() {
    return switch (this) {
      case SYSTEM -> Locale.getDefault();
      case ENGLISH -> Locale.ROOT;
      case FRENCH -> Locale.FRENCH;
    };
  }
}
