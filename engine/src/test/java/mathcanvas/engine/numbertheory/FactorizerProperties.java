package mathcanvas.engine.numbertheory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.numbertheory.Factorizer.Budget;
import mathcanvas.engine.numbertheory.Factorizer.Factorization;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * Factorisations multiply back to their number with prime factors, and primality agrees with trial
 * division below 10⁶.
 */
class FactorizerProperties {

  private static final int[] BELOW_MILLION = Primes.sieve(1_000_000);

  /** A random integer of 1 to 40 digits, half of them products of primes of chosen sizes. */
  private static BigInteger number(Random random) {
    if (random.nextBoolean()) {
      int digits = 1 + random.nextInt(40);
      return new BigInteger(digits * 4, random).add(BigInteger.ONE);
    }
    BigInteger n = BigInteger.ONE;
    for (int k = 0; k < 1 + random.nextInt(4); k++) {
      n = n.multiply(BigInteger.probablePrime(4 + random.nextInt(40), random));
    }
    return n;
  }

  @Property(tries = 200, seed = "8101")
  void aFactorisationMultipliesBackWithPrimeFactors(@ForAll long seed) {
    BigInteger n = number(new Random(seed));
    if (new Random(seed).nextInt(3) == 0) {
      n = n.negate();
    }
    Factorization f = Factorizer.factor(n, Budget.FULL, Cancellation.NEVER);
    BigInteger product = BigInteger.valueOf(f.sign());
    for (Map.Entry<BigInteger, Integer> e : f.primes().entrySet()) {
      assertThat(Primes.isPrime(e.getKey())).as("%s in %s", e.getKey(), n).isTrue();
      product = product.multiply(e.getKey().pow(e.getValue()));
    }
    for (Map.Entry<BigInteger, Integer> e : f.unfinished().entrySet()) {
      assertThat(Primes.isPrime(e.getKey())).isFalse();
      product = product.multiply(e.getKey().pow(e.getValue()));
    }
    assertThat(product).isEqualTo(n);
    if (n.abs().bitLength() <= 100) {
      assertThat(f.complete()).as("%s", n).isTrue();
    }
  }

  @Property(tries = 1000, seed = "8102")
  void primalityAgreesWithTrialDivision(@ForAll @IntRange(min = -10, max = 1_000_000) int n) {
    boolean prime = java.util.Arrays.binarySearch(BELOW_MILLION, n) >= 0;
    assertThat(Primes.isPrime(BigInteger.valueOf(n))).isEqualTo(prime);
  }

  @Property(tries = 200, seed = "8103")
  void productsOfTwoPrimesAreComposite(@ForAll long seed) {
    Random random = new Random(seed);
    BigInteger p = BigInteger.probablePrime(2 + random.nextInt(60), random);
    BigInteger q = BigInteger.probablePrime(2 + random.nextInt(60), random);
    assertThat(Primes.isPrime(p)).isTrue();
    assertThat(Primes.isPrime(p.multiply(q))).isFalse();
  }
}
