package mathcanvas.engine.number;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An inexact real: a {@code BigDecimal} with its trailing zeros stripped, so that {@code equals}
 * agrees with {@code compareTo} ({@code 1.0} and {@code 1.00} are the same {@code Real}). A {@code
 * Real} never becomes exact implicitly, even when its value is an integer.
 *
 * @param value the decimal value, stored with trailing zeros stripped
 */
public record Real(BigDecimal value) implements RealNumeric {

  /**
   * Strips trailing zeros from the value.
   *
   * @throws NullPointerException if {@code value} is null
   */
  public Real {
    value = Objects.requireNonNull(value, "value").stripTrailingZeros();
  }

  /**
   * Returns the real {@code value}, unrounded.
   *
   * @param value the value
   * @return the real
   */
  public static Real of(BigDecimal value) {
    return new Real(value);
  }

  /**
   * Returns the real a decimal numeral denotes, as {@link BigDecimal#BigDecimal(String)} reads it.
   *
   * @param text the numeral, such as {@code 2.5} or {@code 1e-3}
   * @return the real
   * @throws NumberFormatException if the text is not a decimal numeral
   */
  public static Real of(String text) {
    return new Real(new BigDecimal(text));
  }

  /**
   * Returns the real a {@code double} denotes, through its shortest decimal representation ({@link
   * BigDecimal#valueOf(double)}, so {@code 0.1} is {@code 0.1}). The result is inexact like every
   * {@code Real}, and a double is itself already a rounded value.
   *
   * @param value the double
   * @return the real
   * @throws IllegalArgumentException if {@code value} is NaN or infinite
   */
  public static Real of(double value) {
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("not a finite double: " + value);
    }
    return new Real(BigDecimal.valueOf(value));
  }

  @Override
  public Real negate() {
    return new Real(value.negate());
  }

  @Override
  public int signum() {
    return value.signum();
  }

  @Override
  public Real floor() {
    return isInteger() ? this : new Real(value.setScale(0, RoundingMode.FLOOR));
  }

  @Override
  public Real ceil() {
    return isInteger() ? this : new Real(value.setScale(0, RoundingMode.CEILING));
  }

  @Override
  public Real round() {
    // HALF_UP rounds a tie away from zero. An integer value (scale ≤ 0) is returned as it is:
    // setScale(0) on a negative scale would write out every digit of 10^-scale.
    return isInteger() ? this : new Real(value.setScale(0, RoundingMode.HALF_UP));
  }

  @Override
  public Real truncate() {
    return isInteger() ? this : new Real(value.setScale(0, RoundingMode.DOWN));
  }

  @Override
  public boolean isPerfectSquare() {
    return false;
  }

  @Override
  public double toDouble() {
    return value.doubleValue();
  }

  @Override
  public Real toReal(MathContext mc) {
    return new Real(value.round(mc));
  }

  @Override
  public boolean isZero() {
    return value.signum() == 0;
  }

  @Override
  public boolean isOne() {
    return value.compareTo(BigDecimal.ONE) == 0;
  }

  @Override
  public boolean isInteger() {
    return value.scale() <= 0;
  }

  @Override
  public boolean isExact() {
    return false;
  }

  /** A decimal point always, so an integer-valued real reads as inexact: {@code 5.0}. */
  @Override
  public String toString() {
    int exponent = value.precision() - value.scale() - 1;
    if (exponent < -6 || exponent > 20) {
      return value.toString();
    }
    String plain = value.toPlainString();
    return plain.indexOf('.') < 0 ? plain + ".0" : plain;
  }
}
