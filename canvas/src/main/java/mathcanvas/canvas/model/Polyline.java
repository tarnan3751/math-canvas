package mathcanvas.canvas.model;

import java.util.Arrays;

/**
 * An immutable polyline of samples: at each vertex the path's parameter and the world point. A path
 * with no parameter, and a region's ring, carry NaN parameters. A polyline has at least one vertex;
 * a sampled curve is split into polylines at every gap, so a polyline never holds a non-finite
 * coordinate.
 *
 * <p>A final class rather than a record because it holds arrays: equality is by value, and the
 * painting loop reads the coordinate arrays through {@link #xs()} and {@link #ys()} without a copy.
 */
public final class Polyline {

  private final double[] ts;
  private final double[] xs;
  private final double[] ys;

  /**
   * Copies the samples.
   *
   * @param ts the parameters
   * @param xs the abscissae, finite
   * @param ys the ordinates, finite
   * @throws IllegalArgumentException if the arrays differ in length, are empty, or hold a
   *     coordinate that is not finite
   */
  public Polyline(double[] ts, double[] xs, double[] ys) {
    if (ts.length != xs.length || xs.length != ys.length) {
      throw new IllegalArgumentException("a polyline's arrays have one length");
    }
    if (xs.length == 0) {
      throw new IllegalArgumentException("a polyline has a vertex");
    }
    for (int k = 0; k < xs.length; k++) {
      if (!Double.isFinite(xs[k]) || !Double.isFinite(ys[k])) {
        throw new IllegalArgumentException("a polyline's vertices are finite");
      }
    }
    this.ts = ts.clone();
    this.xs = xs.clone();
    this.ys = ys.clone();
  }

  /**
   * A polyline with no parameter.
   *
   * @param xs the abscissae
   * @param ys the ordinates
   * @return the polyline
   */
  public static Polyline unparametrised(double[] xs, double[] ys) {
    double[] ts = new double[xs.length];
    Arrays.fill(ts, Double.NaN);
    return new Polyline(ts, xs, ys);
  }

  /**
   * The number of vertices.
   *
   * @return at least 1
   */
  public int size() {
    return xs.length;
  }

  /**
   * A vertex's parameter.
   *
   * @param k the vertex
   * @return the parameter, NaN on a path with none
   */
  public double t(int k) {
    return ts[k];
  }

  /**
   * A vertex's abscissa.
   *
   * @param k the vertex
   * @return the abscissa
   */
  public double x(int k) {
    return xs[k];
  }

  /**
   * A vertex's ordinate.
   *
   * @param k the vertex
   * @return the ordinate
   */
  public double y(int k) {
    return ys[k];
  }

  /**
   * The abscissae, not copied, for the painting loop ({@code Painter.polyline(xs, ys, n)}).
   * <b>Read-only</b>: writing to the array breaks the polyline's immutability.
   *
   * @return the array, {@link #size()} long
   */
  public double[] xs() {
    return xs;
  }

  /**
   * The ordinates, not copied, for the painting loop. <b>Read-only</b>, as {@link #xs()}.
   *
   * @return the array, {@link #size()} long
   */
  public double[] ys() {
    return ys;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Polyline p
        && Arrays.equals(ts, p.ts)
        && Arrays.equals(xs, p.xs)
        && Arrays.equals(ys, p.ys);
  }

  @Override
  public int hashCode() {
    return 31 * (31 * Arrays.hashCode(ts) + Arrays.hashCode(xs)) + Arrays.hashCode(ys);
  }

  @Override
  public String toString() {
    return "Polyline[" + xs.length + " vertices]";
  }
}
