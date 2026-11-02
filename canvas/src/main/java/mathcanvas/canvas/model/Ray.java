package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The ray from a point through another; its parameter runs from 0 at the start, through 1 at the
 * second point, to ∞ ({@link ParamKind#LINE}). Undefined while the points coincide.
 *
 * @param id the id
 * @param style the style
 * @param start the start's id
 * @param through the id of the point it passes through
 */
public record Ray(ObjectId id, Style style, ObjectId start, ObjectId through)
    implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if the points are one
   */
  public Ray {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(through, "through");
    if (start.equals(through)) {
      throw new IllegalArgumentException("a ray passes through two different points");
    }
  }

  @Override
  public List<ObjectId> parents() {
    return List.of(start, through);
  }

  @Override
  public Set<String> reads() {
    return Set.of();
  }

  @Override
  public Ray withStyle(Style style) {
    return new Ray(id, style, start, through);
  }
}
