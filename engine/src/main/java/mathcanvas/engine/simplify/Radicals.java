package mathcanvas.engine.simplify;

import java.math.BigInteger;
import java.math.MathContext;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;

/** Exact arithmetic on numeric radicals: exact roots and perfect-power factors of integers. */
final class Radicals {

  /** Trial division runs over the primes below this bound. */
  static final int TRIAL_BOUND = 10_000;

  /** The largest radicand, in bits, whose perfect-power factor is extracted. */
  static final int MAX_BITS = 10_000;

  private static final int[] PRIMES = primesBelow(TRIAL_BOUND);

  private Radicals() {}

  /**
   * The exact {@code n}-th root of a non-negative rational.
   *
   * @param x the value, an {@code Int} or a {@code Rational}
   * @param n the degree, at least 2
   * @return the root when it is exact, else null
   */
  static RealNumeric exactRoot(RealNumeric x, int n) {
    try {
      RealNumeric r = x.root(n, MathContext.UNLIMITED);
      return r.isExact() ? r : null;
    } catch (ArithmeticException inexact) {
      return null;
    }
  }

  /**
   * A positive rational other than 1 written as {@code root^exponent}, with {@code root > 1} not a
   * perfect power and {@code exponent} a non-zero integer, negative for a value below 1.
   *
   * @param root the root
   * @param exponent the exponent
   */
  record PerfectPower(RealNumeric root, int exponent) {}

  /**
   * Writes {@code b} as a perfect power of a root that is none: the exact {@code p}-th roots are
   * tried for the primes {@code p} up to the root's bit length (below {@link #TRIAL_BOUND}, which
   * covers every radicand of at most {@link #MAX_BITS} bits), and each one found is taken.
   *
   * @param b a positive rational other than 1
   * @return {@code b} as {@code root^exponent}
   */
  static PerfectPower perfectPower(RealNumeric b) {
    RealNumeric r = b;
    int k = 1;
    if (r.compareValue(Int.ONE) < 0) {
      r = (RealNumeric) Int.ONE.divide(r, MathContext.UNLIMITED);
      k = -1;
    }
    boolean reduced = true;
    while (reduced) {
      reduced = false;
      long limit = bits(r);
      for (int p : PRIMES) {
        if (p > limit) {
          break;
        }
        RealNumeric root = integerRoot(r, p);
        if (root != null) {
          r = root;
          k *= p;
          reduced = true;
          break;
        }
      }
    }
    return new PerfectPower(r, k);
  }

  /**
   * The exact {@code p}-th root of a positive rational by integer Newton iteration on its numerator
   * and denominator, or null: no {@code BigDecimal}, so testing many degrees stays cheap.
   */
  private static RealNumeric integerRoot(RealNumeric x, int p) {
    BigInteger num = x instanceof Rational q ? q.num() : ((Int) x).value();
    BigInteger den = x instanceof Rational q ? q.den() : BigInteger.ONE;
    BigInteger a = integerRoot(num, p);
    BigInteger b = integerRoot(den, p);
    return a == null || b == null ? null : Rational.of(a, b);
  }

  /** The exact {@code p}-th root of a positive integer, or null. */
  private static BigInteger integerRoot(BigInteger n, int p) {
    if (n.equals(BigInteger.ONE)) {
      return BigInteger.ONE;
    }
    if (p >= n.bitLength()) {
      return null;
    }
    BigInteger bp = BigInteger.valueOf(p);
    BigInteger pm1 = BigInteger.valueOf(p - 1);
    // Newton from above: x = ((p - 1)x + n / x^(p-1)) / p decreases to floor(n^(1/p)).
    BigInteger x = BigInteger.ONE.shiftLeft((n.bitLength() + p - 1) / p);
    while (true) {
      BigInteger y = pm1.multiply(x).add(n.divide(x.pow(p - 1))).divide(bp);
      if (y.compareTo(x) >= 0) {
        break;
      }
      x = y;
    }
    return x.pow(p).equals(n) ? x : null;
  }

  private static long bits(RealNumeric q) {
    return q instanceof Rational r
        ? Math.max(r.num().bitLength(), r.den().bitLength())
        : ((Int) q).value().bitLength();
  }

  private static int[] primesBelow(int bound) {
    boolean[] composite = new boolean[bound];
    int count = 0;
    for (int i = 2; i < bound; i++) {
      if (!composite[i]) {
        count++;
        for (long j = (long) i * i; j < bound; j += i) {
          composite[(int) j] = true;
        }
      }
    }
    int[] primes = new int[count];
    int k = 0;
    for (int i = 2; i < bound; i++) {
      if (!composite[i]) {
        primes[k++] = i;
      }
    }
    return primes;
  }
}
