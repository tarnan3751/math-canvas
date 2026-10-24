package mathcanvas.engine.algebra;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The positive divisors of an integer, for the rational-root search and Kronecker's method. The
 * integer is factored by trial division up to {@link #TRIAL_BOUND}; what is left is taken as prime
 * when it is below the bound's square or a probable prime, and otherwise the divisors are unknown.
 */
final class IntegerDivisors {

  /** The largest trial divisor. */
  static final int TRIAL_BOUND = 100_000;

  private static final BigInteger SQUARE_BOUND =
      BigInteger.valueOf(TRIAL_BOUND).multiply(BigInteger.valueOf(TRIAL_BOUND));
  private static final int CERTAINTY = 50;

  private IntegerDivisors() {}

  /**
   * Returns the positive divisors of {@code |n|} in ascending order.
   *
   * @param n a non-zero integer
   * @param cap the most divisors wanted
   * @return the divisors, or empty when {@code |n|} cannot be factored within the trial bound or
   *     has more than {@code cap} divisors
   */
  static Optional<List<BigInteger>> of(BigInteger n, int cap) {
    BigInteger m = n.abs();
    List<BigInteger> primes = new ArrayList<>();
    List<Integer> exponents = new ArrayList<>();
    for (long d = 2; d <= TRIAL_BOUND; d = d == 2 ? 3 : d + 2) {
      BigInteger bd = BigInteger.valueOf(d);
      if (bd.multiply(bd).compareTo(m) > 0) {
        break;
      }
      int e = 0;
      while (true) {
        BigInteger[] qr = m.divideAndRemainder(bd);
        if (qr[1].signum() != 0) {
          break;
        }
        m = qr[0];
        e++;
      }
      if (e > 0) {
        primes.add(bd);
        exponents.add(e);
      }
    }
    if (m.compareTo(BigInteger.ONE) > 0) {
      if (m.compareTo(SQUARE_BOUND) >= 0 && !m.isProbablePrime(CERTAINTY)) {
        return Optional.empty();
      }
      primes.add(m);
      exponents.add(1);
    }
    long count = 1;
    for (int e : exponents) {
      count *= e + 1;
      if (count > cap) {
        return Optional.empty();
      }
    }
    List<BigInteger> out = new ArrayList<>();
    out.add(BigInteger.ONE);
    for (int i = 0; i < primes.size(); i++) {
      int size = out.size();
      BigInteger power = BigInteger.ONE;
      for (int e = 1; e <= exponents.get(i); e++) {
        power = power.multiply(primes.get(i));
        for (int j = 0; j < size; j++) {
          out.add(out.get(j).multiply(power));
        }
      }
    }
    out.sort(null);
    return Optional.of(out);
  }
}
