package mathcanvas.canvas.model;

/**
 * The world rectangle the window-sampled objects are sampled over: unbounded function graphs,
 * implicit curves, regions, and the drawn extent of lines. The integration series maps the view's
 * viewport to it.
 *
 * @param minX the least abscissa
 * @param maxX the greatest abscissa, above the least
 * @param minY the least ordinate
 * @param maxY the greatest ordinate, above the least
 */
public record Window(double minX, double maxX, double minY, double maxY) {

  /**
   * Checks the fields.
   *
   * @throws IllegalArgumentException if a bound is not finite or a side is empty
   */
  public Window {
    if (!Double.isFinite(minX)
        || !Double.isFinite(maxX)
        || !Double.isFinite(minY)
        || !Double.isFinite(maxY)) {
      throw new IllegalArgumentException("a window has finite bounds");
    }
    if (!(minX < maxX) || !(minY < maxY)) {
      throw new IllegalArgumentException("a window is not empty");
    }
  }

  /**
   * The width.
   *
   * @return the width
   */
  public double width() {
    return maxX - minX;
  }

  /**
   * The height.
   *
   * @return the height
   */
  public double height() {
    return maxY - minY;
  }

  /**
   * The length of the diagonal, the scale the model's tolerances are relative to.
   *
   * @return the diagonal
   */
  public double diagonal() {
    return Math.hypot(width(), height());
  }

  /**
   * This window grown by a fraction of its size on every side.
   *
   * @param fraction the fraction of the width and height added on each side
   * @return the window
   */
  public Window widened(double fraction) {
    double dx = width() * fraction;
    double dy = height() * fraction;
    return new Window(minX - dx, maxX + dx, minY - dy, maxY + dy);
  }
}
