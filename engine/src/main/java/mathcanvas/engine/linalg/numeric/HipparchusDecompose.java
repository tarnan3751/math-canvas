package mathcanvas.engine.linalg.numeric;

import java.util.Arrays;
import java.util.Optional;
import mathcanvas.engine.provider.Decompose;
import mathcanvas.engine.provider.Decomposition;
import mathcanvas.engine.provider.Decomposition.Status;
import mathcanvas.engine.provider.Provider;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import org.hipparchus.exception.MathIllegalArgumentException;
import org.hipparchus.linear.CholeskyDecomposition;
import org.hipparchus.linear.DecompositionSolver;
import org.hipparchus.linear.LUDecomposition;
import org.hipparchus.linear.MatrixUtils;
import org.hipparchus.linear.QRDecomposition;
import org.hipparchus.linear.RealMatrix;
import org.hipparchus.linear.SingularValueDecomposition;

/**
 * The default {@link Decompose} provider, named {@code default}: Hipparchus's decompositions in
 * {@code double}. It always answers, with the factors or with the status that says why there are
 * none, so a singular or indefinite input never gives a matrix.
 *
 * <p>Every decomposition but the exponential runs on the matrix scaled by a power of two that
 * brings its largest entry in magnitude into {@code [1, 2)} (a power of four, into {@code [1, 4)},
 * for Cholesky, so that the square root of the scale is a power of two too), which is exact in
 * {@code double} and keeps Householder's sums of squares and Cholesky's absolute positivity test
 * clear of underflow and overflow; the factors that carry the scale are unscaled exactly: {@code U}
 * of LU, {@code R} of QR and {@code Σ} of the SVD by the scale, Cholesky's {@code L} by its square
 * root, and least squares' solution by the ratio of the scales of {@code A} and {@code b}. The
 * orthogonal factors, the permutation and LU's unit {@code L} do not depend on the scale.
 *
 * <p>LU and the QR behind least squares call a matrix singular when a pivot (LU) or a diagonal
 * entry of {@code R} (QR) is below {@code max(m, n)·ε} times the largest entry in magnitude, {@code
 * ε = 2^-52}, the standard numerical rank tolerance; a zero matrix is singular. Cholesky keeps
 * Hipparchus's thresholds, which the scaling makes relative to the matrix. The matrix exponential
 * is {@link MatrixUtils#matrixExponential} (Higham's Padé approximant with scaling and squaring,
 * 2005), on the matrix as given, since {@code e^A} does not scale.
 *
 * <p>Of Hipparchus, only {@code org.hipparchus.linear} is used, and {@code
 * org.hipparchus.exception.MathIllegalArgumentException}, the one failure it reports by throwing
 * (Cholesky's).
 */
public record HipparchusDecompose() implements Provider<Decompose, Decomposition> {

  /** The machine epsilon of {@code double}, {@code 2^-52}. */
  public static final double EPSILON = Math.ulp(1.0);

  @Override
  public String name() {
    return "default";
  }

  @Override
  public Optional<Decomposition> apply(Decompose input, Context ctx, Steps steps) {
    ctx.cancellation().check();
    double[][] data = input.a();
    int rows = data.length;
    int columns = data[0].length;
    boolean square = rows == columns;
    int e = exponent(data);
    Decomposition out =
        switch (input.kind()) {
          case LU -> square ? lu(data, e) : Decomposition.failed(Status.NOT_SQUARE);
          case QR -> {
            QRDecomposition qr = new QRDecomposition(scaled(data, -e));
            yield ok(qr.getQ().getData(), unscaled(qr.getR(), e));
          }
          case CHOLESKY -> square ? cholesky(data) : Decomposition.failed(Status.NOT_SQUARE);
          case SVD -> {
            SingularValueDecomposition svd = new SingularValueDecomposition(scaled(data, -e));
            yield ok(svd.getU().getData(), unscaled(svd.getS(), e), svd.getV().getData());
          }
          case LEAST_SQUARES -> leastSquares(data, input.b(), e);
          case EXP ->
              square
                  ? ok(MatrixUtils.matrixExponential(MatrixUtils.createRealMatrix(data)).getData())
                  : Decomposition.failed(Status.NOT_SQUARE);
        };
    ctx.cancellation().check();
    return Optional.of(out);
  }

  private static Decomposition lu(double[][] data, int e) {
    RealMatrix a = scaled(data, -e);
    double threshold = data.length * EPSILON * max(a.getData());
    if (threshold == 0) {
      return Decomposition.failed(Status.SINGULAR);
    }
    LUDecomposition lu = new LUDecomposition(a, threshold);
    // getL is null when a pivot fell below the threshold
    RealMatrix l = lu.getL();
    return l == null
        ? Decomposition.failed(Status.SINGULAR)
        : ok(lu.getP().getData(), l.getData(), unscaled(lu.getU(), e));
  }

  private static Decomposition cholesky(double[][] data) {
    // a power of four, so that L carries half its exponent exactly
    int half = Math.floorDiv(exponent(data), 2);
    try {
      RealMatrix l = new CholeskyDecomposition(scaled(data, -2 * half)).getL();
      return ok(unscaled(l, half));
    } catch (MathIllegalArgumentException notSymmetricPositiveDefinite) {
      return Decomposition.failed(Status.NOT_SPD);
    }
  }

  private static Decomposition leastSquares(double[][] data, double[] b, int e) {
    if (data.length < data[0].length) {
      return Decomposition.failed(Status.RANK_DEFICIENT);
    }
    RealMatrix a = scaled(data, -e);
    double threshold = Math.max(data.length, data[0].length) * EPSILON * max(a.getData());
    DecompositionSolver solver = new QRDecomposition(a, threshold).getSolver();
    if (threshold == 0 || !solver.isNonSingular()) {
      return Decomposition.failed(Status.RANK_DEFICIENT);
    }
    // b scaled on its own: A·x = b is (2^-e A)·(2^(e - f) x) = 2^-f b
    int f = exponent(new double[][] {b});
    double[] y = new double[b.length];
    for (int i = 0; i < b.length; i++) {
      y[i] = Math.scalb(b[i], -f);
    }
    double[] x = solver.solve(MatrixUtils.createRealVector(y)).toArray();
    double[][] solution = new double[x.length][];
    for (int i = 0; i < x.length; i++) {
      solution[i] = new double[] {Math.scalb(x[i], f - e)};
    }
    return ok(solution);
  }

  /**
   * The exponent {@code e} of the largest entry in magnitude, so that {@code 2^-e} brings it into
   * {@code [1, 2)}; 0 for a zero matrix. A subnormal's is its true exponent, below {@code -1022},
   * which {@link Math#getExponent} does not give (it answers {@code -1023} for every subnormal).
   */
  private static int exponent(double[][] data) {
    double max = max(data);
    if (max == 0) {
      return 0;
    }
    return max < Double.MIN_NORMAL
        ? Math.getExponent(Math.scalb(max, 64)) - 64
        : Math.getExponent(max);
  }

  private static double max(double[][] data) {
    double max = 0;
    for (double[] row : data) {
      for (double x : row) {
        max = Math.max(max, Math.abs(x));
      }
    }
    return max;
  }

  /** The matrix times {@code 2^n}. */
  private static RealMatrix scaled(double[][] data, int n) {
    double[][] out = new double[data.length][];
    for (int i = 0; i < data.length; i++) {
      out[i] = new double[data[i].length];
      for (int j = 0; j < out[i].length; j++) {
        out[i][j] = Math.scalb(data[i][j], n);
      }
    }
    return MatrixUtils.createRealMatrix(out);
  }

  /** A factor's entries times {@code 2^n}. */
  private static double[][] unscaled(RealMatrix m, int n) {
    double[][] out = m.getData();
    for (double[] row : out) {
      for (int j = 0; j < row.length; j++) {
        row[j] = Math.scalb(row[j], n);
      }
    }
    return out;
  }

  private static Decomposition ok(double[][]... factors) {
    return new Decomposition(Status.OK, Arrays.asList(factors));
  }
}
