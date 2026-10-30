package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The segment between two points; its parameter runs from 0 at the start to 1 at the end ({@link
 * ParamKind#LINE}). Two coincident ends make a segment of length zero, which is defined.
 *
 * @param id the id
 * @param style the style
 * @param start the first point's id
 * @param end the second point's id
 */
public record Segment(ObjectId id, Style style, ObjectId start, ObjectId end)
    implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if the ends are one point
   */
  public Segment {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    if (start.equals(end)) {
      throw new IllegalArgumentException("a segment joins two different points");
    }
  }

  @Override
  public List<ObjectId> parents() {
    return List.of(start, end);
  }

  @Override
  public Set<String> reads() {
    return Set.of();
  }

  @Override
  public Segment withStyle(Style style) {
    return new Segment(id, style, start, end);
  }
}
