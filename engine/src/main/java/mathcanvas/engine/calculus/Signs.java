package mathcanvas.engine.calculus;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.assume.Range;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.simplify.Simplifier;

/**
 * Decisions about values that do not depend on the variable: whether one is zero, and the sign of a
 * real one. A number decides by its value; an expression with symbols by the assumptions; a closed
 * constant ({@code √2 − 1}, {@code sin(1)}) by the simplifier, which proves a zero, or by its
 * numeric value at two precisions, which proves a sign only when both agree and stand well clear of
 * zero. Anything else is undecided: a caller then declines, or records the condition.
 */
final class Signs {

  private Signs() {}

  /** The magnitude below which a numeric value proves nothing. */
  private static final BigDecimal CLEAR = new BigDecimal("1e-25");

  /**
   * Whether {@code c} is zero.
   *
   * @param c a canonical expression free of the variable
   * @param ctx the assumptions and numerics
   * @return the answer
   */
  static Tri zero(Expr c, Context ctx) {
    if (c instanceof Num n) {
      return Tri.of(n.value().isZero());
    }
    if (!finite(c)) {
      return Tri.UNKNOWN;
    }
    Tri known = ctx.assumptions().isZero(c);
    if (known != Tri.UNKNOWN) {
      return known;
    }
    Optional<Integer> bounded = Range.of(c, ctx.assumptions()).flatMap(Range::sign);
    if (bounded.isPresent() && bounded.get() != 0) {
      return Tri.FALSE;
    }
    if (hasSymbol(c)) {
      return Tri.UNKNOWN;
    }
    Expr s = simplified(c, ctx);
    if (s instanceof Num n) {
      return Tri.of(n.value().isZero());
    }
    Optional<Numeric> v = clear(s, ctx);
    return v.isPresent() ? Tri.FALSE : Tri.UNKNOWN;
  }

  /**
   * The sign of a real {@code c}: -1, 0 or 1.
   *
   * @param c a canonical expression free of the variable
   * @param ctx the assumptions and numerics
   * @return the sign, or empty when it is not decided or {@code c} is not known to be real
   */
  static Optional<Integer> sign(Expr c, Context ctx) {
    if (c instanceof Num n) {
      return n.value() instanceof RealNumeric r ? Optional.of(r.signum()) : Optional.empty();
    }
    if (!finite(c)) {
      return Optional.empty();
    }
    Assumptions a = ctx.assumptions();
    if (a.isZero(c) == Tri.TRUE) {
      return Optional.of(0);
    }
    if (a.isPositive(c) == Tri.TRUE) {
      return Optional.of(1);
    }
    if (a.isNegative(c) == Tri.TRUE) {
      return Optional.of(-1);
    }
    Optional<Integer> bounded = Range.of(c, a).flatMap(Range::sign);
    if (bounded.isPresent()) {
      return bounded;
    }
    if (hasSymbol(c)) {
      return Optional.empty();
    }
    Expr s = simplified(c, ctx);
    if (s instanceof Num n) {
      return n.value() instanceof RealNumeric r ? Optional.of(r.signum()) : Optional.empty();
    }
    Optional<Numeric> v = clear(s, ctx);
    if (v.isEmpty() || !(v.get() instanceof RealNumeric r)) {
      return Optional.empty();
    }
    return Optional.of(r.signum());
  }

  /** Whether {@code c} is decided positive. */
  static boolean positive(Expr c, Context ctx) {
    return sign(c, ctx).orElse(0) > 0;
  }

  /**
   * The numeric value of a closed constant when it is clear of zero at 40 and 80 digits with the
   * same sign of each part: a real value when the imaginary part vanishes at both.
   */
  private static Optional<Numeric> clear(Expr c, Context ctx) {
    Optional<Numeric> low = ctx.numerics().value(c, new MathContext(40));
    Optional<Numeric> high = ctx.numerics().value(c, new MathContext(80));
    if (low.isEmpty() || high.isEmpty()) {
      return Optional.empty();
    }
    Numeric l = low.get();
    Numeric h = high.get();
    boolean realL = l.im().isZero();
    boolean realH = h.im().isZero();
    if (realL != realH) {
      return Optional.empty();
    }
    if (realL) {
      return big(l.re()) && big(h.re()) && l.re().signum() == h.re().signum()
          ? Optional.of(h.re())
          : Optional.empty();
    }
    return big(l.im()) && big(h.im()) && l.im().signum() == h.im().signum()
        ? Optional.of(h)
        : Optional.empty();
  }

  private static boolean big(RealNumeric r) {
    return r.toReal(MathContext.DECIMAL128).value().abs().compareTo(CLEAR) > 0;
  }

  private static Expr simplified(Expr c, Context ctx) {
    Expr folded = Canonical.of(ctx.numerics().exact(c, ctx.precision()), ctx.precision());
    return Simplifier.simplify(folded, ctx.withTrace(false)).expr();
  }

  /** Whether {@code e} has no {@code ∞} and no {@code undefined} in it. */
  static boolean finite(Expr e) {
    if (e.equals(Constant.INFINITY) || e.equals(Constant.UNDEFINED)) {
      return false;
    }
    for (Expr o : Operands.of(e)) {
      if (!finite(o)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Whether {@code e} is arithmetic over numbers and symbols, with functions: no collection,
   * lambda, truth value, relation, interval, piecewise expression or domain anywhere in it.
   */
  static boolean scalar(Expr e) {
    boolean whole =
        switch (e) {
          case mathcanvas.engine.expr.Vector _,
              mathcanvas.engine.expr.Matrix _,
              mathcanvas.engine.expr.ListExpr _,
              mathcanvas.engine.expr.SetExpr _,
              mathcanvas.engine.expr.Bool _,
              mathcanvas.engine.expr.Relation _,
              mathcanvas.engine.expr.Interval _,
              mathcanvas.engine.expr.Lambda _,
              mathcanvas.engine.expr.Piecewise _ ->
              false;
          case Constant c ->
              c.kind() != Constant.Kind.INTEGERS
                  && c.kind() != Constant.Kind.RATIONALS
                  && c.kind() != Constant.Kind.REALS
                  && c.kind() != Constant.Kind.COMPLEXES;
          case Num _,
              mathcanvas.engine.expr.Based _,
              Symbol _,
              mathcanvas.engine.expr.Unit _,
              mathcanvas.engine.expr.Sum _,
              mathcanvas.engine.expr.Product _,
              mathcanvas.engine.expr.Power _,
              mathcanvas.engine.expr.Apply _,
              mathcanvas.engine.expr.Derivative _,
              mathcanvas.engine.expr.Integral _,
              mathcanvas.engine.expr.Limit _,
              mathcanvas.engine.expr.BigSum _,
              mathcanvas.engine.expr.BigProduct _ ->
              true;
        };
    if (!whole) {
      return false;
    }
    for (Expr o : Operands.of(e)) {
      if (!scalar(o)) {
        return false;
      }
    }
    return true;
  }

  /** Whether a symbol occurs in {@code e}. */
  static boolean hasSymbol(Expr e) {
    if (e instanceof Symbol) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (hasSymbol(o)) {
        return true;
      }
    }
    return false;
  }

  /** {@code c} canonical at the context's precision. */
  static Expr canon(Expr c, Context ctx) {
    return Canonical.of(c, ctx.precision(), ctx.cancellation());
  }
}
