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

  /**
   * A vector drawn as an arrow from its tail to its head, with the construction lines drawn with
   * it: a vector sum's translated vector and parallelogram sides, a projection's perpendicular.
   *
   * @param tail the tail
   * @param head the head
   * @param guides the construction lines
   */
  record Arrow(Point tail, Point head, List<Guide> guides) implements Geometry {

    /**
     * Copies the guides.
     *
     * @throws NullPointerException if a field is null
     */
    public Arrow {
      Objects.requireNonNull(tail, "tail");
      Objects.requireNonNull(head, "head");
      guides = List.copyOf(guides);
    }

    /**
     * The vector's abscissa, the head's less the tail's.
     *
     * @return it
     */
    public double dx() {
      return head.x() - tail.x();
    }

    /**
     * The vector's ordinate, the head's less the tail's.
     *
     * @return it
     */
    public double dy() {
      return head.y() - tail.y();
    }

    /**
     * The scalar component of this vector along another, {@code u·v / |v|}: the signed length of
     * this vector's projection onto it.
     *
     * @param v the vector along which
     * @return the component, NaN when {@code v} is the zero vector
     */
    public double componentAlong(Arrow v) {
      double length = Math.hypot(v.dx(), v.dy());
      return length > 0 ? (dx() * v.dx() + dy() * v.dy()) / length : Double.NaN;
    }
  }

  /**
   * A construction line drawn with an arrow: an arrow of its own (a vector sum's translated
   * vector), or a dashed line.
   *
   * @param from where it starts
   * @param to where it ends
   * @param arrow whether it is drawn solid with a head, else dashed with none
   */
  record Guide(Point from, Point to, boolean arrow) {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public Guide {
      Objects.requireNonNull(from, "from");
      Objects.requireNonNull(to, "to");
    }
  }

  /**
   * An angle at a vertex, drawn as an arc from the direction {@code start} counterclockwise through
   * {@code sweep}: the angle between two arms, never reflex, so the arc is on the smaller side.
   *
   * @param vertex the vertex
   * @param start the direction of the arc's first arm, in radians
   * @param sweep the measure, in radians, from 0 to π
   */
  record Angle(Point vertex, double start, double sweep) implements Geometry {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if the vertex is null
     * @throws IllegalArgumentException if the start is not finite or the sweep is outside [0, π]
     */
    public Angle {
      Objects.requireNonNull(vertex, "vertex");
      if (!Double.isFinite(start) || !(sweep >= 0 && sweep <= Math.PI)) {
        throw new IllegalArgumentException("an angle has a finite start and a sweep in [0, π]");
      }
    }
  }
}
