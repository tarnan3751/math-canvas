package mathcanvas.engine.linalg.numeric;

import static mathcanvas.engine.linalg.numeric.Mat.distance;
import static mathcanvas.engine.linalg.numeric.Mat.identity;
import static mathcanvas.engine.linalg.numeric.Mat.norm;
import static mathcanvas.engine.linalg.numeric.Mat.run;
import static mathcanvas.engine.linalg.numeric.Mat.times;
import static mathcanvas.engine.linalg.numeric.Mat.transpose;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import mathcanvas.engine.provider.Decompose.Kind;
import mathcanvas.engine.provider.Decomposition;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * The decompositions reconstruct their input: random {@code double} matrices up to 8×8, each
 * identity checked to {@code 1e-10} relative to the size of the matrix.
 */
class HipparchusDecomposeProperties {

  private static final double TOLERANCE = 1e-10;

  private static void close(double[][] actual, double[][] expected, double scale) {
    assertThat(distance(actual, expected)).isLessThanOrEqualTo(TOLERANCE * Math.max(1, scale));
  }

  private static List<double[][]> factors(Kind kind, double[][] a) {
    Decomposition d = run(kind, a);
    assertThat(d.status()).isEqualTo(Decomposition.Status.OK);
    return d.factors();
  }

  @Property(tries = 200)
  void luReconstructs(@ForAll Random random, @ForAll @IntRange(min = 1, max = 8) int n) {
    double[][] a = Mat.random(random, n, n);
    // a random matrix of [-10, 10] is never numerically singular
    List<double[][]> f = factors(Kind.LU, a);
    double[][] p = f.get(0);
    double[][] l = f.get(1);
    double[][] u = f.get(2);
    close(times(p, a), times(l, u), norm(a));
    for (int i = 0; i < n; i++) {
      assertThat(l[i][i]).isEqualTo(1.0);
      for (int j = i + 1; j < n; j++) {
        assertThat(l[i][j]).isZero();
      }
      for (int j = 0; j < i; j++) {
        assertThat(u[i][j]).isZero();
      }
    }
    close(times(p, transpose(p)), identity(n), 1);
  }

  @Property(tries = 200)
  void qrReconstructs(
      @ForAll Random random,
      @ForAll @IntRange(min = 1, max = 8) int m,
      @ForAll @IntRange(min = 1, max = 8) int n) {
    double[][] a = Mat.random(random, m, n);
    List<double[][]> f = factors(Kind.QR, a);
    double[][] q = f.get(0);
    double[][] r = f.get(1);
    close(times(q, r), a, norm(a));
    close(times(transpose(q), q), identity(m), 1);
    for (int i = 0; i < m; i++) {
      for (int j = 0; j < Math.min(i, n); j++) {
        assertThat(r[i][j]).isZero();
      }
    }
  }

  @Property(tries = 200)
  void choleskyReconstructsASymmetricPositiveDefiniteMatrix(
      @ForAll Random random, @ForAll @IntRange(min = 1, max = 8) int n) {
    double[][] b = Mat.random(random, n, n);
    double[][] a = times(b, transpose(b));
    for (int i = 0; i < n; i++) {
      a[i][i] += 1;
    }
    double[][] l = factors(Kind.CHOLESKY, a).getFirst();
    close(times(l, transpose(l)), a, norm(a));
    for (int i = 0; i < n; i++) {
      for (int j = i + 1; j < n; j++) {
        assertThat(l[i][j]).isZero();
      }
    }
  }

  @Property(tries = 200)
  void svdReconstructsWithDescendingSingularValues(
      @ForAll Random random,
      @ForAll @IntRange(min = 1, max = 8) int m,
      @ForAll @IntRange(min = 1, max = 8) int n) {
    double[][] a = Mat.random(random, m, n);
    List<double[][]> f = factors(Kind.SVD, a);
    double[][] u = f.get(0);
    double[][] s = f.get(1);
    double[][] v = f.get(2);
    int p = Math.min(m, n);
    assertThat(u.length).isEqualTo(m);
    assertThat(u[0].length).isEqualTo(p);
    assertThat(s.length).isEqualTo(p);
    assertThat(v.length).isEqualTo(n);
    close(times(times(u, s), transpose(v)), a, norm(a));
    close(times(transpose(u), u), identity(p), 1);
    close(times(transpose(v), v), identity(p), 1);
    for (int i = 0; i < p; i++) {
      assertThat(s[i][i]).isGreaterThanOrEqualTo(0);
      if (i > 0) {
        assertThat(s[i][i]).isLessThanOrEqualTo(s[i - 1][i - 1]);
      }
      for (int j = 0; j < p; j++) {
        if (i != j) {
          assertThat(s[i][j]).isZero();
        }
      }
    }
  }

  @Property(tries = 200)
  void theExponentialOfMinusAIsTheInverse(
      @ForAll Random random, @ForAll @IntRange(min = 1, max = 8) int n) {
    // entries in [-1, 1], so the exponentials stay well conditioned
    double[][] a = Mat.random(random, n, n);
    for (double[] row : a) {
      for (int j = 0; j < n; j++) {
        row[j] /= 10;
      }
    }
    double[][] e = factors(Kind.EXP, a).getFirst();
    double[][] f = factors(Kind.EXP, Mat.negate(a)).getFirst();
    close(times(e, f), identity(n), norm(e) * norm(f));
  }

  @Property(tries = 200)
  void leastSquaresSatisfiesTheNormalEquations(
      @ForAll Random random,
      @ForAll @IntRange(min = 1, max = 8) int n,
      @ForAll @IntRange(min = 0, max = 4) int extra) {
    int m = n + extra;
    double[][] a = Mat.random(random, m, n);
    double[] b = Mat.random(random, 1, m)[0];
    Decomposition d = run(Kind.LEAST_SQUARES, a, b);
    assertThat(d.status()).isEqualTo(Decomposition.Status.OK);
    double[][] x = d.factors().getFirst();
    // Aᵀ(A·x − b) = 0
    double[][] residual = times(a, x);
    for (int i = 0; i < m; i++) {
      residual[i][0] -= b[i];
    }
    double[][] gradient = times(transpose(a), residual);
    assertThat(norm(gradient))
        .isLessThanOrEqualTo(1e-8 * Math.max(1, norm(a) * norm(a) * Math.max(1, norm(x))));
  }

  /** {@code m} times {@code 2^k}, exactly. */
  private static double[][] scalb(double[][] m, int k) {
    double[][] out = new double[m.length][];
    for (int i = 0; i < m.length; i++) {
      out[i] = new double[m[i].length];
      for (int j = 0; j < m[i].length; j++) {
        out[i][j] = Math.scalb(m[i][j], k);
      }
    }
    return out;
  }

  /** The matrices differ by at most 1e-10 of {@code scale}, however small or large it is. */
  private static void closeAtScale(double[][] actual, double[][] expected, double scale) {
    assertThat(distance(actual, expected)).isLessThanOrEqualTo(TOLERANCE * scale);
  }

  @Property(tries = 200)
  void everyDecompositionHoldsAtEveryScale(
      @ForAll Random random,
      @ForAll @IntRange(min = 1, max = 6) int n,
      @ForAll @IntRange(min = 0, max = 2) int extra,
      @ForAll @IntRange(min = -500, max = 500) int k) {
    int m = n + extra;
    // a matrix of [-10, 10] times 2^k, and the identities checked relative to its size
    double[][] a = scalb(Mat.random(random, m, n), k);
    double size = norm(a);

    List<double[][]> qr = factors(Kind.QR, a);
    closeAtScale(times(qr.get(0), qr.get(1)), a, size);
    close(times(transpose(qr.get(0)), qr.get(0)), identity(m), 1);

    List<double[][]> svd = factors(Kind.SVD, a);
    closeAtScale(times(times(svd.get(0), svd.get(1)), transpose(svd.get(2))), a, size);
    close(times(transpose(svd.get(0)), svd.get(0)), identity(n), 1);

    double[][] square = scalb(Mat.random(random, n, n), k);
    List<double[][]> lu = factors(Kind.LU, square);
    closeAtScale(times(lu.get(0), square), times(lu.get(1), lu.get(2)), norm(square));

    double[][] b = Mat.random(random, n, n);
    double[][] spd = times(b, transpose(b));
    for (int i = 0; i < n; i++) {
      spd[i][i] += 1;
    }
    spd = scalb(spd, k);
    double[][] l = factors(Kind.CHOLESKY, spd).getFirst();
    closeAtScale(times(l, transpose(l)), spd, norm(spd));

    // least squares: x is the same at every scale of A and b together
    double[] rhs = Mat.random(random, 1, m)[0];
    Decomposition unscaledSolution = run(Kind.LEAST_SQUARES, scalb(a, -k), rhs);
    double[] scaledRhs = scalb(new double[][] {rhs}, k)[0];
    Decomposition scaledSolution = run(Kind.LEAST_SQUARES, a, scaledRhs);
    assertThat(scaledSolution.status()).isEqualTo(Decomposition.Status.OK);
    double[][] x = unscaledSolution.factors().getFirst();
    assertThat(distance(scaledSolution.factors().getFirst(), x))
        .isLessThanOrEqualTo(1e-10 * Math.max(1, norm(x)));
  }
}
