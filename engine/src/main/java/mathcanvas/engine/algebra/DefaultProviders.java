package mathcanvas.engine.algebra;

import java.util.Optional;
import mathcanvas.engine.provider.Factor;
import mathcanvas.engine.provider.Provider;
import mathcanvas.engine.provider.Providers;
import mathcanvas.engine.provider.Solve;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.solve.SolveProvider;

/**
 * The engine's own providers, registered by {@link Providers#defaults()}. Each is named {@code
 * default}.
 */
public final class DefaultProviders {

  /** The gcd: {@link Gcd#gcd}. */
  public record GcdProvider() implements Provider<mathcanvas.engine.provider.Gcd, Polynomial> {

    @Override
    public String name() {
      return "default";
    }

    @Override
    public Optional<Polynomial> apply(
        mathcanvas.engine.provider.Gcd input, Context ctx, Steps steps) {
      return Optional.of(Gcd.gcd(input.a(), input.b()));
    }
  }

  /** The factorization over ℚ: {@link Factoring#factor}, recording its steps. */
  public record FactorProvider() implements Provider<Factor, Factorization> {

    @Override
    public String name() {
      return "default";
    }

    @Override
    public Optional<Factorization> apply(Factor input, Context ctx, Steps steps) {
      return Optional.of(Factoring.factor(input.p(), steps));
    }
  }

  /** The default registry. */
  public static final Providers REGISTRY =
      Providers.EMPTY
          .register(mathcanvas.engine.provider.Gcd.class, new GcdProvider())
          .register(Factor.class, new FactorProvider())
          .register(Solve.class, new SolveProvider());

  private DefaultProviders() {}
}
