package mathcanvas.engine.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Promotion and the four operations, dispatched by pattern-matching switches over pairs of the
 * sealed kinds (no {@code default}: the compiler checks every pair is covered).
 *
 * <p>Two exact operands are combined as fractions and normalised by {@link Rational#of}. When
 * either is a {@code Real}, both are written as a decimal fraction {@code n/d} ({@code n} a {@code
 * BigDecimal}, {@code d} the exact denominator), the operation is carried out exactly on those
 * fractions, and the single final step rounds to the context: a multiplication by {@link
 * BigDecimal#round} or a division by {@link BigDecimal#divide(BigDecimal, MathContext)}. So {@code
 * 0.1 + 0.2} is exactly {@code 0.3} before it is rounded, and a {@code Rational} operand is never
 * rounded on its own first.
 *
 * <p>Complex operations work part by part through the real ones. Where a part needs more than one
 * real operation (a product or quotient of two complex numbers), it is formed exactly as a fraction
 * ({@code pr - qs}, {@code ps + qr}, and over {@code r² + s²}) and rounded once; it is exact when
 * its operands are; a divisor whose parts are far apart is bracketed rather than formed exactly,
 * and the quotient is still rounded once ({@link #productSumOverNorm}). A complex result is built
 * by {@link Complex#of(RealNumeric, RealNumeric, MathContext)}, so an exact part beside a {@code
 * Real} one is promoted ({@code (1 + 2i) + 0.5} is {@code 1.5 + 2.0i}).
 */
final class Arithmetic {

  private Arithmetic() {}

  /** The message of every inexact result requested under an unlimited context. */
  static final String NEEDS_PRECISION = "an inexact result needs a finite precision";

  /** The magnitude gap, in digits, up to which a mixed sum is formed exactly. */
  private static final int EXACT_SPREAD = 10_000;

  private record Pair(Numeric left, Numeric right) {}

  private record RealPair(RealNumeric left, RealNumeric right) {}

  /** A real as an exact decimal fraction {@code num / den}, {@code den} positive. */
  private record Fraction(BigDecimal num, BigDecimal den) {}

  @FunctionalInterface
  private interface ExactOp {
    RealNumeric apply(BigInteger n1, BigInteger d1, BigInteger n2, BigInteger d2);
  }

  @FunctionalInterface
  private interface InexactOp {
    BigDecimal apply(Fraction left, Fraction right, MathContext mc);
  }

  static Numeric add(Numeric x, Numeric y, MathContext mc) {
    return switch (new Pair(x, y)) {
      case Pair(RealNumeric a, RealNumeric b) -> add(a, b, mc);
      case Pair(Complex _, Numeric _), Pair(RealNumeric _, Complex _) ->
          Complex.of(add(x.re(), y.re(), mc), add(x.im(), y.im(), mc), mc);
    };
  }

  static Numeric multiply(Numeric x, Numeric y, MathContext mc) {
    return switch (new Pair(x, y)) {
      case Pair(RealNumeric a, RealNumeric b) -> multiply(a, b, mc);
      case Pair(Complex a, RealNumeric b) ->
          Complex.of(multiply(a.re(), b, mc), multiply(a.im(), b, mc), mc);
      case Pair(RealNumeric a, Complex b) ->
          Complex.of(multiply(a, b.re(), mc), multiply(a, b.im(), mc), mc);
      case Pair(Complex a, Complex b) ->
          // (p + qi)(r + si) = (pr - qs) + (ps + qr)i
          Complex.of(
              productSum(a.re(), b.re(), a.im().negate(), b.im(), mc),
              productSum(a.re(), b.im(), a.im(), b.re(), mc),
              mc);
    };
  }

  static Numeric divide(Numeric x, Numeric y, MathContext mc) {
    return switch (new Pair(x, y)) {
      case Pair(RealNumeric a, RealNumeric b) -> divide(a, b, mc);
      case Pair(Complex a, RealNumeric b) ->
          Complex.of(divide(a.re(), b, mc), divide(a.im(), b, mc), mc);
      case Pair(Numeric a, Complex b) -> {
        // (p + qi)/(r + si) = ((pr + qs) + (qr - ps)i) / (r² + s²)
        RealNumeric p = a.re();
        RealNumeric q = a.im();
        yield Complex.of(
            productSumOverNorm(p, b.re(), q, b.im(), b, mc),
            productSumOverNorm(q, b.re(), p.negate(), b.im(), b, mc),
            mc);
      }
    };
  }

  static RealNumeric add(RealNumeric x, RealNumeric y, MathContext mc) {
    return binary(
        x,
        y,
        mc,
        (n1, d1, n2, d2) ->
            d1.equals(d2)
                ? Rational.of(n1.add(n2), d1)
                : Rational.of(n1.multiply(d2).add(n2.multiply(d1)), d1.multiply(d2)),
        (a, b, c) -> {
          if (whole(a, b)) {
            return a.num.add(b.num, c);
          }
          BigDecimal den = a.den.multiply(b.den);
          return quotient(sum(a.num.multiply(b.den), b.num.multiply(a.den), den, c), den, c);
        });
  }

  static RealNumeric subtract(RealNumeric x, RealNumeric y, MathContext mc) {
    return add(x, y.negate(), mc);
  }

  static RealNumeric multiply(RealNumeric x, RealNumeric y, MathContext mc) {
    return binary(
        x,
        y,
        mc,
        (n1, d1, n2, d2) -> Rational.of(n1.multiply(n2), d1.multiply(d2)),
        (a, b, c) ->
            whole(a, b)
                ? a.num.multiply(b.num, c)
                : quotient(a.num.multiply(b.num), a.den.multiply(b.den), c));
  }

  static RealNumeric divide(RealNumeric x, RealNumeric y, MathContext mc) {
    if (y.isZero()) {
      throw new ArithmeticException("division by zero");
    }
    return binary(
        x,
        y,
        mc,
        (n1, d1, n2, d2) -> Rational.of(n1.multiply(d2), d1.multiply(n2)),
        (a, b, c) -> quotient(a.num.multiply(b.den), a.den.multiply(b.num), c));
  }

  static int compareValue(RealNumeric x, RealNumeric y) {
    return switch (new RealPair(x, y)) {
      case RealPair(Int a, Int b) -> a.value().compareTo(b.value());
      case RealPair(Int a, Rational b) -> a.value().multiply(b.den()).compareTo(b.num());
      case RealPair(Rational a, Int b) -> a.num().compareTo(b.value().multiply(a.den()));
      case RealPair(Rational a, Rational b) ->
          a.num().multiply(b.den()).compareTo(b.num().multiply(a.den()));
      case RealPair(Real _, RealNumeric _), RealPair(RealNumeric _, Real _) -> {
        Fraction a = fraction(x);
        Fraction b = fraction(y);
        yield a.num.multiply(b.den).compareTo(b.num.multiply(a.den));
      }
    };
  }

  /**
   * {@code w·x + y·z} rounded once: exact when all four are exact, otherwise a {@code Real} from
   * the exact value of the expression.
   */
  private static RealNumeric productSum(
      RealNumeric w, RealNumeric x, RealNumeric y, RealNumeric z, MathContext mc) {
    if (w.isExact() && x.isExact() && y.isExact() && z.isExact()) {
      return add(multiply(w, x, mc), multiply(y, z, mc), mc);
    }
    Fraction wx = product(fraction(w), fraction(x));
    Fraction yz = product(fraction(y), fraction(z));
    BigDecimal den = wx.den.multiply(yz.den);
    return new Real(
        quotient(sum(wx.num.multiply(yz.den), yz.num.multiply(wx.den), den, mc), den, mc));
  }

  /**
   * {@code (w·x + y·z) / |d|²} rounded once: exact when every operand is exact, otherwise a {@code
   * Real} from the exact value of the expression. When the parts of {@code d} are so far apart that
   * {@code r²} and {@code s²} differ by more than {@link #EXACT_SPREAD} digits, the exact norm
   * would be that long, so it is bracketed instead: truncated at {@code p + digits(den) + 3}
   * digits, the exact norm lies between the truncation and one unit above it. The quotient is
   * computed against both ends; rounding is monotone, so when the two results are equal that is the
   * exact quotient rounded once. They differ only when the quotient lies within the bracket's width
   * of a rounding boundary (an exact value just below a power of ten, rounded UP); then the
   * truncation precision is doubled, up to {@code 4p + 400} digits, and at that cap the norm is
   * formed exactly. The usual case costs one division more than an exact norm would.
   */
  private static RealNumeric productSumOverNorm(
      RealNumeric w, RealNumeric x, RealNumeric y, RealNumeric z, Complex d, MathContext mc) {
    RealNumeric r = d.re();
    RealNumeric s = d.im();
    if (w.isExact() && x.isExact() && y.isExact() && z.isExact() && d.isExact()) {
      RealNumeric norm = add(multiply(r, r, mc), multiply(s, s, mc), mc);
      return divide(add(multiply(w, x, mc), multiply(y, z, mc), mc), norm, mc);
    }
    Fraction wx = product(fraction(w), fraction(x));
    Fraction yz = product(fraction(y), fraction(z));
    Fraction rr = product(fraction(r), fraction(r));
    Fraction ss = product(fraction(s), fraction(s));
    BigDecimal normDen = rr.den.multiply(ss.den);
    BigDecimal rrTerm = rr.num.multiply(ss.den);
    BigDecimal ssTerm = ss.num.multiply(rr.den);
    BigDecimal left = wx.num.multiply(yz.den).multiply(normDen);
    BigDecimal right = yz.num.multiply(wx.den).multiply(normDen);
    BigDecimal outer = wx.den.multiply(yz.den);
    if (rrTerm.signum() == 0
        || ssTerm.signum() == 0
        || mc.getPrecision() == 0
        || Math.abs(exponent(rrTerm) - exponent(ssTerm)) <= EXACT_SPREAD) {
      return new Real(overNorm(left, right, outer, rrTerm.add(ssTerm), mc));
    }
    boolean rrLarger = exponent(rrTerm) > exponent(ssTerm);
    BigDecimal larger = rrLarger ? rrTerm : ssTerm;
    BigDecimal smaller = rrLarger ? ssTerm : rrTerm;
    long cap = 4L * mc.getPrecision() + 400;
    int digits = mc.getPrecision() + normDen.multiply(outer).precision() + 3;
    while (digits <= cap) {
      BigDecimal low = rrTerm.add(ssTerm, new MathContext(digits, RoundingMode.DOWN));
      if (low.subtract(larger).compareTo(smaller) == 0) {
        // The truncation is the exact norm.
        return new Real(overNorm(left, right, outer, low, mc));
      }
      BigDecimal high = low.add(BigDecimal.ONE.scaleByPowerOfTen(-low.scale()));
      BigDecimal below = overNorm(left, right, outer, high, mc);
      BigDecimal above = overNorm(left, right, outer, low, mc);
      if (below.compareTo(above) == 0) {
        return new Real(below);
      }
      digits *= 2;
    }
    return new Real(overNorm(left, right, outer, rrTerm.add(ssTerm), mc));
  }

  /** {@code (left + right) / (outer · norm)} rounded once, the numerator rounded to odd if far. */
  private static BigDecimal overNorm(
      BigDecimal left, BigDecimal right, BigDecimal outer, BigDecimal norm, MathContext mc) {
    if (norm.signum() == 0) {
      throw new ArithmeticException("division by zero");
    }
    BigDecimal den = outer.multiply(norm);
    return quotient(sum(left, right, den, mc), den, mc);
  }

  private static Fraction product(Fraction a, Fraction b) {
    return new Fraction(a.num.multiply(b.num), a.den.multiply(b.den));
  }

  /** Rounds a {@code Real} to {@code mc}; an exact number is returned as it is. */
  static RealNumeric round(RealNumeric x, MathContext mc) {
    return switch (x) {
      case Int i -> i;
      case Rational r -> r;
      case Real r -> new Real(r.value().round(mc));
    };
  }

  private static RealNumeric binary(
      RealNumeric x, RealNumeric y, MathContext mc, ExactOp exact, InexactOp inexact) {
    BigInteger one = BigInteger.ONE;
    return switch (new RealPair(x, y)) {
      case RealPair(Int a, Int b) -> exact.apply(a.value(), one, b.value(), one);
      case RealPair(Int a, Rational b) -> exact.apply(a.value(), one, b.num(), b.den());
      case RealPair(Rational a, Int b) -> exact.apply(a.num(), a.den(), b.value(), one);
      case RealPair(Rational a, Rational b) -> exact.apply(a.num(), a.den(), b.num(), b.den());
      case RealPair(Real _, RealNumeric _), RealPair(RealNumeric _, Real _) ->
          new Real(inexact.apply(fraction(x), fraction(y), mc));
    };
  }

  /**
   * The numerator {@code left + right} of a sum over {@code den}: exact, unless the two terms are
   * more than {@link #EXACT_SPREAD} digits apart in magnitude, where the exact sum would be that
   * long. Then it is rounded to odd at {@code p + digits(den) + 3} digits: truncated, with the last
   * digit made odd when anything was dropped. A sum rounded to odd at two or more digits beyond the
   * final precision rounds afterwards exactly as the exact sum would (S. Boldo and G. Melquiond,
   * "Emulation of FMA and correctly rounded sums: proved algorithms using rounding to odd", IEEE
   * Trans. Computers 57(4), 2008); the extra {@code digits(den)} keep the quotient by {@code den}
   * on the same side of every rounding boundary. The exact sum with a zero term is the other term,
   * returned as it is: adding a zero whose scale is far from the other term's would write that term
   * out to the zero's scale, as in {@code (1 + i)^n} with a {@code Real 0} part.
   */
  private static BigDecimal sum(BigDecimal left, BigDecimal right, BigDecimal den, MathContext mc) {
    if (left.signum() == 0) {
      return right;
    }
    if (right.signum() == 0) {
      return left;
    }
    if (mc.getPrecision() == 0 || Math.abs(exponent(left) - exponent(right)) <= EXACT_SPREAD) {
      return left.add(right);
    }
    boolean leftLarger = exponent(left) > exponent(right);
    BigDecimal larger = leftLarger ? left : right;
    BigDecimal smaller = leftLarger ? right : left;
    MathContext truncate =
        new MathContext(mc.getPrecision() + den.precision() + 3, RoundingMode.DOWN);
    BigDecimal sum = left.add(right, truncate);
    // Exact when the truncated sum less the larger term is the smaller term: one cheap comparison.
    boolean exact = sum.subtract(larger).compareTo(smaller) == 0;
    if (!exact && !sum.unscaledValue().testBit(0)) {
      sum = sum.add(BigDecimal.valueOf(sum.signum(), sum.scale()));
    }
    return sum;
  }

  private static long exponent(BigDecimal x) {
    return (long) x.precision() - x.scale();
  }

  private static boolean whole(Fraction a, Fraction b) {
    return a.den.compareTo(BigDecimal.ONE) == 0 && b.den.compareTo(BigDecimal.ONE) == 0;
  }

  /**
   * {@code num / den} rounded once to {@code mc}, for a non-zero {@code den}.
   *
   * @throws ArithmeticException with {@link #NEEDS_PRECISION} if {@code mc} is unlimited and the
   *     quotient does not terminate
   */
  static BigDecimal quotient(BigDecimal num, BigDecimal den, MathContext mc) {
    if (den.compareTo(BigDecimal.ONE) == 0) {
      return num.round(mc);
    }
    try {
      return num.divide(den, mc);
    } catch (ArithmeticException nonTerminating) {
      if (mc.getPrecision() == 0) {
        throw new ArithmeticException(NEEDS_PRECISION);
      }
      throw nonTerminating;
    }
  }

  private static Fraction fraction(RealNumeric x) {
    return switch (x) {
      case Int i -> new Fraction(new BigDecimal(i.value()), BigDecimal.ONE);
      case Rational r -> new Fraction(new BigDecimal(r.num()), new BigDecimal(r.den()));
      case Real r -> new Fraction(r.value(), BigDecimal.ONE);
    };
  }
}
