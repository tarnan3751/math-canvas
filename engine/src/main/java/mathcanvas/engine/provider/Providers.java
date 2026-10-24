package mathcanvas.engine.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.algebra.DefaultProviders;
import mathcanvas.engine.rule.Context;

/**
 * The registry: for each operation, its providers in the order they are tried. It is an immutable
 * value carried by the {@link Context}, so a test swaps providers with {@link
 * Context#withProviders} and no static state is involved.
 *
 * @param entries the providers, each with its operation, in the order they are tried
 */
public record Providers(List<Entry> entries) {

  /**
   * One registration.
   *
   * @param operation the operation's class
   * @param name the provider's name
   * @param call the provider, applied to an operation of that class
   */
  public record Entry(Class<?> operation, String name, Call call) {}

  /** A registered provider, typed at registration so that running it needs no generic cast. */
  @FunctionalInterface
  public interface Call {

    /**
     * Applies the provider.
     *
     * @param operation an operation of the class it was registered for
     * @param ctx the context
     * @param steps the step sink
     * @return its result, or empty when it declines
     */
    Optional<?> apply(Operation<?> operation, Context ctx, Steps steps);
  }

  /** No providers. */
  public static final Providers EMPTY = new Providers(List.of());

  /**
   * Copies the entries and checks the names.
   *
   * @throws IllegalArgumentException if two providers of one operation share a name
   */
  public Providers {
    entries = List.copyOf(entries);
    for (int i = 0; i < entries.size(); i++) {
      for (int j = 0; j < i; j++) {
        if (entries.get(i).operation() == entries.get(j).operation()
            && entries.get(i).name().equals(entries.get(j).name())) {
          throw new IllegalArgumentException(
              "two providers named "
                  + entries.get(i).name()
                  + " for "
                  + entries.get(i).operation().getSimpleName());
        }
      }
    }
  }

  /**
   * The outcome of running an operation.
   *
   * @param value the normalised result
   * @param provider the name of the provider that answered
   * @param <O> the type of the result
   */
  public record Outcome<O>(O value, String provider) {}

  /**
   * Returns the engine's own providers, the only ones in the undergraduate scope.
   *
   * @return the default registry
   */
  public static Providers defaults() {
    return DefaultProviders.REGISTRY;
  }

  /**
   * Returns this registry with {@code provider} tried at position {@code index} among the providers
   * of {@code operation}.
   *
   * @param operation the operation's class
   * @param provider the provider
   * @param index its position, 0 for first
   * @param <I> the operation
   * @param <O> its result
   * @return the new registry
   * @throws IllegalArgumentException if a provider of that name is registered for the operation
   * @throws IndexOutOfBoundsException if {@code index} is beyond the operation's providers
   */
  public <I extends Operation<O>, O> Providers register(
      Class<I> operation, Provider<I, O> provider, int index) {
    Entry entry =
        new Entry(
            operation,
            provider.name(),
            (op, ctx, steps) -> provider.apply(operation.cast(op), ctx, steps));
    int seen = 0;
    List<Entry> out = new ArrayList<>(entries.size() + 1);
    boolean added = false;
    for (Entry e : entries) {
      if (e.operation() == operation) {
        if (seen == index) {
          out.add(entry);
          added = true;
        }
        seen++;
      }
      out.add(e);
    }
    if (!added) {
      if (index != seen) {
        throw new IndexOutOfBoundsException("position " + index + " of " + seen + " providers");
      }
      out.add(entry);
    }
    return new Providers(out);
  }

  /**
   * Returns this registry with {@code provider} tried after the operation's other providers.
   *
   * @param operation the operation's class
   * @param provider the provider
   * @param <I> the operation
   * @param <O> its result
   * @return the new registry
   * @throws IllegalArgumentException if a provider of that name is registered for the operation
   */
  public <I extends Operation<O>, O> Providers register(
      Class<I> operation, Provider<I, O> provider) {
    return register(operation, provider, names(operation).size());
  }

  /**
   * Returns this registry without the provider named {@code name} for {@code operation}.
   *
   * @param operation the operation's class
   * @param name the provider's name
   * @return the new registry, this one when there is no such provider
   */
  public Providers unregister(Class<? extends Operation<?>> operation, String name) {
    List<Entry> out = new ArrayList<>(entries);
    out.removeIf(e -> e.operation() == operation && e.name().equals(name));
    return new Providers(out);
  }

  /**
   * Returns the names of an operation's providers, in the order they are tried.
   *
   * @param operation the operation's class
   * @return the names
   */
  public List<String> names(Class<?> operation) {
    return entries.stream().filter(e -> e.operation() == operation).map(e -> e.name()).toList();
  }

  /**
   * Runs an operation: its providers are tried in order, and the first result is normalised.
   *
   * @param operation the operation and its inputs
   * @param ctx the context, passed to the providers
   * @param steps where the providers record their steps
   * @param <O> the type of the result
   * @return the normalised result and the name of the provider that gave it
   * @throws IllegalStateException if no provider answers
   */
  public <O> Outcome<O> run(Operation<O> operation, Context ctx, Steps steps) {
    for (Entry e : entries) {
      if (e.operation() != operation.getClass()) {
        continue;
      }
      Optional<?> result = e.call().apply(operation, ctx, steps);
      if (result.isPresent()) {
        return new Outcome<>(
            operation.normalise(operation.outputType().cast(result.get())), e.name());
      }
    }
    throw new IllegalStateException(
        "no provider answers for " + operation.getClass().getSimpleName());
  }
}
