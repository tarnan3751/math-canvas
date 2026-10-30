package mathcanvas.app.ui;

import java.util.Optional;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.Path;
import mathcanvas.canvas.model.Polyline;

/**
 * Where a point of the plane falls on a sampled curve, for placing and dragging a point on it: the
 * nearest point of the curve's polylines, with the path's parameter interpolated linearly between
 * the two samples of its segment. Plain {@code double} geometry on what the worker sampled; the
 * point's recompute then evaluates the path at the parameter.
 */
final class PathProjection {

  /**
   * A projection.
   *
   * @param kind what the parameter means; {@link ParamKind#NONE} on a path with no parameter
   * @param t the parameter, NaN when the kind is {@code NONE}
   * @param x the abscissa of the nearest point
   * @param y the ordinate of the nearest point
   */
  record Projection(ParamKind kind, double t, double x, double y) {}

  private PathProjection() {}

  /**
   * Projects a point onto a curve.
   *
   * @param curve the curve
   * @param x the abscissa
   * @param y the ordinate
   * @return the projection, or empty when the curve has no sample
   */
  static Optional<Projection> onto(Geometry.Curve curve, double x, double y) {
    ParamKind kind = curve.path() instanceof Path.Parametrised p ? p.kind() : ParamKind.NONE;
    double best = Double.POSITIVE_INFINITY;
    Projection near = null;
    for (Polyline b : curve.branches()) {
      for (int k = 0; k < b.size(); k++) {
        int j = Math.min(k + 1, b.size() - 1);
        double dx = b.x(j) - b.x(k);
        double dy = b.y(j) - b.y(k);
        double d2 = dx * dx + dy * dy;
        double u = d2 == 0 ? 0 : Math.clamp(((x - b.x(k)) * dx + (y - b.y(k)) * dy) / d2, 0, 1);
        double px = b.x(k) + u * dx;
        double py = b.y(k) + u * dy;
        double dist = (px - x) * (px - x) + (py - y) * (py - y);
        if (dist < best) {
          best = dist;
          double t = kind == ParamKind.NONE ? Double.NaN : b.t(k) + u * (b.t(j) - b.t(k));
          near = new Projection(kind, t, px, py);
        }
      }
    }
    return Optional.ofNullable(near);
  }
}
