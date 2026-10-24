package mathcanvas.engine.simplify;

import java.util.List;
import java.util.Random;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.rule.RuleSet;
import mathcanvas.engine.testing.RandomAssumptions;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Reporter;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;

/**
 * The trig rule set alone and the standard list with it, over trigonometric expressions: sound and
 * defined at points within random assumptions (ranges among them), and terminating.
 */
class TrigProperties {

  private static final List<RuleSet> TRIG = List.of(RuleSets.TRIG);

  @BeforeProperty
  void begin() {
    SoundnessChecks.begin();
  }

  @AfterProperty
  void end(Reporter reporter) {
    SoundnessChecks.end(reporter);
  }

  @Provide
  Arbitrary<Expr> exprs() {
    return ExprArbitraries.trigExprs();
  }

  @Provide
  Arbitrary<Assumptions> assumptions() {
    return RandomAssumptions.withRanges(ExprArbitraries.SYMBOLS);
  }

  @Property(tries = 5000)
  void sound(@ForAll("exprs") Expr e, @ForAll("assumptions") Assumptions a, @ForAll Random random) {
    SoundnessChecks.check(TRIG, e, a, random);
  }

  @Property(tries = 5000)
  void soundInTheStandardList(
      @ForAll("exprs") Expr e, @ForAll("assumptions") Assumptions a, @ForAll Random random) {
    SoundnessChecks.check(Simplifier.standard(), e, a, random);
  }

  @Property(tries = 200)
  void terminates(@ForAll("assumptions") Assumptions a, @ForAll Random random) {
    SoundnessChecks.terminates(TRIG, exprs(), a, random, 20);
  }

  @Property(tries = 500)
  void terminatesInTheStandardList(@ForAll("assumptions") Assumptions a, @ForAll Random random) {
    SoundnessChecks.terminates(Simplifier.standard(), exprs(), a, random, 20);
  }
}
