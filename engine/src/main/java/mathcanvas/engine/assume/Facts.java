package mathcanvas.engine.assume;

import java.util.Objects;
import mathcanvas.engine.number.RealNumeric;

/**
 * What is known about one symbol: the combination of every relation recorded about it.
 *
 * @param domain the domain; {@link Domain#REALS} when nothing narrows or widens it
 * @param lower the lower bound, or null
 * @param upper the upper bound, or null
 * @param parity the parity, {@link Parity#NONE} when not known
 * @param nonZero whether the symbol is known not to be zero
 */
public record Facts(Domain domain, Bound lower, Bound upper, Parity parity, boolean nonZero) {

  /** The facts of a symbol nothing is recorded about: real, and nothing more. */
  public static final Facts NONE = new Facts(Domain.REALS, null, null, Parity.NONE, false);

  /** The domains, narrowest first. */
  public enum Domain {
    /** ℤ. */
    INTEGERS,
    /** ℚ. */
    RATIONALS,
    /** ℝ, the default. */
    REALS,
    /** ℂ, only when assumed. */
    COMPLEXES
  }

  /** A known parity. */
  public enum Parity {
    /** Not known. */
    NONE,
    /** Even. */
    EVEN,
    /** Odd. */
    ODD
  }

  /**
   * A bound on a real symbol.
   *
   * @param value the bounding value
   * @param strict whether the value itself is excluded
   */
  public record Bound(RealNumeric value, boolean strict) {

    /**
     * Checks the value.
     *
     * @throws NullPointerException if {@code value} is null
     */
    public Bound {
      Objects.requireNonNull(value, "value");
    }
  }

  /**
   * Checks the domain and the parity.
   *
   * @throws NullPointerException if {@code domain} or {@code parity} is null
   */
  public Facts {
    Objects.requireNonNull(domain, "domain");
    Objects.requireNonNull(parity, "parity");
  }
}
