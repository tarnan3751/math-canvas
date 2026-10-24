package mathcanvas.engine.number;

import java.math.MathContext;
import java.util.Objects;

/**
 * A complex number {@code re + im·i} whose imaginary part is not an exact zero: a complex number
 * with imaginary part {@code Int 0} is its real part, which {@link #of(RealNumeric, RealNumeric)}
 * returns. A {@code Real} zero imaginary part stays, since it is inexact. The parts may be of
 * different kinds ({@code 1 + 2.0i}); the number is exact only when both are.
 *
 * @param re the real part
 * @param im the imaginary part, never {@code Int 0}
 */
public record Complex(RealNumeric re, RealNumeric im) implements Numeric {

  /** The imaginary unit. */
  public static final Complex I = new Complex(Int.ZERO, Int.ONE);

  /**
   * Checks the parts.
   *
   * @throws IllegalArgumentException if {@code im} is {@code Int 0} (the value is real; use {@link
   *     #of(RealNumeric, RealNumeric)})
   */
  public Complex {
    Objects.requireNonNull(re, "re");
    Objects.requireNonNull(im, "im");
    if (im instanceof Int i && i.isZero()) {
      throw new IllegalArgumentException(
          "a complex number with imaginary part 0 is real; use Complex.of: " + re);
    }
  }

  /**
   * Returns {@code re + im·i}: {@code re} itself when {@code im} is {@code Int 0}.
   *
   * @param re the real part
   * @param im the imaginary part
   * @return the number
   */
  public static Numeric of(RealNumeric re, RealNumeric im) {
    return im instanceof Int i && i.isZero() ? re : new Complex(re, im);
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
