package mathcanvas.engine.solve;

import static mathcanvas.engine.solve.Chain.bind;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Elementary;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Numerics;
import mathcanvas.engine.solve.Support.Declined;

/**
 * The real roots of an equation that is not a polynomial in its unknown, numerically: a scan of
 * {@code [−10⁶, 10⁶]} on a geometric grid, 40 points per decade from {@code 10⁻⁶} out each way and
 * 0, for sign changes between neighbouring real values, each refined by Brent's method at the
 * session's precision. A root is kept only where the value has fallen to the rounding of the values
 * around it, which rules out poles and jumps. The scan can miss roots: a pair closer than the
 * grid's spacing, a root where the function only touches zero, one outside the interval. With a
 * periodic function of the unknown the grid, ever sparser outward, aliases the periods, which shows
 * as roots far out: more than one root found, one of them beyond {@link #ALIAS_REACH} in magnitude,
 * is taken as a sample of infinitely many and the scan declines; a few roots near the origin are
 * answered.
 */
final class Scan {

  /** The largest root magnitude at which a periodic equation's roots are taken as found. */
  static final BigDecimal ALIAS_REACH = BigDecimal.TEN;

  /** The points per decade. */
  static final int PER_DECADE = 40;

  /** The scan reaches {@code 10^DECADES} from {@code 10^-DECADES}. */
  static final int DECADES = 6;

  /** More sign changes than this suggests infinitely many roots. */
  static final int MAX_ROOTS = 20;

  /** The digits the scan's signs are taken at. */
  private static final int SCAN_DIGITS = 20;

  private static final Set<String> PERIODIC = Set.of("sin", "cos", "tan", "cot", "sec", "csc");

  private static final Set<String> TRIG =
      Set.of(
          "sin", "cos", "tan", "cot", "sec", "csc", "asin", "acos", "atan", "acot", "asec", "acsc",
          "atan2");

  private Scan() {}

  /** The interval scanned, for the trace. */
  static Expr interval() {
    Num end = Num.of(1_000_000);
    return Interval.of(Num.of(-1_000_000), end, true, true);
  }

  /**
   * The roots of {@code e = 0} found by the scan, as {@code Real} numbers at the precision.
   *
   * @throws Declined without numeric values, in degrees or gradians with an angle of the unknown,
   *     with no root found, or with too many sign changes
   */
  static List<Expr> roots(Expr e, Symbol x, Context ctx) {
    Numerics numerics = ctx.numerics();
    if (numerics == Numerics.NONE) {
      throw new Declined("no-numerics");
    }
    if (!numerics.radians() && hasAngle(e, x)) {
      throw new Declined("angle-mode");
    }
    int digits = ctx.precision().getPrecision();
    MathContext scan = Brent.digits(SCAN_DIGITS);
    MathContext working = Brent.digits(digits + NumericRoots.GUARD);
    List<BigDecimal> points = grid(scan);
    List<BigDecimal> values = new ArrayList<>(points.size());
    for (BigDecimal t : points) {
      ctx.cancellation().check();
      values.add(value(e, x, t, scan, numerics));
    }
    int changes = 0;
    for (int i = 0; i < points.size(); i++) {
      BigDecimal v = values.get(i);
      BigDecimal w = i + 1 < points.size() ? values.get(i + 1) : null;
      if (v != null && (v.signum() == 0 || w != null && v.signum() * w.signum() < 0)) {
        changes++;
      }
    }
    if (changes > MAX_ROOTS) {
      throw new Declined("too-many-roots", bind("n_", Num.of(MAX_ROOTS)));
    }
    List<Expr> out = new ArrayList<>();
    Brent.Fn f = t -> value(e, x, t, working, numerics);
    for (int i = 0; i < points.size(); i++) {
      ctx.cancellation().check();
      BigDecimal v = values.get(i);
      if (v != null && v.signum() == 0) {
        out.add(Num.of(new Real(points.get(i).round(ctx.precision()))));
        continue;
      }
      BigDecimal w = i + 1 < points.size() ? values.get(i + 1) : null;
      if (v == null || w == null || v.signum() * w.signum() >= 0) {
        continue;
      }
      BigDecimal a = points.get(i);
      BigDecimal b = points.get(i + 1);
      BigDecimal fa = f.at(a);
      BigDecimal fb = f.at(b);
      if (fa == null || fb == null || fa.signum() * fb.signum() > 0) {
        continue;
      }
      BigDecimal root = Brent.zero(f, a, fa, b, fb, working, ctx.cancellation());
      if (root == null) {
        continue;
      }
      BigDecimal fr = f.at(root);
      BigDecimal around = fa.abs().max(fb.abs());
      if (fr != null && fr.abs().compareTo(around.movePointLeft(digits - 5)) <= 0) {
        out.add(Num.of(new Real(root.round(ctx.precision()))));
      }
    }
    if (out.isEmpty()) {
      throw new Declined("no-root-found", bind("interval_", interval()));
    }
    if (out.size() > 1 && hasPeriodic(e, x) && reachesFar(out)) {
      // a periodic function of the unknown aliases on the grid, which shows as roots far out:
      // the roots found are a sample
      throw new Declined("aliased", bind("x_", x));
    }
    return out;
  }

  /** The grid from −10⁶ to 10⁶, ascending. */
  private static List<BigDecimal> grid(MathContext mc) {
    Numeric step =
        Elementary.exp(
            Elementary.log(new Real(BigDecimal.TEN), mc)
                .divide(new Real(BigDecimal.valueOf(PER_DECADE)), mc),
            mc);
    BigDecimal ratio = ((RealNumeric) step).toReal(mc).value();
    List<BigDecimal> positive = new ArrayList<>();
    for (int decade = -DECADES; decade < DECADES; decade++) {
      BigDecimal t = BigDecimal.ONE.scaleByPowerOfTen(decade);
      for (int j = 0; j < PER_DECADE; j++) {
        positive.add(t.round(Brent.digits(8)));
        t = t.multiply(ratio, mc);
      }
    }
    positive.add(BigDecimal.ONE.scaleByPowerOfTen(DECADES));
    List<BigDecimal> out = new ArrayList<>(2 * positive.size() + 1);
    for (int i = positive.size() - 1; i >= 0; i--) {
      out.add(positive.get(i).negate());
    }
    out.add(BigDecimal.ZERO);
    out.addAll(positive);
    return out;
  }

  /** The real value of {@code e} at {@code x = t}, or null. */
  private static BigDecimal value(Expr e, Symbol x, BigDecimal t, MathContext mc, Numerics n) {
    Expr at = Canonical.of(replace(e, x, Num.of(new Real(t))), mc);
    Optional<Numeric> v;
    try {
      v = n.value(at, mc);
    } catch (ArithmeticException outOfRange) {
      return null;
    }
    if (v.isEmpty()) {
      return null;
    }
    Numeric value = v.get();
    if (value instanceof RealNumeric r) {
      return r.toReal(mc).value();
    }
    if (value.im().isZero()) {
      return value.re().toReal(mc).value();
    }
    return null;
  }

  /** {@code e} with {@code x} replaced, except inside a node that binds {@code x} itself. */
  static Expr replace(Expr e, Symbol x, Expr value) {
    if (e.equals(x)) {
      return value;
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    for (int i = 0; i < ops.size(); i++) {
      if (Operands.isBound(e, i) && ops.get(i).equals(x)) {
        return e;
      }
    }
    List<Expr> out = new ArrayList<>(ops.size());
    for (Expr op : ops) {
      out.add(replace(op, x, value));
    }
    return Operands.rebuild(e, out);
  }

  /** Whether a root lies beyond {@link #ALIAS_REACH}. */
  private static boolean reachesFar(List<Expr> roots) {
    for (Expr r : roots) {
      if (((Real) ((Num) r).value()).value().abs().compareTo(ALIAS_REACH) > 0) {
        return true;
      }
    }
    return false;
  }

  /** Whether a periodic function takes an argument holding {@code x}. */
  private static boolean hasPeriodic(Expr e, Symbol x) {
    if (e instanceof Apply a && PERIODIC.contains(a.head().name()) && Support.contains(a, x)) {
      return true;
    }
    for (Expr op : Operands.of(e)) {
      if (hasPeriodic(op, x)) {
        return true;
      }
    }
    return false;
  }

  /** Whether a trigonometric function or its inverse takes an argument holding {@code x}. */
  private static boolean hasAngle(Expr e, Symbol x) {
    if (e instanceof Apply a && TRIG.contains(a.head().name()) && Support.contains(a, x)) {
      return true;
    }
    for (Expr op : Operands.of(e)) {
      if (hasAngle(op, x)) {
        return true;
      }
    }
    return false;
  }
}
