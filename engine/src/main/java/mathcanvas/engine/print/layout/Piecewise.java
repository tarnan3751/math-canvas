package mathcanvas.engine.print.layout;

import java.util.List;
import java.util.Objects;
import mathcanvas.engine.rule.Path;

/**
 * A piecewise expression: a brace before rows of a value and its condition, then the value taken
 * otherwise. Its linear form is the call {@code piecewise(v₁, c₁, …, otherwise)}.
 *
 * @param pieces the values and their conditions, at least one
 * @param otherwise the value otherwise
 * @param path the path, or null
 */
public record Piecewise(List<Piece> pieces, Layout otherwise, Path path) implements Layout {

  /**
   * One row.
   *
   * @param value the value
   * @param condition its condition
   */
  public record Piece(Layout value, Layout condition) {

    /** Checks the parts. */
    public Piece {
      Objects.requireNonNull(value);
      Objects.requireNonNull(condition);
    }
  }

  /** Copies the pieces. */
  public Piecewise {
    pieces = List.copyOf(pieces);
    Objects.requireNonNull(otherwise);
    if (pieces.isEmpty()) {
      throw new IllegalArgumentException("no piece");
    }
  }
}
