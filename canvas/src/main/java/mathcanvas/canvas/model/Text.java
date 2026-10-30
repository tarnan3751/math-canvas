package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Text at a world anchor.
 *
 * @param id the id
 * @param style the style
 * @param x the anchor's abscissa
 * @param y the anchor's ordinate
 * @param text the text
 */
public record Text(ObjectId id, Style style, double x, double y, String text)
    implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if a coordinate is not finite
   */
  public Text {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(text, "text");
    if (!Double.isFinite(x) || !Double.isFinite(y)) {
      throw new IllegalArgumentException("a text has a finite anchor");
    }
  }

  @Override
  public List<ObjectId> parents() {
    return List.of();
  }

  @Override
  public Set<String> reads() {
    return Set.of();
  }

  @Override
  public Text withStyle(Style style) {
    return new Text(id, style, x, y, text);
  }
}
