package mathcanvas.engine.expr;

import java.util.List;

/**
 * A vector {@code ⟨a, b⟩}, with at least one element; {@code []} is the empty {@link ListExpr}.
 *
 * @param items the items
 */
public record Vector(List<Expr> items) implements Expr {

  /**
   * Checks and copies the items.
   *
   * @throws NullPointerException if an item is null
   * @throws IllegalArgumentException if there are no items
   */
  public Vector {
    items = List.copyOf(items);
    if (items.isEmpty()) {
      throw new IllegalArgumentException("a vector needs at least one element");
    }
  }

  /**
   * Returns the vector of {@code items}.
   *
   * @param items the items
   * @return the vector
   */
  public static Vector of(Expr... items) {
    return new Vector(List.of(items));
  }

  /**
   * Returns the vector of {@code items}.
   *
   * @param items the items
   * @return the vector
   */
  public static Vector of(List<? extends Expr> items) {
    return new Vector(List.copyOf(items));
  }

  @Override
  public String toString() {
    return Debug.of("Vector", items);
  }
}
