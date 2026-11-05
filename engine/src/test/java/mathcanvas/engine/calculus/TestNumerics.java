package mathcanvas.engine.calculus;

import java.math.MathContext;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Evaluator;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Numerics;
import mathcanvas.engine.testing.Contexts;
import mathcanvas.engine.testing.NumericEvaluator;

/**
 * Numeric values for the calculus tests, through the test evaluator, in radians; the functions of
 * numbers evaluated by the engine's evaluator, as a session's numerics do.
 */
final class TestNumerics implements Numerics {

  /** The default context with these numerics and a generous cap. */
  static final Context CONTEXT =
      Contexts.withRewriteCap(Context.DEFAULT.withNumerics(new TestNumerics()), 100_000);

  @Override
  public Optional<Numeric> value(Expr e, MathContext mc) {
    return NumericEvaluator.eval(e, Map.of(), mc);
  }

  @Override
  public Expr exact(Expr e, MathContext mc) {
    return Evaluator.evaluate(e, Environment.EMPTY, false).value();
  }
}
