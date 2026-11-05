package mathcanvas.engine.calculus;

import static mathcanvas.engine.testing.Inputs.canonical;
import static mathcanvas.engine.testing.Inputs.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Rule;

/**
 * The per-rule cases of {@code diff}, written in the language: each positive case names the rule
 * that must fire on its input and the value the set reaches (before any simplification); each
 * negative case names a rule that must not fire. Every rule has at least one of each.
 */
final class DiffCases {

  private final List<String> positive = new ArrayList<>();
  private final List<String> negative = new ArrayList<>();

  /** The assumptions of relations written in the language. */
  static Assumptions facts(String... relations) {
    Assumptions a = Assumptions.EMPTY;
    for (String r : relations) {
      a = a.with((Relation) parse(r));
    }
    return a;
  }

  /** {@code input} rewritten with {@code diff} under {@code facts}, traced. */
  static Rewriter.Result rewrite(String input, String... facts) {
    return Rewriter.rewrite(
        parse(input), RuleSets.DIFF, Context.DEFAULT.withAssumptions(facts(facts)).withTrace(true));
  }

  /** {@code rule} fires on {@code input} under {@code facts}, and the set reaches {@code value}. */
  DiffCases fires(String rule, String input, String value, String... facts) {
    Rewriter.Result r = rewrite(input, facts);
    assertThat(fired(r)).as("%s on %s", rule, input).contains(rule);
    assertThat(r.expr()).as("%s on %s", rule, input).isEqualTo(canonical(value));
    positive.add(rule);
    return this;
  }

  /** {@code rule} fires on the tree {@code input} and the set reaches {@code value}. */
  DiffCases fires(String rule, Expr input, Expr value) {
    Rewriter.Result r = Rewriter.rewrite(input, RuleSets.DIFF, Context.DEFAULT.withTrace(true));
    assertThat(fired(r)).as("%s on %s", rule, input).contains(rule);
    assertThat(r.expr()).as("%s on %s", rule, input).isEqualTo(Canonical.of(value));
    positive.add(rule);
    return this;
  }

  /** {@code rule} does not fire on {@code input} under {@code facts}. */
  DiffCases skips(String rule, String input, String... facts) {
    assertThat(fired(rewrite(input, facts))).as("%s on %s", rule, input).doesNotContain(rule);
    negative.add(rule);
    return this;
  }

  /** Checks that every rule of the set has a positive and a negative case. */
  void covered() {
    TreeSet<String> names =
        RuleSets.DIFF.rules().stream()
            .map(Rule::name)
            .collect(Collectors.toCollection(TreeSet::new));
    assertThat(new TreeSet<>(positive)).as("rules with a positive case").isEqualTo(names);
    assertThat(new TreeSet<>(negative)).as("rules with a negative case").isEqualTo(names);
  }

  static List<String> fired(Rewriter.Result r) {
    return r.trace().rewrites().stream().map(Rewrite::rule).toList();
  }
}
