package mathcanvas.engine.testing;

import java.util.List;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * A fixture rule set encoding PLAN's two assumption pairs end to end, until the radical and exp-log
 * rule sets carry them: {@code sqrt(x^2)} is {@code x} for a non-negative {@code x} and {@code |x|}
 * for a real one; {@code log(a b)} splits only when both factors are positive. The rules are
 * written in the language, as rule sets are.
 */
final class FixtureRules {

  private FixtureRules() {}

  private static Expr template(String source) {
    return Parser.parseExpression(source, Names.NONE);
  }

  static final RuleSet RULES =
      new RuleSet(
          "fixture",
          List.of(
              Rule.of(
                  "sqrt-square-nonnegative",
                  Pattern.parse("sqrt(x_^2)")
                      .where(List.of("x_"), (b, a) -> a.isNonNegative(b.get("x_")) == Tri.TRUE),
                  template("x_")),
              Rule.of(
                  "sqrt-square-real",
                  Pattern.parse("sqrt(x_^2)")
                      .where(List.of("x_"), (b, a) -> a.isReal(b.get("x_")) == Tri.TRUE),
                  template("|x_|")),
              Rule.of(
                  "log-product",
                  Pattern.parse("log(a_ * rest__)")
                      .where(
                          List.of("a_", "rest__"),
                          (b, a) ->
                              a.isPositive(b.get("a_")) == Tri.TRUE
                                  && a.isPositive(b.get("rest__")) == Tri.TRUE),
                  template("log(a_) + log(rest__)"))));
}
