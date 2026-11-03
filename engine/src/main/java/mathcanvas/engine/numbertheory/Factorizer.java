package mathcanvas.engine.numbertheory;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import mathcanvas.engine.Cancellation;

/**
 * Integer factorisation ({@code docs/reference/engine.md}, <b>Number theory</b>): trial division by
 * the primes up to {@link Primes#SMALL_BOUND}, then for each cofactor left the primality test of
 * {@link Primes}, a perfect-power test, Pollard's rho in Brent's variant within an iteration cap
 * (on two 64-bit limbs in Montgomery form below 2¹²⁶, on {@code BigInteger} above), and then
 * Lenstra's elliptic-curve method on Montgomery curves with Suyama's parametrisation, σ = 6, 7, 8,
 * …, in two stages of B1 ({@link #ECM_STAGES}) each with a prime continuation to 50·B1. A factor
 * found is split again until every part is prime. Every method is deterministic, and the work of
 * the elliptic curves, counted in modular multiplications, is capped for the whole call: a cofactor
 * not split within the caps is returned unfinished.
 */
public final class Factorizer {

  /** The elliptic-curve stages: B1 and the number of curves at it. */
  static final long[][] ECM_STAGES = {{2_000, 25}, {11_000, 90}};

  /** The prime continuation of each curve goes to this multiple of B1. */
  static final int B2_FACTOR = 50;

  /** The modular multiplications the elliptic curves of one call may do. */
  public static final long ECM_WORK = 6_000_000;

  private static final int D = 210;

  private static final boolean[] PRIME_TO_B2 = primeTable(ECM_STAGES[1][0] * B2_FACTOR);

  private static final BigInteger[] STAGE_ONE = {
    stageOne((int) ECM_STAGES[0][0]), stageOne((int) ECM_STAGES[1][0])
  };

  private static final BigInteger LIMIT_128 = BigInteger.ONE.shiftLeft(126);

  private Factorizer() {}

  /**
   * How hard to try.
   *
   * @param rhoIterations the iterations of rho per cofactor
   * @param ecmWork the modular multiplications the elliptic curves may do in the whole call; 0 for
   *     none
   */
  public record Budget(long rhoIterations, long ecmWork) {

    /** The budget of {@code ifactor} and the divisor functions. */
    public static final Budget FULL = new Budget(1L << 20, ECM_WORK);

    /** The budget of the polynomial algebra's divisor searches: a short rho, no curves. */
    public static final Budget SHORT = new Budget(20_000, 0);
  }

  /**
   * How a factor was found, for the narration.
   *
   * @param method {@code trial}, {@code power}, {@code rho} or {@code ecm}
   * @param factor the factor found
   * @param of the number it was found in
   */
  public record Event(String method, BigInteger factor, BigInteger of) {}

  /**
   * A factorisation {@code sign · Π pᵉ · Π cᶠ}, the {@code c} the composite cofactors not split.
   *
   * @param sign −1 or 1
   * @param primes the primes found and their exponents
   * @param unfinished the composite cofactors left and their exponents; empty when complete
   * @param events how each factor was found, in order
   */
  public record Factorization(
      int sign,
      SortedMap<BigInteger, Integer> primes,
      SortedMap<BigInteger, Integer> unfinished,
      List<Event> events) {

    /** Copies the maps and the list. */
    public Factorization {
      primes = Collections.unmodifiableSortedMap(new TreeMap<>(primes));
      unfinished = Collections.unmodifiableSortedMap(new TreeMap<>(unfinished));
      events = List.copyOf(events);
    }

    /**
     * Whether every factor is prime.
     *
     * @return true when nothing is unfinished
     */
    public boolean complete() {
      return unfinished.isEmpty();
    }
  }

  /** The curves' work cap was reached. */
  private static final class Exhausted extends RuntimeException {
    private static final long serialVersionUID = 1L;

    Exhausted() {
      super(null, null, false, false);
    }
  }

  /**
   * Factors a non-zero integer.
   *
   * @param n the integer, not 0
   * @param budget how hard to try
   * @param cancellation the request to stop
   * @return the factorisation
   * @throws IllegalArgumentException if {@code n} is 0
   */
  public static Factorization factor(BigInteger n, Budget budget, Cancellation cancellation) {
    if (n.signum() == 0) {
      throw new IllegalArgumentException("0 has no factorisation");
    }
    return new Run(budget, cancellation).factor(n);
  }

  /**
   * The positive divisors of {@code |n|} in ascending order, from a factorisation within {@link
   * Budget#SHORT}.
   *
   * @param n a non-zero integer
   * @param cap the most divisors wanted
   * @param cancellation the request to stop
   * @return the divisors, or empty when {@code |n|} is not factored within the budget or has more
   *     than {@code cap} divisors
   */
  public static Optional<List<BigInteger>> divisors(
      BigInteger n, int cap, Cancellation cancellation) {
    Factorization f = factor(n, Budget.SHORT, cancellation);
    if (!f.complete()) {
      return Optional.empty();
    }
    return divisors(f.primes(), cap);
  }

  /**
   * The positive divisors of {@code Π pᵉ}, ascending.
   *
   * @param primes the primes and their exponents
   * @param cap the most divisors wanted
   * @return the divisors, or empty when there are more than {@code cap}
   */
  public static Optional<List<BigInteger>> divisors(
      SortedMap<BigInteger, Integer> primes, long cap) {
    long count = 1;
    for (int e : primes.values()) {
      count *= e + 1;
      if (count > cap) {
        return Optional.empty();
      }
    }
    List<BigInteger> out = new ArrayList<>();
    out.add(BigInteger.ONE);
    for (var entry : primes.entrySet()) {
      int size = out.size();
      BigInteger power = BigInteger.ONE;
      for (int e = 1; e <= entry.getValue(); e++) {
        power = power.multiply(entry.getKey());
        for (int j = 0; j < size; j++) {
          out.add(out.get(j).multiply(power));
        }
      }
    }
    out.sort(null);
    return Optional.of(out);
  }

  /** One factorisation in progress. */
  private static final class Run {
    private final Budget budget;
    private final Cancellation cancellation;
    private final TreeMap<BigInteger, Integer> primes = new TreeMap<>();
    private final TreeMap<BigInteger, Integer> unfinished = new TreeMap<>();
    private final List<Event> events = new ArrayList<>();
    private long ecmWork;

    Run(Budget budget, Cancellation cancellation) {
      this.budget = budget;
      this.cancellation = cancellation;
    }

    Factorization factor(BigInteger n) {
      BigInteger m = n.abs();
      for (int p : Primes.small()) {
        BigInteger bp = BigInteger.valueOf(p);
        if (bp.multiply(bp).compareTo(m) > 0) {
          break;
        }
        int e = 0;
        while (true) {
          BigInteger[] qr = m.divideAndRemainder(bp);
          if (qr[1].signum() != 0) {
            break;
          }
          m = qr[0];
          e++;
        }
        if (e > 0) {
          primes.merge(bp, e, Integer::sum);
          events.add(new Event("trial", bp, n.abs()));
        }
      }
      Deque<BigInteger[]> work = new ArrayDeque<>();
      if (m.compareTo(BigInteger.ONE) > 0) {
        work.push(new BigInteger[] {m, BigInteger.ONE});
      }
      while (!work.isEmpty()) {
        cancellation.check();
        BigInteger[] item = work.pop();
        BigInteger c = item[0];
        int k = item[1].intValueExact();
        if (c.bitLength() <= 31 && c.intValue() <= Primes.SMALL_BOUND * Primes.SMALL_BOUND
            || Primes.isPrime(c)) {
          // no factor below the trial bound is left, so a cofactor below its square is prime
          primes.merge(c, k, Integer::sum);
          continue;
        }
        BigInteger[] power = perfectPower(c);
        if (power != null) {
          events.add(new Event("power", power[0], c));
          work.push(
              new BigInteger[] {power[0], BigInteger.valueOf((long) k * power[1].intValue())});
          continue;
        }
        BigInteger d = split(c);
        if (d == null) {
          unfinished.merge(c, k, Integer::sum);
          continue;
        }
        BigInteger e = c.divide(d);
        work.push(new BigInteger[] {d, BigInteger.valueOf(k)});
        work.push(new BigInteger[] {e, BigInteger.valueOf(k)});
      }
      return new Factorization(n.signum(), primes, unfinished, events);
    }

    /** A proper factor of the odd composite {@code c}, or null within the budget. */
    private BigInteger split(BigInteger c) {
      BigInteger d =
          c.compareTo(LIMIT_128) < 0
              ? new Rho128(c, cancellation).run(budget.rhoIterations())
              : rhoBig(c, budget.rhoIterations());
      if (d != null) {
        events.add(new Event("rho", d.min(c.divide(d)), c));
        return d;
      }
      if (budget.ecmWork() == 0 || ecmWork >= budget.ecmWork()) {
        return null;
      }
      Ecm ecm = new Ecm(c, budget.ecmWork() - ecmWork, cancellation);
      try {
        d = ecm.run();
      } catch (Exhausted e) {
        d = null;
      }
      ecmWork += ecm.work;
      if (d != null) {
        events.add(new Event("ecm", d.min(c.divide(d)), c));
      }
      return d;
    }

    /** Brent's rho on {@code BigInteger}, the polynomials {@code x² + 1}, {@code x² + 3}, …. */
    private BigInteger rhoBig(BigInteger n, long cap) {
      long used = 0;
      for (long c0 = 1; used < cap; c0 += 2) {
        BigInteger c = BigInteger.valueOf(c0);
        BigInteger y = BigInteger.TWO;
        BigInteger x = y;
        BigInteger ys = y;
        BigInteger q = BigInteger.ONE;
        BigInteger g = BigInteger.ONE;
        long r = 1;
        final long m = 128;
        while (g.equals(BigInteger.ONE) && used < cap) {
          x = y;
          for (long i = 0; i < r; i++) {
            y = y.multiply(y).add(c).mod(n);
          }
          long k = 0;
          while (k < r && g.equals(BigInteger.ONE) && used < cap) {
            cancellation.check();
            ys = y;
            long lim = Math.min(m, r - k);
            for (long i = 0; i < lim; i++) {
              y = y.multiply(y).add(c).mod(n);
              q = q.multiply(x.subtract(y)).mod(n);
            }
            used += lim;
            g = q.gcd(n);
            k += m;
          }
          r *= 2;
        }
        if (g.equals(n)) {
          do {
            ys = ys.multiply(ys).add(c).mod(n);
            g = x.subtract(ys).gcd(n);
          } while (g.equals(BigInteger.ONE));
        }
        if (g.compareTo(BigInteger.ONE) > 0 && g.compareTo(n) < 0) {
          return g;
        }
      }
      return null;
    }
  }

  /**
   * {@code [r, k]} with {@code r^k = c} and {@code k ≥ 2} largest, or null when {@code c} is not a
   * perfect power; {@code c} has no prime factor below the trial bound.
   */
  static BigInteger[] perfectPower(BigInteger c) {
    int maxK = (int) (c.bitLength() / 13.28) + 1;
    for (int k = maxK; k >= 2; k--) {
      BigInteger r = root(c, k);
      if (r.pow(k).equals(c)) {
        return new BigInteger[] {r, BigInteger.valueOf(k)};
      }
    }
    return null;
  }

  /** {@code ⌊c^(1/k)⌋} by Newton's method from above. */
  static BigInteger root(BigInteger c, int k) {
    if (k == 2) {
      return c.sqrt();
    }
    BigInteger bk = BigInteger.valueOf(k);
    BigInteger x = BigInteger.ONE.shiftLeft(c.bitLength() / k + 1);
    while (true) {
      BigInteger y = x.multiply(BigInteger.valueOf(k - 1L)).add(c.divide(x.pow(k - 1))).divide(bk);
      if (y.compareTo(x) >= 0) {
        return x;
      }
      x = y;
    }
  }

  /** Brent's rho on an odd {@code n < 2¹²⁶} in Montgomery form on two 64-bit limbs. */
  private static final class Rho128 {
    private final BigInteger big;
    private final long n0;
    private final long n1;
    private final long ninv;
    private final Cancellation cancellation;
    private long hi;
    private long r0;
    private long r1;

    Rho128(BigInteger n, Cancellation cancellation) {
      this.big = n;
      this.n0 = n.longValue();
      this.n1 = n.shiftRight(64).longValue();
      long inv = n0;
      for (int i = 0; i < 5; i++) {
        inv *= 2 - n0 * inv;
      }
      this.ninv = -inv;
      this.cancellation = cancellation;
    }

    /** {@code x + y·z + c}, the low limb returned and the high in {@link #hi}. */
    private long mac(long x, long y, long z, long c) {
      long lo = y * z;
      long h = Math.unsignedMultiplyHigh(y, z);
      long s = lo + x;
      long c1 = Long.compareUnsigned(s, lo) < 0 ? 1 : 0;
      long s2 = s + c;
      long c2 = Long.compareUnsigned(s2, s) < 0 ? 1 : 0;
      hi = h + c1 + c2;
      return s2;
    }

    private long add(long x, long c) {
      long s = x + c;
      hi = Long.compareUnsigned(s, x) < 0 ? 1 : 0;
      return s;
    }

    /** {@code a·b·2⁻¹²⁸ mod n} into {@link #r0}, {@link #r1}, for {@code a, b < n}. */
    private void mul(long a0, long a1, long b0, long b1) {
      long t0;
      long t1;
      long t2;
      long t3;
      long c;
      t0 = mac(0, a0, b0, 0);
      c = hi;
      t1 = mac(0, a1, b0, c);
      t2 = hi;
      long m = t0 * ninv;
      mac(t0, m, n0, 0);
      c = hi;
      t0 = mac(t1, m, n1, c);
      c = hi;
      t1 = add(t2, c);
      t2 = hi;
      t0 = mac(t0, a0, b1, 0);
      c = hi;
      t1 = mac(t1, a1, b1, c);
      c = hi;
      t2 = add(t2, c);
      t3 = hi;
      m = t0 * ninv;
      mac(t0, m, n0, 0);
      c = hi;
      t0 = mac(t1, m, n1, c);
      c = hi;
      t1 = add(t2, c);
      c = hi;
      t2 = t3 + c;
      if (t2 != 0 || geq(t0, t1)) {
        long b = Long.compareUnsigned(t0, n0) < 0 ? 1 : 0;
        t0 -= n0;
        t1 = t1 - n1 - b;
      }
      r0 = t0;
      r1 = t1;
    }

    private boolean geq(long a0, long a1) {
      int h = Long.compareUnsigned(a1, n1);
      return h > 0 || (h == 0 && Long.compareUnsigned(a0, n0) >= 0);
    }

    /** {@code (a − b) mod n} into {@link #r0}, {@link #r1}, for {@code a, b < n}. */
    private void sub(long a0, long a1, long b0, long b1) {
      long borrow = Long.compareUnsigned(a0, b0) < 0 ? 1 : 0;
      long d0 = a0 - b0;
      long d1 = a1 - b1 - borrow;
      int h = Long.compareUnsigned(a1, b1);
      if (h < 0 || (h == 0 && Long.compareUnsigned(a0, b0) < 0)) {
        long s0 = d0 + n0;
        long carry = Long.compareUnsigned(s0, d0) < 0 ? 1 : 0;
        d1 = d1 + n1 + carry;
        d0 = s0;
      }
      r0 = d0;
      r1 = d1;
    }

    /** {@code y² + c} in Montgomery form, reduced below n. */
    private void step(long y0, long y1, long c) {
      mul(y0, y1, y0, y1);
      long s0 = r0 + c;
      long s1 = r1 + (Long.compareUnsigned(s0, r0) < 0 ? 1 : 0);
      if (geq(s0, s1)) {
        long b = Long.compareUnsigned(s0, n0) < 0 ? 1 : 0;
        s0 -= n0;
        s1 = s1 - n1 - b;
      }
      r0 = s0;
      r1 = s1;
    }

    private static BigInteger big(long a0, long a1) {
      byte[] bytes = new byte[17];
      for (int i = 0; i < 8; i++) {
        bytes[16 - i] = (byte) (a0 >>> (8 * i));
        bytes[8 - i] = (byte) (a1 >>> (8 * i));
      }
      return new BigInteger(bytes);
    }

    BigInteger run(long cap) {
      long used = 0;
      for (long c = 1; used < cap; c += 2) {
        long y0 = 2;
        long y1 = 0;
        long x0 = y0;
        long x1 = y1;
        long ys0 = y0;
        long ys1 = y1;
        long q0 = 1;
        long q1 = 0;
        BigInteger g = BigInteger.ONE;
        long r = 1;
        final long m = 256;
        while (g.equals(BigInteger.ONE) && used < cap) {
          x0 = y0;
          x1 = y1;
          for (long i = 0; i < r; i++) {
            step(y0, y1, c);
            y0 = r0;
            y1 = r1;
          }
          long k = 0;
          while (k < r && g.equals(BigInteger.ONE) && used < cap) {
            cancellation.check();
            ys0 = y0;
            ys1 = y1;
            long lim = Math.min(m, r - k);
            for (long i = 0; i < lim; i++) {
              step(y0, y1, c);
              y0 = r0;
              y1 = r1;
              sub(x0, x1, y0, y1);
              mul(q0, q1, r0, r1);
              q0 = r0;
              q1 = r1;
            }
            used += lim;
            g = big(q0, q1).gcd(big);
            k += m;
          }
          r *= 2;
        }
        if (g.equals(big)) {
          do {
            step(ys0, ys1, c);
            ys0 = r0;
            ys1 = r1;
            sub(x0, x1, ys0, ys1);
            g = big(r0, r1).gcd(big);
          } while (g.equals(BigInteger.ONE));
        }
        if (g.compareTo(BigInteger.ONE) > 0 && g.compareTo(big) < 0) {
          return g;
        }
      }
      return null;
    }
  }

  /** Lenstra's elliptic-curve method on Montgomery curves, x and z only. */
  private static final class Ecm {
    private final BigInteger n;
    private final long cap;
    private final Cancellation cancellation;
    private BigInteger a24;
    long work;

    Ecm(BigInteger n, long cap, Cancellation cancellation) {
      this.n = n;
      this.cap = cap;
      this.cancellation = cancellation;
    }

    private BigInteger mul(BigInteger a, BigInteger b) {
      if (++work > cap) {
        throw new Exhausted();
      }
      return a.multiply(b).mod(n);
    }

    private BigInteger[] dbl(BigInteger[] p) {
      BigInteger s = p[0].add(p[1]);
      BigInteger d = p[0].subtract(p[1]);
      BigInteger t1 = mul(s, s);
      BigInteger t2 = mul(d, d);
      BigInteger t3 = t1.subtract(t2);
      return new BigInteger[] {mul(t1, t2), mul(t3, t2.add(mul(a24, t3)))};
    }

    /** {@code p + q} given {@code p − q}. */
    private BigInteger[] add(BigInteger[] p, BigInteger[] q, BigInteger[] diff) {
      BigInteger u = mul(p[0].subtract(p[1]), q[0].add(q[1]));
      BigInteger v = mul(p[0].add(p[1]), q[0].subtract(q[1]));
      BigInteger s = u.add(v);
      BigInteger d = u.subtract(v);
      return new BigInteger[] {mul(diff[1], mul(s, s)), mul(diff[0], mul(d, d))};
    }

    private BigInteger[] ladder(BigInteger k, BigInteger[] p) {
      BigInteger[] r0 = p;
      BigInteger[] r1 = dbl(p);
      for (int i = k.bitLength() - 2; i >= 0; i--) {
        if ((i & 63) == 0) {
          cancellation.check();
        }
        if (k.testBit(i)) {
          r0 = add(r1, r0, p);
          r1 = dbl(r1);
        } else {
          r1 = add(r0, r1, p);
          r0 = dbl(r0);
        }
      }
      return r0;
    }

    BigInteger run() {
      long sigma = 6;
      for (int stage = 0; stage < ECM_STAGES.length; stage++) {
        long b1 = ECM_STAGES[stage][0];
        for (long curve = 0; curve < ECM_STAGES[stage][1]; curve++, sigma++) {
          BigInteger d = curve(BigInteger.valueOf(sigma), stage, b1);
          if (d != null) {
            return d;
          }
        }
      }
      return null;
    }

    /** One curve: a proper factor, or null. */
    private BigInteger curve(BigInteger sigma, int stage, long b1) {
      BigInteger u = sigma.multiply(sigma).subtract(BigInteger.valueOf(5)).mod(n);
      BigInteger v = sigma.shiftLeft(2).mod(n);
      BigInteger u3 = mul(mul(u, u), u);
      BigInteger vmu = v.subtract(u);
      BigInteger num = mul(mul(mul(vmu, vmu), vmu), u.multiply(BigInteger.valueOf(3)).add(v));
      BigInteger den = mul(u3, v).shiftLeft(4).mod(n);
      BigInteger g = den.gcd(n);
      if (!g.equals(BigInteger.ONE)) {
        return g.compareTo(n) < 0 ? g : null;
      }
      a24 = mul(num, den.modInverse(n));
      BigInteger[] q = ladder(STAGE_ONE[stage], new BigInteger[] {u3, mul(mul(v, v), v)});
      g = q[1].gcd(n);
      if (g.compareTo(BigInteger.ONE) > 0) {
        return g.compareTo(n) < 0 ? g : null;
      }
      return stageTwo(q, b1, b1 * B2_FACTOR);
    }

    /** The prime continuation over {@code (b1, b2]} with steps of {@link #D}. */
    private BigInteger stageTwo(BigInteger[] q, long b1, long b2) {
      BigInteger[][] s = new BigInteger[D / 2 + 1][];
      s[1] = q;
      BigInteger[] two = dbl(q);
      s[3] = add(two, q, q);
      for (int j = 5; j <= D / 2; j += 2) {
        s[j] = add(s[j - 2], two, s[j - 4]);
      }
      long m = b1 / D;
      BigInteger[] r = ladder(BigInteger.valueOf(m * D), q);
      BigInteger[] previous = ladder(BigInteger.valueOf((m - 1) * D), q);
      BigInteger[] step = ladder(BigInteger.valueOf(D), q);
      BigInteger acc = BigInteger.ONE;
      for (; (m - 1) * D <= b2; m++) {
        cancellation.check();
        for (int j = 1; j <= D / 2; j += 2) {
          if (j % 3 == 0 || j % 5 == 0 || j % 7 == 0) {
            continue;
          }
          long lo = m * D - j;
          long hi = m * D + j;
          if (prime(lo, b1, b2) || prime(hi, b1, b2)) {
            acc = mul(acc, mul(r[0], s[j][1]).subtract(mul(s[j][0], r[1])));
          }
        }
        BigInteger[] next = add(r, step, previous);
        previous = r;
        r = next;
      }
      BigInteger g = acc.gcd(n);
      return g.compareTo(BigInteger.ONE) > 0 && g.compareTo(n) < 0 ? g : null;
    }

    private static boolean prime(long p, long b1, long b2) {
      return p > b1 && p <= b2 && PRIME_TO_B2[(int) p];
    }
  }

  private static boolean[] primeTable(long bound) {
    boolean[] table = new boolean[(int) bound + 1];
    for (int p : Primes.sieve((int) bound)) {
      table[p] = true;
    }
    return table;
  }

  /** The product of the greatest powers of the primes up to {@code b1} not above it. */
  private static BigInteger stageOne(int b1) {
    BigInteger k = BigInteger.ONE;
    for (int p : Primes.sieve(b1)) {
      long q = p;
      while (q * p <= b1) {
        q *= p;
      }
      k = k.multiply(BigInteger.valueOf(q));
    }
    return k;
  }
}
