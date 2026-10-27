package mathcanvas.engine.solve;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import mathcanvas.engine.Cancellation;

/**
 * Brent's method for a root bracketed by a sign change: inverse quadratic interpolation and the
 * secant step where they make progress, bisection otherwise, so it never does worse than bisection
 * (Brent, <i>Algorithms for Minimization without Derivatives</i>, 1973, ch. 4, procedure {@code
 * zero}), in {@link BigDecimal} at a working precision.
 */
final class Brent {

  /** A function of a real variable; null where it has no real value. */
  @FunctionalInterface
  interface Fn {
    BigDecimal at(BigDecimal t);
  }

  private Brent() {}

  /**
   * Finds a root in {@code [a, b]}, where {@code f(a)} and {@code f(b)} have opposite signs or one
   * is zero.
   *
   * @param f the function
   * @param a an end
   * @param fa its value
   * @param b the other end
   * @param fb its value
   * @param mc the working precision; the root is found to a relative tolerance of {@code 2·10^-p}
   * @return the root, or null when the function has no value at a point tried
   */
  static BigDecimal zero(
      Fn f,
      BigDecimal a,
      BigDecimal fa,
      BigDecimal b,
      BigDecimal fb,
      MathContext mc,
      Cancellation cancellation) {
    BigDecimal two = BigDecimal.TWO;
    BigDecimal eps = BigDecimal.ONE.movePointLeft(mc.getPrecision());
    BigDecimal floor = BigDecimal.ONE.movePointLeft(2 * mc.getPrecision());
    BigDecimal c = a;
    BigDecimal fc = fa;
    BigDecimal d = b.subtract(a, mc);
    BigDecimal e = d;
    int cap = 10 * mc.getPrecision() + 200;
    for (int i = 0; i < cap; i++) {
      cancellation.check();
      if (fb.signum() == 0) {
        return b;
      }
      if (fb.signum() == fc.signum()) {
        c = a;
        fc = fa;
        d = b.subtract(a, mc);
        e = d;
      }
      if (fc.abs().compareTo(fb.abs()) < 0) {
        a = b;
        b = c;
        c = a;
        fa = fb;
        fb = fc;
        fc = fa;
      }
      BigDecimal tol = two.multiply(eps, mc).multiply(b.abs(), mc).add(floor, mc);
      BigDecimal m = c.subtract(b, mc).divide(two, mc);
      if (m.abs().compareTo(tol) <= 0 || fb.signum() == 0) {
        return b;
      }
      if (e.abs().compareTo(tol) >= 0 && fa.abs().compareTo(fb.abs()) > 0) {
        BigDecimal s = fb.divide(fa, mc);
        BigDecimal p;
        BigDecimal q;
        if (a.compareTo(c) == 0) {
          p = two.multiply(m, mc).multiply(s, mc);
          q = BigDecimal.ONE.subtract(s, mc);
        } else {
          BigDecimal qq = fa.divide(fc, mc);
          BigDecimal r = fb.divide(fc, mc);
          p =
              s.multiply(
                  two.multiply(m, mc)
                      .multiply(qq, mc)
                      .multiply(qq.subtract(r, mc), mc)
                      .subtract(b.subtract(a, mc).multiply(r.subtract(BigDecimal.ONE, mc), mc), mc),
                  mc);
          q =
              qq.subtract(BigDecimal.ONE, mc)
                  .multiply(r.subtract(BigDecimal.ONE, mc), mc)
                  .multiply(s.subtract(BigDecimal.ONE, mc), mc);
        }
        if (p.signum() > 0) {
          q = q.negate();
        } else {
          p = p.negate();
        }
        BigDecimal limit =
            BigDecimal.valueOf(3)
                .multiply(m, mc)
                .multiply(q, mc)
                .subtract(tol.multiply(q, mc).abs(), mc)
                .min(e.multiply(q, mc).abs());
        if (two.multiply(p, mc).compareTo(limit) < 0) {
          e = d;
          d = p.divide(q, mc);
        } else {
          d = m;
          e = m;
        }
      } else {
        d = m;
        e = m;
      }
      a = b;
      fa = fb;
      b = b.add(d.abs().compareTo(tol) > 0 ? d : m.signum() >= 0 ? tol : tol.negate(), mc);
      fb = f.at(b);
      if (fb == null) {
        return null;
      }
    }
    return b;
  }

  /** A context of {@code digits} digits, rounding half even. */
  static MathContext digits(int digits) {
    return new MathContext(digits, RoundingMode.HALF_EVEN);
  }
}
