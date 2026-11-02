package mathcanvas.engine.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A numeric matrix decomposition, or a numeric operation of the same family, over {@code double}
 * matrices. A result is correct when its factors reconstruct the input to working precision ({@link
 * Kind} names each), or when its status says why there are none. It is normalised by copying the
 * factors, turning every {@code -0.0} into {@code 0.0}, and giving the status {@link
 * Decomposition.Status#NOT_FINITE} to factors with a NaN or an infinity. The arrays are copied in,
 * so the record compares them by identity: it is a transient input, never a key.
 *
 * @param kind which operation
 * @param a the matrix, by rows, rectangular and non-empty
 * @param b the right side of {@link Kind#LEAST_SQUARES}, null for every other kind
 */
public record Decompose(Kind kind, double[][] a, double[] b) implements Operation<Decomposition> {

  /** The operations and the factors each gives, in order. */
  public enum Kind {
    /** {@code [P, L, U]} with {@code P·A = L·U}; a square, nonsingular matrix. */
    LU,
    /** {@code [Q, R]} with {@code A = Q·R}, {@code Q} orthogonal (m×m), {@code R} upper (m×n). */
    QR,
    /** {@code [L]} with {@code A = L·Lᵀ}; a symmetric positive-definite matrix. */
    CHOLESKY,
    /**
     * {@code [U, Σ, V]} with {@code A = U·Σ·Vᵀ}, compact: {@code U} m×p, {@code Σ} p×p diagonal
     * with the singular values descending and non-negative, {@code V} n×p, {@code p = min(m, n)}.
     */
    SVD,
    /**
     * {@code [x]} as an n×1 matrix, minimising {@code |A·x − b|}; {@code A} of full column rank.
     */
    LEAST_SQUARES,
    /** {@code [e^A]}; a square matrix. */
    EXP
  }

  /**
   * Checks and copies the operands.
   *
   * @throws IllegalArgumentException if the matrix is empty or ragged, or {@code b} is given for
   *     another kind than {@link Kind#LEAST_SQUARES}, or missing or of another length than the
   *     matrix's rows for it
   */
  public Decompose {
    Objects.requireNonNull(kind, "kind");
    a = copy(a);
    if (a.length == 0 || a[0].length == 0) {
      throw new IllegalArgumentException("a matrix needs at least one row and one column");
    }
    for (double[] row : a) {
      if (row.length != a[0].length) {
        throw new IllegalArgumentException("a matrix must be rectangular");
      }
    }
    if ((kind == Kind.LEAST_SQUARES) != (b != null)) {
      throw new IllegalArgumentException("a right side is given exactly for least squares");
    }
    if (b != null && b.length != a.length) {
      throw new IllegalArgumentException("the right side needs one entry per row");
    }
    b = b == null ? null : b.clone();
  }

  @Override
  public Class<Decomposition> outputType() {
    return Decomposition.class;
  }

  @Override
  public Decomposition normalise(Decomposition result) {
    if (result.status() != Decomposition.Status.OK) {
      return Decomposition.failed(result.status());
    }
    List<double[][]> factors = new ArrayList<>();
    for (double[][] f : result.factors()) {
      double[][] g = copy(f);
      for (double[] row : g) {
        for (int j = 0; j < row.length; j++) {
          if (!Double.isFinite(row[j])) {
            return Decomposition.failed(Decomposition.Status.NOT_FINITE);
          }
          // -0.0 is 0
          row[j] += 0.0;
        }
      }
      factors.add(g);
    }
    return new Decomposition(Decomposition.Status.OK, factors);
  }

  private static double[][] copy(double[][] m) {
    double[][] out = new double[m.length][];
    for (int i = 0; i < m.length; i++) {
      out[i] = m[i].clone();
    }
    return out;
  }
}
