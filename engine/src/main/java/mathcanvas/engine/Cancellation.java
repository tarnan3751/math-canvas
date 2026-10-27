package mathcanvas.engine;

/**
 * A cooperative request to stop: the caller that runs the engine on a worker sets it, and the
 * engine checks it at every loop that can run long (the rewriter, the evaluator, the numeric
 * series, the algebra and the solver), throwing {@link CancelledException} at the next check. A
 * thread interrupt counts as a request too, so {@code Future.cancel(true)} works without a token;
 * the interrupt flag is read, never cleared.
 *
 * <p>It is explicit state, carried by {@code rule.Context} and passed to the numeric layer: the
 * engine keeps no thread-local and reads no clock. It lives in this package, which imports nothing
 * from the engine, so that every package, {@code number} included, can check it.
 */
@FunctionalInterface
public interface Cancellation {

  /** Never requested: only a thread interrupt stops the work. */
  Cancellation NEVER = () -> false;

  /**
   * Returns whether the work should stop. It is read often, so it must be cheap: a volatile read.
   *
   * @return true once stopping is requested
   */
  boolean requested();

  /**
   * Throws when stopping is requested or the current thread is interrupted.
   *
   * @throws CancelledException if so
   */
  default void check() {
    if (requested() || Thread.currentThread().isInterrupted()) {
      throw new CancelledException();
    }
  }
}
