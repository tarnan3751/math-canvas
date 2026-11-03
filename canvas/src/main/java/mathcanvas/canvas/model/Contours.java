package mathcanvas.canvas.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Expr;

/**
 * A contour plot's levels (docs/reference/canvas.md, <b>Object model</b>): its values, each
 * evaluated through the sampler, or for a count {@code n} the values {@code min + k·(max − min)/(n
 * + 1)}, {@code k} from 1 to {@code n}, between the least and the greatest value of the function
 * over a grid of {@value #GRID} by {@value #GRID} points of the visible window, so the levels
 * follow the window. Each level is traced through the sampler over the widened window as an
 * implicit curve is, and labelled at the middle of the part of it in view: the middle vertex, among
 * those inside the visible window less {@value #INSET} of it on each side, of the polyline with the
 * most such vertices. A value that is not a number, a function with no value in view, or one that
 * is constant there, has no level; equal values are one level.
 */
final class Contours {

  /** The points along each side of the grid a count's range is found on. */
  static final int GRID = 64;

  /** The fraction of the window on each side kept clear of labels. */
  static final double INSET = 0.05;

  private Contours() {}

  /**
   * A contour plot's levels.
   *
   * @param plot the plot
   * @param sampler evaluates the function and the values, and traces each level
   * @param window the visible window
   * @param wide the widened window the levels are traced over
   * @param cancellation checked as it goes
   * @return the geometry
   */
  static Geometry of(
      ContourPlot plot, Sampler sampler, Window window, Window wide, Cancellation cancellation) {
    TreeSet<Double> values = new TreeSet<>();
    switch (plot.levels()) {
      case ContourPlot.Count c -> {
        double[] xs = new double[GRID * GRID];
        double[] ys = new double[GRID * GRID];
        for (int j = 0; j < GRID; j++) {
          for (int i = 0; i < GRID; i++) {
            xs[j * GRID + i] = window.minX() + window.width() * i / (GRID - 1);
            ys[j * GRID + i] = window.minY() + window.height() * j / (GRID - 1);
          }
        }
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (double v : sampler.plane(plot.function(), xs, ys, cancellation)) {
          if (Double.isFinite(v)) {
            min = Math.min(min, v);
            max = Math.max(max, v);
          }
        }
        if (min < max) {
          for (int k = 1; k <= c.n(); k++) {
            double v = min + k * (max - min) / (c.n() + 1);
            if (Double.isFinite(v)) {
              values.add(v);
            }
          }
        }
      }
      case ContourPlot.Values v -> {
        for (Expr e : v.values()) {
          double value = sampler.value(e, Map.of());
          if (Double.isFinite(value)) {
            values.add(value);
          }
        }
      }
    }
    Window inset = window.widened(-INSET);
    List<Geometry.Level> levels = new ArrayList<>();
    for (double v : values) {
      List<Polyline> lines = sampler.level(plot.function(), v, wide, cancellation);
      levels.add(new Geometry.Level(v, lines, label(lines, inset)));
    }
    return new Geometry.Contours(levels);
  }

  /** Where a level's label goes: the middle of the part of its fullest polyline in view. */
  static Optional<Geometry.Point> label(List<Polyline> lines, Window inset) {
    List<Integer> best = List.of();
    Polyline bestLine = null;
    for (Polyline l : lines) {
      List<Integer> inside = new ArrayList<>();
      for (int k = 0; k < l.size(); k++) {
        double x = l.x(k);
        double y = l.y(k);
        if (x >= inset.minX() && x <= inset.maxX() && y >= inset.minY() && y <= inset.maxY()) {
          inside.add(k);
        }
      }
      if (inside.size() > best.size()) {
        best = inside;
        bestLine = l;
      }
    }
    if (bestLine == null) {
      return Optional.empty();
    }
    int k = best.get(best.size() / 2);
    return Optional.of(new Geometry.Point(bestLine.x(k), bestLine.y(k)));
  }
}
