package mathcanvas.engine.eval;

import java.math.MathContext;
import java.util.Optional;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.rule.Numerics;

/**
 * The numeric values the evaluator supplies to the operations: the {@code numeric} pass ({@link
 * NumericForm}), every function of the language through the inexact path, in radians.
 *
 * @param halfTurn the session's half turn in its angle unit, 0 in radians ({@link
 *     AngleMode#halfTurn})
 * @param cancellation the statement's request to stop
 */
record SessionNumerics(int halfTurn, Cancellation cancellation) implements Numerics {

  @Override
  public Optional<Numeric> value(Expr e, MathContext mc) {
    try {
      return NumericForm.numeric(e, mc.getPrecision(), e, cancellation) instanceof Num n
          ? Optional.of(n.value())
          : Optional.empty();
    } catch (EvaluationException | ArithmeticException outOfRange) {
      return Optional.empty();
    }
  }
}
