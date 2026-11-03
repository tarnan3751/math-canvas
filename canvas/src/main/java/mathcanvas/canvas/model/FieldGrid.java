package mathcanvas.canvas.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.Cancellation;

/**
 * The marks of a vector field and a slope field (docs/reference/canvas.md, <b>Object model</b>): a
 * grid over the visible window, {@code density} columns across its width and as many rows as make
 * the cells as tall as they are wide on a square view, at most four times as many as the columns.
 * The grid is aligned to whole multiples of the cell from the origin, so a pan moves the marks with
 * the plane rather than past it; a zoom or a resize lays a new grid. Each mark is centred on its
 * cell, laid out in the cell's own units so a mark of one length in cells is one length on the
 * screen: a slope field's segment {@value #SEGMENT} of a cell long along {@code (1, f)} (upright
 * where {@code f} is infinite); a vector field's arrow along {@code (P, Q)}, {@value #ARROW} of a
 * cell long at the cap and shorter in proportion below it, the cap the {@value #CAP_QUANTILE}
 * quantile of the magnitudes in view, so one huge magnitude near a singularity does not shrink the
 * rest to dots. Its weight on the colour ramp is its length's fraction of the longest. A point
 * where a component has no value, or where the field is zero, has no mark.
 */
public final class FieldGrid {

  /** The fewest columns. */
  public static final int MIN_DENSITY = 10;

  /** The most columns. */
  public static final int MAX_DENSITY = 40;

  /** The columns of a new field. */
  public static final int DEFAULT_DENSITY = 20;

  /** A slope field's segment, in cells. */
  static final double SEGMENT = 0.7;

  /** A vector field's arrow at the cap, in cells. */
  static final double ARROW = 0.9;

  /** The quantile of the magnitudes in view an arrow's length is capped at. */
  static final double CAP_QUANTILE = 0.9;

  private FieldGrid() {}

  /**
   * Checks a density.
   *
   * @param density the columns
   * @throws IllegalArgumentException if it is out of its range
   */
  static void checkDensity(int density) {
    if (density < MIN_DENSITY || density > MAX_DENSITY) {
      throw new IllegalArgumentException(
          "a field's density is " + MIN_DENSITY + " to " + MAX_DENSITY);
    }
  }

  /** The grid's points, the centres of its cells, and the cell's size. */
  record Grid(double[] xs, double[] ys, double cellWidth, double cellHeight) {}

  /**
   * The grid over a window.
   *
   * @param density the columns
   * @param w the window
   * @return the grid
   */
  static Grid grid(int density, Window w) {
    double cw = w.width() / density;
    int rows =
        (int) Math.max(1, Math.min(4L * density, Math.round(density * w.height() / w.width())));
    double ch = w.height() / rows;
    long i0 = (long) Math.ceil(w.minX() / cw - 0.5);
    long i1 = (long) Math.floor(w.maxX() / cw - 0.5);
    long j0 = (long) Math.ceil(w.minY() / ch - 0.5);
    long j1 = (long) Math.floor(w.maxY() / ch - 0.5);
    int nx = (int) Math.max(0, i1 - i0 + 1);
    int ny = (int) Math.max(0, j1 - j0 + 1);
    double[] xs = new double[nx * ny];
    double[] ys = new double[nx * ny];
    int k = 0;
    for (int j = 0; j < ny; j++) {
      for (int i = 0; i < nx; i++, k++) {
        xs[k] = (i0 + i + 0.5) * cw;
        ys[k] = (j0 + j + 0.5) * ch;
      }
    }
    return new Grid(xs, ys, cw, ch);
  }

  /**
   * A vector field's arrows over the window.
   *
   * @param field the field
   * @param sampler evaluates its components
   * @param window the visible window
   * @param cancellation checked as it goes
   * @return the geometry
   */
  static Geometry vectors(
      VectorField field, Sampler sampler, Window window, Cancellation cancellation) {
    Grid g = grid(field.density(), window);
    double[] p = sampler.plane(field.p(), g.xs(), g.ys(), cancellation);
    double[] q = sampler.plane(field.q(), g.xs(), g.ys(), cancellation);
    double[] magnitude = new double[p.length];
    double[] finite = new double[p.length];
    int n = 0;
    for (int k = 0; k < p.length; k++) {
      magnitude[k] = Math.hypot(p[k], q[k]);
      if (Double.isFinite(magnitude[k]) && magnitude[k] > 0) {
        finite[n++] = magnitude[k];
      }
    }
    List<Geometry.Mark> marks = new ArrayList<>();
    if (n > 0) {
      double[] sorted = Arrays.copyOf(finite, n);
      Arrays.sort(sorted);
      double cap = sorted[(int) Math.floor(CAP_QUANTILE * (n - 1))];
      for (int k = 0; k < p.length; k++) {
        double m = magnitude[k];
        if (Double.isFinite(m) && m > 0) {
          double r = Math.min(m / cap, 1);
          marks.add(mark(g, k, p[k], q[k], ARROW * r, r));
        }
      }
    }
    return new Geometry.Field(marks, true, Optional.empty());
  }

  /**
   * A slope field's segments over the window.
   *
   * @param field the field
   * @param sampler evaluates its slope
   * @param window the visible window
   * @param cancellation checked as it goes
   * @return the geometry
   */
  static Geometry slopes(
      SlopeField field, Sampler sampler, Window window, Cancellation cancellation) {
    Grid g = grid(field.density(), window);
    double[] f = sampler.plane(field.slope(), g.xs(), g.ys(), cancellation);
    List<Geometry.Mark> marks = new ArrayList<>();
    for (int k = 0; k < f.length; k++) {
      if (Double.isFinite(f[k])) {
        marks.add(mark(g, k, 1, f[k], SEGMENT, 1));
      } else if (Double.isInfinite(f[k])) {
        marks.add(mark(g, k, 0, 1, SEGMENT, 1));
      }
    }
    return new Geometry.Field(marks, false, Optional.of(field.slope()));
  }

  /**
   * A mark centred on a grid point along a world direction, of a length in cells: the direction is
   * measured in the cell's units, so its slope on the plane is kept and its length is the same in
   * every direction on a square view.
   */
  private static Geometry.Mark mark(
      Grid g, int k, double dx, double dy, double length, double weight) {
    double a = dx / g.cellWidth();
    double b = dy / g.cellHeight();
    double norm = Math.hypot(a, b);
    double hx = length / 2 * g.cellWidth() * a / norm;
    double hy = length / 2 * g.cellHeight() * b / norm;
    double x = g.xs()[k];
    double y = g.ys()[k];
    return new Geometry.Mark(
        new Geometry.Point(x - hx, y - hy), new Geometry.Point(x + hx, y + hy), weight);
  }
}
