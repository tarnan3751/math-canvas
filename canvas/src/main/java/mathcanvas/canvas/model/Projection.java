package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The projection {@code proj_v u} of a vector onto another (arrows, sums or projections), drawn
 * from {@code v}'s tail along {@code v}, with the dashed perpendicular from {@code u}'s head (from
 * {@code u} translated to {@code v}'s tail, drawn dashed, when the tails differ). Undefined while
 * {@code v} is the zero vector.
 *
 * @param id the id
 * @param style the style
 * @param u the vector projected
 * @param v the vector projected onto
 */
public record Projection(ObjectId id, Style style, ObjectId u, ObjectId v) implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if the vectors are one
   */
  public Projection {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(u, "u");
    Objects.requireNonNull(v, "v");
    if (u.equals(v)) {
      throw new IllegalArgumentException("a projection is onto another vector");
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
  public Projection withStyle(Style style) {
    return new Projection(id, style, u, v);
  }
}
