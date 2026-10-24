package mathcanvas.engine.algebra;

import java.math.BigInteger;
import java.util.List;
import java.util.Random;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

/**
 * Generators of polynomials for the algebra's properties: variables from {@code x y z}, small
 * integer coefficients most often, some simple rationals and some 30-digit integers, and products
 * of generated factors so that gcds, repeated factors and factorizations are not trivial.
 */
public final class PolynomialArbitraries {

  /** The variables generated, in the order the polynomials sort them. */
  public static final List<Symbol> VARIABLES =
      List.of(Symbol.of("x"), Symbol.of("y"), Symbol.of("z"));

  /** The first variable, {@code x}. */
  public static final Symbol X = VARIABLES.getFirst();

  private PolynomialArbitraries() {}

  /**
   * Polynomials in up to {@code variables} variables of total degree at most {@code degree}, with
   * up to {@code terms} terms; zero included.
   *
   * @param variables how many of {@code x y z}
   * @param degree the largest total degree
   * @param terms the most terms
   * @return the generator
   */
  public static Arbitrary<Polynomial> polynomials(int variables, int degree, int terms) {
    return Arbitraries.randomValue(r -> polynomial(r, variables, degree, terms));
  }

  /**
   * Non-zero polynomials in {@code x} alone.
   *
   * @param degree the largest degree
   * @return the generator
   */
  public static Arbitrary<Polynomial> univariate(int degree) {
    return Arbitraries.randomValue(
        r -> {
          Polynomial p;
          do {
            p = polynomial(r, 1, degree, degree + 1);
          } while (p.isZero());
          return p;
        });
  }

  /**
   * Products of one to three non-zero factors, each possibly squared.
   *
   * @param variables how many of {@code x y z}
   * @param degree the largest total degree of a factor
   * @return the generator
   */
  public static Arbitrary<Polynomial> products(int variables, int degree) {
    return Arbitraries.randomValue(
        r -> {
          Polynomial p = Polynomial.ONE;
          int n = 1 + r.nextInt(3);
          for (int i = 0; i < n; i++) {
            Polynomial f;
            do {
              f = polynomial(r, variables, degree, 4);
            } while (f.isZero());
            p = p.multiply(r.nextInt(4) == 0 ? f.multiply(f) : f);
          }
          return p;
        });
  }

  static Polynomial polynomial(Random r, int variables, int degree, int terms) {
    return polynomial(r, VARIABLES.subList(0, variables), degree, terms);
  }

  /**
   * Polynomials in the given kernels of total degree at most {@code degree}, with up to {@code
   * terms} terms; zero included.
   *
   * @param kernels the variables, symbols or other kernels
   * @param degree the largest total degree
   * @param terms the most terms
   * @return the generator
   */
  public static Arbitrary<Polynomial> polynomials(
      List<? extends Expr> kernels, int degree, int terms) {
    return Arbitraries.randomValue(r -> polynomial(r, kernels, degree, terms));
  }

  static Polynomial polynomial(Random r, List<? extends Expr> kernels, int degree, int terms) {
    Polynomial p = Polynomial.ZERO;
    int n = r.nextInt(terms + 1);
    for (int i = 0; i < n; i++) {
      Polynomial t = Polynomial.constant(coefficient(r));
      int left = r.nextInt(degree + 1);
      for (int v = 0; v < kernels.size() && left > 0; v++) {
        int k = v == kernels.size() - 1 ? left : r.nextInt(left + 1);
        t = t.multiply(Polynomial.variable(kernels.get(v)).pow(k));
        left -= k;
      }
      p = p.add(t);
    }
    return p;
  }

  static RealNumeric coefficient(Random r) {
    int kind = r.nextInt(12);
    if (kind < 9) {
      int c = r.nextInt(19) - 9;
      return Int.of(c == 0 ? 1 : c);
    }
    if (kind < 11) {
      return Rational.of(r.nextInt(19) - 9, 1 + r.nextInt(6));
    }
    return Int.of(new BigInteger(100, r).subtract(BigInteger.ONE.shiftLeft(99)));
  }
}
