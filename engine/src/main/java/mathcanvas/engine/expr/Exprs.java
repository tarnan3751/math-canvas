package mathcanvas.engine.expr;

import mathcanvas.engine.number.Rational;

/**
 * Raw constructions for the notations that have no node of their own: negation, subtraction,
 * division, reciprocal and square root. Each builds the node exactly; nothing is canonicalised.
 */
public final class Exprs {

  private static final Num HALF = Num.of(Rational.of(1, 2));

  private Exprs() {}

  /**
   * Returns {@code -e}, which is {@code Product(-1, e)}.
   *
   * @param e the operand
   * @return the negation
   */
  public static Product neg(Expr e) {
    return Product.of(Num.MINUS_ONE, e);
  }

  /**
   * Returns {@code a - b}, which is {@code Sum(a, Product(-1, b))}.
   *
   * @param a the minuend
   * @param b the subtrahend
   * @return the difference
   */
  public static Sum sub(Expr a, Expr b) {
    return Sum.of(a, neg(b));
  }

  /**
   * Returns {@code a / b}, which is {@code Product(a, Power(b, -1))}.
   *
   * @param a the dividend
   * @param b the divisor
   * @return the quotient
   */
  public static Product div(Expr a, Expr b) {
    return Product.of(a, inv(b));
  }

  /**
   * Returns {@code 1/e}, which is {@code Power(e, -1)}.
   *
   * @param e the operand
   * @return the reciprocal
   */
  public static Power inv(Expr e) {
    return Power.of(e, Num.MINUS_ONE);
  }

  /**
   * Returns {@code √e}, which is {@code Power(e, 1/2)}.
   *
   * @param e the operand
   * @return the square root
   */
  public static Power sqrt(Expr e) {
    return Power.of(e, HALF);
  }
}
