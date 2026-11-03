package mathcanvas.engine.numbertheory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.numbertheory.Factorizer.Budget;
import mathcanvas.engine.numbertheory.Factorizer.Factorization;
import org.junit.jupiter.api.Test;

/** Trial division, perfect powers, rho, the elliptic curves, and the short budget. */
class FactorizerTest {

  private static Factorization full(String n) {
    return Factorizer.factor(new BigInteger(n), Budget.FULL, Cancellation.NEVER);
  }

  private static Map<BigInteger, Integer> map(long... pairs) {
    Map<BigInteger, Integer> m = new TreeMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      m.put(BigInteger.valueOf(pairs[i]), (int) pairs[i + 1]);
    }
    return m;
  }

  @Test
  void smallNumbers() {
    assertThat(full("360").primes()).isEqualTo(map(2, 3, 3, 2, 5, 1));
    assertThat(full("360").sign()).isEqualTo(1);
    Factorization minus = full("-12");
    assertThat(minus.sign()).isEqualTo(-1);
    assertThat(minus.primes()).isEqualTo(map(2, 2, 3, 1));
    assertThat(full("1").primes()).isEmpty();
    assertThat(full("-1").sign()).isEqualTo(-1);
    assertThat(full("97").primes()).isEqualTo(map(97, 1));
    assertThat(full("99999989").primes()).isEqualTo(map(99_999_989, 1));
    assertThat(full("97").events()).isEmpty();
    assertThat(full("360").events()).extracting(Factorizer.Event::method).containsOnly("trial");
    assertThatThrownBy(() -> full("0")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rhoSplitsAProductOfTwoLargePrimes() {
    Factorization f = full("1000000016000000063");
    assertThat(f.complete()).isTrue();
    assertThat(f.primes()).isEqualTo(map(1_000_000_007, 1, 1_000_000_009, 1));
    assertThat(f.events()).extracting(Factorizer.Event::method).containsExactly("rho");
    Factorization fermat =
        Factorizer.factor(
            BigInteger.TWO.pow(64).add(BigInteger.ONE), Budget.FULL, Cancellation.NEVER);
    assertThat(fermat.primes()).isEqualTo(map(274_177, 1, 67_280_421_310_721L, 1));
  }

  @Test
  void aPerfectPowerIsRecognised() {
    BigInteger p = BigInteger.valueOf(10_007);
    Factorization f = Factorizer.factor(p.pow(5).shiftLeft(3), Budget.FULL, Cancellation.NEVER);
    assertThat(f.primes()).isEqualTo(map(2, 3, 10_007, 5));
    assertThat(f.events()).extracting(Factorizer.Event::method).containsExactly("trial", "power");
  }

  @Test
  void theCurvesSplitWhatRhoCannot() {
    // two 15-digit primes: rho's 2²⁰ iterations do not reach them
    BigInteger p = new BigInteger("400000000000063");
    BigInteger q = new BigInteger("500000000000057");
    Factorization f = Factorizer.factor(p.multiply(q), Budget.FULL, Cancellation.NEVER);
    assertThat(f.complete()).isTrue();
    assertThat(f.primes()).containsOnlyKeys(p, q);
    assertThat(f.events()).extracting(Factorizer.Event::method).containsExactly("ecm");
  }

  @Test
  void aBalancedSemiprimeBeyondTheCapsIsUnfinished() {
    BigInteger p = BigInteger.TEN.pow(24).nextProbablePrime();
    BigInteger q = BigInteger.TEN.pow(24).multiply(BigInteger.valueOf(3)).nextProbablePrime();
    BigInteger n = p.multiply(q).shiftLeft(2);
    Factorization f = Factorizer.factor(n, new Budget(1_000, 20_000), Cancellation.NEVER);
    assertThat(f.complete()).isFalse();
    assertThat(f.primes()).isEqualTo(map(2, 2));
    assertThat(f.unfinished()).isEqualTo(Map.of(p.multiply(q), 1));
  }

  @Test
  void theShortBudgetGivesTheDivisorsOrNothing() {
    assertThat(Factorizer.divisors(BigInteger.valueOf(-12), 100, Cancellation.NEVER))
        .contains(List.of(1, 2, 3, 4, 6, 12).stream().map(BigInteger::valueOf).toList());
    assertThat(Factorizer.divisors(BigInteger.valueOf(720_720), 10, Cancellation.NEVER)).isEmpty();
    BigInteger hard = new BigInteger("400000000000063").multiply(new BigInteger("500000000000057"));
    assertThat(Factorizer.divisors(hard, 100, Cancellation.NEVER)).isEmpty();
    assertThat(Factorizer.divisors(new BigInteger("1000000016000000063"), 100, Cancellation.NEVER))
        .isPresent();
  }

  @Test
  void cancellationStopsTheWork() {
    BigInteger p = BigInteger.TEN.pow(24).nextProbablePrime();
    BigInteger q = BigInteger.TEN.pow(25).nextProbablePrime();
    assertThatThrownBy(() -> Factorizer.factor(p.multiply(q), Budget.FULL, () -> true))
        .isInstanceOf(mathcanvas.engine.CancelledException.class);
  }

  @Test
  void integerRoots() {
    assertThat(Factorizer.root(BigInteger.valueOf(1_000_000), 3))
        .isEqualTo(BigInteger.valueOf(100));
    assertThat(Factorizer.root(BigInteger.valueOf(999_999), 3)).isEqualTo(BigInteger.valueOf(99));
    assertThat(Factorizer.perfectPower(BigInteger.valueOf(10_007 * 10_009L))).isNull();
  }
}
