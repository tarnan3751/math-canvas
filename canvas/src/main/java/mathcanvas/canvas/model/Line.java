package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;

/**
 * An infinite line: through two points, through a point with a slope, or constructed (a
 * perpendicular or a parallel through a point, a perpendicular bisector, an angle bisector, a
 * tangent to a circle). Its parameter is affine ({@link ParamKind#LINE}): through two points, the
 * first at 0 and the second at 1; through a point with slope {@code m}, the point at 0 and the
 * direction {@code (1, m)}; a constructed line, its point at 0 and a unit direction (a tangent,
 * from its point toward the point of contact).
 *
 * @param id the id
 * @param style the style
 * @param definition how the line is given
 */
public record Line(ObjectId id, Style style, Definition definition) implements CanvasObject {

  /** How a line is given. */
  public sealed interface Definition {}

  /**
   * The line through two points; undefined while they coincide.
   *
   * @param a the first point's id
   * @param b the second point's id
   */
  public record Through(ObjectId a, ObjectId b) implements Definition {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     * @throws IllegalArgumentException if the points are one
     */
    public Through {
      Objects.requireNonNull(a, "a");
      Objects.requireNonNull(b, "b");
      if (a.equals(b)) {
        throw new IllegalArgumentException("a line passes through two different points");
      }
    }
  }

  /**
   * The line through a point with a slope: the tangent form. The slope is evaluated with {@code at}
   * bound to the point's abscissa, so the tangent to the graph of {@code f} at a point on it is the
   * slope {@code f'(x)} at {@code x}; a slope free of {@code at} is a fixed slope. A slope that is
   * not a finite number (a vertical tangent) leaves the line undefined.
   *
   * @param point the point's id
   * @param slope the slope
   * @param at the variable bound to the point's abscissa
   */
  public record PointSlope(ObjectId point, Expr slope, Symbol at) implements Definition {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public PointSlope {
      Objects.requireNonNull(point, "point");
      Objects.requireNonNull(slope, "slope");
      Objects.requireNonNull(at, "at");
    }
  }

  /**
   * The line through a point perpendicular to a line, segment or ray.
   *
   * @param point the point's id
   * @param to the line's, segment's or ray's id
   */
  public record Perpendicular(ObjectId point, ObjectId to) implements Definition {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public Perpendicular {
      Objects.requireNonNull(point, "point");
      Objects.requireNonNull(to, "to");
    }
  }

  /**
   * The line through a point parallel to a line, segment or ray.
   *
   * @param point the point's id
   * @param to the line's, segment's or ray's id
   */
  public record Parallel(ObjectId point, ObjectId to) implements Definition {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public Parallel {
      Objects.requireNonNull(point, "point");
      Objects.requireNonNull(to, "to");
    }
  }

  /**
   * The perpendicular bisector of two points (of a segment, by its ends); undefined while they
   * coincide.
   *
   * @param a the first point's id
   * @param b the second point's id
   */
  public record PerpendicularBisector(ObjectId a, ObjectId b) implements Definition {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     * @throws IllegalArgumentException if the points are one
     */
    public PerpendicularBisector {
      Objects.requireNonNull(a, "a");
      Objects.requireNonNull(b, "b");
      if (a.equals(b)) {
        throw new IllegalArgumentException("a bisector is of two different points");
      }
    }
  }

  /**
   * The bisector of the angle at a vertex between the arms to two points: the line through the
   * vertex halving the angle, the perpendicular to the arms where they are opposite; undefined
   * while an arm has length zero.
   *
   * @param a the first arm's point
   * @param vertex the vertex
   * @param c the second arm's point
   */
  public record AngleBisector(ObjectId a, ObjectId vertex, ObjectId c) implements Definition {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     * @throws IllegalArgumentException if two of the points are one
     */
    public AngleBisector {
      Objects.requireNonNull(a, "a");
      Objects.requireNonNull(vertex, "vertex");
      Objects.requireNonNull(c, "c");
      if (a.equals(vertex) || c.equals(vertex) || a.equals(c)) {
        throw new IllegalArgumentException("an angle bisector is of three different points");
      }
    }
  }

  /**
   * A tangent to a circle through a point. From a point outside the circle there are two, told
   * apart by the side: index 0 touches the circle counterclockwise from the direction of the point
   * as seen from the centre, index 1 clockwise, so each follows the point continuously. From a
   * point on the circle both are the one tangent there; from a point inside, both are undefined.
   *
   * @param circle the circle's id
   * @param point the point's id
   * @param index 0 or 1
   */
  public record CircleTangent(ObjectId circle, ObjectId point, int index) implements Definition {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     * @throws IllegalArgumentException if the index is not 0 or 1
     */
    public CircleTangent {
      Objects.requireNonNull(circle, "circle");
      Objects.requireNonNull(point, "point");
      if (index != 0 && index != 1) {
        throw new IllegalArgumentException("a tangent's index is 0 or 1");
      }
    }
  }

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public Line {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(definition, "definition");
  }

  @Override
  public List<ObjectId> parents() {
    return switch (definition) {
      case Through t -> List.of(t.a(), t.b());
      case PointSlope p -> List.of(p.point());
      case Perpendicular p -> List.of(p.point(), p.to());
      case Parallel p -> List.of(p.point(), p.to());
      case PerpendicularBisector b -> List.of(b.a(), b.b());
      case AngleBisector b -> List.of(b.a(), b.vertex(), b.c());
      case CircleTangent t -> List.of(t.circle(), t.point());
    };
  }

  @Override
  public Set<String> reads() {
    return definition instanceof PointSlope p ? Reads.of(Set.of(p.at()), p.slope()) : Set.of();
  }

  @Override
  public Line withStyle(Style style) {
    return new Line(id, style, definition);
  }
}
