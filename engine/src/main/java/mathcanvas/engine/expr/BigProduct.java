package mathcanvas.engine.expr;

import java.util.Objects;

/**
 * The unevaluated product {@code Π[var = from, to] body}.
 *
 * @param body the expression multiplied
 * @param var the index
 * @param from the first value of the index
 * @param to the last value of the index
 */
public record BigProduct(Expr body, Symbol var, Expr from, Expr to) implements Expr {

  /**
   * Checks the operands.
   *
   * @throws NullPointerException if an operand is null
   */
  public BigProduct {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(var, "var");
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
  }

  /**
   * Returns the product {@code Π[var = from, to] body}.
   *
   * @param body the expression multiplied
   * @param var the index
   * @param from the first value of the index
   * @param to the last value of the index
   * @return the product
   */
  public static BigProduct of(Expr body, Symbol var, Expr from, Expr to) {
    return new BigProduct(body, var, from, to);
  }

  @Override
  public String toString() {
    return Debug.of("BigProduct", body, var.name(), from + ", " + to);
  }
}
