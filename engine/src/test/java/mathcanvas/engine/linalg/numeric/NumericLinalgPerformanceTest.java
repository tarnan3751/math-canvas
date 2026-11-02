package mathcanvas.engine.linalg.numeric;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import mathcanvas.engine.provider.Decompose.Kind;
import mathcanvas.engine.provider.Decomposition;
import org.junit.jupiter.api.Test;

/** The numeric decompositions at a size a worksheet may reach, against the whole-run budget. */
class NumericLinalgPerformanceTest {

  // The whole run: typically well under a second locally (docs/reference/engine.md, Timing
  // budgets).
  private static final long LIMIT_MS = 30_000;

  @Test
  void theSvdOfATwoHundredSquareMatrix() {
    double[][] a = Mat.random(new Random(200), 200, 200);
    long t = System.nanoTime();
    Decomposition d = Mat.run(Kind.SVD, a);
    long ms = (System.nanoTime() - t) / 1_000_000;
    assertThat(d.status()).isEqualTo(Decomposition.Status.OK);
    assertThat(ms).isLessThan(LIMIT_MS);
  }
}
