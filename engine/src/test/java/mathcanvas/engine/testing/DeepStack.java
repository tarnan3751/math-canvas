package mathcanvas.engine.testing;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Runs a computation on a thread with a large stack, as the app's worker and the CLI do: the tests
 * run with a 1 MB stack, which a recursion of the full call limit outgrows ({@code
 * docs/language.md} §19).
 */
public final class DeepStack {

  /** The stack the deep runs have. */
  public static final long STACK = 64L << 20;

  private DeepStack() {}

  /**
   * The value of {@code body} computed on a thread with a {@link #STACK} stack.
   *
   * @param body the computation
   * @param <T> its value's type
   * @return its value
   */
  public static <T> T run(Supplier<T> body) {
    AtomicReference<T> value = new AtomicReference<>();
    AtomicReference<Throwable> failure = new AtomicReference<>();
    Thread t =
        new Thread(
            null,
            () -> {
              try {
                value.set(body.get());
              } catch (Throwable e) {
                failure.set(e);
              }
            },
            "deep-stack",
            STACK);
    t.start();
    try {
      t.join();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
    if (failure.get() instanceof RuntimeException e) {
      throw e;
    }
    if (failure.get() instanceof Error e) {
      throw e;
    }
    return value.get();
  }
}
