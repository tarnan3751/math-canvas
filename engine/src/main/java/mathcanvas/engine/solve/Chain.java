package mathcanvas.engine.solve;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.provider.Steps;

/**
 * The states a solve passes through: each step replaces the whole current state (an equation, a
 * matrix, a list of solved equations, the set of solutions) and is recorded only when it changes
 * it, so the steps form a chain from the input to the result.
 */
final class Chain {

  private final Steps sink;
  private final Expr initial;
  private Expr current;

  Chain(Steps sink, Expr initial) {
    this.sink = sink;
    this.initial = initial;
    this.current = initial;
  }

  /** The first state. */
  Expr initial() {
    return initial;
  }

  /** The current state. */
  Expr current() {
    return current;
  }

  /**
   * Records the step {@code rule} to {@code after}, unless it changes nothing.
   *
   * @param rule the step's name
   * @param after the new state
   * @param bindings the values its narration prints
   */
  void step(String rule, Expr after, Bindings bindings) {
    if (!after.equals(current)) {
      sink.record(rule, current, after, bindings);
      current = after;
    }
  }

  /**
   * Bindings from name and value pairs, in any order; a null value is left out.
   *
   * @param pairs names (ending in {@code _}) and values, alternately
   * @return the bindings, sorted by name
   */
  static Bindings bind(Object... pairs) {
    Bindings b = Bindings.EMPTY;
    List<Bindings.Binding> out = new ArrayList<>();
    for (int i = 0; i < pairs.length; i += 2) {
      if (pairs[i + 1] != null) {
        out.add(new Bindings.Binding((String) pairs[i], (Expr) pairs[i + 1], null, null));
      }
    }
    for (Bindings.Binding one : out) {
      b = b.with(one);
    }
    return b;
  }
}
