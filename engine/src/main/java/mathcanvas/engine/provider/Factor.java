package mathcanvas.engine.provider;

import mathcanvas.engine.algebra.Factorization;
import mathcanvas.engine.algebra.Polynomial;

/**
 * The factorization of a polynomial over ℚ. A result is correct when its product is the polynomial;
 * it is normalised by {@link Factorization#normalised()}.
 *
 * @param p the polynomial
 */
public record Factor(Polynomial p) implements Operation<Factorization> {

  @Override
  public Class<Factorization> outputType() {
    return Factorization.class;
  }

  @Override
  public Factorization normalise(Factorization result) {
    return result.normalised();
  }
}
