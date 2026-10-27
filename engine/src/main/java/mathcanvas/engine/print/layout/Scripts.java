package mathcanvas.engine.print.layout;

import java.util.Objects;
import mathcanvas.engine.rule.Path;

/**
 * A base with a subscript, a superscript or both, each with the form the linear text gives it.
 *
 * @param base the base
 * @param sub the subscript, or null
 * @param sup the superscript, or null
 * @param subForm the subscript's linear form, null without one
 * @param supForm the superscript's linear form, null without one
 * @param path the path, or null
 */
public record Scripts(Layout base, Layout sub, Layout sup, Form subForm, Form supForm, Path path)
    implements Layout {

  /** How a script is written in the linear text. */
  public enum Form {
    /** After {@code ^}: {@code e^x}. */
    CARET,
    /** After {@code ^} in parentheses: {@code e^(x²)}. */
    CARET_WRAPPED,
    /** In Unicode superscript or subscript characters: {@code x²}, {@code x₁}. */
    CHARACTERS,
    /** Written after the base as it is: the side of a limit point, {@code 0+}. */
    APPENDED,
    /** After {@code _}: a name {@code x_ab}. */
    UNDERSCORE
  }

  /** Checks that each script has a form, and that there is a script. */
  public Scripts {
    Objects.requireNonNull(base);
    if ((sub == null) != (subForm == null) || (sup == null) != (supForm == null)) {
      throw new IllegalArgumentException("a script and its form go together");
    }
    if (sub == null && sup == null) {
      throw new IllegalArgumentException("no script");
    }
  }
}
