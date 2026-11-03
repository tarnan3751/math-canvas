package mathcanvas.engine.units;

import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.RealNumeric;

/**
 * A dimension: the exact exponents of the seven SI base dimensions, in the order mass, length,
 * time, electric current, temperature, amount of substance and luminous intensity ({@code kg m s A
 * K mol cd}). Exponents are rational, so {@code √(2 m)} has the dimension length^(1/2).
 *
 * @param exponents the seven exact exponents
 */
public record Dimension(List<RealNumeric> exponents) {

  /** The base dimensions' unit names, in order. */
  public static final List<String> BASE = List.of("kg", "m", "s", "A", "K", "mol", "cd");

  /** The dimension of a number. */
  public static final Dimension NONE = of(0, 0, 0, 0, 0, 0, 0);

  /** Checks seven exact exponents and copies them. */
  public Dimension {
    exponents = List.copyOf(exponents);
    if (exponents.size() != BASE.size() || !exponents.stream().allMatch(RealNumeric::isExact)) {
      throw new IllegalArgumentException("a dimension has seven exact exponents");
    }
  }

  /**
   * The dimension with these integer exponents.
   *
   * @param exponents mass, length, time, current, temperature, amount, luminous intensity
   * @return the dimension
   */
  public static Dimension of(int... exponents) {
    List<RealNumeric> out = new ArrayList<>();
    for (int e : exponents) {
      out.add(Int.of(e));
    }
    return new Dimension(out);
  }

  /**
   * The product's dimension: the exponents added.
   *
   * @param other the other dimension
   * @return the sum of exponents
   */
  public Dimension times(Dimension other) {
    List<RealNumeric> out = new ArrayList<>();
    for (int k = 0; k < BASE.size(); k++) {
      out.add((RealNumeric) exponents.get(k).add(other.exponents.get(k), MathContext.UNLIMITED));
    }
    return new Dimension(out);
  }

  /**
   * The power's dimension: every exponent times {@code q}.
   *
   * @param q an exact exponent
   * @return the scaled dimension
   */
  public Dimension pow(RealNumeric q) {
    Objects.requireNonNull(q);
    List<RealNumeric> out = new ArrayList<>();
    for (RealNumeric e : exponents) {
      out.add((RealNumeric) e.multiply(q, MathContext.UNLIMITED));
    }
    return new Dimension(out);
  }

  /**
   * Whether this is the dimension of a number.
   *
   * @return whether every exponent is zero
   */
  public boolean isNone() {
    return exponents.stream().allMatch(RealNumeric::isZero);
  }
}
