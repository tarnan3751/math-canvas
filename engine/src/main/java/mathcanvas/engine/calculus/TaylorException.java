package mathcanvas.engine.calculus;

/**
 * A Taylor polynomial that does not exist: a derivative it needs is undefined at the point, or
 * could not be computed. The message names the order.
 */
public final class TaylorException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int order;

  /**
   * Creates the exception.
   *
   * @param order the order of the derivative that failed
   * @param message the reason, naming the order
   */
  TaylorException(int order, String message) {
    super(message);
    this.order = order;
  }

  /**
   * Returns the order of the derivative that failed.
   *
   * @return the order, 0 for the function itself
   */
  public int order() {
    return order;
  }
}
