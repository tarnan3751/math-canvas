package mathcanvas.app.document;

import java.util.Objects;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Settings;

/**
 * The settings that belong to the document and change through its commands, so an undo reverts
 * them: the precision and the angle mode evaluation uses, and the number format results are shown
 * in with its digits.
 *
 * @param precision the digits of inexact arithmetic, from {@link Settings#MIN_PRECISION} to {@link
 *     Settings#MAX_PRECISION}
 * @param angleMode the angle mode
 * @param numberFormat how numeric results are shown
 * @param numberDigits the significant digits of the decimal and scientific formats, from 1 to
 *     {@link #MAX_DIGITS}
 */
public record DocumentSettings(
    int precision, AngleMode angleMode, NumberFormat numberFormat, int numberDigits) {

  /** The most digits of the decimal and scientific formats. */
  public static final int MAX_DIGITS = 10_000;

  /** The digits of the decimal and scientific formats unless set. */
  public static final int DEFAULT_DIGITS = 10;

  /** The engine's defaults (34 digits, radians), exact results, 10 digits when decimal. */
  public static final DocumentSettings DEFAULT =
      new DocumentSettings(
          Settings.DEFAULT.precision(),
          Settings.DEFAULT.angleMode(),
          NumberFormat.EXACT,
          DEFAULT_DIGITS);

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if the angle mode or the number format is null
   * @throws IllegalArgumentException if the precision or the digits are out of range
   */
  public DocumentSettings {
    if (!Settings.isPrecision(precision)) {
      throw new IllegalArgumentException("precision out of range: " + precision);
    }
    if (numberDigits < 1 || numberDigits > MAX_DIGITS) {
      throw new IllegalArgumentException("digits out of range: " + numberDigits);
    }
    Objects.requireNonNull(angleMode, "angleMode");
    Objects.requireNonNull(numberFormat, "numberFormat");
  }

  /**
   * The engine settings evaluation runs under.
   *
   * @return the precision and the angle mode
   */
  public Settings engine() {
    return new Settings(precision, angleMode);
  }
}
