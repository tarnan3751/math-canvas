package mathcanvas.engine.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import mathcanvas.engine.provider.Decompose.Kind;
import mathcanvas.engine.provider.Decomposition.Status;
import mathcanvas.engine.rule.Context;
import org.junit.jupiter.api.Test;

/**
 * The decompose operation is a real seam: a provider that answers the exponential of 1×1 matrices
 * only, with a negative zero, registered ahead of the default, receives them and is normalised; the
 * rest falls through to the default.
 */
class DecomposeSeamTest {

  /** Answers {@code e^[[a]]} as {@code [[e^a]]}, and {@code [[-0.0]]} for a minus infinity. */
  record ScalarExp(AtomicInteger calls) implements Provider<Decompose, Decomposition> {

    @Override
    public String name() {
      return "scalar-exp";
    }

    @Override
    public Optional<Decomposition> apply(Decompose input, Context ctx, Steps steps) {
      if (input.kind() != Kind.EXP || input.a().length != 1 || input.a()[0].length != 1) {
        return Optional.empty();
      }
      calls.incrementAndGet();
      double a = input.a()[0][0];
      double value = a == Double.NEGATIVE_INFINITY ? -0.0 : Math.exp(a);
      return Optional.of(
          new Decomposition(Status.OK, List.<double[][]>of(new double[][] {{value}})));
    }
  }

  @Test
  void routesToTheRegisteredProviderAndFallsThrough() {
    AtomicInteger calls = new AtomicInteger();
    Providers registry = Providers.defaults().register(Decompose.class, new ScalarExp(calls), 0);
    assertThat(registry.names(Decompose.class)).containsExactly("scalar-exp", "default");
    Context ctx = Context.DEFAULT.withProviders(registry);

    Providers.Outcome<Decomposition> one =
        registry.run(new Decompose(Kind.EXP, new double[][] {{1}}, null), ctx, Steps.NONE);
    assertThat(one.provider()).isEqualTo("scalar-exp");
    assertThat(calls).hasValue(1);

    Providers.Outcome<Decomposition> zero =
        registry.run(
            new Decompose(Kind.EXP, new double[][] {{Double.NEGATIVE_INFINITY}}, null),
            ctx,
            Steps.NONE);
    assertThat(Double.doubleToRawLongBits(zero.value().factors().getFirst()[0][0]))
        .isEqualTo(Double.doubleToRawLongBits(0.0));

    Providers.Outcome<Decomposition> two =
        registry.run(
            new Decompose(Kind.EXP, new double[][] {{1, 0}, {0, 1}}, null), ctx, Steps.NONE);
    assertThat(two.provider()).isEqualTo("default");
    assertThat(calls).hasValue(2);

    // both providers agree where both answer
    double ours = one.value().factors().getFirst()[0][0];
    double theirs =
        Providers.defaults()
            .run(new Decompose(Kind.EXP, new double[][] {{1}}, null), Context.DEFAULT, Steps.NONE)
            .value()
            .factors()
            .getFirst()[0][0];
    assertThat(ours).isCloseTo(theirs, org.assertj.core.data.Offset.offset(1e-14));
  }
}
