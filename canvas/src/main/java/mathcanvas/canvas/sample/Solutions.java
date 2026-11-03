package mathcanvas.canvas.sample;

import java.util.List;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.model.Window;
import mathcanvas.engine.Cancellation;

/**
 * The solution of {@code y' = f(x, y)} through a point, traced in both directions with {@link
 * DormandPrince} (docs/reference/canvas.md, <b>Sampling</b>). The curve is the integral curve of
 * the direction field {@code (1, f)}, integrated by its arc length in the window's own units
 * ({@code x} and {@code y} each divided by the window's side), so a step is a short piece of the
 * curve as drawn whatever its slope: the unit direction {@code (1, g)/√(1 + g²)} with {@code g =
 * f·width/height}, upright where {@code g} is infinite, and turned round whenever it points against
 * the direction at the start of the step, so the orientation stays continuous. The curve therefore
 * continues through a vertical tangent, a pole of {@code f}, whether the slope changes sign there
 * (a turning point, where it comes back, as a circle of {@code y' = −x/y} does) or not (where it
 * goes on upright). Each direction stops where the curve leaves the window after being in it, at a
 * blow-up (where {@code f} has no value), after {@value #LENGTH} of the window's sides of arc,
 * after {@value #BUDGET} attempts, or when the orbit closes: a step passes within {@value #CLOSE}
 * of the window's side of the start, heading the way it started, once the curve has been further
 * away; the curve is then closed at the start and not traced the other way.
 */
final class Solutions {

  /** The longest step, as a fraction of the window's side: the polyline's coarsest vertex gap. */
  static final double MAX_STEP = 1.0 / 400;

  /** The error allowed per step, as a fraction of the window's side. */
  static final double TOLERANCE = 1e-6;

  /** The most arc length traced each way, in the window's sides. */
  static final double LENGTH = 8;

  /** The most attempts each way. */
  static final int BUDGET = 8000;

  /** How near the start a step passes, in the window's sides, for the orbit to close. */
  static final double CLOSE = 1e-3;

  private Solutions() {}

  /**
   * The solution through a point.
   *
   * @param f the slope, NaN where it has no value
   * @param x0 the point's abscissa
   * @param y0 the point's ordinate
   * @param window the window the curve is traced in (the widened window)
   * @param cancellation checked as it goes
   * @return one polyline through the point, or none when the slope has no value there
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  static List<Polyline> through(
      MarchingSquares.Field f, double x0, double y0, Window window, Cancellation cancellation) {
    if (!Double.isFinite(f.at(x0, y0))) {
      return List.of();
    }
    double[] start = {
      (x0 - window.minX()) / window.width(), (y0 - window.minY()) / window.height()
    };
    Trace ahead = new Trace(f, window, start);
    DormandPrince.Trajectory forward = ahead.run(LENGTH, cancellation);
    if (ahead.closed) {
      return List.of(polyline(window, null, forward, true));
    }
    DormandPrince.Trajectory backward = new Trace(f, window, start).run(-LENGTH, cancellation);
    return List.of(polyline(window, backward, forward, false));
  }

  /** The two directions joined through the start, or one closed at the start. */
  private static Polyline polyline(
      Window window,
      DormandPrince.Trajectory behind,
      DormandPrince.Trajectory ahead,
      boolean closed) {
    int back = behind == null ? 1 : behind.ys().length;
    int n = back + ahead.ys().length - 1 + (closed ? 1 : 0);
    double[] xs = new double[n];
    double[] ys = new double[n];
    int k = 0;
    if (behind == null) {
      k = put(window, ahead.ys()[0], xs, ys, k);
    } else {
      for (int i = behind.ys().length - 1; i >= 0; i--) {
        k = put(window, behind.ys()[i], xs, ys, k);
      }
    }
    for (int i = 1; i < ahead.ys().length; i++) {
      k = put(window, ahead.ys()[i], xs, ys, k);
    }
    if (closed) {
      put(window, ahead.ys()[0], xs, ys, k);
    }
    return Polyline.unparametrised(xs, ys);
  }

  private static int put(Window window, double[] uv, double[] xs, double[] ys, int k) {
    xs[k] = window.minX() + uv[0] * window.width();
    ys[k] = window.minY() + uv[1] * window.height();
    return k + 1;
  }

  /**
   * One direction of the curve: the oriented direction field, the direction of the last accepted
   * state it is kept continuous with, and the stops.
   */
  private static final class Trace {

    private final MarchingSquares.Field f;
    private final Window window;
    private final double[] start;
    private final double[] heading = new double[2];
    private final double[] initial = new double[2];
    private final double[] last;
    private boolean inside;
    private boolean away;
    private boolean closed;

    Trace(MarchingSquares.Field f, Window window, double[] start) {
      this.f = f;
      this.window = window;
      this.start = start;
      this.last = start.clone();
      this.inside = in(start);
      direction(start, heading, false);
      initial[0] = heading[0];
      initial[1] = heading[1];
    }

    DormandPrince.Trajectory run(double length, Cancellation cancellation) {
      return DormandPrince.integrate(
          (s, uv, d) -> direction(uv, d, true),
          0,
          start,
          length,
          TOLERANCE,
          0,
          MAX_STEP,
          BUDGET,
          this::accepted,
          cancellation);
    }

    /**
     * The unit direction of the field at a state, turned round when {@code oriented} and it points
     * against the heading.
     */
    private void direction(double[] uv, double[] d, boolean oriented) {
      double w = window.width();
      double h = window.height();
      double g = f.at(window.minX() + uv[0] * w, window.minY() + uv[1] * h) * w / h;
      if (Double.isInfinite(g)) {
        d[0] = 0;
        d[1] = Math.signum(g);
      } else {
        double norm = Math.hypot(1, g);
        d[0] = 1 / norm;
        d[1] = g / norm;
      }
      if (oriented && d[0] * heading[0] + d[1] * heading[1] < 0) {
        d[0] = -d[0];
        d[1] = -d[1];
      }
    }

    /** After an accepted state: the new heading, then whether to stop. */
    private boolean accepted(double s, double[] uv) {
      double[] d = new double[2];
      direction(uv, d, true);
      if (Double.isFinite(d[0]) && Double.isFinite(d[1])) {
        heading[0] = d[0];
        heading[1] = d[1];
      }
      boolean now = in(uv);
      boolean left = inside && !now;
      inside = now;
      double gap = distance(start, last, uv);
      if (away && gap <= CLOSE && heading[0] * initial[0] + heading[1] * initial[1] > 0) {
        closed = true;
      } else if (Math.hypot(uv[0] - start[0], uv[1] - start[1]) > 2 * CLOSE) {
        away = true;
      }
      last[0] = uv[0];
      last[1] = uv[1];
      return left || closed;
    }
  }

  /** The distance from a point to a segment. */
  private static double distance(double[] p, double[] a, double[] b) {
    double dx = b[0] - a[0];
    double dy = b[1] - a[1];
    double l2 = dx * dx + dy * dy;
    double t = l2 == 0 ? 0 : ((p[0] - a[0]) * dx + (p[1] - a[1]) * dy) / l2;
    t = Math.max(0, Math.min(1, t));
    return Math.hypot(a[0] + t * dx - p[0], a[1] + t * dy - p[1]);
  }

  private static boolean in(double[] uv) {
    return uv[0] >= 0 && uv[0] <= 1 && uv[1] >= 0 && uv[1] <= 1;
  }
}
