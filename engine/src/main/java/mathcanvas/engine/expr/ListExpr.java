package mathcanvas.engine.expr;

import java.util.List;

/**
 * A general list {@code [a, b]}: what a bracket constructor builds when its elements are neither
 * all scalars nor all vectors of one length; possibly empty.
 *
 * @param items the items
 */
public record ListExpr(List<Expr> items) implements Expr {

  /**
   * Checks and copies the items.
   *
   * @throws NullPointerException if an item is null
   */
  public ListExpr {
    items = List.copyOf(items);
  }

  /**
   * Returns the list of {@code items}.
   *
   * @param items the items
   * @return the list
   */
  public static ListExpr of(Expr... items) {
    return new ListExpr(List.of(items));
  }

  /**
   * Returns the list of {@code items}.
   *
   * @param items the items
   * @return the list
   */
  public static ListExpr of(List<? extends Expr> items) {
    return new ListExpr(List.copyOf(items));
  }

  @Override
  public String toString() {
    return Debug.of("ListExpr", items);
  }
}
