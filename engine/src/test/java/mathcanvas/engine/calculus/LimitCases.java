package mathcanvas.engine.calculus;

import static mathcanvas.engine.testing.Inputs.canonical;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.eval.Narration;
import mathcanvas.engine.expr.Limit.Direction;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;

/** Limits run through {@link Limits} with numerics and the trace on, for the tests. */
final class LimitCases {

  static final Symbol X = Symbol.of("x");
  static final Context CTX = TestNumerics.CONTEXT.withTrace(true);

  private LimitCases() {}

  static Rewriter.Result run(String f, String point, Direction d, Context ctx) {
    return Limits.limit(canonical(f), X, canonical(point), d, ctx);
  }

  static Rewriter.Result run(String f, String point, Direction d) {
    return run(f, point, d, CTX);
  }

  static Rewriter.Result run(String f, String point) {
    return run(f, point, Direction.BOTH);
  }

  static String shown(Rewriter.Result r) {
    return UnicodePrinter.INSTANCE.display(r.expr());
  }

  static String shown(String f, String point, Direction d) {
    return shown(run(f, point, d));
  }

  static String shown(String f, String point) {
    return shown(f, point, Direction.BOTH);
  }

  /** The narrated steps of a limit's trace. */
  static List<String> steps(Rewriter.Result r) {
    List<Step> steps = Narration.steps(r.trace(), CTX, StepText.standard());
    return steps.stream().map(Step::text).toList();
  }

  /** The narrated steps of the limit's own rewrites, after the simplifier's of the body. */
  static List<String> limitSteps(Rewriter.Result r) {
    List<Step> steps = Narration.steps(r.trace(), CTX, StepText.standard());
    return steps.stream()
        .filter(s -> s.rewrites().getFirst().ruleSet().equals(Limits.RULE_SET))
        .map(Step::text)
        .toList();
  }

  /**
   * The rule that decided a limit: the one rewrite of the rule set {@code limit} in its trace,
   * after the simplifier's rewrites of the body, its trace a chain ({@link
   * TraceChecks#assertChain}), a note that the limit stays its terminal record.
   */
  static String rule(Rewriter.Result r) {
    List<Rewrite> own =
        r.trace().rewrites().stream().filter(w -> w.ruleSet().equals(Limits.RULE_SET)).toList();
    assertThat(own).hasSize(1);
    Rewrite last = r.trace().rewrites().getLast();
    assertThat(last).isSameAs(own.getFirst());
    // a note (the limit stays) leaves its node, a terminal record the chain allows
    TraceChecks.assertChain(r);
    return last.rule();
  }
}
