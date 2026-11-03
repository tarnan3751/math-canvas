package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.expr.Matrix;
import org.junit.jupiter.api.Test;

/** A numeric ODE at a size a worksheet may reach, against the whole-run budget. */
class NumericOdePerformanceTest {

  // The whole run: typically well under a second locally (docs/reference/engine.md, Timing
  // budgets).
  private static final long LIMIT_MS = 30_000;

  @Test
  void aStiffishEquationOnAFineGrid() {
    long t = System.nanoTime();
    Matrix m =
        (Matrix)
            Run.value(
                "odesolve(y'(t) = -50(y(t) - cos(t)), y(t), y(0) = 1, 0, 100, steps = 10000)");
    long ms = (System.nanoTime() - t) / 1_000_000;
    assertThat(m.rows()).hasSize(10001);
    assertThat(ms).isLessThan(LIMIT_MS);
  }
}
