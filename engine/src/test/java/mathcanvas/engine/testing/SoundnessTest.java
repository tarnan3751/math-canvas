package mathcanvas.engine.testing;

import static mathcanvas.engine.testing.Inputs.parse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;
import org.junit.jupiter.api.Test;

/** The points and the check catch a rule that is wrong only at a non-strict bound. */
class SoundnessTest {

  private static final Symbol Y = Symbol.of("y");

  @Test
  void aNonStrictBoundIsDrawnItself() {
    Assumptions a = Assumptions.EMPTY.with((Relation) parse("y >= 0"));
    List<Map<Symbol, Numeric>> points = Soundness.points(List.of(Y), a, 200, new Random(1));
    assertThat(points).anyMatch(p -> p.get(Y).isZero());
    assertThat(points).allMatch(p -> !p.get(Y).isZero() || p.get(Y).isExact());
  }

  @Test
  void aRuleWrongOnlyAtTheBoundIsCaught() {
    // 0^y → 0 for y ≥ 0 is wrong at y = 0, where 0^0 is 1: the condition should ask y > 0.
    RuleSet mutated =
        new RuleSet(
            "mutated",
            List.of(
                Rule.of(
                    "zero-to-nonnegative-power",
                    Pattern.parse("0^y_")
                        .where(List.of("y_"), (b, a) -> a.isNonNegative(b.get("y_")) == Tri.TRUE),
                    Num.ZERO)));
    Assumptions a = Assumptions.EMPTY.with((Relation) parse("y >= 0"));
    Expr before = parse("0^y");
    Expr after = Rewriter.rewrite(before, mutated, Context.DEFAULT.withAssumptions(a)).expr();
    assertThat(after).isEqualTo(Num.ZERO);
    assertThatThrownBy(
            () ->
                Soundness.check(before, after, Soundness.points(List.of(Y), a, 40, new Random(7))))
        .isInstanceOf(AssertionError.class);
  }
}
