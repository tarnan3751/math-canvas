package mathcanvas.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import mathcanvas.engine.algebra.ExpandRules;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.simplify.Simplifier;
import mathcanvas.engine.testing.RandomAssumptions;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Grouping the rules by the root they can match changes nothing: the rewrite and its trace are
 * those of trying every rule at every node, for the standard list and the expand rules.
 */
class RewriterIndexProperties {

  /** Every rule set the engine has: the standard list, then expand. */
  static RuleSet allRules() {
    List<Rule> rules = new ArrayList<>();
    for (RuleSet set : Simplifier.standard()) {
      rules.addAll(set.rules());
    }
    rules.addAll(ExpandRules.RULES.rules());
    return new RuleSet("all", rules);
  }

  private static final RuleSet ALL = allRules();

  @Provide
  Arbitrary<Expr> exprs() {
    return Arbitraries.oneOf(ExprArbitraries.elementaryExprs(), ExprArbitraries.exprs());
  }

  @Provide
  Arbitrary<Assumptions> assumptions() {
    return RandomAssumptions.about(ExprArbitraries.SYMBOLS);
  }

  @Property(tries = 2000)
  void theIndexChangesNeitherTheResultNorTheTrace(
      @ForAll("exprs") Expr e, @ForAll("assumptions") Assumptions a) {
    Context ctx = Context.DEFAULT.withAssumptions(a).withTrace(true).withRewriteCap(2_000);
    Object indexed = outcome(() -> Rewriter.rewrite(e, ALL, ctx));
    Object everyRule = outcome(() -> Rewriter.rewriteTryingEveryRule(e, ALL, ctx));
    assertThat(indexed).as("%s", e).isEqualTo(everyRule);
  }

  /** The result, or the failure's class and message. */
  private static Object outcome(Supplier<Rewriter.Result> run) {
    try {
      return run.get();
    } catch (RuntimeException failure) {
      return failure.getClass().getName() + ": " + failure.getMessage();
    }
  }
}
