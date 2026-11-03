package mathcanvas.engine.expr;

import java.util.Objects;
import mathcanvas.engine.number.Int;

/**
 * An integer shown in another base ({@code docs/language.md} §14.27): what {@code hex}, {@code
 * bin}, {@code oct} and {@code base} return. It is a display of its integer, never a number of its
 * own: the canonical form replaces it by its integer under every parent but a list, a vector or a
 * matrix, so {@code hex(255) + 1} is {@code 256}, and it survives only as a value, or an item of
 * one, to be shown: {@code 0xff}, {@code zz₍₃₆₎}. It is not {@code equals} to its integer; a
 * relation compares it by value.
 *
 * @param value the integer
 * @param base the base, from 2 to 36, not 10
 */
public record Based(Int value, int base) implements Expr {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if {@code value} is null
   * @throws IllegalArgumentException if the base is outside 2 to 36 or is 10
   */
  public Based {
    Objects.requireNonNull(value, "value");
    if (base < 2 || base > 36 || base == 10) {
      throw new IllegalArgumentException("a base from 2 to 36 other than 10: " + base);
    }
  }

  /**
   * The integer as a number node.
   *
   * @return the number
   */
  public Num number() {
    return Num.of(value);
  }

  /**
   * An expression with a based integer at its root replaced by the integer.
   *
   * @param e the expression
   * @return the integer, or {@code e}
   */
  public static Expr unwrap(Expr e) {
    return e instanceof Based b ? b.number() : e;
  }

  @Override
  public String toString() {
    return "Based " + value + " base " + base;
  }
}
