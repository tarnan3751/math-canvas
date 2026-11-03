package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The solution of a slope field's {@code y' = f(x, y)} through a point, integrated from it in both
 * directions by the sampler ({@link Sampler#solution}) to the edge of the widened window, a blow-up
 * or a step budget, so it follows the point as it is dragged and the field as it changes.
 *
 * @param id the id
 * @param style the style
 * @param field the slope field
 * @param point the point it passes through
 */
public record SolutionCurve(ObjectId id, Style style, ObjectId field, ObjectId point)
    implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public SolutionCurve {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(field, "field");
    Objects.requireNonNull(point, "point");
  }

  @Override
  public List<ObjectId> parents() {
    return List.of(field, point);
  }

  @Override
  public Set<String> reads() {
    return Set.of();
  }

  @Override
  public SolutionCurve withStyle(Style style) {
    return new SolutionCurve(id, style, field, point);
  }
}
