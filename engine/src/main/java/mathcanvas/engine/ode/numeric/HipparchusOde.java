package mathcanvas.engine.ode.numeric;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import mathcanvas.engine.provider.OdeSolution;
import mathcanvas.engine.provider.OdeSolve;
import mathcanvas.engine.provider.Provider;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import org.hipparchus.exception.Localizable;
import org.hipparchus.exception.LocalizedCoreFormats;
import org.hipparchus.exception.MathIllegalArgumentException;
import org.hipparchus.exception.MathIllegalStateException;
import org.hipparchus.ode.LocalizedODEFormats;
import org.hipparchus.ode.ODEState;
import org.hipparchus.ode.OrdinaryDifferentialEquation;
import org.hipparchus.ode.nonstiff.DormandPrince54Integrator;
import org.hipparchus.ode.sampling.ODEStateInterpolator;
import org.hipparchus.ode.sampling.ODEStepHandler;

/**
 * The default {@link OdeSolve} provider, named {@code default}: Hipparchus's Dormand–Prince 5(4)
 * embedded Runge–Kutta integrator with adaptive steps, the states at the requested times read from
 * each step's dense output (its continuous interpolant, of order 4) rather than forced as steps.
 * The times after {@code t₀} come from one integration forward to the last of them, those before it
 * from one integration backward to the first; a time equal to {@code t₀} is the initial state. It
 * always answers: with the states, {@code NOT_FINITE} when a state leaves the {@code double} range
 * or the integrator meets a NaN, or {@code FAILED} with the reason when it gives up (a step below
 * its bound, or more than {@link #MAX_EVALUATIONS} evaluations of the right side).
 *
 * <p>The step is bounded above by the span of the integration and below by nothing; the request to
 * stop is checked at every step. Of Hipparchus, only {@code org.hipparchus.ode} and its {@code
 * nonstiff} and {@code sampling} packages are used, with the two exceptions the integrator throws.
 */
public record HipparchusOde() implements Provider<OdeSolve, OdeSolution> {

  /** The most evaluations of the right side in one integration. */
  public static final int MAX_EVALUATIONS = 5_000_000;

  @Override
  public String name() {
    return "default";
  }

  @Override
  public Optional<OdeSolution> apply(OdeSolve input, Context ctx, Steps steps) {
    ctx.cancellation().check();
    double t0 = input.t0();
    double[] times = input.times();
    double[][] states = new double[times.length][];
    double first = t0;
    double last = t0;
    for (int i = 0; i < times.length; i++) {
      if (times[i] == t0) {
        states[i] = input.y0().clone();
      }
      first = Math.min(first, times[i]);
      last = Math.max(last, times[i]);
    }
    try {
      if (last > t0) {
        integrate(input, ctx, last, states);
      }
      if (first < t0) {
        integrate(input, ctx, first, states);
      }
    } catch (MathIllegalStateException e) {
      return Optional.of(failure(e.getSpecifier(), e.getMessage(Locale.ROOT)));
    } catch (MathIllegalArgumentException e) {
      return Optional.of(failure(e.getSpecifier(), e.getMessage(Locale.ROOT)));
    }
    for (double[] s : states) {
      for (double y : s) {
        if (!Double.isFinite(y)) {
          return Optional.of(OdeSolution.failed(OdeSolution.Status.NOT_FINITE, ""));
        }
      }
    }
    return Optional.of(new OdeSolution(OdeSolution.Status.OK, states, ""));
  }

  /** The result of an integration the integrator gave up. */
  private static OdeSolution failure(Localizable why, String message) {
    if (why == LocalizedODEFormats.NAN_APPEARING_DURING_INTEGRATION) {
      return OdeSolution.failed(OdeSolution.Status.NOT_FINITE, "");
    }
    if (why == LocalizedCoreFormats.MAX_COUNT_EXCEEDED) {
      return OdeSolution.failed(
          OdeSolution.Status.FAILED,
          "more than " + MAX_EVALUATIONS + " evaluations of the right side");
    }
    if (why == LocalizedODEFormats.MINIMAL_STEPSIZE_REACHED_DURING_INTEGRATION) {
      return OdeSolution.failed(OdeSolution.Status.FAILED, "the step size became too small");
    }
    return OdeSolution.failed(OdeSolution.Status.FAILED, message);
  }

  /**
   * The longest step the integrator may take towards {@code end}: the smallest gap between
   * consecutive requested times on that side of {@code t₀}, counting {@code t₀} itself. The pair's
   * dense output is of order four, one below its steps, so a requested time deep inside one long
   * step gets a value less accurate than the error control promises (the logistic equation at 0.1
   * in a first step of 0.6 missed a relative 1e-8 by a factor of two); a step no longer than the
   * spacing keeps every requested time within one spacing of a step end. The floor of a thousandth
   * of the span bounds the number of steps a long table forces.
   */
  private static double maxStep(double t0, double end, double[] times) {
    double span = Math.abs(end - t0);
    boolean forward = end > t0;
    double[] side = Arrays.stream(times).filter(t -> forward ? t > t0 : t < t0).sorted().toArray();
    double gap = span;
    double previous = t0;
    for (double t : forward ? side : reversed(side)) {
      gap = Math.min(gap, Math.abs(t - previous));
      previous = t;
    }
    return Math.max(Math.min(gap, span), span / 1000);
  }

  private static double[] reversed(double[] values) {
    double[] out = new double[values.length];
    for (int i = 0; i < values.length; i++) {
      out[i] = values[values.length - 1 - i];
    }
    return out;
  }

  /** One integration from {@code t₀} to {@code end}, filling the states of the times it passes. */
  private static void integrate(OdeSolve input, Context ctx, double end, double[][] states) {
    double t0 = input.t0();
    double[] times = input.times();
    int n = input.y0().length;
    DormandPrince54Integrator integrator =
        new DormandPrince54Integrator(
            0, maxStep(t0, end, times), input.absoluteTolerance(), input.relativeTolerance());
    integrator.setMaxEvaluations(MAX_EVALUATIONS);
    boolean forward = end > t0;
    integrator.addStepHandler(
        new ODEStepHandler() {
          @Override
          public void handleStep(ODEStateInterpolator step) {
            ctx.cancellation().check();
            double a = step.getPreviousState().getTime();
            double b = step.getCurrentState().getTime();
            double lo = Math.min(a, b);
            double hi = Math.max(a, b);
            for (int i = 0; i < times.length; i++) {
              double t = times[i];
              if (states[i] == null && (forward ? t > t0 : t < t0) && t >= lo && t <= hi) {
                states[i] = step.getInterpolatedState(t).getPrimaryState();
              }
            }
          }
        });
    OrdinaryDifferentialEquation ode =
        new OrdinaryDifferentialEquation() {
          @Override
          public int getDimension() {
            return n;
          }

          @Override
          public double[] computeDerivatives(double t, double[] y) {
            double[] dy = new double[n];
            input.rhs().derivatives(t, y, dy);
            return dy;
          }
        };
    integrator.integrate(ode, new ODEState(t0, input.y0()), end);
    for (int i = 0; i < times.length; i++) {
      if (states[i] == null && (forward ? times[i] > t0 : times[i] < t0)) {
        // a time the dense output did not reach, which floating point can leave at the very end
        states[i] = new double[n];
        Arrays.fill(states[i], Double.NaN);
      }
    }
  }
}
