package mathcanvas.engine.print.layout;

import java.util.Objects;
import mathcanvas.engine.rule.Path;

/**
 * One character: a one-letter name, a constant, an operator sign. The code point is the one the
 * text has ({@code -}, {@code ·}); a typesetter chooses the glyph (U+2212, the italic letter).
 *
 * @param codePoint the character
 * @param style upright or italic
 * @param cls the spacing class
 * @param path the path, or null
 */
public record Glyph(int codePoint, Style style, Cls cls, Path path) implements Layout {

  /** Checks the style and the class. */
  public Glyph {
    Objects.requireNonNull(style);
    Objects.requireNonNull(cls);
  }
}
