package mathcanvas.engine.simplify;

import static mathcanvas.engine.simplify.RuleCases.facts;
import static mathcanvas.engine.simplify.RuleCases.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

/** The standard list on a large expression stays within a generous budget. */
class SimplifierPerformanceTest {

  @Test
  void aThousandTermsOfRadicalsLogsAndAbsoluteValues() {
    List<Expr> terms = new ArrayList<>();
    for (int k = 1; k <= 1000; k++) {
      terms.add(parse("sqrt(" + k + "^3) + abs(-" + k + " y) + log(" + (2 * k) + " x) + exp(2)"));
    }
    Expr e = Sum.of(terms);
    Context ctx = Context.DEFAULT.withAssumptions(facts("x > 0"));
    long start = System.nanoTime();
    Rewriter.Result r = Simplifier.simplify(e, ctx.withTrace(true));
    long millis = (System.nanoTime() - start) / 1_000_000;
    assertThat(r.trace().rewrites()).hasSizeLessThan(ctx.rewriteCap());
    assertThat(millis).isLessThan(10_000);
  }
}
