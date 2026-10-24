package mathcanvas.engine.solve;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Every root of a polynomial at once, by the Durand–Kerner (Weierstrass) iteration {@code z_k ← z_k
 * − p(z_k)/(a_n·∏_{j≠k}(z_k − z_j))} (Kerner, "Ein Gesamtschrittverfahren zur Berechnung der
 * Nullstellen von Polynomen", Numer. Math. 8 (1966)), then each root polished by Newton's method at
 * a rising precision, in {@link BigDecimal} complex arithmetic.
 */
final class DurandKerner {

  /** A complex number of two decimals. */
  record C(BigDecimal re, BigDecimal im) {

    static final C ZERO = new C(BigDecimal.ZERO, BigDecimal.ZERO);

    C add(C o, MathContext mc) {
      return new C(re.add(o.re, mc), im.add(o.im, mc));
    }

    C sub(C o, MathContext mc) {
      return new C(re.subtract(o.re, mc), im.subtract(o.im, mc));
    }

    C mul(C o, MathContext mc) {
      return new C(
          re.multiply(o.re, mc).subtract(im.multiply(o.im, mc), mc),
          re.multiply(o.im, mc).add(im.multiply(o.re, mc), mc));
    }

    C div(C o, MathContext mc) {
      BigDecimal den = o.re.multiply(o.re, mc).add(o.im.multiply(o.im, mc), mc);
      return new C(
          re.multiply(o.re, mc).add(im.multiply(o.im, mc), mc).divide(den, mc),
          im.multiply(o.re, mc).subtract(re.multiply(o.im, mc), mc).divide(den, mc));
    }

    /** {@code |re| + |im|}, a norm within a factor √2 of the modulus. */
    BigDecimal size() {
      return re.abs().add(im.abs());
    }

    boolean isZero() {
      return re.signum() == 0 && im.signum() == 0;
    }
  }

  private DurandKerner() {}

  /** {@code p(z)} by Horner's rule; {@code c} from the constant term up. */
  static C at(List<C> c, C z, MathContext mc) {
    C v = C.ZERO;
    for (int k = c.size() - 1; k >= 0; k--) {
      v = v.mul(z, mc).add(c.get(k), mc);
    }
    return v;
  }

  /** {@code p'(z)}. */
  static C derivativeAt(List<C> c, C z, MathContext mc) {
    C v = C.ZERO;
    for (int k = c.size() - 1; k >= 1; k--) {
      C kc =
          new C(
              c.get(k).re().multiply(BigDecimal.valueOf(k)),
              c.get(k).im().multiply(BigDecimal.valueOf(k)));
      v = v.mul(z, mc).add(kc, mc);
    }
    return v;
  }

  /**
   * Approximates every root.
   *
   * @param c the coefficients, from the constant term up, the leading one non-zero
   * @param radius a bound on the roots' moduli
   * @param mc the precision of the iteration
   * @param cap the most sweeps
   * @return the approximations, or null when they did not settle within the cap
   */
  static List<C> roots(List<C> c, BigDecimal radius, MathContext mc, int cap) {
    int n = c.size() - 1;
    C lead = c.get(n);
    List<C> monic = new ArrayList<>(n + 1);
    for (C a : c) {
      monic.add(a.div(lead, mc));
    }
    // the classical start: powers of 0.4 + 0.9i, scaled to the bound
    C seed = new C(new BigDecimal("0.4"), new BigDecimal("0.9"));
    C scale = new C(radius, BigDecimal.ZERO);
    List<C> z = new ArrayList<>(n);
    C power = new C(BigDecimal.ONE, BigDecimal.ZERO);
    for (int k = 0; k < n; k++) {
      z.add(power.mul(scale, mc));
      power = power.mul(seed, mc);
    }
    BigDecimal tolerance = BigDecimal.ONE.movePointLeft(mc.getPrecision() - 5);
    for (int sweep = 0; sweep < cap; sweep++) {
      boolean settled = true;
      for (int k = 0; k < n; k++) {
        C zk = z.get(k);
        C den = new C(BigDecimal.ONE, BigDecimal.ZERO);
        for (int j = 0; j < n; j++) {
          if (j != k) {
            den = den.mul(zk.sub(z.get(j), mc), mc);
          }
        }
        if (den.isZero()) {
          // two approximations met: move one off
          zk = zk.add(new C(tolerance, tolerance), mc);
          z.set(k, zk);
          settled = false;
          continue;
        }
        C step = at(monic, zk, mc).div(den, mc);
        z.set(k, zk.sub(step, mc));
        BigDecimal size = zk.size().max(BigDecimal.ONE);
        if (step.size().compareTo(tolerance.multiply(size)) > 0) {
          settled = false;
        }
      }
      if (settled) {
        return z;
      }
    }
    return null;
  }

  /**
   * Polishes a root by Newton's method, doubling the precision from {@code from} to {@code to}
   * digits, then iterating at {@code to} until the step is below its last digits.
   *
   * @return the root, or null when Newton's method did not settle
   */
  static C polish(List<C> c, C z, int from, int to) {
    int digits = Math.max(from, 16);
    C current = z;
    while (true) {
      MathContext mc = Brent.digits(digits + 5);
      C d = derivativeAt(c, current, mc);
      if (d.isZero()) {
        return null;
      }
      C step = at(c, current, mc).div(d, mc);
      current = current.sub(step, mc);
      if (digits >= to) {
        BigDecimal size = current.size().max(BigDecimal.ONE.movePointLeft(to));
        for (int i = 0; i < 20; i++) {
          if (step.size().compareTo(size.movePointLeft(to - 2)) <= 0) {
            return current;
          }
          d = derivativeAt(c, current, mc);
          if (d.isZero()) {
            return null;
          }
          step = at(c, current, mc).div(d, mc);
          current = current.sub(step, mc);
        }
        return null;
      }
      digits = Math.min(2 * digits, to);
    }
  }
}
