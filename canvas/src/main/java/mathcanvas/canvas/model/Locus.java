package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The locus of a point as a driver, a point on a path, moves along its path: the path's parameter
 * range is sampled, the point recomputed at each sample through the objects between the two, and
 * the positions drawn as polylines (docs/reference/canvas.md, <b>Loci</b>). A path with no
 * parameter of its own. Undefined unless the driver is a point on a parametrised path, placed by
 * its parameter (not at an expression), and the point depends on it.
 *
 * @param id the id
 * @param style the style
 * @param point the point traced
 * @param driver the point on a path that moves
 */
public record Locus(ObjectId id, Style style, ObjectId point, ObjectId driver)
    implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if the point is its own driver
   */
  public Locus {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(point, "point");
    Objects.requireNonNull(driver, "driver");
    if (point.equals(driver)) {
      throw new IllegalArgumentException("a locus traces another point than its driver");
    }
  }

  @Override
  public List<ObjectId> parents() {
    return List.of(point, driver);
  }

  @Override
  public Set<String> reads() {
    return Set.of();
  }

  @Override
  public Locus withStyle(Style style) {
    return new Locus(id, style, point, driver);
  }
}
