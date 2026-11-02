package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Expr;

/**
 * The dilation of an object from a point by a factor: a number, or an expression that reads
 * sliders; a negative factor dilates through the centre. Undefined while the factor is not a real
 * number, and, as any construction, while the image is degenerate (a circle dilated by 0).
 *
 * @param id the id
 * @param style the style
 * @param source the object dilated
 * @param shape the source's category
 * @param center the centre's id
 * @param factor the factor
 */
public record Dilation(
    ObjectId id, Style style, ObjectId source, Shape shape, ObjectId center, Expr factor)
    implements Transform {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if the source is its own centre
   */
  public Dilation {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(shape, "shape");
    Objects.requireNonNull(center, "center");
    Objects.requireNonNull(factor, "factor");
    if (source.equals(center)) {
      throw new IllegalArgumentException("a point is not dilated from itself");
    }
  }

  @Override
  public List<ObjectId> parents() {
    return List.of(source, center);
  }

  @Override
  public Set<String> reads() {
    return Reads.of(Set.of(), factor);
  }

  @Override
  public Dilation withStyle(Style style) {
    return new Dilation(id, style, source, shape, center, factor);
  }
}
