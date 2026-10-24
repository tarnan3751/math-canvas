package mathcanvas.engine.expr;

import java.util.Objects;

/**
 * The unevaluated limit of {@code expr} as {@code var} approaches {@code point}.
 *
 * @param expr the expression
 * @param var the variable
 * @param point the point approached, possibly {@code ∞}
 * @param direction the side approached from
 */
public record Limit(Expr expr, Symbol var, Expr point, Direction direction) implements Expr {

  /** The side a limit approaches from. */
  public enum Direction {
    /** Both sides: the two-sided limit. */
    BOTH,
    /** From the left, below the point. */
    LEFT,
    /** From the right, above the point. */
    RIGHT
  }

  /**
   * Checks the operands.
   *
   * @throws NullPointerException if an operand is null
   */
  public Limit {
    Objects.requireNonNull(expr, "expr");
    Objects.requireNonNull(var, "var");
    Objects.requireNonNull(point, "point");
    Objects.requireNonNull(direction, "direction");
  }

  /**
   * Returns the limit of {@code expr} as {@code var} approaches {@code point}.
   *
   * @param expr the expression
   * @param var the variable
   * @param point the point approached
   * @param direction the side approached from
   * @return the limit
   */
  public static Limit of(Expr expr, Symbol var, Expr point, Direction direction) {
    return new Limit(expr, var, point, direction);
  }

  @Override
  public String toString() {
    return Debug.of("Limit", expr, var.name(), point, direction);
  }
}
