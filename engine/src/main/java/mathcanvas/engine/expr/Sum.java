package mathcanvas.engine.expr;

import java.util.List;

/**
 * An n-ary sum. A raw sum holds its terms as typed. In canonical form no term is a sum, at most one
 * is a number (the first), no two terms differ only in their numeric coefficient, and the terms are
 * in {@link ExprOrder} order.
 *
 * @param terms the terms, at least two
 */
public record Sum(List<Expr> terms) implements Expr {

  /**
   * Checks and copies the terms.
   *
   * @throws NullPointerException if a term is null
   * @throws IllegalArgumentException if there are fewer than two terms
   */
  public Sum {
    terms = List.copyOf(terms);
    if (terms.size() < 2) {
      throw new IllegalArgumentException("a sum needs at least two terms: " + terms);
    }
  }

  /**
   * Returns the raw sum of {@code terms}.
   *
   * @param terms the terms, at least two
   * @return the sum
   */
  public static Sum of(Expr... terms) {
    return new Sum(List.of(terms));
  }

  /**
   * Returns the raw sum of {@code terms}.
   *
   * @param terms the terms, at least two
   * @return the sum
   */
  public static Sum of(List<? extends Expr> terms) {
    return new Sum(List.copyOf(terms));
  }

  @Override
  public String toString() {
    return Debug.of("Sum", terms);
  }
}
