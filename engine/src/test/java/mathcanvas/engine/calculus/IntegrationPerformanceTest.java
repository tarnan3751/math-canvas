package mathcanvas.engine.calculus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.testing.Inputs;
import org.junit.jupiter.api.Test;

/**
 * Integration of large integrands, and of integrands with no elementary antiderivative, whose
 * search tries every technique, stays within a generous budget for each test's whole run.
 */
class IntegrationPerformanceTest {

  private static final Symbol X = Symbol.of("x");

  /** The budget of each test's whole run, on a loaded machine. */
  private static final long BUDGET_MS = 30_000;

  private static final Context CONTEXT = Context.DEFAULT.withTrace(true);

  private static long millis(Expr f) {
    long start = System.nanoTime();
    Rewriter.Result r = Calculus.integrate(f, X, null, null, CONTEXT);
    long millis = (System.nanoTime() - start) / 1_000_000;
    assertThat(r.trace().rewrites()).hasSizeLessThan(CONTEXT.rewriteCap());
    return millis;
  }

  @Test
  void aSumOfTwoHundredTerms() {
    List<Expr> terms = new ArrayList<>();
    for (int k = 1; k <= 200; k++) {
      terms.add(Inputs.parse(k + " x^" + k + " + sin(" + k + " x) + e^(" + k + "x)"));
    }
    // typically well under a second locally
    assertThat(millis(Sum.of(terms))).isLessThan(BUDGET_MS);
  }

  @Test
  void highPowersByParts() {
    long total = 0;
    for (String f : List.of("x^20 e^x", "x^8 e^x sin(x)", "x^30 sin(x)", "(x^2 + 1)^12 e^x")) {
      Expr e = Inputs.parse(f);
      total += millis(e);
      assertThat(Calculus.integrate(e, X, null, null, Context.DEFAULT).expr())
          .as(f)
          .matches(r -> IntegrateRules.complete(r, X));
    }
    // the four together: typically about 3.5 s locally
    assertThat(total).isLessThan(BUDGET_MS);
  }

  @Test
  void integrandsWithNoElementaryAntiderivative() {
    long total = 0;
    for (String f :
        List.of(
            "e^(x^2) sin(x) log(x)",
            "sin(x^2) cos(x^3) e^(x + sin(x)) tan(log(x))",
            "x^x log(x) atan(x) sqrt(x + sin(x))",
            "1/(x^7 + x + 1)")) {
      Expr e = Inputs.parse(f);
      total += millis(e);
      assertThat(Calculus.integrate(e, X, null, null, Context.DEFAULT).expr())
          .isInstanceOf(Integral.class);
    }
    // the four together: typically well under a second locally
    assertThat(total).isLessThan(BUDGET_MS);
  }
}
