package mathcanvas.engine.simplify;

import mathcanvas.engine.rule.RuleSet;
import mathcanvas.engine.units.UnitRules;

/**
 * The simplifier's rule sets, each a list of named rules through the matcher with conditions on the
 * assumptions; {@link Simplifier#standard()} runs them combined. {@code docs/reference/engine.md},
 * section <b>Simplification</b>, lists every rule.
 */
public final class RuleSets {

  /** {@code basic}: absolute value, sign, powers of powers and products, {@code undefined}. */
  public static final RuleSet BASIC = BasicRules.RULES;

  /**
   * {@code rational}: common polynomial factors of a quotient cancel, sums of fractions combine
   * over the lcm when that adds no nodes.
   */
  public static final RuleSet RATIONAL = RationalRules.RULES;

  /** {@code exp-log}: natural logarithms, {@code exp} as a power of {@code e}, log expansion. */
  public static final RuleSet EXPLOG = ExpLogRules.RULES;

  /**
   * {@code radical}: numeric radicals in textbook form, denesting, {@code root} and {@code cbrt}.
   */
  public static final RuleSet RADICAL = RadicalRules.RULES;

  /**
   * {@code trig}: exact values, shifts and parity, the Pythagorean identities and double angles,
   * products of circular functions, compositions with the inverse functions.
   */
  public static final RuleSet TRIG = TrigRules.RULES;

  /**
   * {@code units}: quantities of one dimension add in one unit, units of one dimension in a product
   * merge, units whose dimensions cancel are their factor (§14.26).
   */
  public static final RuleSet UNITS = UnitRules.RULES;

  /**
   * {@code logic}: negations moved inward, the constants, repeated and complementary operands, and
   * absorption.
   */
  public static final RuleSet LOGIC = LogicRules.RULES;

  /**
   * {@code special}: the gamma function at the integers and half-integers, the beta function
   * through it, the symmetries and limits of the error functions.
   */
  public static final RuleSet SPECIAL = SpecialRules.RULES;

  private RuleSets() {}
}
