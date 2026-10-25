package mathcanvas.engine.parse;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

/**
 * The values of numerals, in time below the square of their length. {@code new BigInteger(String)}
 * converts digit by digit, quadratically, which at {@link Lexer#MAX_DIGITS} digits takes many
 * seconds; here a long run of digits is split in two, each half converted the same way and the high
 * half multiplied by a power of ten, so the multiplications are {@code BigInteger}'s Karatsuba and
 * Toom-Cook ones and the whole costs about one multiplication of the full length per level of
 * splitting.
 */
final class Numerals {

  /** Runs this short are converted directly, which is faster there. */
  private static final int DIRECT = 1_000;

  private final Map<Integer, BigInteger> powers = new HashMap<>();

  private Numerals() {}

  /**
   * The integer a run of decimal digits denotes, possibly after a sign.
   *
   * @param text the digits, possibly after {@code -} or {@code +}
   * @return the integer
   * @throws NumberFormatException if the text is not digits
   */
  static BigInteger integer(String text) {
    if (text.length() <= DIRECT) {
      return new BigInteger(text);
    }
    boolean negative = text.charAt(0) == '-';
    int from = negative || text.charAt(0) == '+' ? 1 : 0;
    BigInteger value = new Numerals().digits(text, from, text.length());
    return negative ? value.negate() : value;
  }

  /**
   * The decimal a decimal literal denotes, with the unscaled value and scale {@code new
   * BigDecimal(String)} would give: digits, an optional fraction after {@code .} and an optional
   * exponent after {@code e} or {@code E}.
   *
   * @param text the literal
   * @return the decimal
   * @throws NumberFormatException if the text is not a decimal literal or its scale is out of range
   */
  static BigDecimal decimal(String text) {
    if (text.length() <= DIRECT) {
      return new BigDecimal(text);
    }
    int e = Math.max(text.indexOf('e'), text.indexOf('E'));
    String mantissa = e < 0 ? text : text.substring(0, e);
    long exponent = e < 0 ? 0 : Long.parseLong(text.substring(e + 1));
    int point = mantissa.indexOf('.');
    String digits =
        point < 0 ? mantissa : mantissa.substring(0, point) + mantissa.substring(point + 1);
    long scale = (point < 0 ? 0 : mantissa.length() - point - 1) - exponent;
    if (scale != (int) scale) {
      throw new NumberFormatException("scale out of range");
    }
    return new BigDecimal(integer(digits), (int) scale);
  }

  /** The value of the digits {@code from} to {@code to}. */
  private BigInteger digits(String text, int from, int to) {
    int n = to - from;
    if (n <= DIRECT) {
      return new BigInteger(text.substring(from, to));
    }
    int low = n / 2;
    BigInteger high = digits(text, from, to - low);
    return high.multiply(power(low)).add(digits(text, to - low, to));
  }

  /** {@code 10^k}, computed once per conversion for each split length. */
  private BigInteger power(int k) {
    return powers.computeIfAbsent(k, BigInteger.TEN::pow);
  }
}
