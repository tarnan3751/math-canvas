package mathcanvas.engine.numbertheory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.numbertheory.Factorizer.Budget;
import org.junit.jupiter.api.Test;

/**
 * The factoriser at its caps, against a whole-run budget: a 30-digit semiprime of two 15-digit
 * primes factors, and a 60-digit one of two 30-digit primes is given up within the work cap.
 */
class NumberTheoryPerformanceTest {

  /** Both cases together; about ten seconds when the machine is idle. */
  private static final long LIMIT_MS = 45_000;

  private static long elapsed;

  private static long time(Runnable r) {
    long t = System.nanoTime();
    r.run();
    long ms = (System.nanoTime() - t) / 1_000_000;
    elapsed += ms;
    return ms;
  }

  @Test
  void aThirtyDigitSemiprimeFactors() {
    BigInteger n = new BigInteger("200000000000027").multiply(new BigInteger("700000000000051"));
    time(
        () ->
            assertThat(Factorizer.factor(n, Budget.FULL, Cancellation.NEVER).complete()).isTrue());
    assertThat(elapsed).isLessThan(LIMIT_MS);
  }

  @Test
  void aSixtyDigitSemiprimeStays() {
    BigInteger p = BigInteger.TEN.pow(29).nextProbablePrime();
    BigInteger q = BigInteger.TEN.pow(29).multiply(BigInteger.valueOf(3)).nextProbablePrime();
    time(
        () ->
            assertThat(Factorizer.factor(p.multiply(q), Budget.FULL, Cancellation.NEVER).complete())
                .isFalse());
    assertThat(elapsed).isLessThan(LIMIT_MS);
  }
}
