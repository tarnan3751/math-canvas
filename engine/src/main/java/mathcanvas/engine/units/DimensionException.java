package mathcanvas.engine.units;

import java.io.Serial;

/**
 * A quantity whose dimensions do not fit (§14.26): a sum or a comparison of two dimensions, an
 * exponent with units or a quantity's exponent that is no rational number, a function of a
 * quantity. The evaluator reports it as an evaluation error at the node that failed.
 */
public final class DimensionException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /** What failed. */
  public enum Kind {
    /** Two terms of a sum of different dimensions: {@link #first()} and {@link #second()}. */
    ADD,
    /** The two sides of a relation, or two arguments of {@code min} or {@code max}. */
    COMPARE,
    /** An exponent with units. */
    EXPONENT_UNITS,
    /** A quantity to a power that is no rational number. */
    EXPONENT,
    /** A function of a quantity: {@link #first()} is the argument, {@link #function()} its name. */
    FUNCTION
  }

  private final Kind kind;
  private final transient mathcanvas.engine.expr.Expr first;
  private final transient mathcanvas.engine.expr.Expr second;
  private final String function;

  /**
   * Creates the exception.
   *
   * @param kind what failed
   * @param first the first term, side or argument, or null
   * @param second the second, or null
   * @param function the function's name, or null
   */
  public DimensionException(
      Kind kind,
      mathcanvas.engine.expr.Expr first,
      mathcanvas.engine.expr.Expr second,
      String function) {
    super(kind.name());
    this.kind = kind;
    this.first = first;
    this.second = second;
    this.function = function;
  }

  /**
   * What failed.
   *
   * @return the kind
   */
  public Kind kind() {
    return kind;
  }

  /**
   * The first term, side or argument.
   *
   * @return it, or null
   */
  public mathcanvas.engine.expr.Expr first() {
    return first;
  }

  /**
   * The second term or side.
   *
   * @return it, or null
   */
  public mathcanvas.engine.expr.Expr second() {
    return second;
  }

  /**
   * The function of a quantity.
   *
   * @return its name, or null
   */
  public String function() {
    return function;
  }
}
