package mathcanvas.canvas.sample;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.model.Window;
import mathcanvas.engine.Cancellation;

/**
 * Adaptive sampling of a curve over a range of its parameter (docs/reference/canvas.md,
 * <b>Sampling</b>): a uniform grid of {@value #INITIAL} samples, then bisection of a segment while
 * its two halves bend by more than half a degree on the screen or an end is undefined, down to the
 * range over 2^{@value #DEPTH}. The curve is split into polylines where it is undefined, at an
 * asymptote (a segment of the minimum step that halving did not shorten as it shortens a continuous
 * curve, and that jumps by more than the window's height or changes sign; see {@link #pole}), and
 * where it runs far outside the window. Screen distances are measured on a view of the size the
 * sampler was given, which the window fills.
 */
final class Adaptive {

  /** The samples of the uniform grid. */
  static final int INITIAL = 256;

  /** The minimum step is the range over two to this power. */
  static final int DEPTH = 14;

  /** The bend, in radians on the screen, above which a segment is halved: half a degree. */
  static final double FLATNESS = Math.toRadians(0.5);

  /** How many samples are taken between checks of the cancellation. */
  static final int CHECK_EVERY = 1024;

  /** A curve: writes the point at a parameter, NaN where it is undefined. */
  @FunctionalInterface
  interface Curve {
    void at(double t, double[] xy);
  }

  private final Curve curve;
  private final Cancellation cancellation;
  private final double minStep;
  private final double scaleX;
  private final double scaleY;
  private final Window wide;
  private final double height;
  private final double[] xy = new double[2];
  private int samples;

  private double[] ts = new double[4 * INITIAL];
  private double[] xs = new double[4 * INITIAL];
  private double[] ys = new double[4 * INITIAL];
  private int size;

  private Adaptive(
      Curve curve,
      double lo,
      double hi,
      Window window,
      double pixelWidth,
      double pixelHeight,
      Cancellation cancellation) {
    this.curve = curve;
    this.cancellation = cancellation;
    this.minStep = (hi - lo) / (1 << DEPTH);
    this.scaleX = pixelWidth / window.width();
    this.scaleY = pixelHeight / window.height();
    this.wide = window.widened(0.5);
    this.height = window.height();
  }

  /**
   * Samples a curve.
   *
   * @param curve the curve
   * @param lo the least parameter, finite
   * @param hi the greatest parameter, finite, not below the least
   * @param window the window it is drawn in
   * @param pixelWidth the width in pixels the window fills
   * @param pixelHeight the height in pixels the window fills
   * @param cancellation checked every {@value #CHECK_EVERY} samples
   * @return the polylines, each vertex carrying its parameter
   */
  static List<Polyline> sample(
      Curve curve,
      double lo,
      double hi,
      Window window,
      double pixelWidth,
      double pixelHeight,
      Cancellation cancellation) {
    Adaptive a = new Adaptive(curve, lo, hi, window, pixelWidth, pixelHeight, cancellation);
    a.run(lo, hi);
    return a.split();
  }

  private void run(double lo, double hi) {
    if (!(hi > lo)) {
      eval(lo);
      emit(lo, xy[0], xy[1]);
      return;
    }
    double t0 = lo;
    eval(t0);
    double x0 = xy[0];
    double y0 = xy[1];
    emit(t0, x0, y0);
    for (int k = 1; k < INITIAL; k++) {
      double t1 = k == INITIAL - 1 ? hi : lo + (hi - lo) * k / (INITIAL - 1);
      eval(t1);
      double x1 = xy[0];
      double y1 = xy[1];
      refine(t0, x0, y0, t1, x1, y1, Double.NaN);
      emit(t1, x1, y1);
      t0 = t1;
      x0 = x1;
      y0 = y1;
    }
  }

  /** Emits the samples strictly inside a segment, halving it while it bends or meets a gap. */
  private void refine(
      double t0, double x0, double y0, double t1, double x1, double y1, double parentJump) {
    boolean f0 = finite(x0, y0);
    boolean f1 = finite(x1, y1);
    double jump = f0 && f1 ? pixels(x1 - x0, y1 - y0) : Double.NaN;
    if (t1 - t0 <= minStep) {
      if (f0 && f1 && pole(y0, y1, jump, parentJump)) {
        emit(Double.NaN, Double.NaN, Double.NaN);
      }
      return;
    }
    double tm = 0.5 * (t0 + t1);
    eval(tm);
    double xm = xy[0];
    double ym = xy[1];
    boolean fm = finite(xm, ym);
    if (!f0 && !f1 && !fm) {
      return;
    }
    boolean split =
        !(f0 && f1 && fm)
            || !outsideOnOneSide(x0, y0, xm, ym, x1, y1)
                && bend(xm - x0, ym - y0, x1 - xm, y1 - ym) > FLATNESS;
    if (split) {
      refine(t0, x0, y0, tm, xm, ym, jump);
      emit(tm, xm, ym);
      refine(tm, xm, ym, t1, x1, y1, jump);
    } else {
      emit(tm, xm, ym);
    }
  }

  /**
   * Whether a segment of the minimum step spans an asymptote. Across a pole the half that holds it
   * jumps more than its parent did, where a continuous curve's half jumps about half as much; so a
   * segment that halving did not shrink below three quarters of its parent is cut when it jumps by
   * more than the window's height or changes sign. A segment whose two values have opposite signs
   * and both lie beyond the window's height is cut in any case.
   */
  private boolean pole(double y0, double y1, double jump, double parentJump) {
    return !(jump < 0.75 * parentJump) && (Math.abs(y1 - y0) > height || y0 * y1 < 0)
        || y0 * y1 < 0 && Math.abs(y0) > height && Math.abs(y1) > height;
  }

  private void eval(double t) {
    if (++samples % CHECK_EVERY == 0) {
      cancellation.check();
    }
    curve.at(t, xy);
  }

  private static boolean finite(double x, double y) {
    return Double.isFinite(x) && Double.isFinite(y);
  }

  private double pixels(double dx, double dy) {
    return Math.hypot(dx * scaleX, dy * scaleY);
  }

  /** The angle between two successive segments on the screen, 0 when either is a point. */
  private double bend(double ux, double uy, double vx, double vy) {
    ux *= scaleX;
    uy *= scaleY;
    vx *= scaleX;
    vy *= scaleY;
    double cross = ux * vy - uy * vx;
    double dot = ux * vx + uy * vy;
    return cross == 0 && dot >= 0 ? 0 : Math.atan2(Math.abs(cross), dot);
  }

  /** Whether three points lie beyond one side of the widened window, where no detail is drawn. */
  private boolean outsideOnOneSide(
      double x0, double y0, double x1, double y1, double x2, double y2) {
    return x0 < wide.minX() && x1 < wide.minX() && x2 < wide.minX()
        || x0 > wide.maxX() && x1 > wide.maxX() && x2 > wide.maxX()
        || y0 < wide.minY() && y1 < wide.minY() && y2 < wide.minY()
        || y0 > wide.maxY() && y1 > wide.maxY() && y2 > wide.maxY();
  }

  private void emit(double t, double x, double y) {
    if (size == xs.length) {
      ts = Arrays.copyOf(ts, 2 * size);
      xs = Arrays.copyOf(xs, 2 * size);
      ys = Arrays.copyOf(ys, 2 * size);
    }
    ts[size] = t;
    xs[size] = x;
    ys[size] = y;
    size++;
  }

  /**
   * The emitted samples as polylines: split where a sample is undefined, and where a run of
   * segments lies outside the widened window, keeping the samples just beyond it so the curve still
   * reaches its edge.
   */
  private List<Polyline> split() {
    List<Polyline> out = new ArrayList<>();
    int start = -1;
    for (int k = 0; k <= size; k++) {
      boolean keep = k < size && finite(xs[k], ys[k]) && (touches(k - 1, k) || touches(k, k + 1));
      boolean lone = k < size && finite(xs[k], ys[k]) && isolated(k) && inside(k);
      if (keep && start < 0) {
        start = k;
      }
      if (!keep && start >= 0) {
        out.add(polyline(start, k));
        start = -1;
      }
      if (lone) {
        out.add(polyline(k, k + 1));
      }
    }
    return out;
  }

  /** Whether a segment between two finite samples meets the widened window. */
  private boolean touches(int a, int b) {
    if (a < 0 || b >= size || !finite(xs[a], ys[a]) || !finite(xs[b], ys[b])) {
      return false;
    }
    return Math.max(xs[a], xs[b]) >= wide.minX()
        && Math.min(xs[a], xs[b]) <= wide.maxX()
        && Math.max(ys[a], ys[b]) >= wide.minY()
        && Math.min(ys[a], ys[b]) <= wide.maxY();
  }

  /** Whether a finite sample has no finite neighbour, so it is a polyline of its own. */
  private boolean isolated(int k) {
    return (k == 0 || !finite(xs[k - 1], ys[k - 1]))
        && (k == size - 1 || !finite(xs[k + 1], ys[k + 1]));
  }

  private boolean inside(int k) {
    return xs[k] >= wide.minX()
        && xs[k] <= wide.maxX()
        && ys[k] >= wide.minY()
        && ys[k] <= wide.maxY();
  }

  private Polyline polyline(int from, int to) {
    return new Polyline(
        Arrays.copyOfRange(ts, from, to),
        Arrays.copyOfRange(xs, from, to),
        Arrays.copyOfRange(ys, from, to));
  }
}
