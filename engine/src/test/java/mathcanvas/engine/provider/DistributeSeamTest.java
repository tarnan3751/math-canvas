package mathcanvas.engine.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import mathcanvas.engine.provider.Distribute.Kind;
import mathcanvas.engine.provider.Distribute.Law;
import mathcanvas.engine.provider.DistributionValue.Status;
import mathcanvas.engine.rule.Context;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * The distribute operation is a real seam: a provider that answers the standard exponential's
 * probabilities only, raw (a negative zero, a value a rounding above 1), registered ahead of the
 * default, receives them and is normalised; the rest falls through to the default.
 */
class DistributeSeamTest {

  /** Answers {@code P(a < X ≤ b)} of the exponential with rate 1, and nothing else. */
  record UnitExponential(AtomicInteger calls) implements Provider<Distribute, DistributionValue> {

    @Override
    public String name() {
      return "unit-exponential";
    }

    @Override
    public Optional<DistributionValue> apply(Distribute input, Context ctx, Steps steps) {
      if (input.law() != Law.EXPONENTIAL
          || input.kind() != Kind.PROBABILITY
          || input.parameters()[0] != 1) {
        return Optional.empty();
      }
      calls.incrementAndGet();
      double a = Math.max(0, input.points()[0]);
      double b = Math.max(0, input.points()[1]);
      // raw: a negative zero for an empty interval, and a little above 1 for the whole line
      double value = a == b ? -0.0 : Math.exp(-a) - Math.exp(-b) + (a == 0 ? 1e-16 : 0);
      return Optional.of(new DistributionValue(Status.OK, value));
    }
  }

  @Test
  void routesToTheRegisteredProviderAndFallsThrough() {
    AtomicInteger calls = new AtomicInteger();
    Providers registry =
        Providers.defaults().register(Distribute.class, new UnitExponential(calls), 0);
    assertThat(registry.names(Distribute.class)).containsExactly("unit-exponential", "default");
    Context ctx = Context.DEFAULT.withProviders(registry);

    Providers.Outcome<DistributionValue> whole =
        registry.run(
            new Distribute(
                Law.EXPONENTIAL,
                Kind.PROBABILITY,
                new double[] {1},
                new double[] {Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}),
            ctx,
            Steps.NONE);
    assertThat(whole.provider()).isEqualTo("unit-exponential");
    assertThat(whole.value().value()).isEqualTo(1.0);

    Providers.Outcome<DistributionValue> empty =
        registry.run(
            new Distribute(
                Law.EXPONENTIAL, Kind.PROBABILITY, new double[] {1}, new double[] {2, 2}),
            ctx,
            Steps.NONE);
    assertThat(Double.doubleToRawLongBits(empty.value().value()))
        .isEqualTo(Double.doubleToRawLongBits(0.0));
    assertThat(calls).hasValue(2);

    Distribute other =
        new Distribute(Law.EXPONENTIAL, Kind.PROBABILITY, new double[] {2}, new double[] {0, 1});
    Providers.Outcome<DistributionValue> fallen = registry.run(other, ctx, Steps.NONE);
    assertThat(fallen.provider()).isEqualTo("default");
    assertThat(fallen.value().value()).isCloseTo(1 - Math.exp(-2), Offset.offset(1e-15));
    assertThat(calls).hasValue(2);

    // both providers agree where both answer
    Distribute unit =
        new Distribute(Law.EXPONENTIAL, Kind.PROBABILITY, new double[] {1}, new double[] {1, 3});
    double ours = registry.run(unit, ctx, Steps.NONE).value().value();
    double theirs = Providers.defaults().run(unit, Context.DEFAULT, Steps.NONE).value().value();
    assertThat(ours).isCloseTo(theirs, Offset.offset(1e-15));
  }

  @Test
  void checksItsOperands() {
    assertThatThrownBy(
            () -> new Distribute(Law.NORMAL, Kind.DENSITY, new double[] {0, 0}, new double[] {1}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new Distribute(Law.ERF, Kind.DENSITY, new double[] {}, new double[] {1}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new Distribute(Law.GAMMA, Kind.VALUE, new double[] {1}, new double[] {1}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Distribute(
                    Law.NORMAL, Kind.PROBABILITY, new double[] {0, 1}, new double[] {2, 1}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Distribute(Law.BINOMIAL, Kind.INVERSE, new double[] {3, 0.5}, new double[] {1}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new Distribute(
                    Law.BINOMIAL, Kind.DENSITY, new double[] {2.5, 0.5}, new double[] {1}))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
