package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Symbol;

/**
 * The curve {@code (x(t), y(t))} over a finite domain of its parameter ({@link ParamKind#T}); a
 * domain whose ends are not finite numbers leaves it undefined.
 *
 * @param id the id
 * @param style the style
 * @param x the abscissa in the parameter
 * @param y the ordinate in the parameter
 * @param parameter the parameter
 * @param domain the parameter's range; its ends may read variables
 */
public record ParametricCurve(
    ObjectId id, Style style, Expr x, Expr y, Symbol parameter, Interval domain)
    implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public ParametricCurve {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(x, "x");
    Objects.requireNonNull(y, "y");
    Objects.requireNonNull(parameter, "parameter");
    Objects.requireNonNull(domain, "domain");
  }

  @Override
  public List<ObjectId> parents() {
    return List.of();
  }

  @Override
  public Set<String> reads() {
    return Reads.of(Set.of(parameter), x, y, domain);
  }

  @Override
  public ParametricCurve withStyle(Style style) {
    return new ParametricCurve(id, style, x, y, parameter, domain);
  }
}
