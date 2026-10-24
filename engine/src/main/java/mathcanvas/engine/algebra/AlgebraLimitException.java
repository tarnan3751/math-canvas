package mathcanvas.engine.algebra;

import java.io.Serial;
import java.util.Locale;

/**
 * An algebra operation refused because its input is past one of the algebra's bounds: a product or
 * power of more than {@link Polynomial#MAX_TERMS} terms, or a polynomial of degree above {@link
 * Polynomial#MAX_DEGREE} in some kernel given to {@code gcd}, {@code lcm}, {@code factor}, {@code
 * apart} or {@code cancel}. The evaluator reports it as an evaluation error.
 */
public final class AlgebraLimitException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * An exception with its reason.
   *
   * @param message the reason
   */
  public AlgebraLimitException(String message) {
    super(message);
  }

  /**
   * Checks the degree bound.
   *
   * @param ps the polynomials
   * @throws AlgebraLimitException if one has a degree above {@link Polynomial#MAX_DEGREE} in some
   *     kernel
   */
  static void checkDegree(Polynomial... ps) {
    for (Polynomial p : ps) {
      if (p.maxDegree() > Polynomial.MAX_DEGREE) {
        throw new AlgebraLimitException(
            String.format(
                Locale.ROOT, "the polynomial's degree exceeds %,d", Polynomial.MAX_DEGREE));
      }
    }
  }
}
