package mathcanvas.engine.provider;

import java.util.List;
import java.util.Objects;

/**
 * The result of a {@link Decompose}: the factors in the order its kind names, or a status that says
 * why there are none.
 *
 * @param status whether the factors exist
 * @param factors the factors, each by rows; empty unless the status is {@link Status#OK}
 */
public record Decomposition(Status status, List<double[][]> factors) {

  /** Whether the operation gave factors, and why not. */
  public enum Status {
    /** The factors are given. */
    OK,
    /** The operation needs a square matrix. */
    NOT_SQUARE,
    /** The matrix is singular to working precision. */
    SINGULAR,
    /** The matrix is not symmetric positive-definite. */
    NOT_SPD,
    /** The matrix has fewer rows than columns, or is not of full column rank. */
    RANK_DEFICIENT,
    /** A factor is beyond the {@code double} range. */
    NOT_FINITE
  }

  /**
   * Checks and copies the operands.
   *
   * @throws IllegalArgumentException if factors are given with a status other than {@code OK}
   */
  public Decomposition {
    Objects.requireNonNull(status, "status");
    factors = List.copyOf(factors);
    if (status != Status.OK && !factors.isEmpty()) {
      throw new IllegalArgumentException("factors with the status " + status);
    }
  }

  /**
   * Returns a result without factors.
   *
   * @param status why there are none, not {@code OK}
   * @return the result
   */
  public static Decomposition failed(Status status) {
    return new Decomposition(status, List.of());
  }
}
