package mathcanvas.app.text;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

/**
 * The app's user-facing texts, from the bundle {@code mathcanvas.app.messages}: the root bundle,
 * English, or the bundle of the language the person chose ({@code messages_fr}), whose missing keys
 * fall back to the root's. The language is set once, when the app starts, before any text is read
 * for the window. Arguments are formatted in the root locale whatever the language, so numbers read
 * as the engine prints them.
 */
public final class Messages {

  private static final String NAME = "mathcanvas.app.messages";

  private static volatile ResourceBundle bundle = ResourceBundle.getBundle(NAME, Locale.ROOT);

  private Messages() {}

  /**
   * Reads the texts of a locale from now on: the app's start calls it with the language setting's
   * locale ({@code Language.locale()}, the root locale for English).
   *
   * @param locale the locale
   */
  public static void setLocale(Locale locale) {
    bundle = ResourceBundle.getBundle(NAME, Objects.requireNonNull(locale, "locale"));
  }

  /**
   * A text, with its arguments put in.
   *
   * @param key the key
   * @param args the arguments, through {@link MessageFormat}
   * @return the text
   */
  public static String text(String key, Object... args) {
    return new MessageFormat(bundle.getString(key), Locale.ROOT).format(args);
  }
}
