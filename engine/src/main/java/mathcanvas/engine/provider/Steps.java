package mathcanvas.engine.provider;

import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.match.Bindings;

/**
 * Where a provider records the steps of its algorithm for the trace: each a named step with its
 * before and after, expressions of the operation's own input. A provider that cannot narrate
 * records nothing.
 */
@FunctionalInterface
public interface Steps {

  /** The sink of an operation run without tracing: nothing is recorded. */
  Steps NONE = (rule, before, after, bindings) -> {};

  /**
   * Records one step.
   *
   * @param rule the step's name, in kebab-case; the narration bundle's key is {@code <operation
   *     rule set>.<rule>}
   * @param before the expression before the step
   * @param after the expression after it
   * @param bindings the named values its narration prints
   */
  void record(String rule, Expr before, Expr after, Bindings bindings);
}
