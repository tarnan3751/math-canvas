package mathcanvas.engine.expr;

import java.util.Objects;

/**
 * The unevaluated integral of {@code integrand} over {@code var}: indefinite when both bounds are
 * null, definite when both are present. Use {@link #indefinite} and {@link #definite}; a pattern
 * tells the two apart with a guard, {@code case Integral(var f, var x, var a, var b) when a ==
 * null}.
 *
 * @param integrand the integrand
 * @param var the variable of integration
 * @param lower the lower bound, or null for an indefinite integral
 * @param upper the upper bound, or null for an indefinite integral
 */
public record Integral(Expr integrand, Symbol var, Expr lower, Expr upper) implements Expr {

  /**
   * Checks the operands.
   *
   * @throws NullPointerException if the integrand or the variable is null
   * @throws IllegalArgumentException if exactly one bound is null
   */
  public Integral {
    Objects.requireNonNull(integrand, "integrand");
    Objects.requireNonNull(var, "var");
    if ((lower == null) != (upper == null)) {
      throw new IllegalArgumentException("an integral has both bounds or neither");
    }
  }

  /**
   * Returns the indefinite integral {@code ∫ integrand dvar}.
   *
   * @param integrand the integrand
   * @param var the variable of integration
   * @return the integral
   */
  public static Integral indefinite(Expr integrand, Symbol var) {
    return new Integral(integrand, var, null, null);
  }

  /**
   * Returns the definite integral {@code ∫[lower, upper] integrand dvar}.
   *
   * @param integrand the integrand
   * @param var the variable of integration
   * @param lower the lower bound
   * @param upper the upper bound
   * @return the integral
   * @throws NullPointerException if a bound is null
   */
  public static Integral definite(Expr integrand, Symbol var, Expr lower, Expr upper) {
    return new Integral(
        integrand,
        var,
        Objects.requireNonNull(lower, "lower"),
        Objects.requireNonNull(upper, "upper"));
  }

  /**
   * Whether the integral has bounds.
   *
   * @return true for a definite integral
   */
  public boolean isDefinite() {
    return lower != null;
  }

  @Override
  public String toString() {
    return isDefinite()
        ? Debug.of("Integral", integrand, var.name(), lower + ", " + upper)
        : Debug.of("Integral", integrand, var.name());
  }
}
