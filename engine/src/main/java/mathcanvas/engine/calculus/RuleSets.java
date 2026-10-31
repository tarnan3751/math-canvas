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

  /**
   * {@code taylor}: a call {@code taylor(f, x, a, n)} with a non-negative integer order to its
   * Taylor polynomial, from the derivatives at the point.
   */
  public static final RuleSet TAYLOR = TaylorRules.RULES;

  /**
   * {@code limit}: a {@code Limit} node to its value by one of the methods, each a rule; what no
   * method decides stays a {@code Limit}.
   */
  public static final RuleSet LIMIT = LimitRules.at(0);

  /**
   * {@code sum}: a {@code Σ} node to its value, term by term for integer bounds with at most
   * 100,000 terms, else by the closed form of its family; what no rule evaluates stays.
   */
  public static final RuleSet SUM = SumRules.SUM;

  /** {@code product}: a {@code Π} node to its value, as {@link #SUM} does for sums. */
  public static final RuleSet PRODUCT = SumRules.PRODUCT;

  private RuleSets() {}
}
