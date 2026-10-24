/**
 * Rules and the rewriter: named rules, <i>pattern → replacement, if condition</i>, grouped into
 * rule sets and applied innermost first to a fixpoint under a cap, with a {@link
 * mathcanvas.engine.rule.Trace} of every rewrite when asked for and the {@link
 * mathcanvas.engine.rule.Narrator} infrastructure that turns a trace into steps. {@code
 * docs/reference/engine.md}, section <b>Patterns and rules</b>, is the reference.
 */
package mathcanvas.engine.rule;
