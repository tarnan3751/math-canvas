package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.AlgebraNarrator;
import mathcanvas.engine.algebra.ExpandRules;
import mathcanvas.engine.calculus.DiffNarrator;
import mathcanvas.engine.calculus.IntegrateNarrator;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;
import mathcanvas.engine.rule.StepText;
import mathcanvas.engine.simplify.Simplifier;
import mathcanvas.engine.simplify.SimplifyNarrator;
import mathcanvas.engine.solve.SolveNarrator;
import org.junit.jupiter.api.Test;

/** The narration bundle has a key for every rule, event and grouped step, and no other key. */
class NarrationBundleTest {

  /**
   * Every rule set the engine has: the standard list, then differentiation, integration, expand.
   */
  private static final List<RuleSet> RULE_SETS = ruleSets();

  private static List<RuleSet> ruleSets() {
    List<RuleSet> sets = new ArrayList<>(Simplifier.standard());
    sets.add(mathcanvas.engine.calculus.RuleSets.DIFF);
    sets.add(mathcanvas.engine.calculus.RuleSets.INTEGRATE);
    sets.add(ExpandRules.RULES);
    return sets;
  }

  /** The events {@code factor} and its default provider record. */
  private static final List<String> FACTOR_EVENTS =
      List.of(
          "common-denominator",
          "cancel",
          "pull-content",
          "pull-power",
          "square-free-split",
          "split-content",
          "rational-root",
          "kronecker-split",
          "homogeneous-split",
          "bivariate-split");

  /** The events the solver records, and the reasons it declines. */
  private static final List<String> SOLVE_EVENTS =
      List.of(
          "clear-denominators",
          "exclude",
          "collect",
          "isolate",
          "divide",
          "discriminant",
          "formula",
          "cubic",
          "quartic",
          "factor",
          "roots",
          "not-an-equation",
          "not-polynomial",
          "all-values",
          "depends-on-parameters",
          "symbolic-degree",
          "not-linear-system",
          "augment",
          "swap-rows",
          "eliminate",
          "back-substitute",
          "free-variables",
          "numeric",
          "no-numerics",
          "angle-mode",
          "no-root-found",
          "too-many-roots",
          "aliased",
          "numeric-degree",
          "numeric-failed",
          "unsolved");

  private static Set<String> referenced() {
    Set<String> keys = new TreeSet<>();
    for (RuleSet set : RULE_SETS) {
      for (Rule r : set.rules()) {
        keys.add(set.name() + "." + r.name());
      }
    }
    FACTOR_EVENTS.forEach(e -> keys.add("factor." + e));
    SOLVE_EVENTS.forEach(e -> keys.add("solve." + e));
    NumericIntegral.STEPS.forEach(e -> keys.add(NumericIntegral.RULE_SET + "." + e));
    keys.add("integrate.none");
    keys.add(Algebra.PROVIDER + ".default");
    keys.addAll(SimplifyNarrator.KEYS);
    keys.addAll(AlgebraNarrator.KEYS);
    keys.addAll(DiffNarrator.KEYS);
    keys.addAll(IntegrateNarrator.KEYS);
    keys.addAll(SolveNarrator.KEYS);
    keys.addAll(StepText.KEYS);
    for (Set<String> continues : SimplifyNarrator.IDEAS.values()) {
      keys.addAll(continues);
    }
    keys.addAll(SimplifyNarrator.IDEAS.keySet());
    return keys;
  }

  @Test
  void everyReferencedKeyIsInTheBundle() {
    StepText texts = StepText.standard();
    assertThat(referenced()).allSatisfy(k -> assertThat(texts.has(k)).as(k).isTrue());
  }

  @Test
  void everyGroupedKeyFormatsWithItsArgumentsAndBeforeAndAfter() {
    StepText texts = StepText.standard();
    Set<String> grouped = new TreeSet<>(StepText.KEYS);
    grouped.addAll(SimplifyNarrator.KEYS);
    grouped.addAll(AlgebraNarrator.KEYS);
    grouped.addAll(DiffNarrator.KEYS);
    grouped.addAll(IntegrateNarrator.KEYS);
    grouped.addAll(SolveNarrator.KEYS);
    List<Expr> args = List.of(Symbol.of("a"), Symbol.of("b"), Symbol.of("c"), Symbol.of("d"));
    for (String key : grouped) {
      String text = texts.format(key, args, Symbol.of("p"), Symbol.of("q"), "l");
      assertThat(text).as(key).doesNotContain("{").doesNotContain("null");
    }
  }

  @Test
  void theBundleHasNoUnusedKey() {
    Set<String> keys = new TreeSet<>(StepText.standard().bundle().keySet());
    keys.removeAll(referenced());
    assertThat(keys).isEmpty();
  }
}
