package mathcanvas.canvas.model;

import java.util.Map;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Interval;

/**
 * The geometry of the 3D view's objects (docs/reference/canvas.md, **The 3D view**), sampled on the
 * worker through the sampler's grids. None of them depends on the plane's window.
 */
final class Spaces {

  private Spaces() {}

  /**
   * An object's geometry in space.
   *
   * @param object the object
   * @param parents the parents' geometry
   * @param sampler the sampler
   * @param cancellation checked as it goes
   * @return the geometry, {@link Geometry#UNDEFINED} when it cannot be computed
   */
  static Geometry of(
      SpaceObject object,
      Map<ObjectId, Geometry> parents,
      Sampler sampler,
      Cancellation cancellation) {
    return switch (object) {
      case Surface s -> surface(s, sampler, cancellation);
    };
  }

  /** {@code z = f(x, y)} on its rectangle: undefined unless both ranges are finite and ordered. */
  private static Geometry surface(Surface s, Sampler sampler, Cancellation cancellation) {
    double[] x = range(s.xDomain(), sampler);
    double[] y = range(s.yDomain(), sampler);
    if (x == null || y == null) {
      return Geometry.UNDEFINED;
    }
    int n = s.n();
    double[] z =
        sampler.grid(s.body(), Surface.X, x[0], x[1], Surface.Y, y[0], y[1], n, cancellation);
    int size = (n + 1) * (n + 1);
    float[] xs = new float[size];
    float[] ys = new float[size];
    float[] zs = new float[size];
    for (int j = 0; j <= n; j++) {
      for (int i = 0; i <= n; i++) {
        int k = j * (n + 1) + i;
        xs[k] = (float) (x[0] + (x[1] - x[0]) * i / n);
        ys[k] = (float) (y[0] + (y[1] - y[0]) * j / n);
        zs[k] = (float) z[k];
      }
    }
    return new Geometry.Mesh(n, n, xs, ys, zs);
  }

  /**
   * A range's ends as numbers, or null unless both are finite and the first is below the second.
   */
  static double[] range(Interval domain, Sampler sampler) {
    double lo = Recomputes.end(domain.lower(), sampler);
    double hi = Recomputes.end(domain.upper(), sampler);
    return Double.isFinite(lo) && Double.isFinite(hi) && lo < hi ? new double[] {lo, hi} : null;
  }
}
