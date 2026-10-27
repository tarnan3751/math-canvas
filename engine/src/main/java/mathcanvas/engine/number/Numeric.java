package mathcanvas.engine.number;

import java.math.MathContext;
import mathcanvas.engine.Cancellation;

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
   * Returns {@code this^y}, the principal value {@code exp(y·Log this)} with the logarithm's
   * argument in {@code (-π, π]}. The result is exact only when both operands are exact and the
   * value is a Gaussian rational:
   *
   * <ul>
   *   <li>an {@code Int} exponent: {@link #pow(int, MathContext)}; outside the {@code int} range
   *       only for the bases 0, 1 and -1;
   *   <li>a rational exponent {@code p/q} on a non-negative exact base whose numerator and
   *       denominator are perfect {@code q}-th powers: {@code 4^(1/2)} is {@code 2}, {@code
   *       8^(1/3)} is {@code 2}, {@code (8/27)^(2/3)} is {@code 4/9};
   *   <li>a negative exact base with {@code q = 2} and {@code |this|} a rational square: {@code
   *       |this|^(p/2)·i^p}, so {@code (-4)^(1/2)} is {@code 2i} and {@code (-4)^(3/2)} is {@code
   *       -8i}; the exponent 1/2 is {@link #sqrt}, exact for a Gaussian rational square.
   * </ul>
   *
   * <p>Otherwise the result is inexact at {@code mc}, accurate as {@link Elementary} states. A
   * negative base with any other non-integer exponent gives the principal complex value: {@code
   * (-8)^(1/3)} is {@code 1 + 1.7320…i}, not {@code -2} (the real root is {@link
   * RealNumeric#root}). A {@code Real} exponent with an integer value is an integer power made
   * inexact, so {@code (-8)^2.0} is {@code Real 64}. For a zero base, {@code 0^y} is 0 when the
   * real part of {@code y} is positive and 1 when {@code y} is zero.
   *
   * @param y the exponent
   * @param mc the context an inexact result is rounded to
   * @return the principal power
   * @throws ArithmeticException for a zero base with an exponent of non-positive real part (other
   *     than zero), or an {@code Int} exponent outside the {@code int} range on another base
   */
  default Numeric pow(Numeric y, MathContext mc) {
    return Powers.pow(this, y, mc);
  }

  /**
   * Returns the principal power as {@link #pow(Numeric, MathContext)} does, checking {@code
   * cancellation} while an inexact power is evaluated.
   *
   * @param y the exponent
   * @param mc the context an inexact result is rounded to
   * @param cancellation the request to stop
   * @return the principal power
   * @throws ArithmeticException as {@link #pow(Numeric, MathContext)} does
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  default Numeric pow(Numeric y, MathContext mc, Cancellation cancellation) {
    return Powers.pow(this, y, mc, cancellation);
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
   * Returns the principal argument in {@code (-π, π]}: {@code 0} for a non-negative real ({@code
   * Int 0} when exact, {@code Real 0} otherwise, and for zero itself), {@code π} at {@code mc} for
   * a negative real, {@code atan2(im, re)} at {@code mc} for a complex number.
   *
   * @param mc the context an inexact result is rounded to
   * @return the argument
   */
  default RealNumeric argument(MathContext mc) {
    return Elementary.argument(this, mc, Cancellation.NEVER);
  }

  /**
   * Returns the principal argument as {@link #argument(MathContext)} does, checking {@code
   * cancellation} while it is evaluated.
   *
   * @param mc the context an inexact result is rounded to
   * @param cancellation the request to stop
   * @return the argument
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  default RealNumeric argument(MathContext mc, Cancellation cancellation) {
    return Elementary.argument(this, mc, cancellation);
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
