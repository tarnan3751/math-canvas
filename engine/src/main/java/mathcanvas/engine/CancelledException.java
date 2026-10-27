package mathcanvas.engine;

import java.io.Serial;

/**
 * The work was stopped at a check of its {@link Cancellation}. It extends {@link RuntimeException}
 * directly, so no catch of an engine failure ({@code ArithmeticException}, {@code
 * IllegalArgumentException}, the limits) takes it; the session turns it into an outcome. It carries
 * no stack trace: it is control flow, not a defect.
 */
public final class CancelledException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** A cancellation. */
  public CancelledException() {
    super("cancelled", null, false, false);
  }
}
