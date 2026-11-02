package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The point halfway between two points: a derived point, not dragged. Two coincident points have
 * their common point as the midpoint.
 *
 * @param id the id
 * @param style the style
 * @param a the first point's id
 * @param b the second point's id
 */
public record Midpoint(ObjectId id, Style style, ObjectId a, ObjectId b) implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if the points are one
   */
  public Midpoint {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(a, "a");
    Objects.requireNonNull(b, "b");
    if (a.equals(b)) {
      throw new IllegalArgumentException("a midpoint is of two different points");
    }
  }

  @Override
  public List<ObjectId> parents() {
    return List.of(a, b);
  }

  @Override
  public Set<String> reads() {
    return Set.of();
  }

  @Override
  public Midpoint withStyle(Style style) {
    return new Midpoint(id, style, a, b);
  }
}
