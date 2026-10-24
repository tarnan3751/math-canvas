package mathcanvas.engine.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * An exact rational that is not an integer: in lowest terms, with a positive denominator other than
 * 1. A quotient with denominator 1 is an {@link Int}; {@link #of(BigInteger, BigInteger)} returns
 * one.
 *
 * @param num the numerator, coprime to the denominator
 * @param den the denominator, at least 2
 */
public record Rational(BigInteger num, BigInteger den) implements RealNumeric {

  /**
   * Reduces to lowest terms and makes the denominator positive.
   *
   * @throws ArithmeticException if the denominator is zero
   * @throws IllegalArgumentException if the reduced denominator is 1 (the value is an {@code Int};
   *     use {@link #of(BigInteger, BigInteger)})
   */
  public Rational {
    Objects.requireNonNull(num, "num");
    Objects.requireNonNull(den, "den");
    if (den.signum() == 0) {
      throw new ArithmeticException("zero denominator");
    }
    BigInteger gcd = num.gcd(den);
    if (den.signum() < 0) {
      gcd = gcd.negate();
    }
    num = num.divide(gcd);
    den = den.divide(gcd);
    if (den.equals(BigInteger.ONE)) {
      throw new IllegalArgumentException(
          "a rational with denominator 1 is an Int; use Rational.of: " + num);
    }
  }

  /**
   * Returns {@code num / den} in lowest terms: an {@link Int} when the denominator divides the
   * numerator, otherwise a {@code Rational}.
   *
   * @param num the numerator
   * @param den the denominator
   * @return the quotient
   * @throws ArithmeticException if {@code den} is zero
   */
  public static RealNumeric of(BigInteger num, BigInteger den) {
    if (den.signum() == 0) {
      throw new ArithmeticException("zero denominator");
    }
    if (den.equals(BigInteger.ONE)) {
      return new Int(num);
    }
    BigInteger[] quotient = num.divideAndRemainder(den);
    return quotient[1].signum() == 0 ? new Int(quotient[0]) : new Rational(num, den);
  }

  /**
   * Returns {@code num / den} in lowest terms, as {@link #of(BigInteger, BigInteger)}.
   *
   * @param num the numerator
   * @param den the denominator
   * @return the quotient
   * @throws ArithmeticException if {@code den} is zero
   */
  public static RealNumeric of(long num, long den) {
    return of(BigInteger.valueOf(num), BigInteger.valueOf(den));
  }

  /**
   * Returns the numerator.
   *
   * @return the numerator
   */
  public Int numerator() {
    return new Int(num);
  }

  /**
   * Returns the denominator, at least 2.
   *
   * @return the denominator
   */
  public Int denominator() {
    return new Int(den);
  }

  @Override
  public Rational negate() {
    return new Rational(num.negate(), den);
  }

  @Override
  public int signum() {
    return num.signum();
  }

  @Override
  public Int floor() {
    // BigInteger.divide truncates; a negative non-integer quotient floors one lower.
    BigInteger quotient = num.divide(den);
    return new Int(num.signum() < 0 ? quotient.subtract(BigInteger.ONE) : quotient);
  }

  @Override
  public Int ceil() {
    BigInteger quotient = num.divide(den);
    return new Int(num.signum() > 0 ? quotient.add(BigInteger.ONE) : quotient);
  }

  @Override
  public Int round() {
    // floor(|x| + 1/2) with the sign restored: halves go away from zero.
    BigInteger magnitude = num.abs().shiftLeft(1).add(den).divide(den.shiftLeft(1));
    return new Int(num.signum() < 0 ? magnitude.negate() : magnitude);
  }

  @Override
  public Int truncate() {
    return new Int(num.divide(den));
  }

  @Override
  public boolean isPerfectSquare() {
    return num.signum() >= 0 && num.sqrt().pow(2).equals(num) && den.sqrt().pow(2).equals(den);
  }

  @Override
  public double toDouble() {
    // Twenty digits leave a double one rounding from the exact quotient: within one ulp.
    return new BigDecimal(num)
        .divide(new BigDecimal(den), new MathContext(20, RoundingMode.HALF_EVEN))
        .doubleValue();
  }

  @Override
  public Real toReal(MathContext mc) {
    return new Real(Arithmetic.quotient(new BigDecimal(num), new BigDecimal(den), mc));
  }

  @Override
  public boolean isZero() {
    return false;
  }

  @Override
  public boolean isOne() {
    return false;
  }

  @Override
  public boolean isInteger() {
    return false;
  }

  @Override
  public boolean isExact() {
    return true;
  }

  @Override
  public String toString() {
    return num + "/" + den;
  }
}
