package mathcanvas.engine.linalg.numeric;

import static mathcanvas.engine.linalg.numeric.Mat.distance;
import static mathcanvas.engine.linalg.numeric.Mat.identity;
import static mathcanvas.engine.linalg.numeric.Mat.run;
import static mathcanvas.engine.linalg.numeric.Mat.times;
import static mathcanvas.engine.linalg.numeric.Mat.transpose;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Outcome;
import mathcanvas.engine.eval.Session;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.provider.Decompose;
import mathcanvas.engine.provider.Decompose.Kind;
import mathcanvas.engine.provider.Decomposition;
import mathcanvas.engine.provider.Decomposition.Status;
import org.junit.jupiter.api.Test;

/**
 * The provider against exact answers: least squares against the normal equations solved over ℚ by
 * the engine's own solver, the exponential of diagonal and nilpotent matrices, and each input that
 * has no decomposition given its status, never a matrix.
 */
class HipparchusDecomposeTest {

  /** The exact solution of {@code AᵀA·x = Aᵀb}, through {@code solve}, as doubles. */
  private static double[] normalEquations(long[][] a, long[] b) {
    int m = a.length;
    int n = a[0].length;
    List<String> equations = new ArrayList<>();
    List<String> names = new ArrayList<>();
    for (int j = 0; j < n; j++) {
      names.add("x" + (char) ('a' + j));
    }
    for (int i = 0; i < n; i++) {
      StringBuilder lhs = new StringBuilder();
      long rhs = 0;
      for (int j = 0; j < n; j++) {
        long c = 0;
        for (int k = 0; k < m; k++) {
          c += a[k][i] * a[k][j];
        }
        lhs.append(j == 0 ? "" : " + ").append("(").append(c).append(")").append(names.get(j));
      }
      for (int k = 0; k < m; k++) {
        rhs += a[k][i] * b[k];
      }
      equations.add(lhs + " = " + rhs);
    }
    String script =
        "solve({" + String.join(", ", equations) + "}, [" + String.join(", ", names) + "])";
    Session s = new Session(Environment.EMPTY);
    Outcome.Value v = (Outcome.Value) s.run(Parser.parseScript(script, s.names())).getLast();
    Expr solution = ((SetExpr) v.value()).items().getFirst();
    double[] x = new double[n];
    if (n == 1) {
      // one unknown: the set holds its values
      x[0] = ((RealNumeric) ((Num) solution).value()).toDouble();
      return x;
    }
    for (Expr row : ((ListExpr) solution).items()) {
      Relation r = (Relation) row;
      x[names.indexOf(name(r.left()))] = ((RealNumeric) ((Num) r.right()).value()).toDouble();
    }
    return x;
  }

  private static String name(Expr symbol) {
    return ((Symbol) symbol).name();
  }

  private static double[][] doubles(long[][] a) {
    double[][] d = new double[a.length][a[0].length];
    for (int i = 0; i < a.length; i++) {
      for (int j = 0; j < a[0].length; j++) {
        d[i][j] = a[i][j];
      }
    }
    return d;
  }

  private static void leastSquaresMatchesTheNormalEquations(long[][] a, long[] b) {
    double[] rhs = new double[b.length];
    for (int i = 0; i < b.length; i++) {
      rhs[i] = b[i];
    }
    Decomposition d = run(Kind.LEAST_SQUARES, doubles(a), rhs);
    assertThat(d.status()).isEqualTo(Status.OK);
    double[] exact = normalEquations(a, b);
    double[][] x = d.factors().getFirst();
    for (int j = 0; j < exact.length; j++) {
      assertThat(x[j][0]).isCloseTo(exact[j], offset(1e-10));
    }
  }

  @Test
  void leastSquaresAgainstTheExactNormalEquations() {
    leastSquaresMatchesTheNormalEquations(
        new long[][] {{1, 1}, {1, 2}, {1, 3}}, new long[] {1, 2, 2});
    leastSquaresMatchesTheNormalEquations(
        new long[][] {{2, -1, 0}, {1, 3, 1}, {0, 1, 4}, {1, 0, 1}, {3, 2, -2}},
        new long[] {1, -2, 5, 0, 7});
    leastSquaresMatchesTheNormalEquations(new long[][] {{3}, {4}}, new long[] {1, 2});
    // a square system: the solution itself
    leastSquaresMatchesTheNormalEquations(new long[][] {{2, 1}, {1, 3}}, new long[] {3, 5});
  }

  @Test
  void theExponentialOfADiagonalMatrix() {
    double[] d = {-2, 0, 0.5, 1, 3};
    double[][] a = new double[d.length][d.length];
    for (int i = 0; i < d.length; i++) {
      a[i][i] = d[i];
    }
    double[][] e = run(Kind.EXP, a).factors().getFirst();
    for (int i = 0; i < d.length; i++) {
      for (int j = 0; j < d.length; j++) {
        double expected = i == j ? Math.exp(d[i]) : 0;
        assertThat(Math.abs(e[i][j] - expected)).isLessThanOrEqualTo(1e-13 * Math.exp(3));
      }
    }
  }

  @Test
  void theExponentialOfANilpotentMatrixIsAFinitePolynomial() {
    // N³ = 0, so e^N = I + N + N²/2 exactly
    double[][] n = {{0, 2, -1}, {0, 0, 3}, {0, 0, 0}};
    double[][] n2 = times(n, n);
    double[][] expected = identity(3);
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        expected[i][j] += n[i][j] + n2[i][j] / 2;
      }
    }
    assertThat(distance(run(Kind.EXP, n).factors().getFirst(), expected)).isLessThan(1e-12);
  }

  @Test
  void aSingularMatrixHasNoLu() {
    assertThat(run(Kind.LU, new double[][] {{1, 2}, {2, 4}}).status()).isEqualTo(Status.SINGULAR);
    assertThat(run(Kind.LU, new double[][] {{0, 0}, {0, 0}}).status()).isEqualTo(Status.SINGULAR);
    assertThat(run(Kind.LU, new double[][] {{1, 2, 3}}).status()).isEqualTo(Status.NOT_SQUARE);
    assertThat(run(Kind.LU, new double[][] {{1, 2}, {2, 4}}).factors()).isEmpty();
  }

  @Test
  void theSingularityTestIsRelativeToTheMatrix() {
    double[][] small = {{1e-12, 0}, {0, 1e-12}};
    Decomposition d = run(Kind.LU, small);
    assertThat(d.status()).isEqualTo(Status.OK);
    assertThat(distance(times(d.factors().get(1), d.factors().get(2)), small)).isZero();
    // the tolerance is n·ε of the largest entry: a pivot of 10^-12 of it is not singular
    assertThat(run(Kind.LU, new double[][] {{1e12, 0}, {0, 1}}).status()).isEqualTo(Status.OK);
    double ulp = Math.ulp(1.0);
    assertThat(run(Kind.LU, new double[][] {{1, 1}, {1, 1 + ulp}}).status())
        .isEqualTo(Status.SINGULAR);
    assertThat(
            run(Kind.LEAST_SQUARES, new double[][] {{1, 1}, {1, 1 + ulp}, {1, 1}}, new double[3])
                .status())
        .isEqualTo(Status.RANK_DEFICIENT);
  }

  @Test
  void tinyAndHugeMatricesAreScaled() {
    // unscaled, Householder's sums of squares go subnormal at 1e-160 and Q is not orthogonal
    double t = 1e-160;
    List<double[][]> qr = run(Kind.QR, new double[][] {{t, 0}, {0, t}}).factors();
    assertThat(distance(times(transpose(qr.get(0)), qr.get(0)), identity(2))).isLessThan(1e-15);
    assertThat(distance(times(qr.get(0), qr.get(1)), new double[][] {{t, 0}, {0, t}}))
        .isLessThan(1e-15 * t);
    double[][] x =
        run(
                Kind.LEAST_SQUARES,
                new double[][] {{t, 0}, {0, t}, {t, t}},
                new double[] {t, 2 * t, 3 * t})
            .factors()
            .getFirst();
    assertThat(x[0][0]).isCloseTo(1, offset(1e-14));
    assertThat(x[1][0]).isCloseTo(2, offset(1e-14));
    // a subnormal largest entry is scaled by its true exponent, not Math.getExponent's -1023
    double sub = 1e-320;
    double[][] root = run(Kind.CHOLESKY, new double[][] {{sub}}).factors().getFirst();
    assertThat(root[0][0]).isCloseTo(Math.sqrt(sub), offset(1e-16 * Math.sqrt(sub)));
    List<double[][]> tiny = run(Kind.QR, new double[][] {{sub, 0}, {0, sub}}).factors();
    // Q to rounding, as at any scale, and R at the subnormal's own scale
    assertThat(distance(times(transpose(tiny.get(0)), tiny.get(0)), identity(2))).isLessThan(1e-15);
    assertThat(Math.abs(tiny.get(1)[0][0])).isCloseTo(sub, offset(1e-15 * sub));
    // at 1e±200 the well-conditioned inputs decompose, with the factors at their scale
    for (double s : new double[] {1e-200, 1e200}) {
      double[][] a = {{4 * s, 2 * s}, {2 * s, 3 * s}};
      assertThat(run(Kind.QR, a).status()).as("qr at %s", s).isEqualTo(Status.OK);
      double[][] l = run(Kind.CHOLESKY, a).factors().getFirst();
      assertThat(l[0][0] / Math.sqrt(s)).isCloseTo(2, offset(1e-14));
      assertThat(distance(times(l, transpose(l)), a)).isLessThan(1e-14 * s);
      double[][] y = run(Kind.LEAST_SQUARES, a, new double[] {6 * s, 5 * s}).factors().getFirst();
      assertThat(y[0][0]).isCloseTo(1, offset(1e-14));
      assertThat(y[1][0]).isCloseTo(1, offset(1e-14));
      List<double[][]> svd = run(Kind.SVD, a).factors();
      assertThat(svd.get(1)[0][0] / s).isCloseTo((7 + Math.sqrt(17)) / 2, offset(1e-13));
    }
  }

  @Test
  void anIndefiniteOrAsymmetricMatrixHasNoCholesky() {
    assertThat(run(Kind.CHOLESKY, new double[][] {{1, 2}, {2, 1}}).status())
        .isEqualTo(Status.NOT_SPD);
    assertThat(run(Kind.CHOLESKY, new double[][] {{2, 1}, {0, 2}}).status())
        .isEqualTo(Status.NOT_SPD);
    assertThat(run(Kind.CHOLESKY, new double[][] {{0}}).status()).isEqualTo(Status.NOT_SPD);
    assertThat(run(Kind.CHOLESKY, new double[][] {{1, 2}}).status()).isEqualTo(Status.NOT_SQUARE);
  }

  @Test
  void leastSquaresNeedsFullColumnRank() {
    assertThat(
            run(Kind.LEAST_SQUARES, new double[][] {{1, 2}, {2, 4}, {3, 6}}, new double[] {1, 2, 3})
                .status())
        .isEqualTo(Status.RANK_DEFICIENT);
    assertThat(run(Kind.LEAST_SQUARES, new double[][] {{1, 2, 3}}, new double[] {1}).status())
        .isEqualTo(Status.RANK_DEFICIENT);
  }

  @Test
  void anOverflowIsNotAMatrix() {
    assertThat(run(Kind.EXP, new double[][] {{1000}}).status()).isEqualTo(Status.NOT_FINITE);
    assertThat(run(Kind.EXP, new double[][] {{1, 2}}).status()).isEqualTo(Status.NOT_SQUARE);
  }

  @Test
  void normalisingTurnsMinusZeroIntoZero() {
    Decompose op = new Decompose(Kind.EXP, new double[][] {{0}}, null);
    Decomposition raw = new Decomposition(Status.OK, List.<double[][]>of(new double[][] {{-0.0}}));
    double x = op.normalise(raw).factors().getFirst()[0][0];
    assertThat(Double.doubleToRawLongBits(x)).isEqualTo(Double.doubleToRawLongBits(0.0));
    Decomposition nan =
        new Decomposition(Status.OK, List.<double[][]>of(new double[][] {{Double.NaN}}));
    assertThat(op.normalise(nan).status()).isEqualTo(Status.NOT_FINITE);
  }

  @Test
  void theOperandsAreChecked() {
    assertThatThrownBy(() -> new Decompose(Kind.LU, new double[][] {{1, 2}, {3}}, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Decompose(Kind.LU, new double[][] {{1}}, new double[] {1}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Decompose(Kind.LEAST_SQUARES, new double[][] {{1}}, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new Decompose(Kind.LEAST_SQUARES, new double[][] {{1}}, new double[] {1, 2}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new Decomposition(Status.SINGULAR, List.<double[][]>of(new double[][] {{1}})))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void theInputIsCopied() {
    double[][] a = {{4, 3}, {6, 3}};
    Decompose op = new Decompose(Kind.LU, a, null);
    a[0][0] = 100;
    assertThat(op.a()[0][0]).isEqualTo(4);
    assertThat(transpose(op.a())[1][0]).isEqualTo(3);
  }
}
