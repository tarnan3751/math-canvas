package mathcanvas.engine.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;

/**
 * Powers, square roots, real {@code n}-th roots and the modulus.
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
 * correctly rounded. An inexact real {@code n}-th root is {@code exp(log(x)/n)} through {@link
 * Elementary}'s agreement loop, so its cost does not grow with {@code n}.
 *
 * <p>An integer-valued {@code Real} exponent {@code n} makes an integer power inexact. An exact
 * base keeps its exact power, rounded once, while that has at most {@value #EXACT_POWER_DIGITS}
 * digits; beyond, the base is rounded at {@code p + digits(n) + 5} digits and the power is the
 * inexact one above, faithful, so {@code (1/3)^1e9} costs what {@code 0.333…^1e9} does.
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

  static Numeric pow(Numeric x, Numeric y, MathContext mc) {
    return switch (y) {
      case Int k -> pow(x, k, mc);
      case Rational q -> nonIntegerPow(x, q, mc);
      case Real r -> nonIntegerPow(x, r, mc);
      case Complex z -> nonIntegerPow(x, z, mc);
    };
  }

  private static Numeric pow(Numeric x, Int k, MathContext mc) {
    if (k.value().bitLength() < Integer.SIZE) {
      return pow(x, k.value().intValue(), mc);
    }
    if (x.isZero()) {
      if (k.signum() < 0) {
        throw new ArithmeticException("zero to a negative power");
      }
      return x;
    }
    if (x.isOne()) {
      return x;
    }
    if (x.negate().isOne()) {
      return k.value().testBit(0) ? x : x.negate();
    }
    throw new ArithmeticException("exponent outside the int range: " + k);
  }

  private static Numeric nonIntegerPow(Numeric x, Numeric y, MathContext mc) {
    boolean exact = x.isExact() && y.isExact();
    if (x.isZero()) {
      int sign = y.re().signum();
      if (y.isZero()) {
        return exact ? Int.ONE : new Real(BigDecimal.ONE);
      }
      if (sign <= 0) {
        throw new ArithmeticException("zero to a power of non-positive real part: " + y);
      }
      return exact ? Int.ZERO : new Real(BigDecimal.ZERO);
    }
    // An integer-valued Real exponent of at most ten digits is converted; a longer one goes to
    // the inexact path, which throws at once when the result is out of range. An exact base
    // keeps its exact power while that has at most EXACT_POWER_DIGITS digits; beyond, it is
    // rounded at the guard precision first, so the power is the bounded inexact one.
    if (y instanceof Real r
        && r.isInteger()
        && r.value().precision() - r.value().scale() <= 10
        && r.value().toBigInteger().bitLength() < Integer.SIZE) {
      int n = r.value().intValueExact();
      Numeric base =
          x.isExact() && exactDigits(x) * Math.abs((long) n) > EXACT_POWER_DIGITS
              ? x.toReal(guard(mc, n))
              : x;
      return pow(base, n, mc).toReal(mc);
    }
    if (x instanceof RealNumeric base && y instanceof RealNumeric exponent) {
      Integer twice = twiceIfIntegerModFour(exponent);
      if (base.signum() < 0 && twice != null) {
        // (-a)^y = a^y · e^(iπy), and e^(iπy) = i^(2y) is a unit when 2y is an integer; the
        // real part of ±i·a^y is an exact zero, as for sqrt.
        RealNumeric magnitude = (RealNumeric) pow(base.negate(), exponent, mc);
        return switch (twice) {
          case 0 -> magnitude;
          case 1 -> Complex.of(Int.ZERO, magnitude);
          case 2 -> magnitude.negate();
          default -> Complex.of(Int.ZERO, magnitude.negate());
        };
      }
      if (base.signum() > 0 && base.isExact() && exponent instanceof Rational q) {
        Numeric root = exactRationalPower(base, q, mc);
        if (root != null) {
          return root;
        }
      }
    }
    if (y.equals(Rational.of(1, 2))) {
      return sqrt(x, mc);
    }
    return Elementary.power(x, y, mc);
  }

  /** {@code 2y mod 4} when {@code 2y} is an integer, else null; never writes out {@code 2y}. */
  private static Integer twiceIfIntegerModFour(RealNumeric y) {
    BigInteger four = BigInteger.valueOf(4);
    return switch (y) {
      case Int i -> i.value().shiftLeft(1).mod(four).intValue();
      case Rational q -> q.den().equals(BigInteger.TWO) ? q.num().mod(four).intValue() : null;
      case Real r -> {
        BigDecimal twice = r.value().multiply(BigDecimal.TWO).stripTrailingZeros();
        if (twice.scale() > 0) {
          yield null;
        }
        // A multiple of 100 is a multiple of 4.
        yield twice.scale() <= -2
            ? 0
            : twice
                .unscaledValue()
                .multiply(BigInteger.TEN.pow(-twice.scale()))
                .mod(four)
                .intValue();
      }
    };
  }

  /** {@code base^(p/q)} when the {@code q}-th roots are exact, else null. */
  private static Numeric exactRationalPower(RealNumeric base, Rational q, MathContext mc) {
    if (q.den().bitLength() >= Integer.SIZE || q.num().bitLength() >= Integer.SIZE) {
      return null;
    }
    int degree = q.den().intValue();
    BigInteger num;
    BigInteger den;
    switch (base) {
      case Int i -> {
        num = i.value();
        den = BigInteger.ONE;
      }
      case Rational r -> {
        num = r.num();
        den = r.den();
      }
      case Real _ -> {
        return null;
      }
    }
    BigInteger numRoot = exactRoot(num, degree);
    BigInteger denRoot = exactRoot(den, degree);
    if (numRoot == null || denRoot == null) {
      return null;
    }
    return pow(Rational.of(numRoot, denRoot), q.num().intValue(), mc);
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
        yield root != null ? new Int(root) : Elementary.root(i, n, mc);
      }
      case Rational r -> {
        BigInteger num = exactRoot(r.num(), n);
        BigInteger den = exactRoot(r.den(), n);
        if (num != null && den != null) {
          yield Rational.of(num, den);
        }
        yield Elementary.root(r, n, mc);
      }
      case Real r -> {
        BigDecimal root = exactDecimalRoot(r.value(), n);
        yield root != null ? new Real(root.round(mc)) : Elementary.root(r, n, mc);
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
    if (value.compareTo(BigInteger.ONE) <= 0) {
      return value;
    }
    if (n >= value.bitLength()) {
      // 1 < value < 2^n: the root lies strictly between 1 and 2.
      return null;
    }
    if (n == 2) {
      BigInteger root = value.sqrt();
      return root.multiply(root).equals(value) ? root : null;
    }
    // No search: one candidate, then its neighbours.
    BigInteger candidate = nearestRoot(value, n);
    for (BigInteger root :
        new BigInteger[] {
          candidate, candidate.subtract(BigInteger.ONE), candidate.add(BigInteger.ONE)
        }) {
      if (isRoot(root, value, n)) {
        return root;
      }
    }
    return null;
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
   * The integer nearest the {@code n}-th root of {@code value > 1}, for {@code 3 ≤ n < bitLength}.
   * No search: a 60-digit {@code exp(log(t)/n)} from the value's top 200 bits {@code t} (through
   * {@link BigDecimalMath}) gives the root's top 100 bits, and Newton's method on integers at
   * doubling binary precision carries them to the full root, at a cost of about two Newton steps at
   * full size. (Evaluating {@code exp(log(value)/n)} to the root's full digit count in {@code
   * BigDecimal} costs seconds for a root of thousands of digits: above about 300 digits {@code
   * BigDecimal} recomputes a power of ten for every rounding.)
   */
  private static BigInteger nearestRoot(BigInteger value, int n) {
    long rootBits = (value.bitLength() + n - 1L) / n;
    int bits = (int) Math.min(rootBits, SEED_BITS);
    long shift = rootBits - bits;
    // log(root / 2^shift) = (log t + drop·ln 2)/n - shift·ln 2, where value ≈ t·2^drop.
    int drop = Math.max(0, value.bitLength() - 200);
    MathContext seed = new MathContext(60, RoundingMode.HALF_EVEN);
    BigDecimal ln2 = BigDecimalMath.log(BigDecimal.TWO, seed);
    BigDecimal log =
        BigDecimalMath.log(new BigDecimal(value.shiftRight(drop)), seed)
            .add(ln2.multiply(BigDecimal.valueOf(drop)), seed)
            .divide(BigDecimal.valueOf(n), seed)
            .subtract(ln2.multiply(BigDecimal.valueOf(shift)), seed);
    BigInteger y = BigDecimalMath.exp(log, seed).setScale(0, RoundingMode.HALF_EVEN).toBigInteger();
    // Each Newton step squares the relative error and multiplies it by about n/2; the guard bits
    // keep the doubled precision within what the step delivers.
    int guard = 32 - Integer.numberOfLeadingZeros(n) + 8;
    BigInteger degree = BigInteger.valueOf(n);
    BigInteger lower = BigInteger.valueOf(n - 1L);
    while (shift > 0) {
      int grow = (int) Math.min(bits - guard, shift);
      shift -= grow;
      bits += grow;
      y = y.shiftLeft(grow);
      BigInteger v = value.shiftRight((int) (n * shift));
      y = lower.multiply(y).add(v.divide(y.pow(n - 1))).divide(degree);
    }
    return y;
  }

  /** Bits of the root the seed carries: a 60-digit estimate holds about 199. */
  private static final int SEED_BITS = 100;

  /**
   * Whether {@code candidate^n == value}: residues modulo two primes first, so a non-power is
   * usually rejected without forming the power.
   */
  private static boolean isRoot(BigInteger candidate, BigInteger value, int n) {
    if (candidate.signum() <= 0) {
      return false;
    }
    BigInteger exponent = BigInteger.valueOf(n);
    for (BigInteger prime : RESIDUE_PRIMES) {
      if (!candidate.modPow(exponent, prime).equals(value.mod(prime))) {
        return false;
      }
    }
    return candidate.pow(n).equals(value);
  }

  private static final List<BigInteger> RESIDUE_PRIMES =
      List.of(BigInteger.valueOf(2_147_483_647L), BigInteger.valueOf(2_305_843_009_213_693_951L));

  /** {@code mc} with guard digits for {@code |n|} roundings, unlimited for an unlimited context. */
  private static MathContext guard(MathContext mc, int n) {
    if (mc.getPrecision() == 0) {
      return MathContext.UNLIMITED;
    }
    int digits = String.valueOf(Math.abs((long) n)).length();
    return new MathContext(mc.getPrecision() + digits + GUARD_DIGITS, RoundingMode.HALF_EVEN);
  }

  /** The number of digits the exact value of {@code x} takes: its larger part's, if complex. */
  private static long exactDigits(Numeric x) {
    return switch (x) {
      case RealNumeric r -> exactDigits(r);
      case Complex z -> Math.max(exactDigits(z.re()), exactDigits(z.im()));
    };
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
