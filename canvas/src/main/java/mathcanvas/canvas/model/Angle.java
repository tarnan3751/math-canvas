package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The angle at a vertex between the arms to two points, never reflex (from 0 to π, whatever the
 * order of the points), drawn as an arc on the smaller side. Its measure is in radians; the app
 * writes it in the angle mode. Undefined while an arm has length zero.
 *
 * @param id the id
 * @param style the style
 * @param a the first arm's point
 * @param vertex the vertex
 * @param c the second arm's point
 */
public record Angle(ObjectId id, Style style, ObjectId a, ObjectId vertex, ObjectId c)
    implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if two of the points are one
   */
  public Angle {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(a, "a");
    Objects.requireNonNull(vertex, "vertex");
    Objects.requireNonNull(c, "c");
    if (a.equals(vertex) || c.equals(vertex) || a.equals(c)) {
      throw new IllegalArgumentException("an angle is of three different points");
    }
  }

  @Override
  public List<ObjectId> parents() {
    return List.of(a, vertex, c);
  }

  @Override
  public Set<String> reads() {
    return Set.of();
  }

  @Override
  public Angle withStyle(Style style) {
    return new Angle(id, style, a, vertex, c);
  }
}
