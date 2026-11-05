package mathcanvas.engine.number;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.function.BiFunction;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.CancelledException;
import org.junit.jupiter.api.Test;

/**
 * The remembered series of the constants: a series cancelled at any of its checks throws and is not
 * remembered, so the next call sums it afresh, and a call after that gives the remembered value
 * without summing. Each trial uses a precision no other test uses, so its series are not remembered
 * when it starts.
 */
class RememberedSeriesTest {

  /** A token that trips at check {@code tripAt} and counts the checks. */
  private static final class Counting implements Cancellation {
    private final long tripAt;
    private long checks;

    Counting(long tripAt) {
      this.tripAt = tripAt;
    }

    @Override
    public boolean requested() {
      return ++checks >= tripAt;
    }
  }

  /** π: the series of {@code atan(1/5)}, then of {@code atan(1/239)}. */
  @Test
  void piCancelledInEitherSeriesRemembersNothing() {
    trials(1237, (m, w) -> m.pi(w));
  }

  /**
   * The logarithm of 1000, {@code 3 ln 10}: the series of {@code atanh(1/3)} for {@code ln 2}, then
   * of {@code atanh(1/9)} for {@code ln 10}.
   */
  @Test
  void ln2AndLn10CancelledInEitherSeriesRememberNothing() {
    trials(1337, (m, w) -> m.log(BigDecimal.valueOf(1000), w));
  }

  private static void trials(int base, BiFunction<BigDecimalMath, MathContext, BigDecimal> f) {
    // the checks of an unremembered call at a neighbouring precision place the trip points
    Counting scale = new Counting(Long.MAX_VALUE);
    f.apply(new BigDecimalMath(scale), new MathContext(base));
    assertThat(scale.checks).isGreaterThan(1_000);
    for (int k = 1; k <= 9; k++) {
      MathContext w = new MathContext(base + k);
      long tripAt = Math.max(1, scale.checks * k / 10);
      assertThatThrownBy(() -> f.apply(new BigDecimalMath(new Counting(tripAt)), w))
          .as("tripped at check %d of about %d", tripAt, scale.checks)
          .isInstanceOf(CancelledException.class);
      Counting fresh = new Counting(Long.MAX_VALUE);
      BigDecimal summed = f.apply(new BigDecimalMath(fresh), w);
      // the series that was cancelled was not remembered, so it is summed again (a series that
      // ended before the trip is remembered); once summed, every series is remembered
      assertThat(fresh.checks).as("checks after a trip at %d", tripAt).isPositive();
      Counting again = new Counting(Long.MAX_VALUE);
      assertThat(f.apply(new BigDecimalMath(again), w)).isEqualTo(summed);
      assertThat(again.checks).isZero();
    }
  }
}
