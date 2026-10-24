package mathcanvas.engine.simplify;

import mathcanvas.engine.rule.RuleSet;

/**
 * The simplifier's rule sets, each a list of named rules through the matcher with conditions on the
 * assumptions; {@link Simplifier#standard()} runs them combined. {@code docs/reference/engine.md},
 * section <b>Simplification</b>, lists every rule.
 */
public final class RuleSets {

  /** {@code basic}: absolute value, sign, powers of powers and products, {@code undefined}. */
  public static final RuleSet BASIC = BasicRules.RULES;

  /** {@code exp-log}: natural logarithms, {@code exp} as a power of {@code e}, log expansion. */
  public static final RuleSet EXPLOG = ExpLogRules.RULES;

  private RuleSets() {}
}
