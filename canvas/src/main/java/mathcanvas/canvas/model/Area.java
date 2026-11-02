package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The area of a polygon (by the shoelace formula, unsigned; a polygon that crosses itself counts
 * its lobes with their signs) or of a circle, updating live; its label is written at the centre.
 *
 * @param id the id
 * @param style the style
 * @param of the polygon's or circle's id
 */
public record Area(ObjectId id, Style style, ObjectId of) implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public Area {
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
  public Area withStyle(Style style) {
    return new Area(id, style, of);
  }
}
