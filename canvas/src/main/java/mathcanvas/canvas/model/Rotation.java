package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Expr;

/**
 * The rotation of an object about a point by an angle: a number, or an expression that reads
 * sliders, in the document's angle mode (its cosine and sine are evaluated through the sampler, so
 * a plain number is in the mode's unit), counterclockwise. Undefined while the angle is not a real
 * number.
 *
 * @param id the id
 * @param style the style
 * @param source the object rotated
 * @param shape the source's category
 * @param center the centre's id
 * @param angle the angle
 */
public record Rotation(
    ObjectId id, Style style, ObjectId source, Shape shape, ObjectId center, Expr angle)
    implements Transform {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if the source is its own centre
   */
  public Rotation {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(shape, "shape");
    Objects.requireNonNull(center, "center");
    Objects.requireNonNull(angle, "angle");
    if (source.equals(center)) {
      throw new IllegalArgumentException("a point is not rotated about itself");
    }
  }

  @Override
  public List<ObjectId> parents() {
    return List.of(source, center);
  }

  @Override
  public Set<String> reads() {
    return Reads.of(Set.of(), angle);
  }

  @Override
  public Rotation withStyle(Style style) {
    return new Rotation(id, style, source, shape, center, angle);
  }
}
