package mathcanvas.engine.algebra;

import java.math.BigInteger;
import java.util.List;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.RealNumeric;

/**
 * Greatest common divisors and contents of polynomials over ℚ.
 *
 * <p>A gcd over ℚ is defined up to a non-zero rational; the one returned is the <b>normal form</b>:
 * the gcd of the inputs' rational contents times the gcd of their primitive parts, which is
 * primitive over ℤ with a positive sign ({@link Gcd#sign}). So {@code gcd(2x + 2, 4x + 4)} is
 * {@code 2x + 2}, {@code gcd(x/2, x/3)} is {@code x/6}, and on integers it is the integer gcd. The
 * rational content of {@code p} is the positive {@code c} for which {@code p/c} has coprime integer
 * coefficients: the gcd of the numerators over the lcm of the denominators.
 *
 * <p>Univariate primitive parts go through the primitive PRS over ℤ (Knuth, vol. 2, §4.6.1,
 * Algorithm E; Geddes, Czapor and Labahn, <i>Algorithms for Computer Algebra</i>, ch. 7). In
 * several variables, a variable only one of them has is removed by taking the other's content in
 * it; then they are taken as univariate in the variable of least degree (the first such in the
 * variable order), with coefficients in ℤ of the others, which shortens the remainder sequence: the
 * gcd is the gcd of the contents, computed recursively, times the primitive part of the last
 * subresultant of the subresultant PRS (Collins 1967; Brown and Traub 1971; Knuth, Algorithm C;
 * Cohen, <i>A Course in Computational Algebraic Number Theory</i>, Algorithm 3.3.1).
 */
public final class Gcd {

  private Gcd() {}

  /**
   * Returns the gcd in normal form.
   *
   * @param a a polynomial
   * @param b a polynomial
   * @return the gcd; 0 when both are zero
   * @throws AlgebraLimitException if a degree exceeds {@link Polynomial#MAX_DEGREE}
   */
  public static Polynomial gcd(Polynomial a, Polynomial b) {
    AlgebraLimitException.checkDegree(a, b);
    if (a.isZero()) {
      return signed(b);
    }
    if (b.isZero()) {
      return signed(a);
    }
    RealNumeric c = gcd(rationalContent(a), rationalContent(b));
    return primitiveGcd(primitivePart(a), primitivePart(b)).multiply(c);
  }

  /**
   * Returns the lcm: {@code a·b/gcd(a, b)} with a positive sign ({@link Gcd#sign}).
   *
   * @param a a polynomial
   * @param b a polynomial
   * @return the lcm; 0 when either is zero
   * @throws AlgebraLimitException if a degree exceeds {@link Polynomial#MAX_DEGREE}
   */
  public static Polynomial lcm(Polynomial a, Polynomial b) {
    if (a.isZero() || b.isZero()) {
      return Polynomial.ZERO;
    }
    // dividing first keeps every intermediate within the inputs' degrees
    return signed(Division.exactQuotient(a, gcd(a, b)).orElseThrow().multiply(b));
  }

  /**
   * Returns the content in one variable: the gcd of the coefficients of the powers of {@code v}, a
   * polynomial in the other variables.
   *
   * @param p a polynomial
   * @param v the variable
   * @return the content in normal form
   */
  public static Polynomial content(Polynomial p, Expr v) {
    int d = p.degree(v);
    Polynomial g = Polynomial.ZERO;
    for (int k = d; k >= 0 && (g.isZero() || !g.isConstant()); k--) {
      g = gcd(g, p.coefficient(v, k));
    }
    if (g.isConstant() && d >= 0) {
      return Polynomial.constant(gcdOfCoefficients(p));
    }
    return g;
  }

  /**
   * Returns the rational content: the positive {@code c} for which {@code p/c} has coprime integer
   * coefficients.
   *
   * @param p a polynomial
   * @return the content; 0 for the zero polynomial
   */
  public static RealNumeric rationalContent(Polynomial p) {
    return gcdOfCoefficients(p);
  }

  /**
   * Returns the primitive part over ℤ with a positive sign ({@link Gcd#sign}).
   *
   * @param p a polynomial
   * @return {@code ±p/c}, {@code c} the rational content; 0 for the zero polynomial
   */
  public static Polynomial primitivePart(Polynomial p) {
    if (p.isZero()) {
      return p;
    }
    RealNumeric c = rationalContent(p);
    if (sign(p) < 0) {
      c = c.negate();
    }
    return p.multiply(Q.divide(Int.ONE, c));
  }

  /** {@code p} or {@code -p}, whichever has the positive {@link #sign}. */
  static Polynomial signed(Polynomial p) {
    return sign(p) < 0 ? p.negate() : p;
  }

  /**
   * The sign of the normal form: that of the leading coefficient under the <b>symbols-first</b>
   * order, which compares the total degree in the symbol kernels, then their exponents
   * lexicographically in variable order, then the total degree in the other kernels, then their
   * exponents lexicographically. That is an admissible monomial order, so the sign is
   * multiplicative ({@code sign(pq) = sign(p)·sign(q)}), and a symbol outranks every other kernel:
   * {@code x - π}, {@code x - sin(x)} and {@code x² - sin(x)²} keep {@code x} positive. The terms
   * stay stored in the graded lexicographic order; the sign scans them.
   *
   * @param p a polynomial
   * @return -1, 0 or 1
   */
  public static int sign(Polynomial p) {
    List<Expr> vs = p.variables();
    boolean[] symbol = new boolean[vs.size()];
    for (int i = 0; i < symbol.length; i++) {
      symbol[i] = vs.get(i) instanceof Symbol;
    }
    Polynomial.Term lead = null;
    for (Polynomial.Term t : p.terms()) {
      if (lead == null || symbolsFirst(t.monomial(), lead.monomial(), symbol) > 0) {
        lead = t;
      }
    }
    return lead == null ? 0 : lead.coefficient().signum();
  }

  private static int symbolsFirst(Monomial a, Monomial b, boolean[] symbol) {
    for (boolean symbols : new boolean[] {true, false}) {
      long da = 0;
      long db = 0;
      for (int i = 0; i < symbol.length; i++) {
        if (symbol[i] == symbols) {
          da += a.exponent(i);
          db += b.exponent(i);
        }
      }
      if (da != db) {
        return Long.compare(da, db);
      }
      for (int i = 0; i < symbol.length; i++) {
        if (symbol[i] == symbols && a.exponent(i) != b.exponent(i)) {
          return Integer.compare(a.exponent(i), b.exponent(i));
        }
      }
    }
    return 0;
  }

  private static RealNumeric gcdOfCoefficients(Polynomial p) {
    BigInteger num = BigInteger.ZERO;
    BigInteger den = BigInteger.ONE;
    for (Polynomial.Term t : p.terms()) {
      num = num.gcd(Q.num(t.coefficient()));
      BigInteger d = Q.den(t.coefficient());
      den = den.divide(den.gcd(d)).multiply(d);
    }
    return Q.of(num, den);
  }

  /** The gcd of two rationals: the gcd of the numerators over the lcm of the denominators. */
  private static RealNumeric gcd(RealNumeric a, RealNumeric b) {
    BigInteger da = Q.den(a);
    BigInteger db = Q.den(b);
    return Q.of(Q.num(a).gcd(Q.num(b)), da.divide(da.gcd(db)).multiply(db));
  }

  /** The gcd of two primitive polynomials with positive sign ({@link Gcd#sign}), in normal form. */
  static Polynomial primitiveGcd(Polynomial a, Polynomial b) {
    if (a.isConstant() || b.isConstant()) {
      return Polynomial.ONE;
    }
    if (a.equals(b)) {
      return a;
    }
    for (Expr w : Polynomial.union(a.variables(), b.variables())) {
      if (!a.variables().contains(w)) {
        return primitiveGcd(a, content(b, w));
      }
      if (!b.variables().contains(w)) {
        return primitiveGcd(content(a, w), b);
      }
    }
    if (a.variables().size() == 1) {
      return ZPoly.gcd(ZPoly.of(a), ZPoly.of(b)).toPolynomial(a.variables().getFirst());
    }
    // the main variable is the one of least degree, which shortens the remainder sequence
    Expr v = a.variables().getFirst();
    for (Expr w : a.variables()) {
      if (Math.max(a.degree(w), b.degree(w)) < Math.max(a.degree(v), b.degree(v))) {
        v = w;
      }
    }
    Polynomial ca = content(a, v);
    Polynomial cb = content(b, v);
    Polynomial c = primitiveGcd(ca, cb);
    Polynomial s =
        subresultant(
            Division.exactQuotient(a, ca).orElseThrow(),
            Division.exactQuotient(b, cb).orElseThrow(),
            v);
    if (s.degree(v) == 0) {
      return c;
    }
    Polynomial g = Division.exactQuotient(s, content(s, v)).orElseThrow();
    return primitivePart(c.multiply(g));
  }

  /**
   * The last non-zero term of the subresultant PRS of {@code a} and {@code b} in {@code v}, or 1
   * when a remainder of degree 0 shows they are coprime (Cohen, Algorithm 3.3.1).
   */
  private static Polynomial subresultant(Polynomial a, Polynomial b, Expr v) {
    if (a.degree(v) < b.degree(v)) {
      Polynomial t = a;
      a = b;
      b = t;
    }
    Polynomial g = Polynomial.ONE;
    Polynomial h = Polynomial.ONE;
    while (true) {
      int delta = a.degree(v) - b.degree(v);
      Polynomial r = Division.pseudoDivide(a, b, v).remainder();
      if (r.isZero()) {
        return b;
      }
      if (r.degree(v) == 0) {
        return Polynomial.ONE;
      }
      a = b;
      b = Division.exactQuotient(r, g.multiply(h.pow(delta))).orElseThrow();
      g = a.leadingCoefficient(v);
      // h = h^(1 - δ)·g^δ
      h = delta == 0 ? h : Division.exactQuotient(g.pow(delta), h.pow(delta - 1)).orElseThrow();
    }
  }
}
