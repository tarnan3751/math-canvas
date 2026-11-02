package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The distance between two points, or from a point to a line, segment or ray (to its nearest
 * point), updating live; its label is written halfway along the shortest segment.
 *
 * @param id the id
 * @param style the style
 * @param a the point's id
 * @param b the other point's, or the line's, segment's or ray's, id
 */
public record Distance(ObjectId id, Style style, ObjectId a, ObjectId b) implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if the two are one
   */
  public Distance {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(a, "a");
    Objects.requireNonNull(b, "b");
    if (a.equals(b)) {
      throw new IllegalArgumentException("a distance is between two different objects");
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
  public Distance withStyle(Style style) {
    return new Distance(id, style, a, b);
  }
}
