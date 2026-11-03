package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Symbol;

/**
 * The surface {@code (x(u, v), y(u, v), z(u, v))} over a rectangle of {@link #U} and {@link #V},
 * sampled on an {@code n × n} grid of cells; a range whose ends are not finite ordered numbers
 * leaves it undefined.
 *
 * @param id the id
 * @param style the style
 * @param x the abscissa in {@code u} and {@code v}
 * @param y the ordinate
 * @param z the height
 * @param uDomain the range of {@code u}; its ends may read variables
 * @param vDomain the range of {@code v}
 * @param n the cells along each side, one of {@link Surface#SIZES}
 */
public record ParametricSurface(
    ObjectId id, Style style, Expr x, Expr y, Expr z, Interval uDomain, Interval vDomain, int n)
    implements SpaceObject {

  /** The first parameter. */
  public static final Symbol U = Symbol.of("u");

  /** The second parameter. */
  public static final Symbol V = Symbol.of("v");

  /** The default range of {@code u}, one turn {@code [0, 2π]}. */
  public static final Interval U_DOMAIN =
      Interval.of(Num.ZERO, Product.of(Num.of(2), Constant.PI), true, true);

  /** The default range of {@code v}, half a turn {@code [0, π]}: a sphere's latitude. */
  public static final Interval V_DOMAIN = Interval.of(Num.ZERO, Constant.PI, true, true);

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if {@code n} is not one of {@link Surface#SIZES}
   */
  public ParametricSurface {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(x, "x");
    Objects.requireNonNull(y, "y");
    Objects.requireNonNull(z, "z");
    Objects.requireNonNull(uDomain, "uDomain");
    Objects.requireNonNull(vDomain, "vDomain");
    if (!Surface.SIZES.contains(n)) {
      throw new IllegalArgumentException("a surface has 50, 100, 150 or 200 cells a side");
    }
  }

  @Override
  public List<ObjectId> parents() {
    return List.of();
  }

  @Override
  public Set<String> reads() {
    return Reads.of(Set.of(U, V), x, y, z, uDomain, vDomain);
  }

  @Override
  public ParametricSurface withStyle(Style style) {
    return new ParametricSurface(id, style, x, y, z, uDomain, vDomain, n);
  }

  /**
   * This surface with another number of cells a side.
   *
   * @param cells one of {@link Surface#SIZES}
   * @return the surface
   */
  public ParametricSurface withN(int cells) {
    return new ParametricSurface(id, style, x, y, z, uDomain, vDomain, cells);
  }
}
