package mathcanvas.engine.provider;

import java.math.MathContext;
import java.util.Objects;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;

/**
 * The limit of an expression as a variable approaches a point. A result is correct when it is the
 * limit: a value free of the variable, {@code ∞}, {@code -∞}, or {@code undefined} when the limit
 * does not exist. It is normalised to its canonical form at the precision. A provider that cannot
 * decide declines.
 *
 * @param expr the expression, canonical
 * @param var the variable, real
 * @param point the point approached, finite or {@code ±∞}, canonical
 * @param direction the side approached from; {@code BOTH} at {@code ±∞}
 * @param precision the precision of the canonical form
 */
public record Limit(
    Expr expr,
    Symbol var,
    Expr point,
    mathcanvas.engine.expr.Limit.Direction direction,
    MathContext precision)
    implements Operation<Expr> {

  /** Checks the operands. */
  public Limit {
    Objects.requireNonNull(expr, "expr");
    Objects.requireNonNull(var, "var");
    Objects.requireNonNull(point, "point");
    Objects.requireNonNull(direction, "direction");
    Objects.requireNonNull(precision, "precision");
  }

  @Override
  public Class<Expr> outputType() {
    return Expr.class;
  }

  @Override
  public Expr normalise(Expr result) {
    return Canonical.of(result, precision);
  }
}
