package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;

/**
 * The geometric side of an object, computed from its definition by a {@link Recompute}: what the
 * painter draws and what hit testing and intersections work on. Undefined is a state, not an error:
 * an object whose geometry cannot be computed has {@link #UNDEFINED}, so do its dependents, and the
 * state clears when its parents are defined again.
 */
public sealed interface Geometry {

  /** The undefined state. */
  record Undefined() implements Geometry {}

  /** The one undefined geometry. */
  Undefined UNDEFINED = new Undefined();

  /**
   * A point.
   *
   * @param x the abscissa, finite
   * @param y the ordinate, finite
   */
  record Point(double x, double y) implements Geometry {

    /**
     * Checks the fields.
     *
     * @throws IllegalArgumentException if a coordinate is not finite
     */
    public Point {
      if (!Double.isFinite(x) || !Double.isFinite(y)) {
        throw new IllegalArgumentException("a point is finite");
      }
    }
  }

  /**
   * A curve: how to evaluate it, and its sampled branches. A curve that is defined may have no
   * branch in the sampled range.
   *
   * @param path how the curve is evaluated away from its samples
   * @param branches the polylines, split at gaps
   */
  record Curve(Path path, List<Polyline> branches) implements Geometry {

    /**
     * Copies the branches.
     *
     * @throws NullPointerException if a field is null
     */
    public Curve {
      Objects.requireNonNull(path, "path");
      branches = List.copyOf(branches);
    }
  }

  /**
   * A region, the inside of its rings under the even-odd rule.
   *
   * @param rings the closed boundary polylines
   */
  record Region(List<Polyline> rings) implements Geometry {

    /** Copies the rings. */
    public Region {
      rings = List.copyOf(rings);
    }
  }

  /**
   * A region with its value: a shaded area's integral, or a Riemann sum's rectangles and their sum.
   * The rings are closed polylines, the inside of the region under the even-odd rule.
   *
   * @param rings the closed boundary polylines
   * @param value the value, finite
   */
  record ValuedRegion(List<Polyline> rings, double value) implements Geometry {

    /**
     * Copies the rings.
     *
     * @throws IllegalArgumentException if the value is not finite
     */
    public ValuedRegion {
      rings = List.copyOf(rings);
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("a valued region has a finite value");
      }
    }
  }

  /**
   * A number with nothing to draw on the plane: a slider's value.
   *
   * @param value the value
   */
  record Value(double value) implements Geometry {}
}
