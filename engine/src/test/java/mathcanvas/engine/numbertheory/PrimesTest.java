package mathcanvas.engine.numbertheory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.List;
import mathcanvas.engine.Cancellation;
import org.junit.jupiter.api.Test;

/** Primality, the primes either side of a number, and the primes in a window. */
class PrimesTest {

  private static BigInteger big(String s) {
    return new BigInteger(s);
  }

  @Test
  void smallNumbers() {
    assertThat(Primes.isPrime(BigInteger.valueOf(-7))).isFalse();
    assertThat(Primes.isPrime(BigInteger.ZERO)).isFalse();
    assertThat(Primes.isPrime(BigInteger.ONE)).isFalse();
    assertThat(Primes.isPrime(BigInteger.TWO)).isTrue();
    assertThat(Primes.isPrime(BigInteger.valueOf(97))).isTrue();
    assertThat(Primes.isPrime(BigInteger.valueOf(9_973))).isTrue();
    assertThat(Primes.isPrime(BigInteger.valueOf(10_007))).isTrue();
    assertThat(Primes.isPrime(BigInteger.valueOf(10_001))).isFalse();
  }

  @Test
  void mersenneAndLargePrimes() {
    assertThat(Primes.isPrime(BigInteger.TWO.pow(61).subtract(BigInteger.ONE))).isTrue();
    assertThat(Primes.isPrime(BigInteger.TWO.pow(67).subtract(BigInteger.ONE))).isFalse();
    assertThat(Primes.isPrime(BigInteger.TWO.pow(127).subtract(BigInteger.ONE))).isTrue();
    assertThat(Primes.isPrime(BigInteger.TWO.pow(521).subtract(BigInteger.ONE))).isTrue();
    assertThat(Primes.isPrime(BigInteger.TWO.pow(523).subtract(BigInteger.ONE))).isFalse();
  }

  @Test
  void carmichaelNumbersAndStrongPseudoprimesAreComposite() {
    for (String s :
        List.of(
            "561",
            "1105",
            "1729",
            "41041",
            "825265",
            "321197185",
            "2047",
            "1373653",
            "25326001",
            "3215031751",
            "2152302898747",
            "3474749660383",
            "341550071728321",
            "3825123056546413051",
            "318665857834031151167461",
            "3317044064679887385961981")) {
      assertThat(Primes.isPrime(big(s))).as(s).isFalse();
    }
  }

  @Test
  void baillieWagstaffBeyondTheBound() {
    BigInteger p = Primes.DETERMINISTIC_BOUND.nextProbablePrime();
    assertThat(Primes.isPrime(p)).isTrue();
    assertThat(Primes.isProven(p)).isFalse();
    assertThat(Primes.isPrime(p.multiply(BigInteger.valueOf(10_007)))).isFalse();
    // 5459 is the least strong Lucas pseudoprime: base 2 rejects it
    assertThat(Primes.strongLucas(BigInteger.valueOf(5_459))).isTrue();
    assertThat(Primes.strongProbablePrime(BigInteger.valueOf(5_459), BigInteger.TWO)).isFalse();
    assertThat(Primes.strongLucas(BigInteger.valueOf(5_461))).isFalse();
    assertThat(Primes.strongLucas(BigInteger.valueOf(10_007))).isTrue();
  }

  @Test
  void jacobi() {
    assertThat(Primes.jacobi(BigInteger.valueOf(1001), BigInteger.valueOf(9907))).isEqualTo(-1);
    assertThat(Primes.jacobi(BigInteger.valueOf(19), BigInteger.valueOf(45))).isEqualTo(1);
    assertThat(Primes.jacobi(BigInteger.valueOf(8), BigInteger.valueOf(21))).isEqualTo(-1);
    assertThat(Primes.jacobi(BigInteger.valueOf(5), BigInteger.valueOf(21))).isEqualTo(1);
    assertThat(Primes.jacobi(BigInteger.valueOf(-7), BigInteger.valueOf(15))).isEqualTo(1);
    assertThat(Primes.jacobi(BigInteger.valueOf(6), BigInteger.valueOf(15))).isZero();
  }

  @Test
  void nextAndPrevious() {
    Cancellation c = Cancellation.NEVER;
    assertThat(Primes.next(BigInteger.valueOf(-5), c)).isEqualTo(BigInteger.TWO);
    assertThat(Primes.next(BigInteger.TWO, c)).isEqualTo(BigInteger.valueOf(3));
    assertThat(Primes.next(BigInteger.valueOf(100), c)).isEqualTo(BigInteger.valueOf(101));
    assertThat(Primes.next(BigInteger.valueOf(113), c)).isEqualTo(BigInteger.valueOf(127));
    assertThat(Primes.previous(BigInteger.TWO, c)).isEmpty();
    assertThat(Primes.previous(BigInteger.valueOf(3), c)).contains(BigInteger.TWO);
    assertThat(Primes.previous(BigInteger.valueOf(100), c)).contains(BigInteger.valueOf(97));
    assertThat(Primes.previous(BigInteger.TEN.pow(20), c)).contains(big("99999999999999999989"));
  }

  @Test
  void theWindow() {
    Cancellation c = Cancellation.NEVER;
    assertThat(Primes.between(BigInteger.TEN, BigInteger.valueOf(30), c))
        .containsExactly(
            BigInteger.valueOf(11),
            BigInteger.valueOf(13),
            BigInteger.valueOf(17),
            BigInteger.valueOf(19),
            BigInteger.valueOf(23),
            BigInteger.valueOf(29));
    assertThat(Primes.between(BigInteger.valueOf(-10), BigInteger.valueOf(3), c))
        .containsExactly(BigInteger.TWO, BigInteger.valueOf(3));
    assertThat(Primes.between(BigInteger.valueOf(14), BigInteger.valueOf(16), c)).isEmpty();
    assertThat(Primes.between(BigInteger.valueOf(30), BigInteger.TEN, c)).isEmpty();
    assertThat(Primes.between(BigInteger.ONE, BigInteger.valueOf(1_000_000), c)).hasSize(78_498);
    BigInteger lo = BigInteger.TEN.pow(18);
    assertThat(Primes.between(lo, lo.add(BigInteger.valueOf(100)), c))
        .containsExactly(
            big("1000000000000000003"),
            big("1000000000000000009"),
            big("1000000000000000031"),
            big("1000000000000000079"));
    BigInteger far = BigInteger.TEN.pow(30);
    assertThat(Primes.between(far, far.add(BigInteger.valueOf(60)), c))
        .containsExactly(big("1000000000000000000000000000057"));
    assertThatThrownBy(() -> Primes.between(BigInteger.ZERO, BigInteger.valueOf(2_000_000), c))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
