package mathcanvas.engine.expr;

import java.util.Objects;

/**
 * An interval of the real line; an infinite end is {@code ∞} or {@code Product(-1, ∞)}. The ends
 * are not checked against each other.
 *
 * @param lower the lower end
 * @param upper the upper end
 * @param lowerClosed whether the lower end belongs to the interval
 * @param upperClosed whether the upper end belongs to the interval
 */
public record Interval(Expr lower, Expr upper, boolean lowerClosed, boolean upperClosed)
    implements Expr {

  /**
   * Checks the ends.
   *
   * @throws NullPointerException if an end is null
   */
  public Interval {
    Objects.requireNonNull(lower, "lower");
    Objects.requireNonNull(upper, "upper");
  }

  /**
   * Returns the interval between {@code lower} and {@code upper}.
   *
   * @param lower the lower end
   * @param upper the upper end
   * @param lowerClosed whether the lower end belongs to the interval
   * @param upperClosed whether the upper end belongs to the interval
   * @return the interval
   */
  public static Interval of(Expr lower, Expr upper, boolean lowerClosed, boolean upperClosed) {
    return new Interval(lower, upper, lowerClosed, upperClosed);
  }

  @Override
  public String toString() {
    return Debug.of(
        "Interval",
        lower + ", " + upper,
        (lowerClosed ? "closed" : "open") + ", " + (upperClosed ? "closed" : "open"));
  }
}
