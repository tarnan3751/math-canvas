package mathcanvas.engine.eval;

import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;

/**
 * The unit a trigonometric function reads a plain number in, and an inverse one answers in ({@code
 * docs/language.md} §14.2).
 */
public enum AngleMode {
  /** Radians: no conversion. */
  RADIANS(0),
  /** Degrees: a plain number {@code x} is the angle {@code x·π/180}. */
  DEGREES(180),
  /** Gradians: a plain number {@code x} is the angle {@code x·π/200}. */
  GRADIANS(200);

  private final int halfTurn;

  AngleMode(int halfTurn) {
    this.halfTurn = halfTurn;
  }

  /**
   * The number of units in a half turn: 180 or 200, and 0 for radians, which convert nothing.
   *
   * @return the units in {@code π} radians, or 0
   */
  public int halfTurn() {
    return halfTurn;
  }

  /**
   * The factor that turns an angle in this unit into radians, raw: {@code π/180} for degrees.
   *
   * @return the factor; {@code 1} for radians
   */
  public Expr toRadians() {
    return this == RADIANS
        ? Num.ONE
        : Product.of(Constant.PI, Power.of(Num.of(halfTurn), Num.MINUS_ONE));
  }

  /**
   * The factor that turns radians into this unit, raw: {@code 180/π} for degrees.
   *
   * @return the factor; {@code 1} for radians
   */
  public Expr fromRadians() {
    return this == RADIANS
        ? Num.ONE
        : Product.of(Num.of(halfTurn), Power.of(Constant.PI, Num.MINUS_ONE));
  }
}
