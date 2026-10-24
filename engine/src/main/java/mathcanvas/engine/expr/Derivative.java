package mathcanvas.engine.expr;

import java.util.Objects;

/**
 * The unevaluated derivative {@code d^order/dvar^order expr}.
 *
 * @param expr the expression differentiated
 * @param var the variable
 * @param order the order, at least 1
 */
public record Derivative(Expr expr, Symbol var, int order) implements Expr {

  /**
   * Checks the operands.
   *
   * @throws NullPointerException if an operand is null
   * @throws IllegalArgumentException if {@code order} is below 1
   */
  public Derivative {
    Objects.requireNonNull(expr, "expr");
    Objects.requireNonNull(var, "var");
    if (order < 1) {
      throw new IllegalArgumentException("a derivative's order is at least 1: " + order);
    }
  }

  /**
   * Returns the derivative {@code d^order/dvar^order expr}.
   *
   * @param expr the expression differentiated
   * @param var the variable
   * @param order the order, at least 1
   * @return the derivative
   */
  public static Derivative of(Expr expr, Symbol var, int order) {
    return new Derivative(expr, var, order);
  }

  @Override
  public String toString() {
    return Debug.of("Derivative", expr, var.name(), order);
  }
}
