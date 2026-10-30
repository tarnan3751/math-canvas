package mathcanvas.canvas.model;

/**
 * One point of a path, NaN where the path is undefined.
 *
 * @param x the abscissa
 * @param y the ordinate
 */
public record Sample(double x, double y) {

  /** The undefined point. */
  public static final Sample UNDEFINED = new Sample(Double.NaN, Double.NaN);

  /**
   * Whether both coordinates are finite.
   *
   * @return true for a defined point
   */
  public boolean isFinite() {
    return Double.isFinite(x) && Double.isFinite(y);
  }
}
