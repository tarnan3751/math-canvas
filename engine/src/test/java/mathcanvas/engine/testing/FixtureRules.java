package mathcanvas.engine.testing;

import java.util.List;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * A fixture rule set encoding PLAN's two assumption pairs end to end, until the radical and exp-log
 * rule sets carry them: {@code sqrt(x^2)} is {@code x} for a non-negative {@code x} and {@code |x|}
 * for a real one; {@code log(a b)} splits only when both factors are positive.
 */
final class FixtureRules {

  private FixtureRules() {}

  private static final Symbol X = Symbol.of("x_");
  private static final Symbol A = Symbol.of("a_");
  private static final Symbol REST = Symbol.of("rest__");
  private static final Symbol LOG = Symbol.of("log");

  private static final Expr SQRT_OF_SQUARE =
      Power.of(Power.of(X, Num.of(2)), Num.of(Rational.of(1, 2)));

  static final RuleSet RULES =
      new RuleSet(
          "fixture",
          List.of(
              Rule.of(
                  "sqrt-square-nonnegative",
                  Pattern.of(SQRT_OF_SQUARE)
                      .where(List.of("x_"), (b, a) -> a.isNonNegative(b.get("x_")) == Tri.TRUE),
                  X),
              Rule.of(
                  "sqrt-square-real",
                  Pattern.of(SQRT_OF_SQUARE)
                      .where(List.of("x_"), (b, a) -> a.isReal(b.get("x_")) == Tri.TRUE),
                  Apply.of(Symbol.of("abs"), X)),
              Rule.of(
                  "log-product",
                  Pattern.of(Apply.of(LOG, Product.of(A, REST)))
                      .where(
                          List.of("a_", "rest__"),
                          (b, a) ->
                              a.isPositive(b.get("a_")) == Tri.TRUE
                                  && a.isPositive(b.get("rest__")) == Tri.TRUE),
                  Sum.of(Apply.of(LOG, A), Apply.of(LOG, REST)))));
}
