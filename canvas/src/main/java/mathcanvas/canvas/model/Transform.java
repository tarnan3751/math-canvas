package mathcanvas.canvas.model;

import java.util.Optional;

/**
 * An object that is the image of another under a transformation of the plane (a reflection, a
 * rotation, a translation, a dilation): a derived object of its source's kind, its geometry the
 * source's mapped. Its {@link Shape} is the source's, fixed when it is made and checked against the
 * source by the state, so whether it is a point, a path, a vector or an angle is known from the
 * record alone, as for every other kind.
 */
public sealed interface Transform extends CanvasObject
    permits Reflection, Rotation, Translation, Dilation {

  /** What an image is: the source's category. */
  enum Shape {
    /** A point. */
    POINT,
    /** A line, segment or ray. */
    LINEAR,
    /** A circle. */
    CIRCLE,
    /** A polygon. */
    POLYGON,
    /** Any other curve: a graph, a parametric, polar or implicit curve, a locus. */
    CURVE,
    /** A vector. */
    VECTOR,
    /** An angle. */
    ANGLE
  }

  /**
   * The object transformed.
   *
   * @return its id
   */
  ObjectId source();

  /**
   * The source's category, which is the image's.
   *
   * @return the shape
   */
  Shape shape();

  /**
   * The category of an object that can be transformed.
   *
   * @param o the object
   * @return its shape, empty for an object that is not transformed (a slider, a text, a region, an
   *     area, a Riemann sum, a measurement)
   */
  static Optional<Shape> shapeOf(CanvasObject o) {
    return switch (o) {
      case Transform t -> Optional.of(t.shape());
      case FreePoint _, PointOnPath _, Intersection _, CriticalPoint _, Midpoint _ ->
          Optional.of(Shape.POINT);
      case Line _, Segment _, Ray _ -> Optional.of(Shape.LINEAR);
      case Circle _ -> Optional.of(Shape.CIRCLE);
      case Polygon _ -> Optional.of(Shape.POLYGON);
      case FunctionGraph _, ParametricCurve _, PolarCurve _, ImplicitCurve _, Locus _ ->
          Optional.of(Shape.CURVE);
      case Arrow _, VectorSum _, Projection _ -> Optional.of(Shape.VECTOR);
      case Angle _ -> Optional.of(Shape.ANGLE);
      case InequalityRegion _,
          ShadedArea _,
          RiemannSum _,
          Slider _,
          Text _,
          Distance _,
          Area _,
          Slope _ ->
          Optional.empty();
    };
  }
}
