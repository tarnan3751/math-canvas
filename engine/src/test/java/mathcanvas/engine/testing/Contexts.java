package mathcanvas.engine.testing;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.provider.Providers;
import mathcanvas.engine.rule.Context;

/** Contexts and provider registries as the tests need them and the engine does not. */
public final class Contexts {

  private Contexts() {}

  /**
   * Returns {@code c} with another cap on rewrites.
   *
   * @param c the context
   * @param cap the most rewrites one call may make
   * @return the context
   * @throws IllegalArgumentException if the cap is below 1
   */
  public static Context withRewriteCap(Context c, int cap) {
    return new Context(
        c.assumptions(),
        c.precision(),
        c.traceEnabled(),
        cap,
        c.providers(),
        c.numerics(),
        c.cancellation());
  }

  /**
   * Returns {@code p} without the provider named {@code name} for {@code operation}.
   *
   * @param p the registry
   * @param operation the operation's class
   * @param name the provider's name
   * @return the registry
   */
  public static Providers unregister(Providers p, Class<?> operation, String name) {
    List<Providers.Entry> out = new ArrayList<>(p.entries());
    out.removeIf(e -> e.operation() == operation && e.name().equals(name));
    return new Providers(out);
  }
}
