package mathcanvas.engine.expr;

import java.util.List;
import java.util.Objects;

/**
 * A piecewise expression: the value of the first piece whose condition holds, else {@code
 * otherwise}, which may be {@link Constant#UNDEFINED}.
 *
 * @param pieces the pieces, at least one
 * @param otherwise the value when no condition holds
 */
public record Piecewise(List<Piece> pieces, Expr otherwise) implements Expr {

  /**
   * One piece: {@code value} where {@code condition} holds.
   *
   * @param value the value
   * @param condition the condition
   */
  public record Piece(Expr value, Expr condition) {

    /**
     * Checks the operands.
     *
     * @throws NullPointerException if an operand is null
     */
    public Piece {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(condition, "condition");
    }

    @Override
    public String toString() {
      return Debug.of("Piece", value, condition);
    }
  }

  /**
   * Checks and copies the operands.
   *
   * @throws NullPointerException if a piece or {@code otherwise} is null
   * @throws IllegalArgumentException if there is no piece
   */
  public Piecewise {
    pieces = List.copyOf(pieces);
    Objects.requireNonNull(otherwise, "otherwise");
    if (pieces.isEmpty()) {
      throw new IllegalArgumentException("a piecewise expression needs at least one piece");
    }
  }

  /**
   * Returns the piecewise expression.
   *
   * @param pieces the pieces, at least one
   * @param otherwise the value when no condition holds
   * @return the piecewise expression
   */
  public static Piecewise of(List<Piece> pieces, Expr otherwise) {
    return new Piecewise(pieces, otherwise);
  }

  @Override
  public String toString() {
    return Debug.of("Piecewise", pieces, otherwise);
  }
}
