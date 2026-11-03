package mathcanvas.engine.provider;

import java.util.Objects;

/**
 * The result of an {@link OdeSolve}: the state at each requested time, in their order, or a status
 * that says why there are none.
 *
 * @param status whether the states exist
 * @param states the state at each time, empty unless the status is {@link Status#OK}
 * @param message the integrator's reason for {@link Status#FAILED}, empty otherwise
 */
public record OdeSolution(Status status, double[][] states, String message) {

  /** Whether the integration gave the states, and why not. */
  public enum Status {
    /** The states are given. */
    OK,
    /** A state is beyond the {@code double} range: the solution blows up. */
    NOT_FINITE,
    /** The integrator stopped: its step shrank below its bound, or it took too many steps. */
    FAILED
  }

  /**
   * Checks the operands.
   *
   * @throws IllegalArgumentException if states are given with a status other than {@code OK}
   */
  public OdeSolution {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(states, "states");
    Objects.requireNonNull(message, "message");
    if (status != Status.OK && states.length != 0) {
      throw new IllegalArgumentException("states with the status " + status);
    }
  }

  /**
   * Returns a result without states.
   *
   * @param status why there are none, not {@code OK}
   * @param message the integrator's reason, or empty
   * @return the result
   */
  public static OdeSolution failed(Status status, String message) {
    return new OdeSolution(status, new double[0][], message);
  }
}
