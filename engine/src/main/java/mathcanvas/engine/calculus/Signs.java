package mathcanvas.engine.calculus;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Optional;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.assume.Facts;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Int;
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

  /**
   * The range a real expression of symbols with bounds takes: numbers, symbols by their bounds,
   * sums, numeric multiples and products of non-negative ranges, and positive integer powers of
   * non-negative ranges; an end is {@code null} where it is unbounded.
   */
  record Range(RealNumeric lo, boolean loOpen, RealNumeric hi, boolean hiOpen) {

    private static final MathContext EXACT = MathContext.UNLIMITED;

    static Optional<Range> of(Expr e, Assumptions a) {
      return Optional.ofNullable(range(e, a));
    }

    /** The sign every value in the range has, or empty when it takes several. */
    Optional<Integer> sign() {
      if (lo != null && (lo.signum() > 0 || lo.signum() == 0 && loOpen)) {
        return Optional.of(1);
      }
      if (hi != null && (hi.signum() < 0 || hi.signum() == 0 && hiOpen)) {
        return Optional.of(-1);
      }
      if (lo != null && hi != null && lo.signum() == 0 && hi.signum() == 0) {
        return Optional.of(0);
      }
      return Optional.empty();
    }

    private boolean nonNegative() {
      return lo != null && lo.signum() >= 0;
    }

    private static Range range(Expr e, Assumptions a) {
      if (e instanceof Num n) {
        return n.value() instanceof RealNumeric r && r.isExact()
            ? new Range(r, false, r, false)
            : null;
      }
      if (e instanceof Symbol s) {
        if (a.isReal(s) != Tri.TRUE) {
          return null;
        }
        Facts f = a.facts(s);
        return new Range(
            f.lower() == null ? null : f.lower().value(),
            f.lower() != null && f.lower().strict(),
            f.upper() == null ? null : f.upper().value(),
            f.upper() != null && f.upper().strict());
      }
      if (e instanceof mathcanvas.engine.expr.Sum sum) {
        Range out = new Range(Int.ZERO, false, Int.ZERO, false);
        for (Expr t : sum.terms()) {
          Range r = range(t, a);
          if (r == null) {
            return null;
          }
          out = out.add(r);
        }
        return out;
      }
      if (e instanceof mathcanvas.engine.expr.Product p) {
        RealNumeric c = Int.ONE;
        Range out = new Range(Int.ONE, false, Int.ONE, false);
        for (Expr f : p.factors()) {
          if (f instanceof Num n && n.value() instanceof RealNumeric r && r.isExact()) {
            c = (RealNumeric) c.multiply(r, EXACT);
            continue;
          }
          Range r = range(f, a);
          if (r == null || !r.nonNegative()) {
            return null;
          }
          out = out.times(r);
        }
        return out.scale(c);
      }
      if (e instanceof mathcanvas.engine.expr.Power w
          && w.exponent() instanceof Num n
          && n.value() instanceof Int k
          && k.signum() > 0
          && k.value().bitLength() < 16) {
        Range r = range(w.base(), a);
        if (r == null || !r.nonNegative()) {
          return null;
        }
        int m = k.value().intValue();
        return new Range(
            (RealNumeric) r.lo.pow(m, EXACT),
            r.loOpen,
            r.hi == null ? null : (RealNumeric) r.hi.pow(m, EXACT),
            r.hiOpen);
      }
      return null;
    }

    private Range add(Range o) {
      return new Range(
          lo == null || o.lo == null ? null : (RealNumeric) lo.add(o.lo, EXACT),
          loOpen || o.loOpen,
          hi == null || o.hi == null ? null : (RealNumeric) hi.add(o.hi, EXACT),
          hiOpen || o.hiOpen);
    }

    /**
     * The product of two non-negative ranges. A positive lower end is taken only when both factors
     * take theirs, so it is open when either is; a lower end of 0 is taken when one factor takes 0,
     * so it is open only when every factor whose lower end is 0 leaves it out. A factor that is 0
     * alone makes the product 0.
     */
    private Range times(Range o) {
      if (isZero() || o.isZero()) {
        return new Range(Int.ZERO, false, Int.ZERO, false);
      }
      RealNumeric l = (RealNumeric) lo.multiply(o.lo, EXACT);
      boolean lOpen =
          l.signum() > 0
              ? loOpen || o.loOpen
              : (lo.signum() != 0 || loOpen) && (o.lo.signum() != 0 || o.loOpen);
      RealNumeric h = hi == null || o.hi == null ? null : (RealNumeric) hi.multiply(o.hi, EXACT);
      return new Range(l, lOpen, h, hiOpen || o.hiOpen);
    }

    /** Whether the range holds 0 alone. */
    private boolean isZero() {
      return lo != null && hi != null && lo.signum() == 0 && hi.signum() == 0 && !loOpen && !hiOpen;
    }

    private Range scale(RealNumeric c) {
      if (c.signum() == 0) {
        return new Range(Int.ZERO, false, Int.ZERO, false);
      }
      RealNumeric l = lo == null ? null : (RealNumeric) lo.multiply(c, EXACT);
      RealNumeric h = hi == null ? null : (RealNumeric) hi.multiply(c, EXACT);
      return c.signum() > 0 ? new Range(l, loOpen, h, hiOpen) : new Range(h, hiOpen, l, loOpen);
    }
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
