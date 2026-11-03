package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;

/**
 * The surface {@code z = body} over a rectangle of {@code x} and {@code y}, sampled on an {@code n
 * × n} grid of cells; a domain whose ends are not finite ordered numbers leaves it undefined.
 *
 * @param id the id
 * @param style the style
 * @param body the height in {@link #X} and {@link #Y}
 * @param xDomain the range of {@code x}; its ends may read variables
 * @param yDomain the range of {@code y}; its ends may read variables
 * @param n the cells along each side, one of {@link #SIZES}
 */
public record Surface(
    ObjectId id, Style style, Expr body, Interval xDomain, Interval yDomain, int n)
    implements SpaceObject {

  /** The abscissa. */
  public static final Symbol X = ImplicitCurve.X;

  /** The ordinate. */
  public static final Symbol Y = ImplicitCurve.Y;

  /** The cells along a side a surface may have: each a multiple of the orbiting mesh's 50. */
  public static final List<Integer> SIZES = List.of(50, 100, 150, 200);

  /** The cells along a side of a new surface. */
  public static final int DEFAULT_N = 100;

  /** The default range of each variable, {@code [−5, 5]}. */
  public static final Interval DEFAULT_DOMAIN = Interval.of(Num.of(-5), Num.of(5), true, true);

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if {@code n} is not one of {@link #SIZES}
   */
  public Surface {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(xDomain, "xDomain");
    Objects.requireNonNull(yDomain, "yDomain");
    if (!SIZES.contains(n)) {
      throw new IllegalArgumentException("a surface has 50, 100, 150 or 200 cells a side");
    }
  }

  @Override
  public List<ObjectId> parents() {
    return List.of();
  }

  @Override
  public Set<String> reads() {
    return Reads.of(Set.of(X, Y), body, xDomain, yDomain);
  }

  @Override
  public Surface withStyle(Style style) {
    return new Surface(id, style, body, xDomain, yDomain, n);
  }

  /**
   * This surface with another number of cells a side.
   *
   * @param cells one of {@link #SIZES}
   * @return the surface
   */
  public Surface withN(int cells) {
    return new Surface(id, style, body, xDomain, yDomain, cells);
  }
}
