package mathcanvas.engine.numbertheory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.Cancellation;

/**
 * Primality and the primes near a number ({@code docs/reference/engine.md}, <b>Number theory</b>).
 * Below {@link #DETERMINISTIC_BOUND} the test is the strong probable-prime test to the thirteen
 * bases 2 to 41, which no composite below the bound passes (Sorenson and Webster, 2015), so the
 * answer is a proof; at or above it the test is Baillie–PSW, the strong test to base 2 and the
 * strong Lucas test with Selfridge's parameters, which no known composite passes. Both are
 * deterministic: no random base is drawn.
 */
public final class Primes {

  /** The least composite that passes the strong test to every base from 2 to 41. */
  public static final BigInteger DETERMINISTIC_BOUND = new BigInteger("3317044064679887385961981");

  /** The bound of the table of small primes, which is also the trial-division bound. */
  public static final int SMALL_BOUND = 10_000;

  /** The most integers {@link #between} scans. */
  public static final int MAX_WINDOW = 1_000_000;

  private static final int[] BASES = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41};

  private static final int[] SMALL = sieve(SMALL_BOUND);

  private Primes() {}

  /**
   * The primes up to {@code bound}, by the sieve of Eratosthenes.
   *
   * @param bound the bound, at least 2
   * @return the primes, ascending
   */
  static int[] sieve(int bound) {
    boolean[] composite = new boolean[bound + 1];
    int count = 0;
    for (int i = 2; i <= bound; i++) {
      if (!composite[i]) {
        count++;
        for (long j = (long) i * i; j <= bound; j += i) {
          composite[(int) j] = true;
        }
      }
    }
    int[] out = new int[count];
    int k = 0;
    for (int i = 2; i <= bound; i++) {
      if (!composite[i]) {
        out[k++] = i;
      }
    }
    return out;
  }

  /**
   * The primes up to {@link #SMALL_BOUND}, the table itself: callers do not write to it.
   *
   * @return the table
   */
  static int[] small() {
    return SMALL;
  }

  /**
   * Whether {@code n} is prime; negative numbers, 0 and 1 are not.
   *
   * @param n the integer
   * @return true if prime (a proof below {@link #DETERMINISTIC_BOUND}, Baillie–PSW above)
   */
  public static boolean isPrime(BigInteger n) {
    if (n.compareTo(BigInteger.TWO) < 0) {
      return false;
    }
    if (n.bitLength() < 31 && n.intValue() <= SMALL_BOUND) {
      return Arrays.binarySearch(SMALL, n.intValue()) >= 0;
    }
    for (int p : SMALL) {
      if (p > 1000) {
        break;
      }
      if (n.mod(BigInteger.valueOf(p)).signum() == 0) {
        return false;
      }
    }
    if (n.compareTo(DETERMINISTIC_BOUND) < 0) {
      for (int a : BASES) {
        if (!strongProbablePrime(n, BigInteger.valueOf(a))) {
          return false;
        }
      }
      return true;
    }
    return strongProbablePrime(n, BigInteger.TWO) && strongLucas(n);
  }

  /**
   * Whether the answer of {@link #isPrime} for {@code n} is a proof rather than Baillie–PSW's.
   *
   * @param n the integer
   * @return true below {@link #DETERMINISTIC_BOUND}
   */
  public static boolean isProven(BigInteger n) {
    return n.compareTo(DETERMINISTIC_BOUND) < 0;
  }

  /** The strong probable-prime test of an odd {@code n > 2} to the base {@code a}. */
  static boolean strongProbablePrime(BigInteger n, BigInteger a) {
    BigInteger m = n.subtract(BigInteger.ONE);
    int s = m.getLowestSetBit();
    BigInteger d = m.shiftRight(s);
    BigInteger x = a.modPow(d, n);
    if (x.equals(BigInteger.ONE) || x.equals(m)) {
      return true;
    }
    for (int r = 1; r < s; r++) {
      x = x.multiply(x).mod(n);
      if (x.equals(m)) {
        return true;
      }
      if (x.equals(BigInteger.ONE)) {
        return false;
      }
    }
    return false;
  }

  /**
   * The strong Lucas probable-prime test of an odd {@code n} that is not a perfect square, with
   * Selfridge's parameters: {@code D} the first of 5, −7, 9, −11, … with Jacobi symbol −1, {@code P
   * = 1}, {@code Q = (1 − D)/4}.
   */
  static boolean strongLucas(BigInteger n) {
    BigInteger root = n.sqrt();
    if (root.multiply(root).equals(n)) {
      return false;
    }
    long d = 5;
    while (true) {
      int j = jacobi(BigInteger.valueOf(d), n);
      if (j == -1) {
        break;
      }
      if (j == 0 && BigInteger.valueOf(Math.abs(d)).compareTo(n) != 0) {
        return false;
      }
      d = d > 0 ? -(d + 2) : -d + 2;
    }
    BigInteger bigD = BigInteger.valueOf(d);
    BigInteger q = BigInteger.valueOf((1 - d) / 4).mod(n);
    BigInteger m = n.add(BigInteger.ONE);
    int s = m.getLowestSetBit();
    BigInteger k = m.shiftRight(s);
    // U_1 = 1, V_1 = P = 1, Q^1
    BigInteger u = BigInteger.ONE;
    BigInteger v = BigInteger.ONE;
    BigInteger qk = q;
    for (int i = k.bitLength() - 2; i >= 0; i--) {
      u = u.multiply(v).mod(n);
      v = v.multiply(v).subtract(qk.shiftLeft(1)).mod(n);
      qk = qk.multiply(qk).mod(n);
      if (k.testBit(i)) {
        BigInteger u2 = half(u.add(v), n);
        BigInteger v2 = half(bigD.multiply(u).add(v), n);
        u = u2;
        v = v2;
        qk = qk.multiply(q).mod(n);
      }
    }
    if (u.signum() == 0 || v.signum() == 0) {
      return true;
    }
    for (int r = 1; r < s; r++) {
      v = v.multiply(v).subtract(qk.shiftLeft(1)).mod(n);
      if (v.signum() == 0) {
        return true;
      }
      qk = qk.multiply(qk).mod(n);
    }
    return false;
  }

  /** {@code x/2} modulo the odd {@code n}. */
  private static BigInteger half(BigInteger x, BigInteger n) {
    BigInteger y = x.mod(n);
    return (y.testBit(0) ? y.add(n) : y).shiftRight(1);
  }

  /**
   * The Jacobi symbol {@code (a/n)} for an odd positive {@code n}.
   *
   * @param a any integer
   * @param n an odd positive integer
   * @return −1, 0 or 1
   */
  static int jacobi(BigInteger a, BigInteger n) {
    BigInteger x = a.mod(n);
    BigInteger y = n;
    int result = 1;
    while (x.signum() != 0) {
      int twos = x.getLowestSetBit();
      x = x.shiftRight(twos);
      int r = y.intValue() & 7;
      if ((twos & 1) == 1 && (r == 3 || r == 5)) {
        result = -result;
      }
      if ((x.intValue() & 3) == 3 && (y.intValue() & 3) == 3) {
        result = -result;
      }
      BigInteger t = y.mod(x);
      y = x;
      x = t;
    }
    return y.equals(BigInteger.ONE) ? result : 0;
  }

  /**
   * The least prime greater than {@code n}: 2 for every {@code n < 2}.
   *
   * @param n the integer
   * @param cancellation the request to stop
   * @return the prime
   */
  public static BigInteger next(BigInteger n, Cancellation cancellation) {
    if (n.compareTo(BigInteger.TWO) < 0) {
      return BigInteger.TWO;
    }
    BigInteger c = n.add(BigInteger.ONE);
    if (c.equals(BigInteger.valueOf(3))) {
      return c;
    }
    if (!c.testBit(0)) {
      c = c.add(BigInteger.ONE);
    }
    while (!isPrime(c)) {
      cancellation.check();
      c = c.add(BigInteger.TWO);
    }
    return c;
  }

  /**
   * The greatest prime less than {@code n}, none for {@code n ≤ 2}.
   *
   * @param n the integer
   * @param cancellation the request to stop
   * @return the prime, or empty
   */
  public static Optional<BigInteger> previous(BigInteger n, Cancellation cancellation) {
    if (n.compareTo(BigInteger.TWO) <= 0) {
      return Optional.empty();
    }
    if (n.equals(BigInteger.valueOf(3))) {
      return Optional.of(BigInteger.TWO);
    }
    BigInteger c = n.subtract(BigInteger.ONE);
    if (!c.testBit(0)) {
      c = c.subtract(BigInteger.ONE);
    }
    while (!isPrime(c)) {
      cancellation.check();
      c = c.subtract(BigInteger.TWO);
    }
    return Optional.of(c);
  }

  /**
   * The primes in {@code [a, b]}, ascending, by a sieve of the window with the primes up to {@code
   * √b}, or by the test of each odd number when {@code √b} is past the small table's square.
   *
   * @param a the lower end
   * @param b the upper end
   * @param cancellation the request to stop
   * @return the primes
   * @throws IllegalArgumentException if the window holds more than {@link #MAX_WINDOW} integers
   */
  public static List<BigInteger> between(BigInteger a, BigInteger b, Cancellation cancellation) {
    BigInteger lo = a.max(BigInteger.TWO);
    List<BigInteger> out = new ArrayList<>();
    if (lo.compareTo(b) > 0) {
      return out;
    }
    if (b.subtract(lo).compareTo(BigInteger.valueOf(MAX_WINDOW)) >= 0) {
      throw new IllegalArgumentException(
          "`primes` takes an interval of at most " + MAX_WINDOW + " integers");
    }
    int size = b.subtract(lo).intValueExact() + 1;
    BigInteger root = b.sqrt();
    if (root.compareTo(BigInteger.valueOf(10_000_000)) > 0) {
      for (int i = 0; i < size; i++) {
        if ((i & 255) == 0) {
          cancellation.check();
        }
        BigInteger c = lo.add(BigInteger.valueOf(i));
        if (isPrime(c)) {
          out.add(c);
        }
      }
      return out;
    }
    int[] sieving = root.intValue() <= SMALL_BOUND ? SMALL : sieve(Math.max(2, root.intValue()));
    boolean[] composite = new boolean[size];
    for (int p : sieving) {
      if ((long) p * p > b.longValue()) {
        break;
      }
      cancellation.check();
      BigInteger bp = BigInteger.valueOf(p);
      long first = lo.add(bp).subtract(BigInteger.ONE).divide(bp).longValue() * p;
      long start = Math.max((long) p * p, first);
      for (long j = start - lo.longValue(); j < size; j += p) {
        composite[(int) j] = true;
      }
    }
    for (int i = 0; i < size; i++) {
      if (!composite[i]) {
        out.add(lo.add(BigInteger.valueOf(i)));
      }
    }
    return out;
  }
}
