package mathcanvas.engine.algebra;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.RealNumeric;

/** Polynomial division: univariate over ℚ, pseudo-division in one variable, and exact division. */
public final class Division {

  /**
   * A quotient and a remainder.
   *
   * @param quotient the quotient
   * @param remainder the remainder
   */
  public record Result(Polynomial quotient, Polynomial remainder) {}

  private Division() {}

  /**
   * Divides with remainder over ℚ: {@code a = q·b + r} with {@code deg r < deg b} (Knuth, vol. 2,
   * §4.6.1, Algorithm D).
   *
   * @param a the dividend
   * @param b the divisor, not zero
   * @return the quotient and remainder
   * @throws ArithmeticException if {@code b} is zero
   * @throws AlgebraLimitException if a degree exceeds {@link Polynomial#MAX_DEGREE}
   * @throws IllegalArgumentException if {@code a} and {@code b} have more than one variable between
   *     them
   */
  public static Result divide(Polynomial a, Polynomial b) {
    if (b.isZero()) {
      throw new ArithmeticException("division by the zero polynomial");
    }
    AlgebraLimitException.checkDegree(a, b);
    List<Expr> vs = Polynomial.union(a.variables(), b.variables());
    if (vs.size() > 1) {
      throw new IllegalArgumentException("a univariate division over several variables: " + vs);
    }
    if (vs.isEmpty()) {
      return new Result(a.multiply(Q.divide(Int.ONE, b.leadingCoefficient())), Polynomial.ZERO);
    }
    // the remainder is kept as one sorted map, so a step costs O(terms of b), not O(terms of a)
    TreeMap<Monomial, RealNumeric> r = new TreeMap<>(Monomial.ORDER.reversed());
    r.putAll(a.over(vs));
    TreeMap<Monomial, RealNumeric> divisor = b.over(vs);
    int n = divisor.lastKey().exponent(0);
    RealNumeric lead = divisor.lastEntry().getValue();
    Map<Monomial, RealNumeric> q = new TreeMap<>(Monomial.ORDER);
    while (!r.isEmpty() && r.firstKey().exponent(0) >= n) {
      Map.Entry<Monomial, RealNumeric> top = r.firstEntry();
      Monomial t = Monomial.power(1, 0, top.getKey().exponent(0) - n);
      RealNumeric k = Q.divide(top.getValue(), lead);
      q.put(t, k);
      subtract(r, t, k, divisor);
    }
    return new Result(Polynomial.build(vs, q), Polynomial.build(vs, r));
  }

  /**
   * Pseudo-divides in one variable: {@code lc(b)^(m-n+1)·a = q·b + r} with {@code deg_v r < deg_v
   * b}, where {@code m} and {@code n} are the degrees in {@code v} and {@code lc(b)} is {@code b}'s
   * leading coefficient in {@code v}, a polynomial in the other variables (Knuth, vol. 2, §4.6.1,
   * Algorithm R). When {@code m < n} the quotient is 0 and the remainder {@code a}.
   *
   * @param a the dividend
   * @param b the divisor, not zero
   * @param v the variable
   * @return the pseudo-quotient and pseudo-remainder
   * @throws ArithmeticException if {@code b} is zero
   * @throws AlgebraLimitException if a degree exceeds {@link Polynomial#MAX_DEGREE}
   */
  public static Result pseudoDivide(Polynomial a, Polynomial b, Expr v) {
    if (b.isZero()) {
      throw new ArithmeticException("division by the zero polynomial");
    }
    AlgebraLimitException.checkDegree(a, b);
    int m = a.degree(v);
    int n = b.degree(v);
    if (m < n) {
      return new Result(Polynomial.ZERO, a);
    }
    Polynomial lead = b.leadingCoefficient(v);
    Polynomial q = Polynomial.ZERO;
    Polynomial r = a;
    int e = m - n + 1;
    while (!r.isZero() && r.degree(v) >= n) {
      Polynomial s = r.leadingCoefficient(v).multiply(Polynomial.variable(v).pow(r.degree(v) - n));
      boolean monic = lead.equals(Polynomial.ONE);
      q = (monic ? q : lead.multiply(q)).add(s);
      r = (monic ? r : lead.multiply(r)).subtract(s.multiply(b));
      e--;
    }
    Polynomial scale = lead.pow(e);
    return new Result(q.multiply(scale), r.multiply(scale));
  }

  /**
   * Divides exactly, in any number of variables: the division algorithm by one divisor in the
   * graded lexicographic order (Cox, Little and O'Shea, <i>Ideals, Varieties, and Algorithms</i>,
   * §2.3). A single polynomial is a Gröbner basis of the ideal it generates, so the remainder is
   * zero exactly when {@code b} divides {@code a}.
   *
   * @param a the dividend
   * @param b the divisor, not zero
   * @return {@code a/b}, or empty when {@code b} does not divide {@code a} over ℚ
   * @throws ArithmeticException if {@code b} is zero
   * @throws AlgebraLimitException if a degree exceeds {@link Polynomial#MAX_DEGREE}
   */
  public static Optional<Polynomial> exactQuotient(Polynomial a, Polynomial b) {
    if (b.isZero()) {
      throw new ArithmeticException("division by the zero polynomial");
    }
    AlgebraLimitException.checkDegree(a, b);
    if (a.isZero()) {
      return Optional.of(Polynomial.ZERO);
    }
    List<Expr> vs = Polynomial.union(a.variables(), b.variables());
    TreeMap<Monomial, RealNumeric> rem = new TreeMap<>(Monomial.ORDER.reversed());
    rem.putAll(a.over(vs));
    TreeMap<Monomial, RealNumeric> divisor = b.over(vs);
    Monomial leadMonomial = divisor.lastKey();
    RealNumeric leadCoefficient = divisor.lastEntry().getValue();
    Map<Monomial, RealNumeric> q = new TreeMap<>(Monomial.ORDER);
    while (!rem.isEmpty()) {
      Map.Entry<Monomial, RealNumeric> top = rem.firstEntry();
      if (!top.getKey().isDivisibleBy(leadMonomial)) {
        return Optional.empty();
      }
      Monomial t = top.getKey().divide(leadMonomial);
      RealNumeric k = Q.divide(top.getValue(), leadCoefficient);
      q.put(t, k);
      subtract(rem, t, k, divisor);
    }
    return Optional.of(Polynomial.build(vs, q));
  }

  /** {@code r -= k·t·divisor}, in place, dropping the terms that cancel. */
  private static void subtract(
      Map<Monomial, RealNumeric> r, Monomial t, RealNumeric k, Map<Monomial, RealNumeric> divisor) {
    for (Map.Entry<Monomial, RealNumeric> d : divisor.entrySet()) {
      Monomial m = t.multiply(d.getKey());
      RealNumeric c = Q.subtract(r.getOrDefault(m, Int.ZERO), Q.multiply(k, d.getValue()));
      if (c.isZero()) {
        r.remove(m);
      } else {
        r.put(m, c);
      }
    }
  }
}
