package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;

/**
 * An infinite line, through two points or through a point with a slope. Its parameter is affine
 * ({@link ParamKind#LINE}): through two points, the first at 0 and the second at 1; through a point
 * with slope {@code m}, the point at 0 and the direction {@code (1, m)}.
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
    };
  }

  @Override
  public Set<String> reads() {
    return switch (definition) {
      case Through _ -> Set.of();
      case PointSlope p -> Reads.of(Set.of(p.at()), p.slope());
    };
  }

  @Override
  public Line withStyle(Style style) {
    return new Line(id, style, definition);
  }
}
