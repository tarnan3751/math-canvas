package mathcanvas.engine.calculus;

import mathcanvas.engine.rule.RuleSet;

/**
 * The calculus rule sets, each a list of named rules through the matcher. {@code
 * docs/reference/engine.md}, section <b>Differentiation</b>, lists every rule.
 */
public final class RuleSets {

  /**
   * {@code diff}: derivatives of {@code Derivative} nodes, by linearity, the product, quotient,
   * power and chain rules and a table of the builtin functions; what it cannot differentiate stays
   * a {@code Derivative}.
   */
  public static final RuleSet DIFF = DiffRules.RULES;

  /**
   * {@code integrate}: antiderivatives of {@code Integral} nodes, by linearity and the techniques
   * {@code docs/reference/engine.md}, <b>Integration</b>, lists; what it cannot integrate stays an
   * {@code Integral}. This is the set at the full search depth ({@link IntegrateRules#at}).
   */
  public static final RuleSet INTEGRATE = IntegrateRules.at(IntegrateRules.DEPTH);

  private RuleSets() {}
}
