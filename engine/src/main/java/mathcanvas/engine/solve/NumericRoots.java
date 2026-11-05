package mathcanvas.engine.solve;

import static mathcanvas.engine.solve.Chain.bind;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Numerics;
import mathcanvas.engine.solve.DurandKerner.C;
import mathcanvas.engine.solve.Support.Declined;

/**
 * The roots of a polynomial in one unknown, numerically, at the session's precision. With real
 * coefficients the real roots are isolated exactly by Sturm sequences ({@link RealRoots}), so none
 * is missed, then found by Brent's method with the polynomial evaluated exactly, and polished by
 * Newton's method; the others, when the real roots do not account for the degree, come from
 * Durand–Kerner and Newton in complex arithmetic. Complex coefficients go to Durand–Kerner alone.
 * Coefficients that are not rational are evaluated 20 digits beyond the precision and taken as the
 * rationals their decimals denote.
 */
final class NumericRoots {

  /** The highest degree solved numerically. */
  static final int MAX_DEGREE = 100;

  /** The digits worked with beyond the precision. */
  static final int GUARD = 10;

  private NumericRoots() {}

  /**
   * The roots of {@code p}, a square-free polynomial in {@code x} whose coefficients are rational
   * or closed expressions, each a {@code Real} or an inexact {@code Complex} number.
   *
   * @throws Declined past the degree bound, without numeric values, or when the iteration fails
   */
  static List<Expr> roots(Polynomial p, Symbol x, Context ctx) {
    int n = p.degree(x);
    if (n > MAX_DEGREE) {
      throw new Declined("numeric-degree", bind("n_", Num.of(n)));
    }
    int digits = ctx.precision().getPrecision();
    List<RealNumeric> real = new ArrayList<>();
    List<C> complex = new ArrayList<>();
    boolean isReal = true;
    MathContext coefficientContext = Brent.digits(digits + 2 * GUARD);
    for (int k = 0; k <= n; k++) {
      Polynomial ck = p.coefficient(x, k);
      if (ck.isConstant()) {
        RealNumeric r = ck.isZero() ? Int.ZERO : ck.leadingCoefficient();
        real.add(r);
        complex.add(new C(r.toReal(coefficientContext).value(), BigDecimal.ZERO));
        continue;
      }
      if (ctx.numerics() == Numerics.NONE) {
        throw new Declined("no-numerics");
      }
      Optional<Numeric> v =
          ctx.numerics().value(Canonical.of(ck.toExpr(), coefficientContext), coefficientContext);
      if (v.isEmpty()) {
        throw new Declined("numeric-failed");
      }
      Numeric value = v.get();
      if (value instanceof RealNumeric r) {
        real.add(exact(r));
        complex.add(new C(r.toReal(coefficientContext).value(), BigDecimal.ZERO));
      } else {
        isReal = false;
        real.add(Int.ZERO);
        complex.add(
            new C(
                value.re().toReal(coefficientContext).value(),
                value.im().toReal(coefficientContext).value()));
      }
    }
    List<Expr> out = new ArrayList<>(n);
    MathContext result = ctx.precision();
    int working = digits + GUARD;
    if (!isReal) {
      for (C z : all(complex, n, digits, working, ctx.cancellation())) {
        out.add(Num.of(complexValue(z, result)));
      }
      return out;
    }
    IntPoly s = integral(real);
    Cancellation cancel = ctx.cancellation();
    RealRoots rr = new RealRoots(s, cancel);
    List<RealRoots.Isolated> isolated = rr.isolate();
    for (RealRoots.Isolated iso : isolated) {
      out.add(Num.of(new Real(realRoot(s, iso, working, cancel).round(result))));
    }
    if (isolated.size() < n) {
      List<C> zs = DurandKerner.roots(complex, bound(s), Brent.digits(start(digits)), 500, cancel);
      if (zs == null) {
        zs = DurandKerner.roots(complex, bound(s), Brent.digits(2 * start(digits)), 2000, cancel);
      }
      if (zs == null) {
        throw new Declined("numeric-failed");
      }
      zs.sort(Comparator.comparing(z -> z.im().abs()));
      List<C> upper = new ArrayList<>();
      for (C z : zs.subList(isolated.size(), n)) {
        if (z.im().signum() > 0) {
          upper.add(z);
        }
      }
      if (2 * upper.size() != n - isolated.size()) {
        throw new Declined("numeric-failed");
      }
      List<C> polished = new ArrayList<>();
      for (C z : upper) {
        C r = DurandKerner.polish(complex, z, start(digits), working, cancel);
        if (r == null || r.im().signum() <= 0) {
          throw new Declined("numeric-failed");
        }
        polished.add(r);
      }
      distinct(polished, digits);
      for (C z : polished) {
        out.add(Num.of(complexValue(z, result)));
        out.add(Num.of(complexValue(new C(z.re(), z.im().negate()), result)));
      }
    }
    return out;
  }

  private static int start(int digits) {
    return Math.min(digits, 30) + GUARD;
  }

  /** Every root of complex coefficients: Durand–Kerner, then Newton on each. */
  private static List<C> all(List<C> c, int n, int digits, int working, Cancellation cancellation) {
    BigDecimal radius = BigDecimal.ONE;
    MathContext mc = Brent.digits(start(digits));
    BigDecimal lead = c.get(n).size();
    for (C a : c) {
      radius = radius.max(a.size().divide(lead, mc).add(BigDecimal.ONE));
    }
    List<C> zs = DurandKerner.roots(c, radius, mc, 500, cancellation);
    if (zs == null) {
      zs = DurandKerner.roots(c, radius, Brent.digits(2 * start(digits)), 2000, cancellation);
    }
    if (zs == null) {
      throw new Declined("numeric-failed");
    }
    List<C> out = new ArrayList<>();
    for (C z : zs) {
      C r = DurandKerner.polish(c, z, start(digits), working, cancellation);
      if (r == null) {
        throw new Declined("numeric-failed");
      }
      out.add(r);
    }
    distinct(out, digits);
    return out;
  }

  /** Refuses roots that have run together: Newton found one root twice. */
  private static void distinct(List<C> zs, int digits) {
    MathContext mc = Brent.digits(digits);
    for (int i = 0; i < zs.size(); i++) {
      for (int j = 0; j < i; j++) {
        BigDecimal scale = zs.get(i).size().max(zs.get(j).size()).max(BigDecimal.ONE);
        if (zs.get(i).sub(zs.get(j), mc).size().compareTo(scale.movePointLeft(digits / 2)) <= 0) {
          throw new Declined("numeric-failed");
        }
      }
    }
  }

  /** The real root in an isolating interval: Brent's method, then Newton's. */
  private static BigDecimal realRoot(
      IntPoly s, RealRoots.Isolated iso, int working, Cancellation cancellation) {
    MathContext mc = Brent.digits(working);
    BigDecimal hiExact = dyadic(iso.hi());
    if (iso.exact()) {
      return hiExact;
    }
    BigDecimal loExact = dyadic(iso.lo());
    BigDecimal lo = loExact.round(new MathContext(working, RoundingMode.CEILING));
    BigDecimal hi = hiExact.round(new MathContext(working, RoundingMode.FLOOR));
    if (lo.compareTo(hi) >= 0) {
      return hiExact;
    }
    BigDecimal flo = s.at(lo);
    BigDecimal fhi = s.at(hi);
    if (flo.signum() * fhi.signum() > 0) {
      // the root lies within the last digit of an end
      return flo.signum() == s.at(loExact).signum() ? hi : lo;
    }
    Brent.Fn f = t -> s.at(t).round(mc);
    BigDecimal root = Brent.zero(f, lo, flo.round(mc), hi, fhi.round(mc), mc, cancellation);
    IntPoly d = s.derivative();
    for (int i = 0; i < 2 && root != null; i++) {
      BigDecimal slope = d.at(root).round(mc);
      if (slope.signum() == 0) {
        break;
      }
      BigDecimal next = root.subtract(s.at(root).round(mc).divide(slope, mc), mc);
      if (next.compareTo(lo) < 0 || next.compareTo(hi) > 0) {
        break;
      }
      root = next;
    }
    return root;
  }

  private static BigDecimal dyadic(RealRoots.Dyadic t) {
    return new BigDecimal(t.n()).divide(new BigDecimal(t.d()));
  }

  /** The polynomial scaled to coprime integer coefficients. */
  private static IntPoly integral(List<RealNumeric> c) {
    BigInteger lcm = BigInteger.ONE;
    for (RealNumeric r : c) {
      BigInteger d = denominator(r);
      lcm = lcm.divide(lcm.gcd(d)).multiply(d);
    }
    BigInteger[] out = new BigInteger[c.size()];
    for (int k = 0; k < out.length; k++) {
      RealNumeric r = c.get(k);
      out[k] = numerator(r).multiply(lcm.divide(denominator(r)));
    }
    return new IntPoly(out).primitive();
  }

  private static BigInteger numerator(RealNumeric r) {
    return r instanceof Rational q ? q.num() : ((Int) r).value();
  }

  private static BigInteger denominator(RealNumeric r) {
    return r instanceof Rational q ? q.den() : BigInteger.ONE;
  }

  /** The rational a decimal's digits denote. */
  private static RealNumeric exact(RealNumeric r) {
    if (!(r instanceof Real d)) {
      return r;
    }
    BigDecimal v = d.value();
    return v.scale() <= 0
        ? Int.of(v.toBigIntegerExact())
        : Rational.of(v.unscaledValue(), BigInteger.TEN.pow(v.scale()));
  }

  /** Cauchy's bound on the moduli of the roots. */
  private static BigDecimal bound(IntPoly s) {
    BigInteger max = BigInteger.ZERO;
    for (BigInteger a : s.c()) {
      max = max.max(a.abs());
    }
    return new BigDecimal(max.divide(s.lead().abs()).add(BigInteger.ONE));
  }

  /**
   * A root as a complex number at the precision: a part below {@code 10^-p} of the other is noise
   * of the iteration and is zero, as the tower's own complex results are accurate only to that.
   */
  private static Numeric complexValue(C z, MathContext mc) {
    BigDecimal re = z.re();
    BigDecimal im = z.im();
    BigDecimal floor = re.abs().max(im.abs()).movePointLeft(mc.getPrecision());
    if (re.abs().compareTo(floor) < 0) {
      re = BigDecimal.ZERO;
    }
    if (im.abs().compareTo(floor) < 0) {
      im = BigDecimal.ZERO;
    }
    return Complex.of(new Real(re.round(mc)), new Real(im.round(mc)), mc);
  }
}
