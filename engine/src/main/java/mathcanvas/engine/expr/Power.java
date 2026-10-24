package mathcanvas.engine.expr;

import java.util.Objects;

/**
 * A power {@code base^exponent}; {@code 1/x} is {@code Power(x, -1)} and {@code sqrt(x)} may be
 * {@code Power(x, 1/2)}.
 *
 * @param base the base
 * @param exponent the exponent
 */
public record Power(Expr base, Expr exponent) implements Expr {

  /**
   * Checks the operands.
   *
   * @throws NullPointerException if an operand is null
   */
  public Power {
    Objects.requireNonNull(base, "base");
    Objects.requireNonNull(exponent, "exponent");
  }

  /**
   * Returns the raw power {@code base^exponent}.
   *
   * @param base the base
   * @param exponent the exponent
   * @return the power
   */
  public static Power of(Expr base, Expr exponent) {
    return new Power(base, exponent);
  }

  @Override
  public String toString() {
    return Debug.of("Power", base, exponent);
  }
}
