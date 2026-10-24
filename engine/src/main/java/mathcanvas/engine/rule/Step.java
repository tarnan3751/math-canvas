package mathcanvas.engine.rule;

import java.util.List;
import mathcanvas.engine.expr.Expr;

/**
 * One human step: its explanation, what it changed, and the rewrites it groups.
 *
 * @param text the explanation
 * @param before the expression before the step
 * @param after the expression after it
 * @param rewrites the rewrites the step stands for
 */
public record Step(String text, Expr before, Expr after, List<Rewrite> rewrites) {

  /** Copies the rewrites. */
  public Step {
    rewrites = List.copyOf(rewrites);
  }
}
