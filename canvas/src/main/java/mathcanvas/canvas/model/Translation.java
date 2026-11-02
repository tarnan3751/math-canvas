package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The translation of an object by a vector: an arrow (any vector object), or the vector from one
 * point to another.
 *
 * @param id the id
 * @param style the style
 * @param source the object translated
 * @param shape the source's category
 * @param by the vector
 */
public record Translation(ObjectId id, Style style, ObjectId source, Shape shape, By by)
    implements Transform {

  /** What an object is translated by. */
  public sealed interface By {}

  /**
   * By a vector object.
   *
   * @param vector its id
   */
  public record Vector(ObjectId vector) implements By {

    /**
     * Checks the field.
     *
     * @throws NullPointerException if it is null
     */
    public Vector {
      Objects.requireNonNull(vector, "vector");
    }
  }

  /**
   * By the vector from one point to another.
   *
   * @param from the first point's id
   * @param to the second point's id
   */
  public record Points(ObjectId from, ObjectId to) implements By {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     * @throws IllegalArgumentException if the points are one
     */
    public Points {
      Objects.requireNonNull(from, "from");
      Objects.requireNonNull(to, "to");
      if (from.equals(to)) {
        throw new IllegalArgumentException("a translation is between two different points");
      }
    }
  }

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public Translation {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(shape, "shape");
    Objects.requireNonNull(by, "by");
  }

  @Override
  public List<ObjectId> parents() {
    return switch (by) {
      case Vector v -> List.of(source, v.vector());
      case Points p -> List.of(source, p.from(), p.to());
    };
  }

  @Override
  public Set<String> reads() {
    return Set.of();
  }

  @Override
  public Translation withStyle(Style style) {
    return new Translation(id, style, source, shape, by);
  }
}
