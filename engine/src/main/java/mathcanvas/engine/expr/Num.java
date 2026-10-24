package mathcanvas.engine.expr;

import java.util.Objects;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;

/**
 * A number of the tower. Two numbers are equal when their values are equal in the tower's sense, so
 * {@code Num(Int 1)} and {@code Num(Real 1.0)} differ.
 *
 * @param value the number
 */
public record Num(Numeric value) implements Expr {

  /** Zero. */
  public static final Num ZERO = new Num(Int.ZERO);

  /** One. */
  public static final Num ONE = new Num(Int.ONE);

  /** Minus one. */
  public static final Num MINUS_ONE = new Num(Int.MINUS_ONE);

  /**
   * Checks the value.
   *
   * @throws NullPointerException if {@code value} is null
   */
  public Num {
    Objects.requireNonNull(value, "value");
  }

  /**
   * Returns the integer {@code value}.
   *
   * @param value the value
   * @return the number
   */
  public static Num of(long value) {
    return new Num(Int.of(value));
  }

  /**
   * Returns the number {@code value}.
   *
   * @param value the value
   * @return the number
   */
  public static Num of(Numeric value) {
    return new Num(value);
  }

  @Override
  public String toString() {
    return "Num " + value;
  }
}
