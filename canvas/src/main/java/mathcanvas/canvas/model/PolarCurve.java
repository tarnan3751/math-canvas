package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Symbol;

/**
 * The polar curve {@code r = radius(θ)}, the point {@code (r cos θ, r sin θ)}, over a finite domain
 * of the angle ({@link ParamKind#THETA}); a negative radius lands opposite. The angle is in the
 * document's angle mode, its domain included, so a whole turn is {@link #turn} of the mode.
 *
 * @param id the id
 * @param style the style
 * @param radius the radius in the angle
 * @param angle the angle variable
 * @param domain the angle's range; its ends may read variables
 */
public record PolarCurve(ObjectId id, Style style, Expr radius, Symbol angle, Interval domain)
    implements CanvasObject {

  /** One turn in radians, {@code [0, 2π]}: the default domain in radian mode. */
  public static final Interval TURN =
      Interval.of(Num.ZERO, Product.of(Num.of(2), Constant.PI), true, true);

  /**
   * One turn in an angle mode's unit, the default domain a tool or an entry gives a polar curve:
   * {@code [0, 2π]} in radians, {@code [0, 360]} in degrees, {@code [0, 400]} in gradians.
   *
   * @param mode the document's angle mode
   * @return the turn
   */
  public static Interval turn(AngleMode mode) {
    return switch (mode) {
      case RADIANS -> TURN;
      case DEGREES -> Interval.of(Num.ZERO, Num.of(360), true, true);
      case GRADIANS -> Interval.of(Num.ZERO, Num.of(400), true, true);
    };
  }

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public PolarCurve {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(radius, "radius");
    Objects.requireNonNull(angle, "angle");
    Objects.requireNonNull(domain, "domain");
  }

  @Override
  public List<ObjectId> parents() {
    return List.of();
  }

  @Override
  public Set<String> reads() {
    return Reads.of(Set.of(angle), radius, domain);
  }

  @Override
  public PolarCurve withStyle(Style style) {
    return new PolarCurve(id, style, radius, angle, domain);
  }
}
