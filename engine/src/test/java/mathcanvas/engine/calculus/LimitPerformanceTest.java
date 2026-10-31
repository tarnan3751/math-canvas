package mathcanvas.engine.calculus;

import static mathcanvas.engine.calculus.LimitCases.run;
import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.expr.Limit.Direction;
import org.junit.jupiter.api.Test;

/**
 * Limits whose methods loop or branch without the levels: L'Hôpital's rule that cycles, a deep
 * composition, a long sum, and limits no method decides. The run has a whole budget of 30 seconds
 * (docs/reference/engine.md, Timing budgets), never a bound per call; typically the whole class
 * takes about two seconds locally.
 */
class LimitPerformanceTest {

  private static final long BUDGET_MS = 30_000;

  @Test
  void theHardCasesEndWithinTheBudget() {
    long start = System.nanoTime();
    // L'Hôpital's rule turns √(x² + 1)/x into x/√(x² + 1) and back
    run("sqrt(x^2 + 1)/x", "inf");
    run("sqrt(x^2 + 1)/x", "0", Direction.RIGHT);
    run("sin(sin(sin(sin(sin(x)))))/x", "0");
    run("e^(e^(e^(1/x)))/x", "0", Direction.LEFT);
    StringBuilder s = new StringBuilder("0");
    for (int k = 1; k <= 60; k++) {
      s.append(" + sin(").append(k).append("x)/x");
    }
    run(s.toString(), "0");
    run("x^(sin(1/x))", "0", Direction.RIGHT);
    run("gcd(x, 2)/(x - 1)", "1");
    long ms = (System.nanoTime() - start) / 1_000_000;
    assertThat(ms).as("the hard limits took %d ms", ms).isLessThan(BUDGET_MS);
  }
}
