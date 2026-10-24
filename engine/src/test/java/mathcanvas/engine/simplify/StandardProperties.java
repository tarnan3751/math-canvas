package mathcanvas.engine.simplify;

import java.util.Random;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.testing.RandomAssumptions;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Reporter;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;

/**
 * The standard list combined, as {@code simplify} runs it: sound and defined at points within
 * random assumptions, and terminating far below the cap.
 */
class StandardProperties {

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
    return ExprArbitraries.elementaryExprs();
  }

  @Provide
  Arbitrary<Assumptions> assumptions() {
    return RandomAssumptions.about(ExprArbitraries.SYMBOLS);
  }

  @Property(tries = 10_000)
  void sound(@ForAll("exprs") Expr e, @ForAll("assumptions") Assumptions a, @ForAll Random random) {
    SoundnessChecks.check(Simplifier.standard(), e, a, random);
  }

  @Property(tries = 500)
  void terminates(@ForAll("assumptions") Assumptions a, @ForAll Random random) {
    SoundnessChecks.terminates(Simplifier.standard(), exprs(), a, random, 20);
  }
}
