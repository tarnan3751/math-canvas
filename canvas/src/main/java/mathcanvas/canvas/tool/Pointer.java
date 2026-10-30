package mathcanvas.canvas.tool;

import java.util.Objects;
import java.util.Optional;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.canvas.model.ObjectId;

/**
 * Where the pointer is, in world coordinates, and what is drawn under it within the hit tolerance,
 * points first.
 *
 * @param x the abscissa
 * @param y the ordinate
 * @param picked the object under the pointer, if any
 */
public record Pointer(double x, double y, Optional<Picked> picked) {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if the pick is null
   */
  public Pointer {
    Objects.requireNonNull(picked, "picked");
  }

  /**
   * The pointer over nothing.
   *
   * @param x the abscissa
   * @param y the ordinate
   * @return the pointer
   */
  public static Pointer at(double x, double y) {
    return new Pointer(x, y, Optional.empty());
  }

  /**
   * An object under the pointer.
   *
   * @param id the object
   * @param hit where on its drawing: a point, or the nearest point of a curve
   */
  public record Picked(ObjectId id, Hit hit) {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public Picked {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(hit, "hit");
    }
  }
}
