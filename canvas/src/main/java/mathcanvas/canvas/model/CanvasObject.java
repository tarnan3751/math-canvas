package mathcanvas.canvas.model;

import java.util.List;
import java.util.Set;

/**
 * An object on the canvas: an immutable record with a stable id, a style and a definition. The
 * definition is the algebraic side (expressions, parameters, parent ids); the geometry drawn for it
 * is computed by a {@link Recompute} and held by the {@link CanvasState}. Code over objects uses an
 * exhaustive {@code switch} with no {@code default}, so a new kind is a compile error wherever it
 * is not handled.
 */
public sealed interface CanvasObject
    permits FreePoint,
        PointOnPath,
        Intersection,
        CriticalPoint,
        FunctionGraph,
        ParametricCurve,
        PolarCurve,
        ImplicitCurve,
        InequalityRegion,
        ShadedArea,
        RiemannSum,
        Slider,
        Text,
        Segment,
        Line,
        Arrow,
        VectorSum,
        Projection,
        Ray,
        Circle,
        Midpoint,
        Polygon,
        Angle {

  /**
   * The object's id.
   *
   * @return the id
   */
  ObjectId id();

  /**
   * How it is drawn.
   *
   * @return the style
   */
  Style style();

  /**
   * The objects its geometry is computed from, in order.
   *
   * @return the parent ids, possibly none
   */
  List<ObjectId> parents();

  /**
   * The engine variables its definition reads: every name that, when its value changes, changes
   * this object's geometry. An over-approximation is allowed (it costs a recompute), a missing name
   * is not.
   *
   * @return the names
   */
  Set<String> reads();

  /**
   * This object with another style.
   *
   * @param style the style
   * @return the object
   */
  CanvasObject withStyle(Style style);

  /**
   * Whether the object is a point: a free point, a point on a path, an intersection, a critical
   * point or a midpoint.
   *
   * @return true for a point
   */
  default boolean isPoint() {
    return this instanceof FreePoint
        || this instanceof PointOnPath
        || this instanceof Intersection
        || this instanceof CriticalPoint
        || this instanceof Midpoint;
  }

  /**
   * Whether the object is a path a point can lie on or an intersection can be taken of.
   *
   * @return true for a function graph, a parametric, polar or implicit curve, a segment, a line, a
   *     ray, a circle or a polygon
   */
  default boolean isPath() {
    return this instanceof FunctionGraph
        || this instanceof ParametricCurve
        || this instanceof PolarCurve
        || this instanceof ImplicitCurve
        || this instanceof Segment
        || this instanceof Line
        || this instanceof Ray
        || this instanceof Circle
        || this instanceof Polygon;
  }

  /**
   * Whether the object is straight: a line, a segment or a ray, which a perpendicular or a parallel
   * is taken to.
   *
   * @return true for a line, a segment or a ray
   */
  default boolean isLinear() {
    return this instanceof Line || this instanceof Segment || this instanceof Ray;
  }

  /**
   * Whether the object is a circle, which a tangent is taken to.
   *
   * @return true for a circle
   */
  default boolean isCircle() {
    return this instanceof Circle;
  }

  /**
   * Whether the object is a vector drawn as an arrow, which a vector sum adds and a projection
   * projects.
   *
   * @return true for an arrow, a vector sum or a projection
   */
  default boolean isVector() {
    return this instanceof Arrow || this instanceof VectorSum || this instanceof Projection;
  }
}
