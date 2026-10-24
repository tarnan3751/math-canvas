package mathcanvas.engine.algebra;

import java.util.Arrays;
import java.util.Comparator;

/**
 * A power product: one non-negative exponent per variable of the polynomial it belongs to, in the
 * order of that polynomial's variable list.
 *
 * <p>Monomials are ordered by {@link #ORDER}, graded lexicographic: the total degree first, then
 * the exponents from the first variable on, a larger exponent being the larger monomial. The order
 * is total and depends only on the variable list, which is sorted by {@link
 * mathcanvas.engine.expr.ExprOrder}, so it is the same on every run.
 *
 * @param exponents the exponents, none negative; the accessor returns a copy
 */
public record Monomial(int[] exponents) {

  /**
   * The graded lexicographic order, ascending: {@code 1 < y < x < y² < xy < x²} for {@code [x, y]}.
   */
  public static final Comparator<Monomial> ORDER = Monomial::compare;

  /**
   * Copies and checks the exponents.
   *
   * @throws NullPointerException if the array is null
   * @throws IllegalArgumentException if an exponent is negative
   */
  public Monomial {
    exponents = exponents.clone();
    for (int e : exponents) {
      if (e < 0) {
        throw new IllegalArgumentException("a negative exponent: " + Arrays.toString(exponents));
      }
    }
  }

  /**
   * Returns the monomial 1 over {@code n} variables.
   *
   * @param n how many variables
   * @return the monomial with every exponent 0
   */
  public static Monomial one(int n) {
    return new Monomial(new int[n]);
  }

  /**
   * Returns {@code v_i^k} over {@code n} variables.
   *
   * @param n how many variables
   * @param i the variable's index
   * @param k the exponent
   * @return the monomial
   */
  public static Monomial power(int n, int i, int k) {
    int[] e = new int[n];
    e[i] = k;
    return new Monomial(e);
  }

  @Override
  public int[] exponents() {
    return exponents.clone();
  }

  /**
   * Returns the exponent of variable {@code i}.
   *
   * @param i the variable's index
   * @return its exponent
   */
  public int exponent(int i) {
    return exponents[i];
  }

  /**
   * Returns how many variables the monomial is over.
   *
   * @return the length of the exponent vector
   */
  public int size() {
    return exponents.length;
  }

  /**
   * Returns the total degree, the sum of the exponents.
   *
   * @return the degree
   */
  public long degree() {
    long d = 0;
    for (int e : exponents) {
      d += e;
    }
    return d;
  }

  /**
   * Returns the product {@code this · other}.
   *
   * @param other a monomial over the same variables
   * @return the product
   * @throws ArithmeticException if an exponent overflows an {@code int}
   */
  public Monomial multiply(Monomial other) {
    int[] e = new int[exponents.length];
    for (int i = 0; i < e.length; i++) {
      e[i] = Math.addExact(exponents[i], other.exponents[i]);
    }
    return new Monomial(e);
  }

  /**
   * Whether {@code other} divides this monomial.
   *
   * @param other a monomial over the same variables
   * @return true if no exponent of {@code other} exceeds this one's
   */
  public boolean isDivisibleBy(Monomial other) {
    for (int i = 0; i < exponents.length; i++) {
      if (other.exponents[i] > exponents[i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the quotient {@code this / other}.
   *
   * @param other a monomial over the same variables that divides this one
   * @return the quotient
   * @throws IllegalArgumentException if {@code other} does not divide this monomial
   */
  public Monomial divide(Monomial other) {
    int[] e = new int[exponents.length];
    for (int i = 0; i < e.length; i++) {
      e[i] = exponents[i] - other.exponents[i];
    }
    return new Monomial(e);
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof Monomial m && Arrays.equals(exponents, m.exponents);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(exponents);
  }

  @Override
  public String toString() {
    return "Monomial" + Arrays.toString(exponents);
  }

  /** Raw access for the package's arithmetic; never modified. */
  int[] raw() {
    return exponents;
  }

  private static int compare(Monomial a, Monomial b) {
    int c = Long.compare(a.degree(), b.degree());
    if (c != 0) {
      return c;
    }
    for (int i = 0; i < a.exponents.length; i++) {
      c = Integer.compare(a.exponents[i], b.exponents[i]);
      if (c != 0) {
        return c;
      }
    }
    return 0;
  }
}
