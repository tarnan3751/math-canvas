package mathcanvas.engine.number;

import java.math.BigDecimal;
import java.math.MathContext;
import mathcanvas.engine.Cancellation;

/**
 * A number on the real line: {@link Int}, {@link Rational} or {@link Real}. These are ordered;
 * {@link Complex} is not.
 */
public sealed interface RealNumeric extends Numeric, Comparable<RealNumeric>
    permits Int, Rational, Real {

  @Override
  RealNumeric negate();

  /**
   * Returns the absolute value, exactly.
   *
   * @return {@code |this|}
   */
  default RealNumeric abs() {
    return signum() < 0 ? negate() : this;
  }

  /**
   * Returns the sign of the value: -1, 0 or 1.
   *
   * @return the sign
   */
  int signum();

  /**
   * The total order used for canonical ordering: by value, and at equal value an exact number
   * before a {@code Real} ({@code 1 < 1.0}). It is consistent with {@code equals}. Use {@link
   * #compareValue} for the mathematical comparison.
   */
  @Override
  default int compareTo(RealNumeric other) {
    int order = compareValue(other);
    return order != 0 ? order : Boolean.compare(!isExact(), !other.isExact());
  }

  /**
   * The mathematical comparison used by relations: by value alone, exactly, so {@code 1} and {@code
   * 1.0} compare equal. {@link #compareTo} is the total order used for canonical ordering.
   *
   * @param other the number to compare with
   * @return a negative number, zero or a positive number as {@code this} is less than, equal to or
   *     greater than {@code other} in value
   */
  default int compareValue(RealNumeric other) {
    return Arithmetic.compareValue(this, other);
  }

  /**
   * Returns the greatest integer not above the value; a {@code Real} stays a {@code Real}.
   *
   * @return the floor
   */
  RealNumeric floor();

  /**
   * Returns the least integer not below the value; a {@code Real} stays a {@code Real}.
   *
   * @return the ceiling
   */
  RealNumeric ceil();

  /**
   * Returns the nearest integer, halves rounded away from zero ({@code round(-2.5)} is {@code -3});
   * a {@code Real} stays a {@code Real}.
   *
   * @return the rounded value
   */
  RealNumeric round();

  /**
   * Returns the integer part, rounded toward zero; a {@code Real} stays a {@code Real}.
   *
   * @return the truncated value
   */
  RealNumeric truncate();

  /**
   * Whether the value is the square of a rational; always false for a {@code Real}.
   *
   * @return true for an exact rational square
   */
  boolean isPerfectSquare();

  /**
   * Returns the value as the nearest {@code double} (within one ulp for a {@code Rational}); for
   * the compiled plotting evaluators only, never for exact work.
   *
   * @return the value as a double, possibly infinite if out of range
   */
  double toDouble();

  /**
   * Returns the real {@code n}-th root: exact when the value is an exact perfect {@code n}-th power
   * ({@code root(8/27, 3)} is {@code 2/3}), otherwise a {@code Real} at {@code mc}. A negative
   * value has the negative real root for odd {@code n} ({@code root(-8, 3)} is {@code -2}); callers
   * wanting the principal complex value use {@code pow}.
   *
   * @param n the root's degree, at least 1
   * @param mc the context an inexact result is rounded to
   * @return the real root
   * @throws IllegalArgumentException if {@code n < 1}
   * @throws ArithmeticException if the value is negative and {@code n} is even
   */
  default RealNumeric root(int n, MathContext mc) {
    return Powers.root(this, n, mc);
  }

  /**
   * Returns the real {@code n}-th root as {@link #root(int, MathContext)} does, checking {@code
   * cancellation} while an inexact root is evaluated.
   *
   * @param n the root's degree, at least 1
   * @param mc the context an inexact result is rounded to
   * @param cancellation the request to stop
   * @return the real root
   * @throws IllegalArgumentException if {@code n < 1}
   * @throws ArithmeticException if the value is negative and {@code n} is even
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  default RealNumeric root(int n, MathContext mc, Cancellation cancellation) {
    return Powers.root(this, n, mc, cancellation);
  }

  @Override
  default RealNumeric re() {
    return this;
  }

  @Override
  default RealNumeric im() {
    return Int.ZERO;
  }

  @Override
  default RealNumeric conj() {
    return this;
  }

  @Override
  default RealNumeric modulus(MathContext mc) {
    return abs();
  }

  @Override
  Real toReal(MathContext mc);

  /**
   * Returns the value as a {@code BigDecimal}: an integer or a decimal exactly, a rational rounded
   * to {@code mc}. {@link #toReal} rounds every kind.
   *
   * @param mc the precision for a rational
   * @return the decimal
   */
  default BigDecimal decimal(MathContext mc) {
    return switch (this) {
      case Int i -> new BigDecimal(i.value());
      case Rational q -> q.toReal(mc).value();
      case Real r -> r.value();
    };
  }
}
