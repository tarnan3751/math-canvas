package mathcanvas.engine.compile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

/** A million evaluations of a 30-node expression stay within the whole-run budget. */
class CompiledPerformanceTest {

  @Test
  void aMillionEvaluationsOfThirtyNodes(TestReporter reporter) {
    Expr e =
        CompiledTest.value(
            "sin(x) x^2 + exp(-x/3) cos(2x) + sqrt(x^2 + 1) + log(x^2 + 2)", Settings.DEFAULT);
    assertThat(nodes(e)).isEqualTo(30);
    Compiled c = Compiled.compile(e, List.of(Symbol.of("x")), Settings.DEFAULT);
    double[] point = new double[1];
    int n = 1_000_000;
    double sum = 0;
    for (int i = 0; i < n / 10; i++) {
      point[0] = -10 + 20.0 * i / n;
      sum += c.eval(point);
    }
    long start = System.nanoTime();
    for (int i = 0; i < n; i++) {
      point[0] = -10 + 20.0 * i / n;
      sum += c.eval(point);
    }
    long nanos = System.nanoTime() - start;
    reporter.publishEntry(
        "per evaluation", String.format("%.1f ns (sum %g)", (double) nanos / n, sum));
    // typically about 30 ns per evaluation locally, 0.03 s in all (docs/reference/engine.md,
    // Timing budgets)
    assertThat(nanos / 1_000_000).isLessThan(30_000);
  }

  private static int nodes(Expr e) {
    int count = 1;
    for (Expr op : Operands.of(e)) {
      count += nodes(op);
    }
    return count;
  }
}
