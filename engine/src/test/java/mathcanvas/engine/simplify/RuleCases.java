package mathcanvas.engine.simplify;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Numerics;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * The per-rule cases of a rule set, written in the language: each positive case names the rule that
 * must fire on its input and the value the set reaches; each negative case names a rule that must
 * not fire. Every rule of the set has at least one of each.
 */
final class RuleCases {

  private final RuleSet set;
  private final List<String> positive = new ArrayList<>();
  private final List<String> negative = new ArrayList<>();
  private int halfTurn;

  RuleCases(RuleSet set) {
    this.set = set;
  }

  /**
   * The cases that follow run with this half turn in the context's numerics: 180 for degrees, 200
   * for gradians, 0 (the default) for radians.
   */
  RuleCases halfTurn(int units) {
    halfTurn = units;
    return this;
  }

  static Expr parse(String source) {
    return Parser.parseExpression(source, Names.NONE);
  }

  static Expr canonical(String source) {
    return Canonical.of(parse(source));
  }

  /** The assumptions of relations written in the language. */
  static Assumptions facts(String... relations) {
    Assumptions a = Assumptions.EMPTY;
    for (String r : relations) {
      a = a.with((Relation) parse(r));
    }
    return a;
  }

  /** {@code input} rewritten with the set under {@code facts}, traced. */
  Rewriter.Result rewrite(String input, String... facts) {
    int units = halfTurn;
    Numerics numerics =
        new Numerics() {
          @Override
          public Optional<Numeric> value(Expr e, MathContext mc) {
            return Optional.empty();
          }

          @Override
          public int halfTurn() {
            return units;
          }
        };
    return Rewriter.rewrite(
        parse(input),
        set,
        Context.DEFAULT.withAssumptions(facts(facts)).withNumerics(numerics).withTrace(true));
  }

  /** {@code rule} fires on {@code input} under {@code facts}, and the set reaches {@code value}. */
  RuleCases fires(String rule, String input, String value, String... facts) {
    Rewriter.Result r = rewrite(input, facts);
    assertThat(fired(r)).as("%s on %s", rule, input).contains(rule);
    assertThat(r.expr()).as("%s on %s", rule, input).isEqualTo(canonical(value));
    positive.add(rule);
    return this;
  }

  /** {@code rule} does not fire on {@code input} under {@code facts}. */
  RuleCases skips(String rule, String input, String... facts) {
    assertThat(fired(rewrite(input, facts))).as("%s on %s", rule, input).doesNotContain(rule);
    negative.add(rule);
    return this;
  }

  /** Checks that every rule of the set has a positive and a negative case. */
  void covered() {
    TreeSet<String> names =
        set.rules().stream().map(Rule::name).collect(Collectors.toCollection(TreeSet::new));
    assertThat(new TreeSet<>(positive)).as("rules with a positive case").isEqualTo(names);
    assertThat(new TreeSet<>(negative)).as("rules with a negative case").isEqualTo(names);
  }

  private static List<String> fired(Rewriter.Result r) {
    return r.trace().rewrites().stream().map(Rewrite::rule).toList();
  }
}
