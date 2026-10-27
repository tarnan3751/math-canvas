package mathcanvas.engine.print;

import java.util.Objects;

/**
 * How the printers write numbers: a display option, never a change to the value printed. {@link
 * #EXACT} is the printers' default; the others are the worksheet's number formats.
 *
 * <ul>
 *   <li>{@link Kind#EXACT}: numbers as they are, rationals as quotients.
 *   <li>{@link Kind#DECIMAL}: rationals and decimals as decimals with {@code digits} significant
 *       digits; a subexpression made only of numbers, such as {@code 3√2/4}, as one decimal,
 *       through the engine's numeric form; integers, integer exponents and root notation as they
 *       are.
 *   <li>{@link Kind#SCIENTIFIC}: as {@link Kind#DECIMAL}, in scientific notation ({@code
 *       1.235e+4}); an integer only when it has more than {@code digits} digits.
 *   <li>{@link Kind#FRACTION}: a rational printed as a number on its own as a mixed number ({@code
 *       3 1/2}); exponents and coefficients written over the line as they are.
 * </ul>
 *
 * @param kind the kind
 * @param digits the significant digits of {@link Kind#DECIMAL} and {@link Kind#SCIENTIFIC}, 0 for
 *     the others
 */
public record Format(Kind kind, int digits) {

  /** The kinds of number format. */
  public enum Kind {
    /** Numbers as they are. */
    EXACT,
    /** Decimals with a number of significant digits. */
    DECIMAL,
    /** Scientific notation with a number of significant digits. */
    SCIENTIFIC,
    /** Mixed numbers for improper rationals. */
    FRACTION
  }

  /** The printers' default: numbers as they are. */
  public static final Format EXACT = new Format(Kind.EXACT, 0);

  /** Mixed numbers. */
  public static final Format FRACTION = new Format(Kind.FRACTION, 0);

  /** The most significant digits, the session precision's limit. */
  public static final int MAX_DIGITS = 10_000;

  /** Checks the digits: 1 to {@link #MAX_DIGITS} for the decimal kinds, 0 for the others. */
  public Format {
    Objects.requireNonNull(kind);
    boolean decimal = kind == Kind.DECIMAL || kind == Kind.SCIENTIFIC;
    if (decimal ? digits < 1 || digits > MAX_DIGITS : digits != 0) {
      throw new IllegalArgumentException(kind + " with " + digits + " digits");
    }
  }

  /**
   * Returns the decimal format.
   *
   * @param digits the significant digits, 1 to {@link #MAX_DIGITS}
   * @return the format
   */
  public static Format decimal(int digits) {
    return new Format(Kind.DECIMAL, digits);
  }

  /**
   * Returns the scientific format.
   *
   * @param digits the significant digits, 1 to {@link #MAX_DIGITS}
   * @return the format
   */
  public static Format scientific(int digits) {
    return new Format(Kind.SCIENTIFIC, digits);
  }

  /**
   * The format of an exponent: never a mixed number ({@code x^(3/2)} stays) and never scientific
   * ({@code x^(1.5)}).
   */
  Format forExponents() {
    return switch (kind) {
      case FRACTION -> EXACT;
      case SCIENTIFIC -> decimal(digits);
      case EXACT, DECIMAL -> this;
    };
  }

  /** Whether numbers are written as decimals. */
  boolean decimal() {
    return kind == Kind.DECIMAL || kind == Kind.SCIENTIFIC;
  }
}
