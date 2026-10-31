package mathcanvas.engine.provider;

import java.util.SortedMap;
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

  /**
   * Records one step with values named for its narration, apart from the bindings, as a computed
   * rule names them ({@code mathcanvas.engine.rule.Replacement.Valued}): each name a lowercase
   * word, printed by a text's {@code {name}}. A sink that keeps no values records the step without
   * them, which is the default.
   *
   * @param rule the step's name, in kebab-case
   * @param before the expression before the step
   * @param after the expression after it
   * @param bindings the named values its narration prints by position
   * @param values the named values its narration prints by name
   */
  default void record(
      String rule, Expr before, Expr after, Bindings bindings, SortedMap<String, Expr> values) {
    record(rule, before, after, bindings);
  }
}
