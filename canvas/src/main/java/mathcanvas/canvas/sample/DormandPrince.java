package mathcanvas.canvas.sample;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.Cancellation;

/**
 * The Dormand–Prince 5(4) pair in {@code double} (docs/reference/canvas.md, <b>Sampling</b>): an
 * explicit Runge–Kutta step of order 5 with an embedded order 4 estimate of its error, the step
 * accepted when the error is within {@code atol + rtol·|y|} in every component and its length
 * adapted after every attempt (the standard controller, {@code 0.9·err^(−1/5)} clamped to [0.2,
 * 5]), up to a longest step. The last stage of an accepted step is the first of the next. It stops
 * at the end of the range, when the caller's stop says so, at a blow-up (a step shrunk below a
 * ten-billionth of the range, which a derivative that is not finite or a solution running to
 * infinity forces), or after a budget of attempts.
 */
final class DormandPrince {

  /** How many attempts are made between checks of the cancellation. */
  static final int CHECK_EVERY = 64;

  /** The shortest step, as a fraction of the range, below which the solution is a blow-up. */
  static final double MIN_STEP = 1e-10;

  private static final double C2 = 1.0 / 5;
  private static final double C3 = 3.0 / 10;
  private static final double C4 = 4.0 / 5;
  private static final double C5 = 8.0 / 9;
  private static final double A21 = 1.0 / 5;
  private static final double A31 = 3.0 / 40;
  private static final double A32 = 9.0 / 40;
  private static final double A41 = 44.0 / 45;
  private static final double A42 = -56.0 / 15;
  private static final double A43 = 32.0 / 9;
  private static final double A51 = 19372.0 / 6561;
  private static final double A52 = -25360.0 / 2187;
  private static final double A53 = 64448.0 / 6561;
  private static final double A54 = -212.0 / 729;
  private static final double A61 = 9017.0 / 3168;
  private static final double A62 = -355.0 / 33;
  private static final double A63 = 46732.0 / 5247;
  private static final double A64 = 49.0 / 176;
  private static final double A65 = -5103.0 / 18656;
  private static final double B1 = 35.0 / 384;
  private static final double B3 = 500.0 / 1113;
  private static final double B4 = 125.0 / 192;
  private static final double B5 = -2187.0 / 6784;
  private static final double B6 = 11.0 / 84;
  // the fifth-order weights less the fourth-order ones
  private static final double E1 = 71.0 / 57600;
  private static final double E3 = -71.0 / 16695;
  private static final double E4 = 71.0 / 1920;
  private static final double E5 = -17253.0 / 339200;
  private static final double E6 = 22.0 / 525;
  private static final double E7 = -1.0 / 40;

  /** A system {@code y' = f(t, y)}. */
  @FunctionalInterface
  interface Ode {

    /**
     * The derivative.
     *
     * @param t the independent variable
     * @param y the state, not to be changed
     * @param dy receives {@code f(t, y)}; a component that is not finite rejects the step
     */
    void derivative(double t, double[] y, double[] dy);
  }

  /** Whether to stop after an accepted state. */
  @FunctionalInterface
  interface Stop {

    /**
     * Whether to stop.
     *
     * @param t the independent variable
     * @param y the state, kept as the trajectory's last
     * @return true to stop here
     */
    boolean at(double t, double[] y);
  }

  /** Why an integration ended. */
  enum End {
    /** It reached the end of its range. */
    REACHED,
    /** The caller's stop said so. */
    STOPPED,
    /**
     * The step shrank below the shortest: the derivative is not finite or the solution runs off.
     */
    BLOW_UP,
    /** The budget of attempts ran out. */
    BUDGET
  }

  /**
   * The accepted states, the first the start.
   *
   * @param ts the independent variable at each
   * @param ys the states
   * @param end why it ended
   */
  record Trajectory(double[] ts, double[][] ys, End end) {}

  private DormandPrince() {}

  /**
   * Integrates a system over a range, which may run backwards.
   *
   * @param f the system
   * @param t0 the start
   * @param y0 the state at the start, finite
   * @param t1 the end
   * @param atol the absolute tolerance, positive
   * @param rtol the relative tolerance, not negative
   * @param maxStep the longest step, positive
   * @param budget the most attempts
   * @param stop asked after each accepted state
   * @param cancellation checked every {@value #CHECK_EVERY} attempts
   * @return the trajectory
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  static Trajectory integrate(
      Ode f,
      double t0,
      double[] y0,
      double t1,
      double atol,
      double rtol,
      double maxStep,
      int budget,
      Stop stop,
      Cancellation cancellation) {
    int n = y0.length;
    double direction = t1 >= t0 ? 1 : -1;
    double span = Math.abs(t1 - t0);
    double hmin = MIN_STEP * span;
    List<Double> ts = new ArrayList<>();
    List<double[]> ys = new ArrayList<>();
    double t = t0;
    double[] y = y0.clone();
    ts.add(t);
    ys.add(y.clone());
    double[] k1 = new double[n];
    double[] k2 = new double[n];
    double[] k3 = new double[n];
    double[] k4 = new double[n];
    double[] k5 = new double[n];
    double[] k6 = new double[n];
    double[] k7 = new double[n];
    double[] stage = new double[n];
    double[] next = new double[n];
    f.derivative(t, y, k1);
    if (!finite(k1)) {
      return trajectory(ts, ys, span == 0 ? End.REACHED : End.BLOW_UP);
    }
    double h = Math.min(maxStep, span / 64);
    for (int attempt = 0; ; attempt++) {
      if (attempt % CHECK_EVERY == 0) {
        cancellation.check();
      }
      double left = Math.abs(t1 - t);
      if (!(left > hmin)) {
        return trajectory(ts, ys, End.REACHED);
      }
      if (attempt >= budget) {
        return trajectory(ts, ys, End.BUDGET);
      }
      h = Math.min(h, left);
      double s = direction * h;
      for (int i = 0; i < n; i++) {
        stage[i] = y[i] + s * A21 * k1[i];
      }
      f.derivative(t + C2 * s, stage, k2);
      for (int i = 0; i < n; i++) {
        stage[i] = y[i] + s * (A31 * k1[i] + A32 * k2[i]);
      }
      f.derivative(t + C3 * s, stage, k3);
      for (int i = 0; i < n; i++) {
        stage[i] = y[i] + s * (A41 * k1[i] + A42 * k2[i] + A43 * k3[i]);
      }
      f.derivative(t + C4 * s, stage, k4);
      for (int i = 0; i < n; i++) {
        stage[i] = y[i] + s * (A51 * k1[i] + A52 * k2[i] + A53 * k3[i] + A54 * k4[i]);
      }
      f.derivative(t + C5 * s, stage, k5);
      for (int i = 0; i < n; i++) {
        stage[i] = y[i] + s * (A61 * k1[i] + A62 * k2[i] + A63 * k3[i] + A64 * k4[i] + A65 * k5[i]);
      }
      f.derivative(t + s, stage, k6);
      for (int i = 0; i < n; i++) {
        next[i] = y[i] + s * (B1 * k1[i] + B3 * k3[i] + B4 * k4[i] + B5 * k5[i] + B6 * k6[i]);
      }
      f.derivative(t + s, next, k7);
      double err = 0;
      for (int i = 0; i < n; i++) {
        double e =
            s * (E1 * k1[i] + E3 * k3[i] + E4 * k4[i] + E5 * k5[i] + E6 * k6[i] + E7 * k7[i]);
        double scale = atol + rtol * Math.max(Math.abs(y[i]), Math.abs(next[i]));
        err = Math.max(err, Math.abs(e) / scale);
      }
      if (!Double.isFinite(err) || !finite(next) || !finite(k7)) {
        err = Double.POSITIVE_INFINITY;
      }
      if (err <= 1) {
        t = left - h <= hmin ? t1 : t + s;
        System.arraycopy(next, 0, y, 0, n);
        System.arraycopy(k7, 0, k1, 0, n);
        ts.add(t);
        ys.add(y.clone());
        if (stop.at(t, y)) {
          return trajectory(ts, ys, End.STOPPED);
        }
        double grow = err == 0 ? 5 : Math.min(5, 0.9 * Math.pow(err, -0.2));
        h = Math.min(maxStep, h * grow);
      } else {
        h *= Double.isFinite(err) ? Math.max(0.2, 0.9 * Math.pow(err, -0.2)) : 0.2;
        if (h < hmin) {
          return trajectory(ts, ys, End.BLOW_UP);
        }
      }
    }
  }

  private static boolean finite(double[] v) {
    for (double x : v) {
      if (!Double.isFinite(x)) {
        return false;
      }
    }
    return true;
  }

  private static Trajectory trajectory(List<Double> ts, List<double[]> ys, End end) {
    double[] t = new double[ts.size()];
    for (int k = 0; k < t.length; k++) {
      t[k] = ts.get(k);
    }
    return new Trajectory(t, ys.toArray(double[][]::new), end);
  }
}
