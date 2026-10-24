package mathcanvas.engine.testing;

import static org.assertj.core.api.Assertions.fail;

import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.RewriteLimitException;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.RuleSet;

/** Runs a rule set over expressions and checks every rewrite reaches its fixpoint under the cap. */
public final class TerminationProbe {

  private TerminationProbe() {}

  /**
   * What a probe found.
   *
   * @param expressions how many expressions were rewritten
   * @param maxRewrites the most rewrites one of them took
   * @param worst the expression that took them, or null when there was none
   */
  public record Report(int expressions, int maxRewrites, Expr worst) {}

  /**
   * Rewrites each expression, failing on the first that exceeds the context's cap.
   *
   * @param rules the rule set
   * @param exprs the expressions
   * @param ctx the context; the trace is turned on to count the rewrites
   * @return the report
   */
  public static Report run(RuleSet rules, Iterable<Expr> exprs, Context ctx) {
    int count = 0;
    int max = 0;
    Expr worst = null;
    for (Expr e : exprs) {
      int rewrites;
      try {
        rewrites = Rewriter.rewrite(e, rules, ctx.withTrace(true)).trace().rewrites().size();
      } catch (RewriteLimitException loop) {
        return fail("%s does not terminate on %s: %s", rules.name(), e, loop.getMessage());
      }
      count++;
      if (worst == null || rewrites > max) {
        max = rewrites;
        worst = e;
      }
    }
    return new Report(count, max, worst);
  }
}
