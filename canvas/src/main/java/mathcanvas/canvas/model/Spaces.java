package mathcanvas.canvas.model;

import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Symbol;

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
      case ParametricSurface s -> parametric(s, sampler, cancellation);
      case SpaceCurve c -> curve(c, sampler, cancellation);
      case GradientArrow g -> gradient(g, parents.get(g.surface()), sampler);
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
    return new Geometry.Mesh(n, n, xs, ys, zs, Optional.of(s.body()));
  }

  /** {@code (x, y, z)} of {@code u} and {@code v} over its rectangle, as a surface is sampled. */
  private static Geometry parametric(
      ParametricSurface s, Sampler sampler, Cancellation cancellation) {
    double[] u = range(s.uDomain(), sampler);
    double[] v = range(s.vDomain(), sampler);
    if (u == null || v == null) {
      return Geometry.UNDEFINED;
    }
    int n = s.n();
    Symbol pu = ParametricSurface.U;
    Symbol pv = ParametricSurface.V;
    float[] xs = floats(sampler.grid(s.x(), pu, u[0], u[1], pv, v[0], v[1], n, cancellation));
    float[] ys = floats(sampler.grid(s.y(), pu, u[0], u[1], pv, v[0], v[1], n, cancellation));
    float[] zs = floats(sampler.grid(s.z(), pu, u[0], u[1], pv, v[0], v[1], n, cancellation));
    return new Geometry.Mesh(n, n, xs, ys, zs);
  }

  /** {@code (x, y, z)} of {@code t} at {@link SpaceCurve#SAMPLES} evenly spaced parameters. */
  private static Geometry curve(SpaceCurve c, Sampler sampler, Cancellation cancellation) {
    double[] t = range(c.domain(), sampler);
    if (t == null) {
      return Geometry.UNDEFINED;
    }
    int n = SpaceCurve.SAMPLES - 1;
    Symbol p = SpaceCurve.T;
    return new Geometry.Path3(
        floats(sampler.line(c.x(), p, t[0], t[1], n, cancellation)),
        floats(sampler.line(c.y(), p, t[0], t[1], n, cancellation)),
        floats(sampler.line(c.z(), p, t[0], t[1], n, cancellation)));
  }

  /**
   * The gradient of a surface at a point: undefined unless the point's coordinates, the height and
   * both partial derivatives there are finite.
   */
  private static Geometry gradient(GradientArrow g, Geometry surface, Sampler sampler) {
    if (!(surface instanceof Geometry.Mesh m) || m.height().isEmpty()) {
      return Geometry.UNDEFINED;
    }
    Expr f = m.height().get();
    double x = sampler.value(g.x(), Map.of());
    double y = sampler.value(g.y(), Map.of());
    if (!Double.isFinite(x) || !Double.isFinite(y)) {
      return Geometry.UNDEFINED;
    }
    Map<Symbol, Double> at = Map.of(Surface.X, x, Surface.Y, y);
    double z = sampler.value(f, at);
    double fx = sampler.value(Derivative.of(f, Surface.X, 1), at);
    double fy = sampler.value(Derivative.of(f, Surface.Y, 1), at);
    return Double.isFinite(z) && Double.isFinite(fx) && Double.isFinite(fy)
        ? new Geometry.Gradient(x, y, z, fx, fy)
        : Geometry.UNDEFINED;
  }

  private static float[] floats(double[] values) {
    float[] out = new float[values.length];
    for (int k = 0; k < values.length; k++) {
      out[k] = (float) values[k];
    }
    return out;
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
