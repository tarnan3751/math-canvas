package mathcanvas.app.text;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/** The app's user-facing texts, from the bundle {@code mathcanvas.app.messages}. */
public final class Messages {

  private static final ResourceBundle BUNDLE =
      ResourceBundle.getBundle("mathcanvas.app.messages", Locale.ROOT);

  private Messages() {}

  /**
   * A text, with its arguments put in.
   *
   * @param key the key
   * @param args the arguments, through {@link MessageFormat}
   * @return the text
   */
  public static String text(String key, Object... args) {
    return new MessageFormat(BUNDLE.getString(key), Locale.ROOT).format(args);
  }
}
