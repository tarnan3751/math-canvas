package mathcanvas.engine.algebra;

import java.util.Optional;
import mathcanvas.engine.provider.Provider;
import mathcanvas.engine.provider.Providers;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;

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

  /** The default registry. */
  public static final Providers REGISTRY =
      Providers.EMPTY.register(mathcanvas.engine.provider.Gcd.class, new GcdProvider());

  private DefaultProviders() {}
}
