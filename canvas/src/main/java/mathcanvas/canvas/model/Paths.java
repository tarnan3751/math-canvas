package mathcanvas.canvas.model;

import java.util.Optional;
import mathcanvas.engine.Cancellation;

/**
 * Points of a path: evaluating a parametrised path at a parameter, and projecting a world point
 * onto a path. Plain {@code double} geometry; expressions are evaluated only through the {@link
 * Sampler}.
 */
final class Paths {

  /** Golden-section steps when refining a projection, enough to reach a double's resolution. */
  private static final int GOLDEN_STEPS = 60;

  private static final double INV_PHI = (Math.sqrt(5) - 1) / 2;

  private Paths() {}

  /**
   * The point of a path at a parameter.
   *
   * @param p the path
   * @param t the parameter
   * @param sampler evaluates an expressed path
   * @return the point, undefined outside the domain or where the path is
   */
  static Sample point(Path.Parametrised p, double t, Sampler sampler) {
    if (!p.contains(t)) {
      return Sample.UNDEFINED;
    }
    return switch (p.parametrisation()) {
      case Parametrisation.Linear l -> l.at(t);
      case Parametrisation.Expressed e -> sampler.point(e, t);
    };
  }

  /**
   * The point of a path nearest a world point, with its parameter.
   *
   * @param t the parameter, NaN on a path with none
   * @param x the abscissa
   * @param y the ordinate
   */
  record Projection(double t, double x, double y) {}

  /**
   * Projects a world point onto a curve: exactly onto a line or segment, else onto the nearest
   * sampled segment and then, on a parametrised curve, refined by a golden-section search of the
   * parameter between the neighbouring samples.
   *
   * @param c the curve
   * @param x the abscissa
   * @param y the ordinate
   * @param sampler evaluates an expressed path
   * @param cancellation checked every 1,024 vertices and at every step of the search
   * @return the projection, empty when the curve has no sample
   */
  static Optional<Projection> project(
      Geometry.Curve c, double x, double y, Sampler sampler, Cancellation cancellation) {
    if (c.path() instanceof Path.Parametrised p
        && p.parametrisation() instanceof Parametrisation.Linear l) {
      double d2 = l.dx() * l.dx() + l.dy() * l.dy();
      double t = d2 == 0 ? 0 : ((x - l.px()) * l.dx() + (y - l.py()) * l.dy()) / d2;
      t = Math.clamp(t, p.lo(), p.hi());
      Sample s = l.at(t);
      return Optional.of(new Projection(t, s.x(), s.y()));
    }
    double best = Double.POSITIVE_INFINITY;
    Projection near = null;
    double lo = Double.NaN;
    double hi = Double.NaN;
    for (Polyline b : c.branches()) {
      for (int k = 0; k < b.size(); k++) {
        if ((k & 1023) == 0) {
          cancellation.check();
        }
        int j = Math.min(k + 1, b.size() - 1);
        double ax = b.x(k);
        double ay = b.y(k);
        double dx = b.x(j) - ax;
        double dy = b.y(j) - ay;
        double d2 = dx * dx + dy * dy;
        double u = d2 == 0 ? 0 : Math.clamp(((x - ax) * dx + (y - ay) * dy) / d2, 0, 1);
        double px = ax + u * dx;
        double py = ay + u * dy;
        double dist = (px - x) * (px - x) + (py - y) * (py - y);
        if (dist < best) {
          best = dist;
          near = new Projection(b.t(k) + u * (b.t(j) - b.t(k)), px, py);
          lo = b.t(Math.max(k - 1, 0));
          hi = b.t(Math.min(k + 2, b.size() - 1));
        }
      }
    }
    if (near == null) {
      return Optional.empty();
    }
    if (c.path() instanceof Path.Parametrised p && !Double.isNaN(near.t())) {
      Projection refined =
          golden(p, Math.max(lo, p.lo()), Math.min(hi, p.hi()), x, y, sampler, cancellation);
      if (refined != null && distance2(refined, x, y) <= best) {
        return Optional.of(refined);
      }
    }
    return Optional.of(near);
  }

  private static double distance2(Projection p, double x, double y) {
    return (p.x() - x) * (p.x() - x) + (p.y() - y) * (p.y() - y);
  }

  /** The parameter in {@code [a, b]} whose point is nearest, by golden-section search. */
  private static Projection golden(
      Path.Parametrised p,
      double a,
      double b,
      double x,
      double y,
      Sampler sampler,
      Cancellation cancellation) {
    double c = b - INV_PHI * (b - a);
    double d = a + INV_PHI * (b - a);
    double fc = cost(p, c, x, y, sampler);
    double fd = cost(p, d, x, y, sampler);
    for (int k = 0; k < GOLDEN_STEPS && b - a > 0; k++) {
      cancellation.check();
      if (fc < fd) {
        b = d;
        d = c;
        fd = fc;
        c = b - INV_PHI * (b - a);
        fc = cost(p, c, x, y, sampler);
      } else {
        a = c;
        c = d;
        fc = fd;
        d = a + INV_PHI * (b - a);
        fd = cost(p, d, x, y, sampler);
      }
    }
    double t = (a + b) / 2;
    Sample s = point(p, t, sampler);
    return s.isFinite() ? new Projection(t, s.x(), s.y()) : null;
  }

  private static double cost(Path.Parametrised p, double t, double x, double y, Sampler sampler) {
    Sample s = point(p, t, sampler);
    if (!s.isFinite()) {
      return Double.POSITIVE_INFINITY;
    }
    return (s.x() - x) * (s.x() - x) + (s.y() - y) * (s.y() - y);
  }
}
