package mathcanvas.engine.number;

import java.math.MathContext;

/**
 * A number of the engine's tower: {@link Int}, {@link Rational}, {@link Real} or {@link Complex}.
 *
 * <p>Arithmetic promotes the lower operand to the higher kind and demotes an exactly representable
 * result; a {@code Real} operand makes the result inexact (see the package documentation). Every
 * operation that can be inexact takes a {@link MathContext}, which exact kinds ignore; a {@code
 * Real} result is the exact result rounded once to it. {@link MathContext#UNLIMITED} is accepted:
 * addition, subtraction and multiplication are then exact, and every operation whose result would
 * be inexact (a division or root whose decimal expansion does not terminate, an irrational value)
 * throws {@link ArithmeticException} with the message "an inexact result needs a finite precision".
 */
public sealed interface Numeric permits RealNumeric, Complex {

  /**
   * Returns {@code this + other}.
   *
   * @param other the addend
   * @param mc the context a {@code Real} result is rounded to
   * @return the sum
   */
  default Numeric add(Numeric other, MathContext mc) {
    return Arithmetic.add(this, other, mc);
  }

  /**
   * Returns {@code this - other}.
   *
   * @param other the subtrahend
   * @param mc the context a {@code Real} result is rounded to
   * @return the difference
   */
  default Numeric subtract(Numeric other, MathContext mc) {
    return Arithmetic.add(this, other.negate(), mc);
  }

  /**
   * Returns {@code this * other}.
   *
   * @param other the multiplier
   * @param mc the context a {@code Real} result is rounded to
   * @return the product
   */
  default Numeric multiply(Numeric other, MathContext mc) {
    return Arithmetic.multiply(this, other, mc);
  }

  /**
   * Returns {@code this / other}, exact when both operands are exact.
   *
   * @param other the divisor
   * @param mc the context a {@code Real} result is rounded to
   * @return the quotient
   * @throws ArithmeticException if {@code other} is zero, exact or not
   */
  default Numeric divide(Numeric other, MathContext mc) {
    return Arithmetic.divide(this, other, mc);
  }

  /**
   * Returns {@code -this}, exactly.
   *
   * @return the negation
   */
  Numeric negate();

  /**
   * Returns {@code this} raised to an integer power, exact for exact kinds; {@code 0^0} is 1.
   *
   * @param n the exponent
   * @param mc the context a {@code Real} result is rounded to
   * @return the power
   * @throws ArithmeticException if {@code this} is zero and {@code n} is negative
   */
  default Numeric pow(int n, MathContext mc) {
    return Powers.pow(this, n, mc);
  }

  /**
   * Returns the principal square root: exact when {@code this} is the square of a rational ({@code
   * sqrt(4/9)} is {@code 2/3}), a {@code Complex} for a negative real ({@code sqrt(-4)} is {@code
   * 2i}), otherwise inexact at {@code mc}.
   *
   * @param mc the context an inexact result is rounded to
   * @return the principal square root
   */
  default Numeric sqrt(MathContext mc) {
    return Powers.sqrt(this, mc);
  }

  /**
   * Returns the modulus {@code |this|}: the absolute value of a real number, exactly; for a complex
   * number {@code sqrt(re² + im²)}, exact when that is a rational square ({@code |3+4i|} is {@code
   * 5}), otherwise faithful (within one ulp): the squares are summed a few digits beyond {@code mc}
   * before the square root, so it is not correctly rounded.
   *
   * @param mc the context an inexact result is rounded to
   * @return the modulus, never negative
   */
  default RealNumeric modulus(MathContext mc) {
    return Powers.modulus(this, mc);
  }

  /**
   * Returns the real part; a real number is its own real part.
   *
   * @return the real part
   */
  RealNumeric re();

  /**
   * Returns the imaginary part; that of a real number is {@code Int 0}.
   *
   * @return the imaginary part
   */
  RealNumeric im();

  /**
   * Returns the complex conjugate; a real number is its own conjugate.
   *
   * @return the conjugate
   */
  Numeric conj();

  /**
   * Returns this number converted to a {@code Real} (each part, for a complex number), rounded to
   * {@code mc}.
   *
   * @param mc the context the conversion rounds to
   * @return the inexact equivalent
   */
  Numeric toReal(MathContext mc);

  /**
   * Whether the value is zero; a value-based test, so {@code Real 0.0} is zero (see {@link
   * #isExact()}).
   *
   * @return true if the value is zero
   */
  boolean isZero();

  /**
   * Whether the value is one; a value-based test, so {@code Real 1.0} is one (see {@link
   * #isExact()}).
   *
   * @return true if the value is one
   */
  boolean isOne();

  /**
   * Whether the value is an integer; a value-based test, so {@code Real 5.0} is an integer (see
   * {@link #isExact()}).
   *
   * @return true if the value is an integer (with a zero imaginary part, for a complex number)
   */
  boolean isInteger();

  /**
   * Whether this number is exact: false for a {@code Real} and for a {@code Complex} with a {@code
   * Real} part.
   *
   * <p>The value predicates ({@link #isZero()}, {@link #isOne()}, {@link #isInteger()}) ignore
   * exactness, so a rule must test both: {@code x·1.0} must not be rewritten to {@code x}, because
   * the product is inexact and {@code x} is not, even though {@code Real 1.0} {@link #isOne() is
   * one}.
   *
   * @return true if no part is a {@code Real}
   */
  boolean isExact();
}
