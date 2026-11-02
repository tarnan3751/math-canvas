package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The slope of a line, segment or ray, updating live; undefined while it is vertical. Its label is
 * written at the middle of the part of it drawn.
 *
 * @param id the id
 * @param style the style
 * @param of the line's, segment's or ray's id
 */
public record Slope(ObjectId id, Style style, ObjectId of) implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public Slope {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(of, "of");
  }

  @Override
  public List<ObjectId> parents() {
    return List.of(of);
  }

  @Override
  public Set<String> reads() {
    return Set.of();
  }

  @Override
  public Slope withStyle(Style style) {
    return new Slope(id, style, of);
  }
}
