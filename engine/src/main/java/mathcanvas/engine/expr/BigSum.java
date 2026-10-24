package mathcanvas.engine.expr;

import java.util.Objects;

/**
 * The unevaluated sum {@code Σ[var = from, to] body}.
 *
 * @param body the expression summed
 * @param var the index
 * @param from the first value of the index
 * @param to the last value of the index
 */
public record BigSum(Expr body, Symbol var, Expr from, Expr to) implements Expr {

  /**
   * Checks the operands.
   *
   * @throws NullPointerException if an operand is null
   */
  public BigSum {
    Objects.requireNonNull(body, "body");
    Objects.requireNonNull(var, "var");
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
  }

  /**
   * Returns the sum {@code Σ[var = from, to] body}.
   *
   * @param body the expression summed
   * @param var the index
   * @param from the first value of the index
   * @param to the last value of the index
   * @return the sum
   */
  public static BigSum of(Expr body, Symbol var, Expr from, Expr to) {
    return new BigSum(body, var, from, to);
  }

  @Override
  public String toString() {
    return Debug.of("BigSum", body, var.name(), from + ", " + to);
  }
}
