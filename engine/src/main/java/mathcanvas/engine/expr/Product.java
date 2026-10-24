package mathcanvas.engine.expr;

import java.util.List;

/**
 * An n-ary product; {@code -x} is {@code Product(-1, x)}. A raw product holds its factors as typed.
 * In canonical form no factor is a product, at most one is a number (the first, the coefficient),
 * no two factors have the same base, the commuting factors are in {@link ExprOrder} order, and the
 * factors built from vectors and matrices follow them in typed order.
 *
 * @param factors the factors, at least two
 */
public record Product(List<Expr> factors) implements Expr {

  /**
   * Checks and copies the factors.
   *
   * @throws NullPointerException if a factor is null
   * @throws IllegalArgumentException if there are fewer than two factors
   */
  public Product {
    factors = List.copyOf(factors);
    if (factors.size() < 2) {
      throw new IllegalArgumentException("a product needs at least two factors: " + factors);
    }
  }

  /**
   * Returns the raw product of {@code factors}.
   *
   * @param factors the factors, at least two
   * @return the product
   */
  public static Product of(Expr... factors) {
    return new Product(List.of(factors));
  }

  /**
   * Returns the raw product of {@code factors}.
   *
   * @param factors the factors, at least two
   * @return the product
   */
  public static Product of(List<? extends Expr> factors) {
    return new Product(List.copyOf(factors));
  }

  @Override
  public String toString() {
    return Debug.of("Product", factors);
  }
}
