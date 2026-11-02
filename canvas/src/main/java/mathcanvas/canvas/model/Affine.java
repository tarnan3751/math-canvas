package mathcanvas.canvas.model;

import org.apache.commons.geometry.euclidean.twod.AffineTransformMatrix2D;

/**
 * An affine map of the plane, {@code (x, y) ↦ (a x + b y + e, c x + d y + f)}: a transformation's
 * map, kept as its six entries so an image's parametrisation compares by value. Made from and
 * applied through Apache Commons Geometry's {@link AffineTransformMatrix2D}.
 *
 * @param a the abscissa's coefficient of x
 * @param b the abscissa's coefficient of y
 * @param c the ordinate's coefficient of x
 * @param d the ordinate's coefficient of y
 * @param e the abscissa's shift
 * @param f the ordinate's shift
 */
public record Affine(double a, double b, double c, double d, double e, double f) {

  /**
   * The map of a matrix.
   *
   * @param m the matrix
   * @return the map
   */
  static Affine of(AffineTransformMatrix2D m) {
    double[] r = m.toArray();
    return new Affine(r[0], r[1], r[3], r[4], r[2], r[5]);
  }

  /**
   * The matrix of this map.
   *
   * @return it
   */
  AffineTransformMatrix2D matrix() {
    return AffineTransformMatrix2D.of(a, b, e, c, d, f);
  }

  /**
   * The image of a point.
   *
   * @param x the abscissa
   * @param y the ordinate
   * @return the image
   */
  public Sample apply(double x, double y) {
    return new Sample(a * x + b * y + e, c * x + d * y + f);
  }

  /**
   * The image of a vector: the linear part alone.
   *
   * @param x the abscissa
   * @param y the ordinate
   * @return the image
   */
  public Sample applyVector(double x, double y) {
    return new Sample(a * x + b * y, c * x + d * y);
  }

  /**
   * The determinant of the linear part: negative for a map that reverses orientation.
   *
   * @return it
   */
  public double determinant() {
    return a * d - b * c;
  }

  /**
   * This map after another: {@code this ∘ inner}.
   *
   * @param inner the map applied first
   * @return the composition
   */
  public Affine after(Affine inner) {
    return of(inner.matrix().premultiply(matrix()));
  }

  /**
   * Whether every entry is finite.
   *
   * @return true when it is
   */
  public boolean isFinite() {
    return Double.isFinite(a)
        && Double.isFinite(b)
        && Double.isFinite(c)
        && Double.isFinite(d)
        && Double.isFinite(e)
        && Double.isFinite(f);
  }
}
