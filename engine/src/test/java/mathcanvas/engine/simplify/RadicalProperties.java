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

/**
 * The radical rule set alone: sound and defined at points within random assumptions, and
 * terminating.
 */
class RadicalProperties {

  private static final List<RuleSet> SETS = List.of(RuleSets.RADICAL);

  @Provide
  Arbitrary<Expr> exprs() {
    return ExprArbitraries.elementaryExprs();
  }

  @Provide
  Arbitrary<Assumptions> assumptions() {
    return RandomAssumptions.about(ExprArbitraries.SYMBOLS);
  }

  @Property(tries = 5000)
  void sound(@ForAll("exprs") Expr e, @ForAll("assumptions") Assumptions a, @ForAll Random random) {
    SoundnessChecks.check(SETS, e, a, random);
  }

  @Property(tries = 200)
  void terminates(@ForAll("assumptions") Assumptions a, @ForAll Random random) {
    SoundnessChecks.terminates(SETS, exprs(), a, random, 20);
  }
}
