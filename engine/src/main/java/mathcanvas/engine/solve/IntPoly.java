package mathcanvas.engine.solve;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * A dense polynomial in one variable over ℤ, coefficients from the constant term up, the leading
 * one non-zero; the exact arithmetic the real-root isolation needs.
 */
record IntPoly(BigInteger[] c) {

  IntPoly {
    int n = c.length;
    while (n > 1 && c[n - 1].signum() == 0) {
      n--;
    }
    c = Arrays.copyOf(c, n);
  }

  int degree() {
    return c.length - 1;
  }

  boolean isZero() {
    return c.length == 1 && c[0].signum() == 0;
  }

  BigInteger lead() {
    return c[c.length - 1];
  }

  IntPoly derivative() {
    if (c.length == 1) {
      return new IntPoly(new BigInteger[] {BigInteger.ZERO});
    }
    BigInteger[] d = new BigInteger[c.length - 1];
    for (int k = 1; k < c.length; k++) {
      d[k - 1] = c[k].multiply(BigInteger.valueOf(k));
    }
    return new IntPoly(d);
  }

  /** This divided by the positive gcd of its coefficients, so its signs are kept. */
  IntPoly primitive() {
    BigInteger g = BigInteger.ZERO;
    for (BigInteger a : c) {
      g = g.gcd(a);
    }
    if (g.signum() == 0 || g.equals(BigInteger.ONE)) {
      return this;
    }
    BigInteger[] out = new BigInteger[c.length];
    for (int k = 0; k < c.length; k++) {
      out[k] = c[k].divide(g);
    }
    return new IntPoly(out);
  }

  /**
   * A positive multiple of the remainder of this divided by {@code b}: the pseudo-remainder of
   * {@code |lc(b)|^(m−n+1)·this} (Knuth, vol. 2, §4.6.1, Algorithm R), so that its sign is the
   * remainder's.
   */
  IntPoly positiveRemainder(IntPoly b) {
    BigInteger[] r = c.clone();
    int n = b.degree();
    BigInteger lead = b.lead();
    BigInteger scale = lead.abs();
    for (int k = r.length - 1; k >= n; k--) {
      BigInteger top = r[k];
      // r ← |lc|·r − sign(lc)·top·x^(k−n)·b
      for (int j = 0; j <= k; j++) {
        r[j] = r[j].multiply(scale);
      }
      BigInteger t = lead.signum() > 0 ? top : top.negate();
      for (int j = 0; j <= n; j++) {
        r[k - n + j] = r[k - n + j].subtract(t.multiply(b.c[j]));
      }
    }
    BigInteger[] out = n == 0 ? new BigInteger[] {BigInteger.ZERO} : Arrays.copyOf(r, n);
    return new IntPoly(out).primitive();
  }

  /** The sign of the value at {@code p/q}, {@code q > 0}. */
  int signAt(BigInteger p, BigInteger q) {
    BigInteger v = BigInteger.ZERO;
    BigInteger qk = BigInteger.ONE;
    BigInteger[] qs = new BigInteger[c.length];
    for (int k = 0; k < c.length; k++) {
      qs[k] = qk;
      qk = qk.multiply(q);
    }
    for (int k = c.length - 1; k >= 0; k--) {
      v = v.multiply(p).add(c[k].multiply(qs[c.length - 1 - k]));
    }
    return v.signum();
  }

  /** The exact value at a decimal. */
  BigDecimal at(BigDecimal t) {
    BigDecimal v = BigDecimal.ZERO;
    for (int k = c.length - 1; k >= 0; k--) {
      v = v.multiply(t).add(new BigDecimal(c[k]));
    }
    return v;
  }
}
