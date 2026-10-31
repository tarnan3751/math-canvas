package mathcanvas.engine.calculus;

import java.util.List;
import mathcanvas.engine.rule.RuleSet;

/**
 * The rule sets {@code sum} and {@code product}: a {@code Σ} or {@code Π} node to its value, each
 * rule a family, or stays. Integer bounds with at most {@link Calculus#MAX_TERMS} terms are the
 * evaluator's, term by term through the session; the closed forms of the families come here.
 */
final class SumRules {

  private SumRules() {}

  static final RuleSet SUM = new RuleSet("sum", List.of());

  static final RuleSet PRODUCT = new RuleSet("product", List.of());
}
