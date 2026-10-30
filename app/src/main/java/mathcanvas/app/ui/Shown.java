package mathcanvas.app.ui;

import mathcanvas.app.text.Messages;

/**
 * The text a cell shows for a result or an error, cut at {@link #MAX_SHOWN} code points so that a
 * huge value (an expansion with thousands of terms) does not flood the window, as the CLI cuts its
 * messages. The full text stays in the cell's result.
 */
public final class Shown {

  /** The most code points shown for one result or error. */
  public static final int MAX_SHOWN = 10_000;

  private Shown() {}

  /**
   * The text as shown: whole when it has at most {@link #MAX_SHOWN} code points, else its first
   * {@link #MAX_SHOWN} followed by a note of how many more there are.
   *
   * @param text the full text
   * @return the text to show
   */
  public static String capped(String text) {
    int length = text.codePointCount(0, text.length());
    if (length <= MAX_SHOWN) {
      return text;
    }
    return text.substring(0, text.offsetByCodePoints(0, MAX_SHOWN))
        + " "
        + Messages.text("output.more", length - MAX_SHOWN);
  }
}
