package mathcanvas.engine.simplify;

import static mathcanvas.engine.simplify.RuleCases.canonical;
import static mathcanvas.engine.simplify.RuleCases.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.MathContext;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.testing.NumericEvaluator;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

/** The soundness checks skip a point whose input is noise magnified around a true zero. */
class SoundnessChecksTest {

  /**
   * {@code log(log e, …)} is exactly 0, computed as noise near 10^-34; its fourth root near 10^-9.
   */
  private static final String ROOT_OF_NOISE = "root(log(log(e), (2^b)^(1/3)), 4)";

  @Test
  void theSimplificationIsZero() {
    assertThat(Simplifier.simplify(parse(ROOT_OF_NOISE), Context.DEFAULT).expr())
        .isEqualTo(canonical("0"));
  }

  @Test
  void theRecheckEvaluationRefusesARootOfAZeroWithinRounding() {
    // At b = -2 the noise survives 100 digits: the root is near 10^-25, where the answer is 0.
    Expr e = canonical(ROOT_OF_NOISE);
    Map<Symbol, Numeric> point = Map.of(Symbol.of("b"), Int.of(-2));
    assertThat(NumericEvaluator.eval(e, point, new MathContext(100))).isPresent();
    assertThat(NumericEvaluator.evalAwayFromCuts(e, point, new MathContext(100))).isEmpty();
  }

  @Property(tries = 50)
  void aCorrectSimplificationOfARootOfNoiseIsNotAFailure(@ForAll Random random) {
    SoundnessChecks.check(Simplifier.standard(), parse(ROOT_OF_NOISE), Assumptions.EMPTY, random);
  }
}
