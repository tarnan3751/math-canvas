package mathcanvas.engine.assume;

import java.math.MathContext;
import java.util.Optional;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.RealNumeric;

/**
 * The range a real expression of symbols with bounds takes: numbers, symbols by their bounds, sums,
 * numeric multiples and products of non-negative ranges, and positive integer powers of
 * non-negative ranges; an end is {@code null} where it is unbounded. A query beside the structural
 * inference ({@link Inference}), used where a sign needs the bounds' values.
 *
 * @param lo the lower end, null when unbounded
 * @param loOpen whether the lower end is left out
 * @param hi the upper end, null when unbounded
 * @param hiOpen whether the upper end is left out
 */
public record Range(RealNumeric lo, boolean loOpen, RealNumeric hi, boolean hiOpen) {

  private static final MathContext EXACT = MathContext.UNLIMITED;

  /**
   * The range of {@code e} under the facts.
   *
   * @param e a canonical expression
   * @param a the assumptions
   * @return the range, or empty when {@code e} is not of a form the range follows
   */
  public static Optional<Range> of(Expr e, Assumptions a) {
    return Optional.ofNullable(range(e, a));
  }

  /**
   * The sign every value in the range has.
   *
   * @return -1, 0 or 1, or empty when the range takes several
   */
  public Optional<Integer> sign() {
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
    if (e instanceof Sum sum) {
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
    if (e instanceof Product p) {
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
    if (e instanceof Power w
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
