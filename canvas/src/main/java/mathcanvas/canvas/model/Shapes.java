package mathcanvas.canvas.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The drawn polylines of circles and polygons, which the model computes itself: a polygon is its
 * closed boundary, vertex to vertex; a circle is sampled at the angular step whose chord stays
 * within {@link #SAGITTA} of the window's diagonal of the arc, whole when it lies inside the
 * widened window, else only along the arcs that meet it, so a circle far larger than the window is
 * drawn as precisely as a small one.
 */
final class Shapes {

  /** The largest gap between an arc and its chord, relative to the window's diagonal. */
  static final double SAGITTA = 1e-3;

  /** The fewest samples of a whole circle. */
  static final int MIN_SAMPLES = 64;

  /** The most samples of one arc. */
  static final int MAX_SAMPLES = 4096;

  private static final double TURN = 2 * Math.PI;

  private Shapes() {}

  /**
   * A circle's polylines.
   *
   * @param c the circle
   * @param window the visible window, whose diagonal sets the step
   * @param wide the window the circle is drawn in
   * @return the arcs, in increasing parameter
   */
  static List<Polyline> circle(Parametrisation.Circular c, Window window, Window wide) {
    double eps = SAGITTA * window.diagonal();
    double step = Math.min(TURN / MIN_SAMPLES, 2 * Math.sqrt(2 * eps / c.r()) + Math.ulp(1.0));
    List<Polyline> out = new ArrayList<>();
    if (c.cx() - c.r() >= wide.minX()
        && c.cx() + c.r() <= wide.maxX()
        && c.cy() - c.r() >= wide.minY()
        && c.cy() + c.r() <= wide.maxY()) {
      out.add(arc(c, 0, TURN, step));
      return out;
    }
    // the parameters where the circle crosses the window's edges cut it into arcs, each inside or
    // outside as its middle is
    List<Double> cuts = new ArrayList<>(List.of(0.0, TURN));
    double[] xs = {wide.minX(), wide.maxX()};
    double[] ys = {wide.minY(), wide.maxY()};
    for (double x : xs) {
      double u = (x - c.cx()) / c.r();
      if (Math.abs(u) <= 1) {
        double a = Math.acos(u);
        cuts.add(c.parameterOf(c.cx() + c.r() * Math.cos(a), c.cy() + c.r() * Math.sin(a)));
        cuts.add(c.parameterOf(c.cx() + c.r() * Math.cos(-a), c.cy() + c.r() * Math.sin(-a)));
      }
    }
    for (double y : ys) {
      double u = (y - c.cy()) / c.r();
      if (Math.abs(u) <= 1) {
        double a = Math.asin(u);
        cuts.add(c.parameterOf(c.cx() + c.r() * Math.cos(a), c.cy() + c.r() * Math.sin(a)));
        cuts.add(
            c.parameterOf(c.cx() + c.r() * Math.cos(Math.PI - a), c.cy() + c.r() * Math.sin(a)));
      }
    }
    cuts.sort(Double::compare);
    double from = Double.NaN;
    for (int k = 0; k + 1 < cuts.size(); k++) {
      double a = cuts.get(k);
      double b = cuts.get(k + 1);
      Sample mid = c.at((a + b) / 2);
      boolean inside =
          b > a
              && mid.x() >= wide.minX()
              && mid.x() <= wide.maxX()
              && mid.y() >= wide.minY()
              && mid.y() <= wide.maxY();
      if (inside && Double.isNaN(from)) {
        from = a;
      } else if (!inside && b > a && !Double.isNaN(from)) {
        out.add(arc(c, from, a, step));
        from = Double.NaN;
      }
    }
    if (!Double.isNaN(from)) {
      out.add(arc(c, from, TURN, step));
    }
    return out;
  }

  private static Polyline arc(Parametrisation.Circular c, double a, double b, double step) {
    int n = (int) Math.min(MAX_SAMPLES, Math.max(2, Math.ceil((b - a) / step)));
    double[] ts = new double[n + 1];
    double[] xs = new double[n + 1];
    double[] ys = new double[n + 1];
    for (int k = 0; k <= n; k++) {
      double t = k == n ? b : a + (b - a) * k / n;
      Sample s = c.at(t);
      ts[k] = t;
      xs[k] = s.x();
      ys[k] = s.y();
    }
    return new Polyline(ts, xs, ys);
  }

  /**
   * A polygon's closed boundary.
   *
   * @param p the polygon
   * @return one polyline from vertex 0 round to vertex 0, its parameter 0 to n
   */
  static Polyline polygon(Parametrisation.Polygonal p) {
    int n = p.size();
    double[] ts = new double[n + 1];
    double[] xs = new double[n + 1];
    double[] ys = new double[n + 1];
    for (int k = 0; k <= n; k++) {
      ts[k] = k;
      xs[k] = p.x(k);
      ys[k] = p.y(k);
    }
    return new Polyline(ts, xs, ys);
  }
}
