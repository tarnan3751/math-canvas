package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A point on a path, at a parameter in the path's own parameterisation: the x of a function graph,
 * the t of a parametric curve, the θ of a polar curve, the affine t of a line or segment. When the
 * path moves the parameter stays and the point lands on the new path; outside the path's domain the
 * point is undefined, and defined again at the same parameter when the domain grows back.
 *
 * <p>The last position, where the point last landed, is kept for the two cases a parameter cannot
 * serve: a path redefined with another kind of parameter, onto which the last position is projected
 * once, and a path with no parameter (an implicit curve, {@link ParamKind#NONE}), onto which it is
 * projected at every recompute. The recompute updates it; the parameter it changes only in those
 * two cases.
 *
 * @param id the id
 * @param style the style
 * @param path the path's id
 * @param kind what the parameter means
 * @param parameter the parameter, finite unless the kind is {@link ParamKind#NONE}
 * @param lastX the abscissa where the point last landed, or NaN before it ever has
 * @param lastY the ordinate where the point last landed, or NaN before it ever has
 */
public record PointOnPath(
    ObjectId id,
    Style style,
    ObjectId path,
    ParamKind kind,
    double parameter,
    double lastX,
    double lastY)
    implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a reference is null
   * @throws IllegalArgumentException if the parameter is not finite for a kind that has one, or the
   *     last position is half known
   */
  public PointOnPath {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(kind, "kind");
    if (kind != ParamKind.NONE && !Double.isFinite(parameter)) {
      throw new IllegalArgumentException("a point on a path has a finite parameter");
    }
    if (Double.isNaN(lastX) != Double.isNaN(lastY)
        || Double.isInfinite(lastX)
        || Double.isInfinite(lastY)) {
      throw new IllegalArgumentException("a last position is finite or unknown");
    }
  }

  @Override
  public List<ObjectId> parents() {
    return List.of(path);
  }

  @Override
  public Set<String> reads() {
    return Set.of();
  }

  @Override
  public PointOnPath withStyle(Style style) {
    return new PointOnPath(id, style, path, kind, parameter, lastX, lastY);
  }

  /**
   * This point at another parameter, as a drag along the path sets it.
   *
   * @param kind what the parameter means
   * @param parameter the parameter
   * @return the point
   */
  public PointOnPath withParameter(ParamKind kind, double parameter) {
    return new PointOnPath(id, style, path, kind, parameter, lastX, lastY);
  }

  /**
   * This point with its last position.
   *
   * @param x the abscissa
   * @param y the ordinate
   * @return the point
   */
  public PointOnPath landedAt(double x, double y) {
    return new PointOnPath(id, style, path, kind, parameter, x, y);
  }
}
