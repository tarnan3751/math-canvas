package mathcanvas.engine.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Integer powers, square roots, real {@code n}-th roots and the modulus.
 *
 * <p>Integer powers are exact for exact kinds. An inexact power ({@code Real}, or {@code Complex}
 * with a {@code Real} part) is the exact power rounded once whenever that has at most {@value
 * #EXACT_POWER_DIGITS} digits, so {@code pow(x, 2)} is {@code x·x}; beyond that it is binary
 * powering at {@code p + digits(n) + 5} digits, rounded at the end, which is faithful (within one
 * ulp). {@link BigDecimal#pow(int, MathContext)} is not used: its documented error is two ulps.
 *
 * <p>A {@code Real} square root is {@link BigDecimal#sqrt(MathContext)}, correctly rounded under
 * the half-way rounding modes and within one ulp under the directed ones. The square root of a
 * non-square {@code Rational}, a complex square root, and the inexact real {@code n}-th roots are
 * computed a few digits beyond the context and rounded at the end: faithful (within one ulp), not
 * correctly rounded.
 */
final class Powers {

  private Powers() {}

  private static final int GUARD_DIGITS = 5;

  /** The size, in digits, up to which an inexact power is formed exactly and rounded once. */
  private static final long EXACT_POWER_DIGITS = 100_000;

  static Numeric pow(Numeric x, int n, MathContext mc) {
    return switch (x) {
      case Int i -> {
        if (n >= 0) {
          yield new Int(i.value().pow(n));
        }
        if (i.isZero()) {
          throw new ArithmeticException("zero to a negative power");
        }
        yield Rational.of(BigInteger.ONE, i.value().pow(-n));
      }
      case Rational r ->
          n >= 0
              ? Rational.of(r.num().pow(n), r.den().pow(n))
              : Rational.of(r.den().pow(-n), r.num().pow(-n));
      case Real r -> pow(r, n, mc);
      case Complex z -> {
        if (n == 0) {
          yield z.isExact() ? Int.ONE : new Real(BigDecimal.ONE);
        }
        if (!z.isExact()
            && (mc.getPrecision() == 0
                || Math.max(exactDigits(z.re()), exactDigits(z.im())) * Math.abs((long) n)
                    <= EXACT_POWER_DIGITS)) {
          // The exact Gaussian-rational power, each part rounded once: z.pow(2) is z·z.
          Numeric exact = pow(Complex.of(exact(z.re()), exact(z.im())), n, mc);
          yield Complex.of(exact.re().toReal(mc), exact.im().toReal(mc));
        }
        MathContext g = guard(mc, n);
        Numeric power = z;
        Numeric result = null;
        // Binary powering over |n|, least significant bit first.
        for (long e = Math.abs((long) n); e > 0; e >>= 1) {
          if ((e & 1) == 1) {
            result = result == null ? power : Arithmetic.multiply(result, power, g);
          }
          if (e > 1) {
            power = Arithmetic.multiply(power, power, g);
          }
        }
        yield n > 0 ? roundParts(result, mc) : Arithmetic.divide(Int.ONE, result, mc);
      }
    };
  }

  private static Real pow(Real x, int n, MathContext mc) {
    if (n == 0) {
      return new Real(BigDecimal.ONE);
    }
    if (x.isZero() && n < 0) {
      throw new ArithmeticException("zero to a negative power");
    }
    long magnitude = Math.abs((long) n);
    if (mc.getPrecision() == 0 || x.value().precision() * magnitude <= EXACT_POWER_DIGITS) {
      BigDecimal exact = x.value().pow((int) magnitude);
      return new Real(n > 0 ? exact.round(mc) : Arithmetic.quotient(BigDecimal.ONE, exact, mc));
    }
    MathContext g = guard(mc, n);
    BigDecimal power = x.value();
    BigDecimal result = BigDecimal.ONE;
    for (long e = magnitude; e > 0; e >>= 1) {
      if ((e & 1) == 1) {
        result = result.multiply(power, g);
      }
      if (e > 1) {
        power = power.multiply(power, g);
      }
    }
    return new Real(n > 0 ? result.round(mc) : BigDecimal.ONE.divide(result, mc));
  }

  static Numeric sqrt(Numeric x, MathContext mc) {
    return switch (x) {
      case RealNumeric r when r.signum() >= 0 -> sqrtNonNegative(r, mc);
      case RealNumeric r -> Complex.of(Int.ZERO, sqrtNonNegative(r.negate(), mc));
      case Complex z -> {
        // Principal root of a + bi with m = |z|: for a ≥ 0, t = sqrt((m + a)/2) and the root is
        // t + (b / 2t)i; for a < 0, t = sqrt((m - a)/2) and it is |b|/2t + sign(b)·t·i. Each
        // branch avoids the cancellation in m ± a.
        MathContext g = guard(mc, 0);
        RealNumeric a = z.re();
        RealNumeric b = z.im();
        RealNumeric m = modulus(z, g);
        RealNumeric two = Int.TWO;
        if (a.signum() >= 0) {
          RealNumeric t = sqrtNonNegative(Arithmetic.divide(Arithmetic.add(m, a, g), two, g), g);
          if (t.isZero()) {
            yield Arithmetic.round(t, mc);
          }
          RealNumeric im = Arithmetic.divide(b, Arithmetic.multiply(two, t, g), g);
          yield Complex.of(Arithmetic.round(t, mc), Arithmetic.round(im, mc));
        }
        RealNumeric t = sqrtNonNegative(Arithmetic.divide(Arithmetic.subtract(m, a, g), two, g), g);
        RealNumeric re = Arithmetic.divide(b.abs(), Arithmetic.multiply(two, t, g), g);
        yield Complex.of(
            Arithmetic.round(re, mc), Arithmetic.round(b.signum() < 0 ? t.negate() : t, mc));
      }
    };
  }

  static RealNumeric modulus(Numeric x, MathContext mc) {
    return switch (x) {
      case RealNumeric r -> r.abs();
      case Complex z -> {
        MathContext g = guard(mc, 0);
        RealNumeric squares =
            Arithmetic.add(
                Arithmetic.multiply(z.re(), z.re(), g), Arithmetic.multiply(z.im(), z.im(), g), g);
        yield sqrtNonNegative(squares, mc);
      }
    };
  }

  static RealNumeric root(RealNumeric x, int n, MathContext mc) {
    if (n < 1) {
      throw new IllegalArgumentException("root degree below 1: " + n);
    }
    if (x.signum() < 0) {
      if (n % 2 == 0) {
        throw new ArithmeticException("even root of a negative number: " + x);
      }
      return root(x.negate(), n, mc).negate();
    }
    if (n == 1) {
      return Arithmetic.round(x, mc);
    }
    if (n == 2) {
      return sqrtNonNegative(x, mc);
    }
    return switch (x) {
      case Int i -> {
        BigInteger root = exactRoot(i.value(), n);
        yield root != null ? new Int(root) : new Real(nthRoot(new BigDecimal(i.value()), n, mc));
      }
      case Rational r -> {
        BigInteger num = exactRoot(r.num(), n);
        BigInteger den = exactRoot(r.den(), n);
        if (num != null && den != null) {
          yield Rational.of(num, den);
        }
        MathContext g = guard(mc, n);
        yield new Real(nthRoot(r.toReal(g).value(), n, mc));
      }
      case Real r -> {
        BigDecimal root = exactDecimalRoot(r.value(), n);
        yield root != null ? new Real(root.round(mc)) : new Real(nthRoot(r.value(), n, mc));
      }
    };
  }

  /**
   * The exact {@code n}-th root of a non-negative decimal, or null when it has none. With trailing
   * zeros stripped, a decimal {@code u·10^-s} is the {@code n}-th power of a decimal exactly when
   * {@code n} divides {@code s} and {@code u} is a perfect {@code n}-th power.
   */
  private static BigDecimal exactDecimalRoot(BigDecimal x, int n) {
    BigDecimal stripped = x.stripTrailingZeros();
    if (Math.floorMod(stripped.scale(), n) != 0) {
      return null;
    }
    BigInteger root = exactRoot(stripped.unscaledValue(), n);
    return root == null ? null : new BigDecimal(root, stripped.scale() / n);
  }

  /**
   * Returns the exact {@code n}-th root of a non-negative integer, or null when it is not a perfect
   * {@code n}-th power.
   */
  static BigInteger exactRoot(BigInteger value, int n) {
    BigInteger root = n == 2 ? value.sqrt() : floorRoot(value, n);
    if (root.compareTo(BigInteger.ONE) <= 0) {
      return root.equals(value) ? root : null;
    }
    return root.pow(n).equals(value) ? root : null;
  }

  private static RealNumeric sqrtNonNegative(RealNumeric x, MathContext mc) {
    return switch (x) {
      case Int i -> {
        BigInteger root = exactRoot(i.value(), 2);
        yield root != null ? new Int(root) : new Real(sqrt(new BigDecimal(i.value()), mc));
      }
      case Rational r -> {
        BigInteger num = exactRoot(r.num(), 2);
        BigInteger den = exactRoot(r.den(), 2);
        if (num != null && den != null) {
          yield Rational.of(num, den);
        }
        yield new Real(sqrt(r.toReal(guard(mc, 0)).value(), mc));
      }
      case Real r -> new Real(sqrt(r.value(), mc));
    };
  }

  /**
   * {@link BigDecimal#sqrt}, with {@link Arithmetic#NEEDS_PRECISION} for an inexact unlimited root.
   */
  private static BigDecimal sqrt(BigDecimal x, MathContext mc) {
    try {
      return x.sqrt(mc);
    } catch (ArithmeticException inexact) {
      if (mc.getPrecision() == 0) {
        throw new ArithmeticException(Arithmetic.NEEDS_PRECISION);
      }
      throw inexact;
    }
  }

  /**
   * {@code floor(value^(1/n))} for a non-negative integer: 1 (or 0) when {@code 2^n} exceeds the
   * value, Newton's method from above for {@code n ≤ 64}, and bisection over the root's bits above
   * that (Newton's linear phase from a power-of-two start takes about {@code 0.7n} steps).
   */
  private static BigInteger floorRoot(BigInteger value, int n) {
    long bits = value.bitLength();
    if (n >= bits) {
      return value.signum() == 0 ? BigInteger.ZERO : BigInteger.ONE;
    }
    // 2^floor((bits - 1)/n) ≤ root < 2^ceil(bits/n).
    int low = (int) ((bits - 1) / n);
    int high = (int) ((bits + n - 1) / n);
    if (n > 64) {
      BigInteger lo = BigInteger.ONE.shiftLeft(low);
      BigInteger hi = BigInteger.ONE.shiftLeft(high);
      while (hi.subtract(lo).compareTo(BigInteger.ONE) > 0) {
        BigInteger mid = lo.add(hi).shiftRight(1);
        if (mid.pow(n).compareTo(value) <= 0) {
          lo = mid;
        } else {
          hi = mid;
        }
      }
      return lo;
    }
    BigInteger big = BigInteger.valueOf(n);
    BigInteger smaller = BigInteger.valueOf(n - 1L);
    BigInteger y = BigInteger.ONE.shiftLeft(high);
    while (true) {
      BigInteger next = smaller.multiply(y).add(value.divide(y.pow(n - 1))).divide(big);
      if (next.compareTo(y) >= 0) {
        return y;
      }
      y = next;
    }
  }

  /**
   * The positive {@code n}-th root of a positive decimal, {@code n ≥ 3}, by Newton's method from
   * above at a guard precision, rounded once to {@code mc}.
   */
  private static BigDecimal nthRoot(BigDecimal x, int n, MathContext mc) {
    if (x.signum() == 0) {
      return BigDecimal.ZERO;
    }
    if (mc.getPrecision() == 0) {
      throw new ArithmeticException(Arithmetic.NEEDS_PRECISION);
    }
    MathContext g = guard(mc, n);
    BigDecimal big = BigDecimal.valueOf(n);
    BigDecimal smaller = BigDecimal.valueOf(n - 1L);
    // 10^ceil((e + 1)/n), with x < 10^(e + 1), is above the root.
    int exponent = x.precision() - x.scale();
    BigDecimal y = BigDecimal.ONE.scaleByPowerOfTen(Math.ceilDiv(exponent, n));
    while (true) {
      BigDecimal next = smaller.multiply(y).add(x.divide(y.pow(n - 1, g), g)).divide(big, g);
      if (next.compareTo(y) >= 0) {
        return y.round(mc);
      }
      y = next;
    }
  }

  /** {@code mc} with guard digits for {@code |n|} roundings, unlimited for an unlimited context. */
  private static MathContext guard(MathContext mc, int n) {
    if (mc.getPrecision() == 0) {
      return MathContext.UNLIMITED;
    }
    int digits = String.valueOf(Math.abs((long) n)).length();
    return new MathContext(mc.getPrecision() + digits + GUARD_DIGITS, RoundingMode.HALF_EVEN);
  }

  /** The number of digits the exact rational value of {@code x} takes. */
  private static long exactDigits(RealNumeric x) {
    return switch (x) {
      case Int i -> i.value().bitLength() / 3 + 1;
      case Rational r -> (r.num().bitLength() + r.den().bitLength()) / 3 + 2;
      case Real r -> r.value().precision() + Math.abs((long) r.value().scale());
    };
  }

  /** The exact rational value of {@code x}. */
  private static RealNumeric exact(RealNumeric x) {
    return switch (x) {
      case Int i -> i;
      case Rational r -> r;
      case Real r -> {
        BigDecimal v = r.value();
        yield v.scale() <= 0
            ? new Int(v.toBigIntegerExact())
            : Rational.of(v.unscaledValue(), BigInteger.TEN.pow(v.scale()));
      }
    };
  }

  private static Numeric roundParts(Numeric x, MathContext mc) {
    return switch (x) {
      case RealNumeric r -> Arithmetic.round(r, mc);
      case Complex z -> Complex.of(Arithmetic.round(z.re(), mc), Arithmetic.round(z.im(), mc));
    };
  }
}
