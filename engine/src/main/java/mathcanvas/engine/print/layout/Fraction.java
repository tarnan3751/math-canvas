package mathcanvas.engine.print.layout;

import java.util.Objects;
import mathcanvas.engine.rule.Path;

/**
 * A quotient, its numerator over its denominator. The flags record the linear form, which the bar
 * makes unnecessary: whether the text parenthesises the numerator or the denominator ({@code (x +
 * 1)/2}, {@code x/(2π)}) and whether it spaces the slash ({@code d / dx}).
 *
 * @param num the numerator
 * @param den the denominator
 * @param numWrapped whether the text parenthesises the numerator
 * @param denWrapped whether the text parenthesises the denominator
 * @param spaced whether the text writes {@code " / "}
 * @param path the path, or null
 */
public record Fraction(
    Layout num, Layout den, boolean numWrapped, boolean denWrapped, boolean spaced, Path path)
    implements Layout {

  /** Checks the parts. */
  public Fraction {
    Objects.requireNonNull(num);
    Objects.requireNonNull(den);
  }
}
