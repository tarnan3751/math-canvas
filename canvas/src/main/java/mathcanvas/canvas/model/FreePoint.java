package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A point at world coordinates, moved by dragging.
 *
 * @param id the id
 * @param style the style
 * @param x the abscissa
 * @param y the ordinate
 */
public record FreePoint(ObjectId id, Style style, double x, double y) implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if the id or the style is null
   * @throws IllegalArgumentException if a coordinate is not finite
   */
  public FreePoint {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    if (!Double.isFinite(x) || !Double.isFinite(y)) {
      throw new IllegalArgumentException("a free point has finite coordinates");
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
  public FreePoint withStyle(Style style) {
    return new FreePoint(id, style, x, y);
  }

  /**
   * This point moved.
   *
   * @param x the abscissa
   * @param y the ordinate
   * @return the point
   */
  public FreePoint at(double x, double y) {
    return new FreePoint(id, style, x, y);
  }
}
