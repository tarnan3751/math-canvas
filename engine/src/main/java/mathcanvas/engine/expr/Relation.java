package mathcanvas.engine.expr;

import java.util.Objects;

/**
 * A relation between two expressions. The parser never decides it; the canonical form turns a
 * relation between two numbers into a {@link Bool}.
 *
 * @param op the relation
 * @param left the left side
 * @param right the right side
 */
public record Relation(Op op, Expr left, Expr right) implements Expr {

  /** The relations, in the order {@link ExprOrder} sorts them. */
  public enum Op {
    /** {@code =}. */
    EQ,
    /** {@code ≠}. */
    NE,
    /** {@code <}. */
    LT,
    /** {@code ≤}. */
    LE,
    /** {@code >}. */
    GT,
    /** {@code ≥}. */
    GE,
    /** {@code ∈}: membership in a domain. */
    IN
  }

  /**
   * Checks the operands.
   *
   * @throws NullPointerException if an operand is null
   */
  public Relation {
    Objects.requireNonNull(op, "op");
    Objects.requireNonNull(left, "left");
    Objects.requireNonNull(right, "right");
  }

  /**
   * Returns the relation {@code left op right}.
   *
   * @param op the relation
   * @param left the left side
   * @param right the right side
   * @return the relation
   */
  public static Relation of(Op op, Expr left, Expr right) {
    return new Relation(op, left, right);
  }

  @Override
  public String toString() {
    return Debug.of("Relation", op, left + ", " + right);
  }
}
