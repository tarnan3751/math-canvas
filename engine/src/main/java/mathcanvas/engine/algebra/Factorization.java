package mathcanvas.engine.algebra;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprOrder;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.RealNumeric;

/**
 * A factorization {@code unit · ∏ base^multiplicity} over ℚ.
 *
 * <p>In normal form ({@link #normalised()}) every base is non-constant, primitive over ℤ with a
 * positive sign ({@link Gcd#sign}), and distinct from the others, and the bases are sorted by the
 * order of their expressions; the unit carries every rational factor.
 *
 * @param unit the rational unit; 0 only for the factorization of 0, which has no factors
 * @param factors the factors
 * @param complete true when every base is proven irreducible over ℚ (over its kernels); false when
 *     a search bound stopped the factoring and some base may still split
 */
public record Factorization(RealNumeric unit, List<Factor> factors, boolean complete) {

  /**
   * One factor: a base and its multiplicity.
   *
   * @param base the base
   * @param multiplicity the multiplicity, at least 1
   */
  public record Factor(Polynomial base, int multiplicity) {

    /**
     * Checks the multiplicity.
     *
     * @throws IllegalArgumentException if it is below 1
     */
    public Factor {
      if (multiplicity < 1) {
        throw new IllegalArgumentException("a multiplicity below 1: " + multiplicity);
      }
    }
  }

  /** Copies the factors. */
  public Factorization {
    factors = List.copyOf(factors);
  }

  /**
   * Returns the product, which is the polynomial factored.
   *
   * @return {@code unit · ∏ base^multiplicity}
   */
  public Polynomial expand() {
    Polynomial p = Polynomial.constant(unit);
    for (Factor f : factors) {
      p = p.multiply(f.base().pow(f.multiplicity()));
    }
    return p;
  }

  /**
   * Returns the product of two factorizations, in normal form: the units multiplied, equal bases'
   * multiplicities added.
   *
   * @param other the other factorization
   * @return the product, complete when both are
   */
  public Factorization multiply(Factorization other) {
    List<Factor> fs = new ArrayList<>(factors);
    fs.addAll(other.factors);
    return new Factorization(Q.multiply(unit, other.unit), fs, complete && other.complete)
        .normalised();
  }

  /**
   * Returns the {@code n}-th power: the unit to the {@code n}, every multiplicity times {@code n}.
   *
   * @param n the exponent, at least 0
   * @return the power
   * @throws AlgebraLimitException if the unit's power would pass {@link Canonical#MAX_FOLD_BITS}
   *     bits (its numerator's and denominator's bit lengths times {@code n}, as the canonical form
   *     folds a number's power) or a multiplicity would overflow an {@code int}
   */
  public Factorization pow(int n) {
    if (n == 0) {
      return new Factorization(Int.ONE, List.of(), true);
    }
    List<Factor> fs = new ArrayList<>(factors.size());
    for (Factor f : factors) {
      long m = (long) f.multiplicity() * n;
      if (m > Integer.MAX_VALUE) {
        throw new AlgebraLimitException("the exponents are too large");
      }
      fs.add(new Factor(f.base(), (int) m));
    }
    return new Factorization(Q.pow(unit, n), fs, complete);
  }

  /**
   * Returns the normal form: constant bases folded into the unit, every base made primitive with a
   * positive sign ({@link Gcd#sign}) (its content and sign going into the unit), equal bases
   * merged, and the bases sorted.
   *
   * @return the normal form, with the same product and completeness
   */
  public Factorization normalised() {
    RealNumeric u = unit;
    List<Factor> out = new ArrayList<>();
    for (Factor f : factors) {
      Polynomial b = f.base();
      if (b.isConstant()) {
        u = Q.multiply(u, Q.pow(b.leadingCoefficient(), f.multiplicity()));
        continue;
      }
      Polynomial pp = Gcd.primitivePart(b);
      RealNumeric c = Q.divide(b.leadingCoefficient(), pp.leadingCoefficient());
      u = Q.multiply(u, Q.pow(c, f.multiplicity()));
      int k = indexOf(out, pp);
      if (k >= 0) {
        out.set(k, new Factor(pp, add(out.get(k).multiplicity(), f.multiplicity())));
      } else {
        out.add(new Factor(pp, f.multiplicity()));
      }
    }
    if (u.isZero()) {
      return new Factorization(Int.ZERO, List.of(), true);
    }
    out.sort(Comparator.comparing(f -> f.base().toExpr(), ExprOrder.INSTANCE));
    return new Factorization(u, out, complete);
  }

  private static int add(int a, int b) {
    try {
      return Math.addExact(a, b);
    } catch (ArithmeticException overflow) {
      throw new AlgebraLimitException("the exponents are too large");
    }
  }

  private static int indexOf(List<Factor> fs, Polynomial p) {
    for (int i = 0; i < fs.size(); i++) {
      if (fs.get(i).base().equals(p)) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Returns the factored expression, canonical: a product of the unit and the bases' powers, which
   * the canonical form keeps apart.
   *
   * @return the expression
   */
  public Expr toExpr() {
    return Canonical.of(raw());
  }

  /** The raw product, for a caller that canonicalises under its own precision. */
  Expr raw() {
    List<Expr> fs = new ArrayList<>();
    fs.add(Num.of(unit));
    for (Factor f : factors) {
      Expr b = f.base().raw();
      fs.add(f.multiplicity() == 1 ? b : Power.of(b, Num.of(f.multiplicity())));
    }
    return fs.size() == 1 ? fs.getFirst() : Product.of(fs);
  }
}
