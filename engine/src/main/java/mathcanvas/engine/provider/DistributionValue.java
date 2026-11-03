package mathcanvas.engine.provider;

import java.util.Objects;

/**
 * The result of a {@link Distribute}: the value, or a status that says why there is none.
 *
 * @param status whether the value exists
 * @param value the value; NaN unless the status is {@link Status#OK}
 */
public record DistributionValue(Status status, double value) {

  /** Whether the operation gave a value, and why not. */
  public enum Status {
    /** The value is given. */
    OK,
    /** The value is beyond the {@code double} range, or not a number. */
    NOT_FINITE,
    /** An iteration (a continued fraction, a root search) did not converge. */
    NO_CONVERGENCE
  }

  /**
   * Checks the operands.
   *
   * @throws IllegalArgumentException if a value is given with a status other than {@code OK}
   */
  public DistributionValue {
    Objects.requireNonNull(status, "status");
    if (status != Status.OK && !Double.isNaN(value)) {
      throw new IllegalArgumentException("a value with the status " + status);
    }
  }

  /**
   * Returns a result without a value.
   *
   * @param status why there is none, not {@code OK}
   * @return the result
   */
  public static DistributionValue failed(Status status) {
    return new DistributionValue(status, Double.NaN);
  }
}
