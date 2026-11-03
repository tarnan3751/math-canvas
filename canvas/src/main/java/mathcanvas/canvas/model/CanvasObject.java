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
        Angle,
        Transform,
        Distance,
        Area,
        Slope,
        Locus,
        // phase 7c: fields and contours
        VectorField,
        SlopeField,
        SolutionCurve,
        ContourPlot {

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
   * point, a midpoint, or the image of a point.
   *
   * @return true for a point
   */
  default boolean isPoint() {
    return this instanceof FreePoint
        || this instanceof PointOnPath
        || this instanceof Intersection
        || this instanceof CriticalPoint
        || this instanceof Midpoint
        || this instanceof Transform t && t.shape() == Transform.Shape.POINT;
  }

  /**
   * Whether the object is a path a point can lie on or an intersection can be taken of.
   *
   * @return true for a function graph, a parametric, polar or implicit curve, a segment, a line, a
   *     ray, a circle, a polygon, a locus, or the image of one of them
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
        || this instanceof Polygon
        || this instanceof Locus
        || this instanceof Transform t
            && switch (t.shape()) {
              case LINEAR, CIRCLE, POLYGON, CURVE -> true;
              case POINT, VECTOR, ANGLE -> false;
            };
  }

  /**
   * Whether the object is straight: a line, a segment or a ray, which a perpendicular or a parallel
   * is taken to.
   *
   * @return true for a line, a segment, a ray, or the image of one
   */
  default boolean isLinear() {
    return this instanceof Line
        || this instanceof Segment
        || this instanceof Ray
        || this instanceof Transform t && t.shape() == Transform.Shape.LINEAR;
  }

  /**
   * Whether the object is a circle, which a tangent is taken to.
   *
   * @return true for a circle, or the image of one
   */
  default boolean isCircle() {
    return this instanceof Circle
        || this instanceof Transform t && t.shape() == Transform.Shape.CIRCLE;
  }

  /**
   * Whether the object is a polygon, whose area is measured.
   *
   * @return true for a polygon, or the image of one
   */
  default boolean isPolygon() {
    return this instanceof Polygon
        || this instanceof Transform t && t.shape() == Transform.Shape.POLYGON;
  }

  /**
   * Whether the object is an angle, drawn as a mark with its measure.
   *
   * @return true for an angle, or the image of one
   */
  default boolean isAngle() {
    return this instanceof Angle
        || this instanceof Transform t && t.shape() == Transform.Shape.ANGLE;
  }

  /**
   * Whether the object is a measurement, a value drawn as a label.
   *
   * @return true for a distance, an area or a slope
   */
  default boolean isMeasure() {
    return this instanceof Distance || this instanceof Area || this instanceof Slope;
  }

  /**
   * Whether the object is a vector drawn as an arrow, which a vector sum adds and a projection
   * projects.
   *
   * @return true for an arrow, a vector sum, a projection, or the image of one
   */
  default boolean isVector() {
    return this instanceof Arrow
        || this instanceof VectorSum
        || this instanceof Projection
        || this instanceof Transform t && t.shape() == Transform.Shape.VECTOR;
  }
}
