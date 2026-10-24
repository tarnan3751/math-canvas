package mathcanvas.engine.number;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Objects;

/**
 * A complex number {@code re + im·i} whose imaginary part is not an exact zero: a complex number
 * with imaginary part {@code Int 0} is its real part, which {@link #of(RealNumeric, RealNumeric)}
 * returns. A {@code Real} zero imaginary part stays, since it is inexact. The parts are both exact
 * or both {@code Real}, so exactness is a property of the whole number: a mixed pair is rejected,
 * and {@link #of(RealNumeric, RealNumeric, MathContext)} promotes the exact part instead ({@code 1
 * + 2.0i} is {@code 1.0 + 2.0i}).
 *
 * @param re the real part
 * @param im the imaginary part, never {@code Int 0}, and a {@code Real} exactly when {@code re} is
 */
public record Complex(RealNumeric re, RealNumeric im) implements Numeric {

  /** The imaginary unit. */
  public static final Complex I = new Complex(Int.ZERO, Int.ONE);

  /**
   * Checks the parts.
   *
   * @throws IllegalArgumentException if {@code im} is {@code Int 0} (the value is real; use {@link
   *     #of(RealNumeric, RealNumeric)}), or if one part is exact and the other a {@code Real} (use
   *     {@link #of(RealNumeric, RealNumeric, MathContext)})
   */
  public Complex {
    Objects.requireNonNull(re, "re");
    Objects.requireNonNull(im, "im");
    if (im instanceof Int i && i.isZero()) {
      throw new IllegalArgumentException(
          "a complex number with imaginary part 0 is real; use Complex.of: " + re);
    }
    if (re.isExact() != im.isExact()) {
      throw new IllegalArgumentException(
          "a complex number's parts are both exact or both Real: " + re + ", " + im);
    }
  }

  /**
   * Returns {@code re + im·i}: {@code re} itself when {@code im} is {@code Int 0}.
   *
   * @param re the real part
   * @param im the imaginary part
   * @return the number
   * @throws IllegalArgumentException if one part is exact and the other a {@code Real}, unless the
   *     imaginary part is {@code Int 0}
   */
  public static Numeric of(RealNumeric re, RealNumeric im) {
    return im instanceof Int i && i.isZero() ? re : new Complex(re, im);
  }

  /**
   * Returns {@code re + im·i} as {@link #of(RealNumeric, RealNumeric)} does, first promoting an
   * exact part to {@code Real} when the other part is a {@code Real}: an {@code Int} exactly, a
   * {@code Rational} rounded to {@code mc}. An {@code Int 0} imaginary part still gives {@code re}.
   *
   * @param re the real part
   * @param im the imaginary part
   * @param mc the context a {@code Rational} part is rounded to
   * @return the number
   * @throws ArithmeticException if {@code mc} is unlimited and a promoted {@code Rational} part
   *     does not terminate
   */
  public static Numeric of(RealNumeric re, RealNumeric im, MathContext mc) {
    if (im instanceof Int i && i.isZero()) {
      return re;
    }
    if (re.isExact() == im.isExact()) {
      return new Complex(re, im);
    }
    return re.isExact() ? new Complex(promote(re, mc), im) : new Complex(re, promote(im, mc));
  }

  private static Real promote(RealNumeric x, MathContext mc) {
    return switch (x) {
      case Int i -> new Real(new BigDecimal(i.value()));
      case Rational r -> r.toReal(mc);
      case Real r -> r;
    };
  }

  @Override
  public Complex negate() {
    return new Complex(re.negate(), im.negate());
  }

  @Override
  public Complex conj() {
    return new Complex(re, im.negate());
  }

  @Override
  public Complex toReal(MathContext mc) {
    return new Complex(re.toReal(mc), im.toReal(mc));
  }

  @Override
  public boolean isZero() {
    return re.isZero() && im.isZero();
  }

  @Override
  public boolean isOne() {
    return re.isOne() && im.isZero();
  }

  @Override
  public boolean isInteger() {
    return re.isInteger() && im.isZero();
  }

  @Override
  public boolean isExact() {
    return re.isExact() && im.isExact();
  }

  @Override
  public String toString() {
    String imaginary =
        im.isOne() && im.isExact() ? "i" : im.negate().isOne() && im.isExact() ? "-i" : im + "i";
    if (re instanceof Int r && r.isZero()) {
      return imaginary;
    }
    return re + (imaginary.startsWith("-") ? "" : "+") + imaginary;
  }
}
