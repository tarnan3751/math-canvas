package mathcanvas.canvas.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Where an intersection last was: its world position, its parameters on its two paths (NaN on a
 * path with no parameter), whether it was a tangency, and the side it came from. An intersection
 * keeps it while undefined, so it re-attaches to a candidate near where it vanished. It is part of
 * the intersection's record, so it is saved with the document and restored by an undo.
 *
 * <p>{@code approach} is where the intersection last was a simple root (not a tangency), empty when
 * it never was one. When several intersections of one pair meet at a tangency, or vanish at one
 * point, their anchors coincide and distance cannot tell them apart; the side each approached from
 * does (docs/reference/canvas.md, **Object model**).
 *
 * @param x the abscissa
 * @param y the ordinate
 * @param t1 the parameter on the first path, or NaN
 * @param t2 the parameter on the second path, or NaN
 * @param tangent whether the paths touched there rather than crossed
 * @param approach where it last was a simple root, if ever
 */
public record Anchor(
    double x, double y, double t1, double t2, boolean tangent, Optional<Approach> approach) {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if the approach is null
   * @throws IllegalArgumentException if the position is not finite or a parameter is infinite
   */
  public Anchor {
    Objects.requireNonNull(approach, "approach");
    if (!Double.isFinite(x) || !Double.isFinite(y)) {
      throw new IllegalArgumentException("an anchor has a finite position");
    }
    if (Double.isInfinite(t1) || Double.isInfinite(t2)) {
      throw new IllegalArgumentException("an anchor's parameter is finite or NaN");
    }
  }
}
