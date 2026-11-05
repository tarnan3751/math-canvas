package mathcanvas.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import mathcanvas.engine.provider.Providers;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Numerics;
import org.junit.jupiter.api.Test;

class CancellationTest {

  @Test
  void neverPassesAndARequestThrows() {
    Cancellation.NEVER.check();
    Cancellation requested = () -> true;
    assertThatThrownBy(requested::check).isInstanceOf(CancelledException.class);
    assertThat(new CancelledException()).isNotInstanceOf(ArithmeticException.class);
    assertThat(new CancelledException().getStackTrace()).isEmpty();
  }

  @Test
  void anInterruptThrowsAndStaysSet() {
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(Cancellation.NEVER::check).isInstanceOf(CancelledException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void theContextCarriesTheTokenThroughEveryWither() {
    assertThat(Context.DEFAULT.cancellation()).isSameAs(Cancellation.NEVER);
    Cancellation token = () -> false;
    Context c = Context.DEFAULT.withCancellation(token);
    assertThat(c.withAssumptions(c.assumptions()).cancellation()).isSameAs(token);
    assertThat(c.withTrace(true).cancellation()).isSameAs(token);
    assertThat(c.withProviders(Providers.defaults()).cancellation()).isSameAs(token);
    assertThat(c.withNumerics(Numerics.NONE).cancellation()).isSameAs(token);
    assertThat(
            new Context(c.assumptions(), c.precision(), false, 10, c.providers(), c.numerics())
                .cancellation())
        .isSameAs(Cancellation.NEVER);
  }
}
