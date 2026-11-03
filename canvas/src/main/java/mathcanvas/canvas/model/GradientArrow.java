package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Expr;

/**
 * The gradient of a surface {@code z = f(x, y)} at a point {@code (x₀, y₀)}: the point on the
 * surface, marked, and the direction of steepest ascent along the surface there, the tangent
 * direction {@code (f_x, f_y, f_x² + f_y²)}, drawn as an arrow; its row gives {@code ∇f = (f_x,
 * f_y)}. The derivatives are the engine's, of the surface's body, evaluated through the sampler.
 *
 * @param id the id
 * @param style the style
 * @param surface the surface, a {@link Surface}
 * @param x the point's abscissa, an expression that may read sliders
 * @param y the point's ordinate, likewise
 */
public record GradientArrow(ObjectId id, Style style, ObjectId surface, Expr x, Expr y)
    implements SpaceObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public GradientArrow {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(surface, "surface");
    Objects.requireNonNull(x, "x");
    Objects.requireNonNull(y, "y");
  }

  @Override
  public List<ObjectId> parents() {
    return List.of(surface);
  }

  @Override
  public Set<String> reads() {
    return Reads.of(Set.of(), x, y);
  }

  @Override
  public GradientArrow withStyle(Style style) {
    return new GradientArrow(id, style, surface, x, y);
  }

  /**
   * This gradient at another point.
   *
   * @param atX the abscissa
   * @param atY the ordinate
   * @return the gradient
   */
  public GradientArrow at(Expr atX, Expr atY) {
    return new GradientArrow(id, style, surface, atX, atY);
  }
}
