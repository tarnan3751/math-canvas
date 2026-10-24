package mathcanvas.engine.rule;

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
 */
public record Rewrite(
    Expr before, Expr after, String ruleSet, String rule, Path path, Bindings bindings) {}
