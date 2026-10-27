package mathcanvas.engine.print.layout;

import java.util.Objects;
import mathcanvas.engine.rule.Path;

/**
 * A run of characters set as one atom: a number, a function name, a word, a name of several
 * letters.
 *
 * @param text the characters
 * @param style upright or italic
 * @param cls the spacing class
 * @param path the path, or null
 */
public record Text(String text, Style style, Cls cls, Path path) implements Layout {

  /** Checks the fields. */
  public Text {
    Objects.requireNonNull(text);
    Objects.requireNonNull(style);
    Objects.requireNonNull(cls);
  }
}
