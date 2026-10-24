package mathcanvas.engine.expr;

import java.util.List;

/**
 * A matrix, stored by rows; rectangular and non-empty.
 *
 * @param rows the rows, each a non-empty list of the same length
 */
public record Matrix(List<List<Expr>> rows) implements Expr {

  /**
   * Checks and copies the rows.
   *
   * @throws NullPointerException if a row or an element is null
   * @throws IllegalArgumentException if there is no row, a row is empty, or two rows differ in
   *     length
   */
  public Matrix {
    rows = rows.stream().<List<Expr>>map(List::copyOf).toList();
    if (rows.isEmpty() || rows.getFirst().isEmpty()) {
      throw new IllegalArgumentException("a matrix needs at least one row and one column");
    }
    int columns = rows.getFirst().size();
    for (List<Expr> row : rows) {
      if (row.size() != columns) {
        throw new IllegalArgumentException("a matrix must be rectangular: " + rows);
      }
    }
  }

  /**
   * Returns the matrix with these rows.
   *
   * @param rows the rows, each a non-empty list of the same length
   * @return the matrix
   */
  public static Matrix of(List<? extends List<? extends Expr>> rows) {
    return new Matrix(rows.stream().<List<Expr>>map(List::copyOf).toList());
  }

  @Override
  public String toString() {
    return Debug.of("Matrix", rows.stream().map(row -> Debug.of("", row)).toList());
  }
}
