package mathcanvas.engine.geometry;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.simplify.Simplifier;

/**
 * The signs and comparisons the geometry solvers decide on closed real values: exactly when the
 * difference simplifies to 0, otherwise by its value at the working digits (the precision plus 10)
 * when that is clear of 0 by {@code 10^-(p + 5)}, and otherwise not at all ({@link Undecided}).
 */
final class Decide {

  /** A sign too close to call, or a value with no number. */
  static final class Undecided extends RuntimeException {
    private static final long serialVersionUID = 1L;

    Undecided() {
      super(null, null, false, false);
    }
  }

  private final Context ctx;
  private final MathContext work;

  Decide(Context ctx) {
    this.ctx = ctx;
    this.work = new MathContext(ctx.precision().getPrecision() + 10);
  }

  /** The standard simplifier's result, untraced. */
  Expr simplify(Expr e) {
    return Simplifier.simplify(Canonical.of(e, ctx.precision()), ctx.withTrace(false)).expr();
  }

  /**
   * A linear combination of angles in its simplest form: expanded, so that a difference of sums
   * collects termwise (the canonical form does not distribute a sign over a sum), then simplified.
   */
  Expr combine(Expr e) {
    return simplify(Algebra.expand(Canonical.of(e, ctx.precision()), ctx.withTrace(false)).expr());
  }

  /**
   * The sign of a closed real value.
   *
   * @throws Undecided when it cannot be decided
   */
  int sign(Expr e) {
    Expr s = simplify(e);
    if (s.equals(Num.ZERO)) {
      return 0;
    }
    Optional<Numeric> v = ctx.numerics().value(Canonical.of(s, ctx.precision()), work);
    if (v.isEmpty()) {
      throw new Undecided();
    }
    BigDecimal re = v.get().re().toReal(work).value();
    BigDecimal im = v.get().im().toReal(work).value();
    BigDecimal tolerance = BigDecimal.ONE.movePointLeft(ctx.precision().getPrecision() + 5);
    if (im.abs().compareTo(tolerance) > 0 || re.abs().compareTo(tolerance) <= 0) {
      throw new Undecided();
    }
    return re.signum();
  }

  /** The sign of {@code a − b}. */
  int compare(Expr a, Expr b) {
    return sign(Exprs.sub(a, b));
  }
}
