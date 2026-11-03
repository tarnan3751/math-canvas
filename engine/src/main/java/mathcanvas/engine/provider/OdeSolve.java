package mathcanvas.engine.provider;

import java.util.Objects;

/**
 * The numeric solution of an initial value problem for a first-order system {@code y' = f(t, y)},
 * {@code y(t₀) = y₀}, in {@code double}, at the given times, which may lie on either side of {@code
 * t₀}. A result is correct when each state is the solution's value at its time to the tolerances,
 * or when its status says why there are none. It is normalised by copying the states, turning every
 * {@code -0.0} into {@code 0.0}, and giving the status {@link OdeSolution.Status#NOT_FINITE} to
 * states with a NaN or an infinity. The arrays are copied in, so the record compares them by
 * identity: it is a transient input, never a key.
 *
 * @param rhs the right side {@code f}
 * @param t0 the initial time
 * @param y0 the initial state, non-empty
 * @param times the times the states are wanted at, in any order
 * @param relativeTolerance the relative tolerance of a step
 * @param absoluteTolerance the absolute tolerance of a step
 */
public record OdeSolve(
    Rhs rhs,
    double t0,
    double[] y0,
    double[] times,
    double relativeTolerance,
    double absoluteTolerance)
    implements Operation<OdeSolution> {

  /** The right side of {@code y' = f(t, y)}. */
  @FunctionalInterface
  public interface Rhs {
    /**
     * Writes {@code f(t, y)} into {@code dy}.
     *
     * @param t the time
     * @param y the state, not to be changed
     * @param dy the derivative, of the state's length
     */
    void derivatives(double t, double[] y, double[] dy);
  }

  /**
   * Checks and copies the operands.
   *
   * @throws IllegalArgumentException if the state is empty, a time or the initial values are not
   *     finite, or a tolerance is not positive
   */
  public OdeSolve {
    Objects.requireNonNull(rhs, "rhs");
    y0 = y0.clone();
    times = times.clone();
    if (y0.length == 0) {
      throw new IllegalArgumentException("an initial value problem needs a state");
    }
    if (!Double.isFinite(t0)) {
      throw new IllegalArgumentException("the initial time must be finite");
    }
    for (double y : y0) {
      if (!Double.isFinite(y)) {
        throw new IllegalArgumentException("the initial values must be finite");
      }
    }
    for (double t : times) {
      if (!Double.isFinite(t)) {
        throw new IllegalArgumentException("the times must be finite");
      }
    }
    if (!(relativeTolerance > 0) || !(absoluteTolerance > 0)) {
      throw new IllegalArgumentException("the tolerances must be positive");
    }
  }

  @Override
  public Class<OdeSolution> outputType() {
    return OdeSolution.class;
  }

  @Override
  public OdeSolution normalise(OdeSolution result) {
    if (result.status() != OdeSolution.Status.OK) {
      return OdeSolution.failed(result.status(), result.message());
    }
    double[][] states = new double[result.states().length][];
    for (int i = 0; i < states.length; i++) {
      states[i] = result.states()[i].clone();
      for (int j = 0; j < states[i].length; j++) {
        if (!Double.isFinite(states[i][j])) {
          return OdeSolution.failed(OdeSolution.Status.NOT_FINITE, "");
        }
        if (states[i][j] == 0.0) {
          states[i][j] = 0.0;
        }
      }
    }
    return new OdeSolution(OdeSolution.Status.OK, states, "");
  }
}
