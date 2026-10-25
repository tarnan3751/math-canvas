package mathcanvas.engine.solve;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

/** The solver's costs at its bounds, against generous limits. */
class SolvePerformanceTest {

  // Each whole solve: typically 0.1 to 1.3 s locally (docs/reference/engine.md, Timing budgets).
  private static final long LIMIT_MS = 30_000;

  private static long time(Runnable r) {
    long t = System.nanoTime();
    r.run();
    return (System.nanoTime() - t) / 1_000_000;
  }

  @Test
  void aPolynomialOfTheHighestNumericDegree() {
    // x^100 − 3x + 1: irreducible over the rationals' reach, solved numerically
    long ms =
        time(
            () -> {
              Rewriter.Result r =
                  SolveCases.run("solve(x^100 - 3x + 1 = 0, x)", NumericSolveTest.digits(34));
              assertThat(((SetExpr) r.expr()).items()).hasSize(100);
            });
    assertThat(ms).isLessThan(LIMIT_MS);
  }

  @Test
  void aSixBySixSymbolicSystem() {
    String[] names = {"x", "y", "z", "u", "v", "w"};
    List<String> eqs = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      StringBuilder lhs = new StringBuilder();
      for (int j = 0; j < 6; j++) {
        String c = i == j ? "a" : Integer.toString((i * 7 + j * 3) % 5 + 1);
        lhs.append(j == 0 ? "" : " + ").append(c).append(" ").append(names[j]);
      }
      eqs.add(lhs + " = " + (i + 1));
    }
    String call = "solve({" + String.join(", ", eqs) + "}, [x, y, z, u, v, w])";
    long ms =
        time(
            () -> {
              Expr r = SolveCases.run(call).expr();
              assertThat(((SetExpr) r).items()).hasSize(1);
            });
    assertThat(ms).isLessThan(LIMIT_MS);
  }

  @Test
  void theScan() {
    long ms =
        time(
            () ->
                assertThat(
                        ((SetExpr)
                                SolveCases.run(
                                        "solve(e^x = x^2 + 2, x)", NumericSolveTest.digits(34))
                                    .expr())
                            .items())
                    .hasSize(1));
    assertThat(ms).isLessThan(LIMIT_MS);
  }
}
