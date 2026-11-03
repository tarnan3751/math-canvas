package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Symbol;

/**
 * The curve {@code (x(t), y(t), z(t))} in space over a range of {@link #T}, sampled at {@link
 * #SAMPLES} evenly spaced parameters; a range whose ends are not finite ordered numbers leaves it
 * undefined.
 *
 * @param id the id
 * @param style the style
 * @param x the abscissa in {@code t}
 * @param y the ordinate
 * @param z the height
 * @param domain the range of {@code t}; its ends may read variables
 */
public record SpaceCurve(ObjectId id, Style style, Expr x, Expr y, Expr z, Interval domain)
    implements SpaceObject {

  /** The parameter. */
  public static final Symbol T = Symbol.of("t");

  /** The samples taken along the range, its ends included. */
  public static final int SAMPLES = 2048;

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public SpaceCurve {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(x, "x");
    Objects.requireNonNull(y, "y");
    Objects.requireNonNull(z, "z");
    Objects.requireNonNull(domain, "domain");
  }

  @Override
  public List<ObjectId> parents() {
    return List.of();
  }

  @Override
  public Set<String> reads() {
    return Reads.of(Set.of(T), x, y, z, domain);
  }

  @Override
  public SpaceCurve withStyle(Style style) {
    return new SpaceCurve(id, style, x, y, z, domain);
  }
}
