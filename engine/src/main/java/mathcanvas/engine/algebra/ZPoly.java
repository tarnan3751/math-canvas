package mathcanvas.engine.algebra;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.number.RealNumeric;

/**
 * A dense univariate polynomial with integer coefficients, for the univariate algorithms over ℤ
 * (gcd, roots, Kronecker's method), where the sparse rational form would cost too much. {@code
 * c[i]} is the coefficient of {@code x^i}; the last is non-zero, and the zero polynomial is empty.
 *
 * @param c the coefficients, lowest degree first; never modified
 */
record ZPoly(BigInteger[] c) {

  static final ZPoly ZERO = new ZPoly(new BigInteger[0]);
  static final ZPoly ONE = new ZPoly(new BigInteger[] {BigInteger.ONE});

  /** Trims the leading zeros. */
  ZPoly {
    int n = c.length;
    while (n > 0 && c[n - 1].signum() == 0) {
      n--;
    }
    c = n == c.length ? c : Arrays.copyOf(c, n);
  }

  /** The polynomial with these integer coefficients, lowest degree first. */
  static ZPoly of(long... coefficients) {
    BigInteger[] b = new BigInteger[coefficients.length];
    for (int i = 0; i < b.length; i++) {
      b[i] = BigInteger.valueOf(coefficients[i]);
    }
    return new ZPoly(b);
  }

  /**
   * Converts a polynomial in at most one variable with integer coefficients.
   *
   * @throws IllegalArgumentException if a coefficient is not an integer
   */
  static ZPoly of(Polynomial p) {
    if (p.isZero()) {
      return ZERO;
    }
    BigInteger[] b = new BigInteger[(int) p.totalDegree() + 1];
    Arrays.fill(b, BigInteger.ZERO);
    for (Polynomial.Term t : p.terms()) {
      if (!t.coefficient().isInteger()) {
        throw new IllegalArgumentException("not an integer polynomial: " + p);
      }
      b[(int) t.monomial().degree()] = Q.num(t.coefficient());
    }
    return new ZPoly(b);
  }

  /** Returns the polynomial in {@code v}. */
  Polynomial toPolynomial(Expr v) {
    Map<Monomial, RealNumeric> m = new TreeMap<>(Monomial.ORDER);
    for (int i = 0; i < c.length; i++) {
      if (c[i].signum() != 0) {
        m.put(Monomial.power(1, 0, i), Q.of(c[i]));
      }
    }
    return Polynomial.build(List.of(v), m);
  }

  int degree() {
    return c.length - 1;
  }

  boolean isZero() {
    return c.length == 0;
  }

  BigInteger lc() {
    return c[c.length - 1];
  }

  BigInteger content() {
    BigInteger g = BigInteger.ZERO;
    for (BigInteger x : c) {
      g = g.gcd(x);
      if (g.equals(BigInteger.ONE)) {
        break;
      }
    }
    return g;
  }

  /** The primitive part with a positive leading coefficient. */
  ZPoly primitive() {
    if (isZero()) {
      return this;
    }
    BigInteger g = content();
    if (lc().signum() < 0) {
      g = g.negate();
    }
    return divide(g);
  }

  ZPoly divide(BigInteger d) {
    if (d.equals(BigInteger.ONE)) {
      return this;
    }
    BigInteger[] b = new BigInteger[c.length];
    for (int i = 0; i < b.length; i++) {
      b[i] = c[i].divide(d);
    }
    return new ZPoly(b);
  }

  ZPoly multiply(ZPoly o) {
    if (isZero() || o.isZero()) {
      return ZERO;
    }
    BigInteger[] b = new BigInteger[c.length + o.c.length - 1];
    Arrays.fill(b, BigInteger.ZERO);
    for (int i = 0; i < c.length; i++) {
      if (c[i].signum() == 0) {
        continue;
      }
      for (int j = 0; j < o.c.length; j++) {
        b[i + j] = b[i + j].add(c[i].multiply(o.c[j]));
      }
    }
    return new ZPoly(b);
  }

  /**
   * The pseudo-remainder {@code prem(this, d)}: {@code lc(d)^(m-n+1)·this mod d}. The dividend is
   * multiplied by the power once (not at all for a monic divisor), and each step's quotient
   * coefficient is then an exact division by {@code lc(d)}, so a step costs {@code O(deg d)}.
   */
  ZPoly pseudoRemainder(ZPoly d) {
    int n = d.degree();
    int m = degree();
    if (m < n) {
      return this;
    }
    BigInteger b = d.lc();
    boolean monic = b.equals(BigInteger.ONE);
    BigInteger scale = monic ? BigInteger.ONE : b.pow(m - n + 1);
    BigInteger[] r = new BigInteger[c.length];
    for (int i = 0; i < r.length; i++) {
      r[i] = monic ? c[i] : c[i].multiply(scale);
    }
    for (int k = m; k >= n; k--) {
      if (r[k].signum() == 0) {
        continue;
      }
      BigInteger q = monic ? r[k] : r[k].divide(b);
      for (int i = 0; i < n; i++) {
        r[k - n + i] = r[k - n + i].subtract(q.multiply(d.c[i]));
      }
      r[k] = BigInteger.ZERO;
    }
    return new ZPoly(Arrays.copyOf(r, n));
  }

  ZPoly times(BigInteger k) {
    BigInteger[] b = new BigInteger[c.length];
    for (int i = 0; i < b.length; i++) {
      b[i] = c[i].multiply(k);
    }
    return new ZPoly(b);
  }

  /**
   * Returns {@code this / d} when {@code d} divides it over ℤ, or null.
   *
   * @param d a non-zero divisor
   */
  ZPoly exactQuotient(ZPoly d) {
    if (isZero()) {
      return ZERO;
    }
    int n = d.degree();
    int m = degree();
    if (m < n) {
      return null;
    }
    BigInteger[] r = c.clone();
    BigInteger[] q = new BigInteger[m - n + 1];
    BigInteger b = d.lc();
    for (int k = m; k >= n; k--) {
      BigInteger[] qr = r[k].divideAndRemainder(b);
      if (qr[1].signum() != 0) {
        return null;
      }
      q[k - n] = qr[0];
      if (qr[0].signum() != 0) {
        for (int i = 0; i <= n; i++) {
          r[k - n + i] = r[k - n + i].subtract(qr[0].multiply(d.c[i]));
        }
      }
    }
    for (int i = 0; i < n; i++) {
      if (r[i].signum() != 0) {
        return null;
      }
    }
    return new ZPoly(q);
  }

  /**
   * The gcd of two primitive polynomials, primitive with a positive leading coefficient: the
   * primitive PRS (Knuth, vol. 2, §4.6.1, Algorithm E).
   */
  static ZPoly gcd(ZPoly a, ZPoly b) {
    if (a.isZero()) {
      return b.primitive();
    }
    if (b.isZero()) {
      return a.primitive();
    }
    if (a.degree() < b.degree()) {
      ZPoly t = a;
      a = b;
      b = t;
    }
    a = a.primitive();
    b = b.primitive();
    while (true) {
      if (b.degree() == 0) {
        return ONE;
      }
      ZPoly r = a.pseudoRemainder(b);
      if (r.isZero()) {
        return b;
      }
      a = b;
      b = r.primitive();
    }
  }

  ZPoly derivative() {
    if (c.length <= 1) {
      return ZERO;
    }
    BigInteger[] b = new BigInteger[c.length - 1];
    for (int i = 1; i < c.length; i++) {
      b[i - 1] = c[i].multiply(BigInteger.valueOf(i));
    }
    return new ZPoly(b);
  }

  /** The value at an integer, by Horner's rule. */
  BigInteger evaluate(BigInteger x) {
    BigInteger v = BigInteger.ZERO;
    for (int i = c.length - 1; i >= 0; i--) {
      v = v.multiply(x).add(c[i]);
    }
    return v;
  }

  /** {@code q^n·f(p/q)}, an integer that is zero exactly when {@code p/q} is a root. */
  BigInteger evaluateScaled(BigInteger p, BigInteger q) {
    BigInteger v = BigInteger.ZERO;
    BigInteger qPower = BigInteger.ONE;
    for (int i = c.length - 1; i >= 0; i--) {
      v = v.multiply(p).add(c[i].multiply(qPower));
      qPower = qPower.multiply(q);
    }
    return v;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof ZPoly z && Arrays.equals(c, z.c);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(c);
  }

  @Override
  public String toString() {
    return "ZPoly" + Arrays.toString(c);
  }
}
