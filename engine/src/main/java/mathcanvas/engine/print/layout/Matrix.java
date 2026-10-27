package mathcanvas.engine.print.layout;

import java.util.List;
import mathcanvas.engine.rule.Path;

/**
 * A matrix: rows of cells between stretchy fences. Its linear form is {@code [[a, b], [c, d]]}.
 *
 * @param rows the cells by row, all rows the same length
 * @param open the opening fence's code point
 * @param close the closing fence's code point
 * @param path the path, or null
 */
public record Matrix(List<List<Layout>> rows, int open, int close, Path path) implements Layout {

  /** Copies the rows and checks that they are a non-empty rectangle. */
  public Matrix {
    rows = rows.stream().map(List::copyOf).toList();
    if (rows.isEmpty() || rows.getFirst().isEmpty()) {
      throw new IllegalArgumentException("an empty matrix");
    }
    for (List<Layout> row : rows) {
      if (row.size() != rows.getFirst().size()) {
        throw new IllegalArgumentException("a ragged matrix");
      }
    }
  }
}
