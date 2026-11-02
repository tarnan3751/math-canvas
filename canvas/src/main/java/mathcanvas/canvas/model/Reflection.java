package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The reflection of an object in a line, segment or ray (the line it lies on) or in a point (a half
 * turn about it). Undefined while the source or the mirror is.
 *
 * @param id the id
 * @param style the style
 * @param source the object reflected
 * @param shape the source's category
 * @param in the line, segment, ray or point reflected in
 */
public record Reflection(ObjectId id, Style style, ObjectId source, Shape shape, ObjectId in)
    implements Transform {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if the source is its own mirror
   */
  public Reflection {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(shape, "shape");
    Objects.requireNonNull(in, "in");
    if (source.equals(in)) {
      throw new IllegalArgumentException("an object is not reflected in itself");
    }
  }

  @Override
  public List<ObjectId> parents() {
    return List.of(source, in);
  }

  @Override
  public Set<String> reads() {
    return Set.of();
  }

  @Override
  public Reflection withStyle(Style style) {
    return new Reflection(id, style, source, shape, in);
  }
}
