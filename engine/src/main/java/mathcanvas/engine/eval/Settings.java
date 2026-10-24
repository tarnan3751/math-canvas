package mathcanvas.engine.eval;

import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * The session settings evaluation reads: the precision of inexact arithmetic, in significant
 * digits, and the angle mode ({@code docs/language.md} §14.1, §14.2).
 *
 * @param precision the digits inexact results are rounded to, from {@link #MIN_PRECISION} to {@link
 *     #MAX_PRECISION}
 * @param angleMode the angle mode
 */
public record Settings(int precision, AngleMode angleMode) {

  /** The fewest digits a precision may have. */
  public static final int MIN_PRECISION = 1;

  /** The most digits a precision may have, for the session and for {@code numeric}. */
  public static final int MAX_PRECISION = 10_000;

  /** 34 digits, radians. */
  public static final Settings DEFAULT = new Settings(34, AngleMode.RADIANS);

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if the angle mode is null
   * @throws IllegalArgumentException if the precision is out of range
   */
  public Settings {
    checkPrecision(precision);
    Objects.requireNonNull(angleMode, "angleMode");
  }

  /**
   * Whether a number of digits is an allowed precision.
   *
   * @param digits the digits
   * @return true if it is from {@link #MIN_PRECISION} to {@link #MAX_PRECISION}
   */
  public static boolean isPrecision(long digits) {
    return digits >= MIN_PRECISION && digits <= MAX_PRECISION;
  }

  private static void checkPrecision(int digits) {
    if (!isPrecision(digits)) {
      throw new IllegalArgumentException(
          "the precision must be an integer from "
              + MIN_PRECISION
              + " to "
              + MAX_PRECISION
              + ": "
              + digits);
    }
  }

  /**
   * The context inexact arithmetic is rounded to: {@link #precision} digits, half-even.
   *
   * @return the context
   */
  public MathContext mathContext() {
    return new MathContext(precision, RoundingMode.HALF_EVEN);
  }

  /**
   * Returns these settings with another precision.
   *
   * @param digits the precision
   * @return the settings
   * @throws IllegalArgumentException if the precision is out of range
   */
  public Settings withPrecision(int digits) {
    return new Settings(digits, angleMode);
  }

  /**
   * Returns these settings with another angle mode.
   *
   * @param mode the angle mode
   * @return the settings
   */
  public Settings withAngleMode(AngleMode mode) {
    return new Settings(precision, mode);
  }
}
