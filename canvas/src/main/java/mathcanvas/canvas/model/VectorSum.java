package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The sum {@code u + v} of two vectors (arrows, sums or projections), drawn from {@code u}'s tail,
 * with {@code v} translated to {@code u}'s head and the parallelogram's other sides dashed.
 *
 * @param id the id
 * @param style the style
 * @param u the first vector's id
 * @param v the second vector's id
 */
public record VectorSum(ObjectId id, Style style, ObjectId u, ObjectId v) implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if the vectors are one
   */
  public VectorSum {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(u, "u");
    Objects.requireNonNull(v, "v");
    if (u.equals(v)) {
      throw new IllegalArgumentException("a vector sum adds two different vectors");
    }
  }

  @Override
  public List<ObjectId> parents() {
    return List.of(u, v);
  }

  @Override
  public Set<String> reads() {
    return Set.of();
  }

  @Override
  public VectorSum withStyle(Style style) {
    return new VectorSum(id, style, u, v);
  }
}
