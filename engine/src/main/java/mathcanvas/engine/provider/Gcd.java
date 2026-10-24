package mathcanvas.engine.provider;

import mathcanvas.engine.algebra.Polynomial;

/**
 * The gcd of two polynomials over ℚ. A result is correct when it is a gcd up to a non-zero rational
 * factor; it is normalised to {@link mathcanvas.engine.algebra.Gcd}'s normal form.
 *
 * @param a a polynomial
 * @param b a polynomial
 */
public record Gcd(Polynomial a, Polynomial b) implements Operation<Polynomial> {

  @Override
  public Class<Polynomial> outputType() {
    return Polynomial.class;
  }

  @Override
  public Polynomial normalise(Polynomial result) {
    return mathcanvas.engine.algebra.Gcd.normalise(result, a, b);
  }
}
