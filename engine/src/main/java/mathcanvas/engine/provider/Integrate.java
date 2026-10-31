package mathcanvas.engine.provider;

import java.math.MathContext;
import java.util.Objects;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;

/**
 * Integration of an expression in a variable. Without bounds a result is correct when it is an
 * antiderivative of the integrand, free of unevaluated integrals in the variable; with bounds, when
 * it is the exact value of the definite integral. It is normalised to its canonical form at the
 * precision.
 *
 * @param integrand the integrand, canonical
 * @param var the variable of integration
 * @param lower the lower bound, or null for an indefinite integral
 * @param upper the upper bound, or null for an indefinite integral
 * @param precision the precision of the canonical form
 */
public record Integrate(Expr integrand, Symbol var, Expr lower, Expr upper, MathContext precision)
    implements Operation<Expr> {

  /**
   * Checks the inputs.
   *
   * @throws NullPointerException if the integrand, the variable or the precision is null
   * @throws IllegalArgumentException if exactly one bound is null
   */
  public Integrate {
    Objects.requireNonNull(integrand, "integrand");
    Objects.requireNonNull(var, "var");
    Objects.requireNonNull(precision, "precision");
    if ((lower == null) != (upper == null)) {
      throw new IllegalArgumentException("an integral has both bounds or neither");
    }
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
  public Class<Expr> outputType() {
    return Expr.class;
  }

  @Override
  public Expr normalise(Expr result) {
    return Canonical.of(result, precision);
  }
}
