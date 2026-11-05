package mathcanvas.engine.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Objects;

/**
 * An exact integer of any size.
 *
 * @param value the integer
 */
public record Int(BigInteger value) implements RealNumeric {

  /** Zero. */
  public static final Int ZERO = new Int(BigInteger.ZERO);

  /** One. */
  public static final Int ONE = new Int(BigInteger.ONE);

  /** Two. */
  public static final Int TWO = new Int(BigInteger.TWO);

  /** Minus one. */
  public static final Int MINUS_ONE = new Int(BigInteger.ONE.negate());

  /**
   * Checks the value.
   *
   * @throws NullPointerException if {@code value} is null
   */
  public Int {
    Objects.requireNonNull(value, "value");
  }

  /**
   * Returns the integer {@code value}.
   *
   * @param value the value
   * @return the integer
   */
  public static Int of(long value) {
    return new Int(BigInteger.valueOf(value));
  }

  /**
   * Returns the integer {@code value}.
   *
   * @param value the value
   * @return the integer
   */
  public static Int of(BigInteger value) {
    return new Int(value);
  }

  /**
   * Returns the numerator, which is this integer.
   *
   * @return this integer
   */
  public Int numerator() {
    return this;
  }

  /**
   * Returns the denominator, which is one.
   *
   * @return {@code Int 1}
   */
  public Int denominator() {
    return ONE;
  }

  /**
   * Returns the greatest common divisor, never negative; {@code gcd(0, 0)} is 0.
   *
   * @param other the other integer
   * @return the gcd
   */
  public Int gcd(Int other) {
    return new Int(value.gcd(other.value));
  }

  /**
   * Returns the least common multiple, never negative; 0 if either integer is 0.
   *
   * @param other the other integer
   * @return the lcm
   */
  public Int lcm(Int other) {
    if (value.signum() == 0 || other.value.signum() == 0) {
      return ZERO;
    }
    return new Int(value.divide(value.gcd(other.value)).multiply(other.value).abs());
  }

  /**
   * Returns the remainder of floor division, whose sign follows the divisor as in {@link
   * Math#floorMod(long, long)}: {@code mod(-7, 3)} is 2 and {@code mod(7, -3)} is -2.
   *
   * @param divisor the divisor
   * @return the remainder, zero or of the divisor's sign
   * @throws ArithmeticException if {@code divisor} is zero
   */
  public Int mod(Int divisor) {
    if (divisor.value.signum() == 0) {
      throw new ArithmeticException("modulus by zero");
    }
    BigInteger remainder = value.mod(divisor.value.abs());
    if (divisor.value.signum() < 0 && remainder.signum() != 0) {
      remainder = remainder.add(divisor.value);
    }
    return new Int(remainder);
  }

  /**
   * Returns {@code n!} for this non-negative {@code n}, by binary splitting.
   *
   * @return the factorial
   * @throws ArithmeticException if this integer is negative or does not fit an {@code int}
   */
  public Int factorial() {
    if (value.signum() < 0) {
      throw new ArithmeticException("factorial of a negative integer: " + value);
    }
    int n = value.intValueExact();
    return new Int(n < 2 ? BigInteger.ONE : product(2, n));
  }

  /**
   * Returns the binomial coefficient {@code C(this, k)}: the falling factorial {@code
   * n(n-1)…(n-k+1)} over {@code k!}, so it is defined for every integer {@code n} and is 0 when
   * {@code 0 ≤ n < k}; 0 for negative {@code k}. For negative {@code n} it is {@code (-1)^k C(k - n
   * - 1, k)}; both products are formed by binary splitting.
   *
   * @param k the lower index
   * @return the binomial coefficient
   * @throws ArithmeticException if the shorter of {@code k} and {@code n - k} does not fit an
   *     {@code int}
   */
  public Int binomial(Int k) {
    if (k.value.signum() < 0) {
      return ZERO;
    }
    if (value.signum() < 0) {
      Int positive = new Int(k.value.subtract(value).subtract(BigInteger.ONE)).binomial(k);
      return k.value.testBit(0) ? positive.negate() : positive;
    }
    if (value.compareTo(k.value) < 0) {
      return ZERO;
    }
    // C(n, k) = C(n, n - k): the shorter product.
    int kk = k.value.min(value.subtract(k.value)).intValueExact();
    if (kk == 0) {
      return ONE;
    }
    BigInteger falling = product(value.subtract(BigInteger.valueOf(kk - 1L)), kk);
    return new Int(falling.divide(kk < 2 ? BigInteger.ONE : product(2, kk)));
  }

  @Override
  public Int negate() {
    return new Int(value.negate());
  }

  @Override
  public int signum() {
    return value.signum();
  }

  @Override
  public Int floor() {
    return this;
  }

  @Override
  public Int ceil() {
    return this;
  }

  @Override
  public Int round() {
    return this;
  }

  @Override
  public Int truncate() {
    return this;
  }

  @Override
  public boolean isPerfectSquare() {
    return value.signum() >= 0 && value.sqrt().pow(2).equals(value);
  }

  @Override
  public double toDouble() {
    return value.doubleValue();
  }

  @Override
  public Real toReal(MathContext mc) {
    return new Real(new BigDecimal(value).round(mc));
  }

  @Override
  public boolean isZero() {
    return value.signum() == 0;
  }

  @Override
  public boolean isOne() {
    return value.equals(BigInteger.ONE);
  }

  @Override
  public boolean isInteger() {
    return true;
  }

  @Override
  public boolean isExact() {
    return true;
  }

  @Override
  public String toString() {
    return value.toString();
  }

  /** The product of the {@code count} integers from {@code first} up, split in halves. */
  private static BigInteger product(BigInteger first, int count) {
    if (count < 16) {
      BigInteger result = first;
      for (int i = 1; i < count; i++) {
        result = result.multiply(first.add(BigInteger.valueOf(i)));
      }
      return result;
    }
    int half = count >>> 1;
    return product(first, half)
        .multiply(product(first.add(BigInteger.valueOf(half)), count - half));
  }

  /** The product of {@code from..to}, split in halves so the operands stay balanced. */
  private static BigInteger product(int from, int to) {
    if (to - from < 16) {
      BigInteger result = BigInteger.valueOf(from);
      for (int i = from + 1; i <= to; i++) {
        result = result.multiply(BigInteger.valueOf(i));
      }
      return result;
    }
    int middle = (from + to) >>> 1;
    return product(from, middle).multiply(product(middle + 1, to));
  }
}
