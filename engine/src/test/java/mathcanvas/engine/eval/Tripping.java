package mathcanvas.engine.eval;

import mathcanvas.engine.Cancellation;

/**
 * A cancellation for the tests that is requested from its {@code n}-th check on, recording when it
 * first was; with {@code n} out of reach it only counts the checks. Single-threaded: the session
 * that checks it runs on the test's thread.
 */
final class Tripping implements Cancellation {

  private final long tripAt;
  private long checks;
  private long trippedAt;

  Tripping(long tripAt) {
    this.tripAt = tripAt;
  }

  /** A token that never trips and counts the checks. */
  static Tripping counting() {
    return new Tripping(Long.MAX_VALUE);
  }

  @Override
  public boolean requested() {
    if (++checks < tripAt) {
      return false;
    }
    if (trippedAt == 0) {
      trippedAt = System.nanoTime();
    }
    return true;
  }

  /** The checks made so far. */
  long checks() {
    return checks;
  }

  /** The {@link System#nanoTime} of the first request, or 0. */
  long trippedAt() {
    return trippedAt;
  }

  /** The trip points for a run of {@code k} checks: 1, then by threes, then {@code k − 1}. */
  static java.util.List<Long> points(long k) {
    java.util.List<Long> out = new java.util.ArrayList<>();
    for (long n = 1; n < k; n *= 3) {
      out.add(n);
    }
    if (k > 1) {
      out.add(k - 1);
    }
    return out;
  }
}
