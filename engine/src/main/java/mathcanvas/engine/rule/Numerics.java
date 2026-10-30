package mathcanvas.engine.rule;

import java.math.MathContext;
import java.util.Optional;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.number.Numeric;

/**
 * The numeric value of a closed expression, for the operations that need one (the numeric fallback
 * of solving). The evaluator supplies the implementation, which evaluates every function of the
 * language; {@link #NONE} has none, and an operation that needs values declines under it.
 */
public interface Numerics {

  /** No numeric values: every expression is empty. */
  Numerics NONE = (e, mc) -> Optional.empty();

  /**
   * Returns the value of an expression free of symbols.
   *
   * @param e the expression, canonical
   * @param mc the precision
   * @return its value, or empty where it is undefined or cannot be evaluated
   */
  Optional<Numeric> value(Expr e, MathContext mc);

  /**
   * Returns the session's half turn in its angle unit: 180 in degrees, 200 in gradians, and 0 in
   * radians, which convert nothing ({@code docs/language.md} §14.2). The trigonometric rules read
   * it for an argument a plain number may be put into.
   *
   * @return 180, 200, or 0 for radians
   */
  default int halfTurn() {
    return 0;
  }

  /**
   * Returns whether trigonometric functions take radians here; in degrees or gradians a value found
   * by evaluating an angle numerically would be in the wrong unit.
   *
   * @return true unless the session's angle mode is degrees or gradians
   */
  default boolean radians() {
    return halfTurn() == 0;
  }
}
