package mathcanvas.engine.expr;

import java.util.List;

/**
 * A set {@code {a, b}}. A raw set holds its items as typed; in canonical form they are sorted by
 * {@link ExprOrder} and duplicates are removed, so {@code {1, 1.0}} keeps both.
 *
 * @param items the items
 */
public record SetExpr(List<Expr> items) implements Expr {

  /**
   * Checks and copies the items.
   *
   * @throws NullPointerException if an item is null
   */
  public SetExpr {
    items = List.copyOf(items);
  }

  /**
   * Returns the set of {@code items}.
   *
   * @param items the items
   * @return the set
   */
  public static SetExpr of(Expr... items) {
    return new SetExpr(List.of(items));
  }

  /**
   * Returns the set of {@code items}.
   *
   * @param items the items
   * @return the set
   */
  public static SetExpr of(List<? extends Expr> items) {
    return new SetExpr(List.copyOf(items));
  }

  @Override
  public String toString() {
    return Debug.of("SetExpr", items);
  }
}
