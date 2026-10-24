package mathcanvas.engine.expr;

import java.util.Objects;

/**
 * A named constant. The imaginary unit is not one: it is {@code Num(Complex.I)}. Negative infinity
 * is {@code Product(-1, INFINITY)}.
 *
 * @param kind which constant
 */
public record Constant(Kind kind) implements Expr {

  /** The constants, in the order {@link ExprOrder} sorts them. */
  public enum Kind {
    /** π. */
    PI,
    /** Euler's number e. */
    E,
    /** Positive infinity ∞. */
    INFINITY,
    /** The value of an operation with no defined result: {@code 1/0}, {@code 0·∞}. */
    UNDEFINED,
    /** The domain ℤ. */
    INTEGERS,
    /** The domain ℚ. */
    RATIONALS,
    /** The domain ℝ. */
    REALS,
    /** The domain ℂ. */
    COMPLEXES
  }

  /** π. */
  public static final Constant PI = new Constant(Kind.PI);

  /** Euler's number. */
  public static final Constant E = new Constant(Kind.E);

  /** Positive infinity. */
  public static final Constant INFINITY = new Constant(Kind.INFINITY);

  /** The undefined value. */
  public static final Constant UNDEFINED = new Constant(Kind.UNDEFINED);

  /** The integers ℤ. */
  public static final Constant INTEGERS = new Constant(Kind.INTEGERS);

  /** The rationals ℚ. */
  public static final Constant RATIONALS = new Constant(Kind.RATIONALS);

  /** The reals ℝ. */
  public static final Constant REALS = new Constant(Kind.REALS);

  /** The complex numbers ℂ. */
  public static final Constant COMPLEXES = new Constant(Kind.COMPLEXES);

  /**
   * Checks the kind.
   *
   * @throws NullPointerException if {@code kind} is null
   */
  public Constant {
    Objects.requireNonNull(kind, "kind");
  }

  /**
   * Returns the constant of a kind.
   *
   * @param kind the kind
   * @return the constant
   */
  public static Constant of(Kind kind) {
    return new Constant(kind);
  }

  @Override
  public String toString() {
    return "Constant " + kind;
  }
}
