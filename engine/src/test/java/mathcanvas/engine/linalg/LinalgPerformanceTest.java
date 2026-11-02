package mathcanvas.engine.linalg;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.rule.Context;
import org.junit.jupiter.api.Test;

/** The exact operations at the sizes a worksheet reaches, against a whole-run budget. */
class LinalgPerformanceTest {

  /** Both cases together; each runs well under a second when the machine is idle. */
  private static final long LIMIT_MS = 30_000;

  private static long elapsed;

  private static long time(Runnable r) {
    long t = System.nanoTime();
    r.run();
    long ms = (System.nanoTime() - t) / 1_000_000;
    elapsed += ms;
    return ms;
  }

  @Test
  void aSymbolicTenByTenDeterminant() {
    // x on the diagonal, small integers elsewhere: the characteristic polynomial's shape
    Random random = new Random(5201);
    List<List<Expr>> rows = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      List<Expr> row = new ArrayList<>();
      for (int j = 0; j < 10; j++) {
        row.add(i == j ? Symbol.of("x") : Num.of(random.nextInt(7) - 3));
      }
      rows.add(row);
    }
    Apply call = new Apply(Symbol.of("det"), List.of(Matrix.of(rows)));
    time(() -> assertThat(Linalg.apply(call, Context.DEFAULT).expr()).isNotInstanceOf(Apply.class));
    assertThat(elapsed).isLessThan(LIMIT_MS);
  }

  @Test
  void aRationalTwentyByTwentyInverse() {
    Random random = new Random(5202);
    Matrix a = LinalgCases.random(random, 20, 20);
    time(
        () -> {
          Matrix inverse = (Matrix) Linalg.inverse(a, Context.DEFAULT);
          LinalgCases.isIdentity(LinalgCases.multiply(a.rows(), inverse.rows()));
        });
    assertThat(elapsed).isLessThan(LIMIT_MS);
  }

  @Test
  void theEigenvectorsOfASixBySix() {
    // P D P⁻¹ with eigenvalues −2 … 3, so the polynomial splits over ℚ
    Random random = new Random(5203);
    List<List<Expr>> d = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      List<Expr> row = new ArrayList<>();
      for (int j = 0; j < 6; j++) {
        row.add(i == j ? Num.of(i - 2) : Num.ZERO);
      }
      d.add(row);
    }
    Matrix p;
    do {
      p = LinalgCases.random(random, 6, 6);
    } while (LinalgCases.value("det", p).equals(Num.ZERO));
    Matrix inverse = (Matrix) Linalg.inverse(p, Context.DEFAULT);
    Matrix a = Matrix.of(LinalgCases.multiply(LinalgCases.multiply(p.rows(), d), inverse.rows()));
    Apply call = new Apply(Symbol.of("eigenvectors"), List.of(a));
    time(() -> assertThat(Linalg.apply(call, Context.DEFAULT).expr()).isNotInstanceOf(Apply.class));
    assertThat(elapsed).isLessThan(LIMIT_MS);
  }
}
