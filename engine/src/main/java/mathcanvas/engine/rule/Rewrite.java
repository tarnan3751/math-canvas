package mathcanvas.engine.rule;

import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.match.Bindings;

/**
 * One rewrite: which rule turned which subexpression into what.
 *
 * @param before the subexpression at {@code path} before
 * @param after the canonical subexpression that replaced it
 * @param ruleSet the rule set's name
 * @param rule the rule's name
 * @param path where the subexpression is, in the tree as it stood ({@link Trace#roots})
 * @param bindings the match's bindings, for narration
 * @param values the values a computed replacement named for narration ({@link Replacement.Valued}),
 *     each canonical, kept apart from the bindings so that their numbering is unchanged; empty for
 *     every other rule
 */
public record Rewrite(
    Expr before,
    Expr after,
    String ruleSet,
    String rule,
    Path path,
    Bindings bindings,
    SortedMap<String, Expr> values) {

  /** Copies the values. */
  public Rewrite {
    values = Collections.unmodifiableSortedMap(new TreeMap<>(values));
  }

  /**
   * A rewrite with no named values.
   *
   * @param before the subexpression at {@code path} before
   * @param after the canonical subexpression that replaced it
   * @param ruleSet the rule set's name
   * @param rule the rule's name
   * @param path where the subexpression is
   * @param bindings the match's bindings
   */
  public Rewrite(
      Expr before, Expr after, String ruleSet, String rule, Path path, Bindings bindings) {
    this(before, after, ruleSet, rule, path, bindings, new TreeMap<>());
  }
}
