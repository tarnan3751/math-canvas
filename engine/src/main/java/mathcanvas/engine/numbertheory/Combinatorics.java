package mathcanvas.engine.numbertheory;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Canonical;

/**
 * The counting functions of {@code docs/language.md} §14.23 on integers: each gives its value, or
 * nothing when the value would pass the canonical form's folding bound ({@link
 * Canonical#MAX_FOLD_BITS}) or the function's own cap, where the call stays. Counts of no structure
 * are 0: a negative argument, {@code k > n}, parts that do not add up.
 */
final class Combinatorics {

  /** The functions of this class. */
  static final Set<String> FUNCTIONS =
      Set.of(
          "permutations",
          "multinomial",
          "fibonacci",
          "lucas",
          "catalan",
          "bell",
          "stirling2",
          "partitions");

  /** The largest {@code n} of {@code bell} and {@code stirling2}. */
  static final int MAX_BELL = 2_000;

  /** The largest {@code n} of {@code partitions}. */
  static final int MAX_PARTITIONS = 10_000;

  /** The largest {@code |n|} of {@code fibonacci} and {@code lucas}: about 2²⁰ bits. */
  static final int MAX_FIBONACCI = 1_500_000;

  private Combinatorics() {}

  private static boolean small(BigInteger n, long cap) {
    return n.abs().compareTo(BigInteger.valueOf(cap)) <= 0;
  }

  /** {@code n(n − 1)…(n − k + 1)}, 0 for {@code k < 0} and for {@code 0 ≤ n < k}. */
  static Optional<BigInteger> permutations(BigInteger n, BigInteger k, Cancellation c) {
    if (k.signum() < 0 || (n.signum() >= 0 && k.compareTo(n) > 0)) {
      return Optional.of(BigInteger.ZERO);
    }
    long bits = n.abs().add(k).bitLength();
    if (k.bitLength() > 31 || k.longValue() * bits > Canonical.MAX_FOLD_BITS) {
      return Optional.empty();
    }
    return Optional.of(falling(n, k.intValue(), c));
  }

  private static BigInteger falling(BigInteger n, int k, Cancellation c) {
    BigInteger p = BigInteger.ONE;
    for (int i = 0; i < k; i++) {
      if ((i & 1023) == 0) {
        c.check();
      }
      p = p.multiply(n.subtract(BigInteger.valueOf(i)));
    }
    return p;
  }

  /** {@code n!/(k₁!…kₘ!)} when the parts add up to {@code n}, else 0. */
  static Optional<BigInteger> multinomial(BigInteger n, List<BigInteger> ks, Cancellation c) {
    BigInteger total = BigInteger.ZERO;
    for (BigInteger k : ks) {
      if (k.signum() < 0) {
        return Optional.of(BigInteger.ZERO);
      }
      total = total.add(k);
    }
    if (!total.equals(n)) {
      return Optional.of(BigInteger.ZERO);
    }
    if (n.bitLength() > 31 || n.longValue() * n.bitLength() > Canonical.MAX_FOLD_BITS) {
      return Optional.empty();
    }
    // a product of binomials: C(k₁, k₁) C(k₁ + k₂, k₂) …
    BigInteger result = BigInteger.ONE;
    BigInteger running = BigInteger.ZERO;
    for (BigInteger k : ks) {
      c.check();
      running = running.add(k);
      result = result.multiply(binomial(running.intValueExact(), k.intValueExact()));
    }
    return Optional.of(result);
  }

  private static BigInteger binomial(int n, int k) {
    return mathcanvas.engine.number.Int.of(n).binomial(mathcanvas.engine.number.Int.of(k)).value();
  }

  /** {@code F(n)} by fast doubling; {@code F(−n) = (−1)^(n+1) F(n)}. */
  static Optional<BigInteger> fibonacci(BigInteger n, Cancellation c) {
    if (!small(n, MAX_FIBONACCI)) {
      return Optional.empty();
    }
    BigInteger f = fib(n.abs().intValue(), c)[0];
    return Optional.of(n.signum() < 0 && !n.testBit(0) ? f.negate() : f);
  }

  /** {@code L(n) = F(n − 1) + F(n + 1)}; {@code L(−n) = (−1)^n L(n)}. */
  static Optional<BigInteger> lucas(BigInteger n, Cancellation c) {
    if (!small(n, MAX_FIBONACCI)) {
      return Optional.empty();
    }
    BigInteger[] f = fib(n.abs().intValue(), c);
    // L(m) = 2F(m + 1) − F(m)
    BigInteger l = f[1].shiftLeft(1).subtract(f[0]);
    return Optional.of(n.signum() < 0 && n.testBit(0) ? l.negate() : l);
  }

  /** {@code [F(m), F(m + 1)]}. */
  private static BigInteger[] fib(int m, Cancellation c) {
    BigInteger a = BigInteger.ZERO;
    BigInteger b = BigInteger.ONE;
    for (int i = 31 - Integer.numberOfLeadingZeros(Math.max(m, 1)); i >= 0; i--) {
      c.check();
      // F(2j) = F(j)(2F(j + 1) − F(j)), F(2j + 1) = F(j)² + F(j + 1)²
      BigInteger d = a.multiply(b.shiftLeft(1).subtract(a));
      BigInteger e = a.multiply(a).add(b.multiply(b));
      if (((m >> i) & 1) == 0) {
        a = d;
        b = e;
      } else {
        a = e;
        b = d.add(e);
      }
    }
    return new BigInteger[] {a, b};
  }

  /** {@code C(2n, n)/(n + 1)}; 0 for {@code n < 0}. */
  static Optional<BigInteger> catalan(BigInteger n, Cancellation c) {
    if (n.signum() < 0) {
      return Optional.of(BigInteger.ZERO);
    }
    if (!small(n, Canonical.MAX_FOLD_BITS / 2)) {
      return Optional.empty();
    }
    int m = n.intValue();
    c.check();
    return Optional.of(binomial(2 * m, m).divide(BigInteger.valueOf(m + 1L)));
  }

  /** The Bell number by the Bell triangle; 0 for {@code n < 0}. */
  static Optional<BigInteger> bell(BigInteger n, Cancellation c) {
    if (n.signum() < 0) {
      return Optional.of(BigInteger.ZERO);
    }
    if (!small(n, MAX_BELL)) {
      return Optional.empty();
    }
    int m = n.intValue();
    BigInteger[] row = {BigInteger.ONE};
    for (int i = 1; i <= m; i++) {
      c.check();
      BigInteger[] next = new BigInteger[i + 1];
      next[0] = row[i - 1];
      for (int j = 1; j <= i; j++) {
        next[j] = next[j - 1].add(row[j - 1]);
      }
      row = next;
    }
    return Optional.of(row[0]);
  }

  /**
   * The Stirling number of the second kind, {@code (1/k!) Σ (−1)^j C(k, j) (k − j)^n}; 0 for a
   * negative argument and for {@code k > n}, and {@code S(0, 0) = 1}.
   */
  static Optional<BigInteger> stirling2(BigInteger n, BigInteger k, Cancellation c) {
    if (n.signum() < 0 || k.signum() < 0 || k.compareTo(n) > 0) {
      return Optional.of(BigInteger.ZERO);
    }
    if (!small(n, MAX_BELL)) {
      return Optional.empty();
    }
    int nn = n.intValue();
    int kk = k.intValue();
    if (nn == 0) {
      return Optional.of(BigInteger.ONE);
    }
    BigInteger sum = BigInteger.ZERO;
    for (int j = 0; j <= kk; j++) {
      c.check();
      BigInteger term = binomial(kk, j).multiply(BigInteger.valueOf(kk - j).pow(nn));
      sum = (j % 2 == 0) ? sum.add(term) : sum.subtract(term);
    }
    return Optional.of(sum.divide(mathcanvas.engine.number.Int.of(kk).factorial().value()));
  }

  /** The partition number by Euler's pentagonal recurrence; 0 for {@code n < 0}. */
  static Optional<BigInteger> partitions(BigInteger n, Cancellation c) {
    if (n.signum() < 0) {
      return Optional.of(BigInteger.ZERO);
    }
    if (!small(n, MAX_PARTITIONS)) {
      return Optional.empty();
    }
    int m = n.intValue();
    BigInteger[] p = new BigInteger[m + 1];
    p[0] = BigInteger.ONE;
    for (int i = 1; i <= m; i++) {
      if ((i & 63) == 0) {
        c.check();
      }
      BigInteger s = BigInteger.ZERO;
      for (int j = 1; ; j++) {
        int g1 = j * (3 * j - 1) / 2;
        if (g1 > i) {
          break;
        }
        int g2 = j * (3 * j + 1) / 2;
        BigInteger t = p[i - g1];
        if (g2 <= i) {
          t = t.add(p[i - g2]);
        }
        s = (j % 2 == 1) ? s.add(t) : s.subtract(t);
      }
      p[i] = s;
    }
    return Optional.of(p[m]);
  }
}
