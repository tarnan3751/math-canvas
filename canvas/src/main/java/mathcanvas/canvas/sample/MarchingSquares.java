package mathcanvas.canvas.sample;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.model.Window;
import mathcanvas.engine.Cancellation;

/**
 * Marching squares over a window (docs/reference/canvas.md, <b>Sampling</b>): a grid of {@value
 * #CELLS} by {@value #CELLS} cells, the field's zero placed on each cell edge by linear
 * interpolation, an ambiguous saddle resolved by the sign at the cell's centre, and every cell the
 * zero crosses refined once into {@value #SUB} by {@value #SUB} subcells. A refined cell's border
 * values are interpolated from its corners rather than evaluated, so its boundary crossings are its
 * neighbours' and the pieces join exactly; the interior is evaluated.
 *
 * <p>Every segment runs with the negative side on its left, and segments are joined into polylines
 * by the cell edge their ends lie on. A contour skips a cell with an undefined corner and drops a
 * segment where the field's magnitude at its midpoint exceeds every corner's, a pole the sign
 * changes across rather than a zero. A region (the negative side) is traced on the grid with a
 * border of positive values around it, so its boundary closes along the window's edge into rings,
 * inside on the left: under the even-odd and the non-zero rules alike the inside is the region. An
 * undefined value is outside the region.
 */
final class MarchingSquares {

  /** The cells along each side of the grid. */
  static final int CELLS = 128;

  /** The subcells along each side of a refined cell. */
  static final int SUB = 8;

  /** A field over the plane, NaN where it is undefined. */
  @FunctionalInterface
  interface Field {
    double at(double x, double y);
  }

  private static final long SUB_IDS = 1L << 32;
  private static final int SUB_EDGES = 2 * (SUB + 1) * (SUB + 1);

  private final Field field;
  private final boolean region;
  private final Cancellation cancellation;
  private final int cols;
  private final double[] gx;
  private final double[] gy;
  private final double[] values;

  private final List<long[]> ids = new ArrayList<>();
  private final List<double[]> points = new ArrayList<>();

  // scratch for one cell
  private final double[] cx = new double[4];
  private final double[] cy = new double[4];
  private final double[] cv = new double[4];
  private final long[] edgeIds = new long[4];
  private final double[] edgeX = new double[4];
  private final double[] edgeY = new double[4];
  private final double[] subValues = new double[(SUB + 1) * (SUB + 1)];

  private MarchingSquares(Field field, Window w, boolean region, Cancellation cancellation) {
    this.field = field;
    this.region = region;
    this.cancellation = cancellation;
    int pad = region ? 1 : 0;
    this.cols = CELLS + 2 * pad;
    this.gx = new double[cols + 1];
    this.gy = new double[cols + 1];
    for (int k = 0; k <= CELLS; k++) {
      gx[k + pad] = k == CELLS ? w.maxX() : w.minX() + w.width() * k / CELLS;
      gy[k + pad] = k == CELLS ? w.maxY() : w.minY() + w.height() * k / CELLS;
    }
    if (region) {
      gx[0] = w.minX();
      gy[0] = w.minY();
      gx[cols] = w.maxX();
      gy[cols] = w.maxY();
    }
    this.values = new double[(cols + 1) * (cols + 1)];
  }

  /**
   * The zero set of a field.
   *
   * @param field the field
   * @param window the window
   * @param cancellation checked once per grid row
   * @return the polylines, closed ones ending where they start
   */
  static List<Polyline> contour(Field field, Window window, Cancellation cancellation) {
    return new MarchingSquares(field, window, false, cancellation).run();
  }

  /**
   * The rings bounding where a field is negative.
   *
   * @param field the field
   * @param window the window
   * @param cancellation checked once per grid row
   * @return the closed rings, inside on the left
   */
  static List<Polyline> region(Field field, Window window, Cancellation cancellation) {
    return new MarchingSquares(field, window, true, cancellation).run();
  }

  private boolean padding(int i, int j) {
    return region && (i == 0 || j == 0 || i == cols || j == cols);
  }

  private List<Polyline> run() {
    for (int j = 0; j <= cols; j++) {
      cancellation.check();
      for (int i = 0; i <= cols; i++) {
        values[j * (cols + 1) + i] = padding(i, j) ? 1 : field.at(gx[i], gy[j]);
      }
    }
    for (int j = 0; j < cols; j++) {
      cancellation.check();
      for (int i = 0; i < cols; i++) {
        coarse(i, j);
      }
    }
    return join();
  }

  private double value(int i, int j) {
    return values[j * (cols + 1) + i];
  }

  private static boolean in(double v) {
    return v < 0;
  }

  /** The id of the edge from vertex (i, j) rightwards or upwards. */
  private long edge(int i, int j, boolean up) {
    return 2L * ((long) j * (cols + 1) + i) + (up ? 1 : 0);
  }

  private void coarse(int i, int j) {
    double v0 = value(i, j);
    double v1 = value(i + 1, j);
    double v2 = value(i + 1, j + 1);
    double v3 = value(i, j + 1);
    int mask = mask(v0, v1, v2, v3);
    if (mask == 0 || mask == 15) {
      return;
    }
    boolean finite =
        Double.isFinite(v0) && Double.isFinite(v1) && Double.isFinite(v2) && Double.isFinite(v3);
    if (!region && !finite) {
      return;
    }
    corners(gx[i], gx[i + 1], gy[j], gy[j + 1], v0, v1, v2, v3);
    long[] coarseIds = {
      edge(i, j, false), edge(i + 1, j, true), edge(i, j + 1, false), edge(i, j, true)
    };
    System.arraycopy(coarseIds, 0, edgeIds, 0, 4);
    crossings();
    boolean degenerate = !(gx[i] < gx[i + 1]) || !(gy[j] < gy[j + 1]);
    if (!finite || degenerate) {
      segments(mask);
      return;
    }
    refine(i, j, v0, v1, v2, v3, coarseIds, edgeX.clone(), edgeY.clone());
  }

  private static int mask(double v0, double v1, double v2, double v3) {
    return (in(v0) ? 1 : 0) | (in(v1) ? 2 : 0) | (in(v2) ? 4 : 0) | (in(v3) ? 8 : 0);
  }

  private void corners(
      double x0, double x1, double y0, double y1, double v0, double v1, double v2, double v3) {
    cx[0] = x0;
    cy[0] = y0;
    cx[1] = x1;
    cy[1] = y0;
    cx[2] = x1;
    cy[2] = y1;
    cx[3] = x0;
    cy[3] = y1;
    cv[0] = v0;
    cv[1] = v1;
    cv[2] = v2;
    cv[3] = v3;
  }

  /** The zero on each edge whose ends differ, from its left or lower end. */
  private void crossings() {
    for (int e = 0; e < 4; e++) {
      int a = e == 2 ? 3 : e == 3 ? 0 : e;
      int b = e == 2 ? 2 : e == 3 ? 3 : e + 1;
      if (in(cv[a]) == in(cv[b])) {
        continue;
      }
      double va = cv[a];
      double vb = cv[b];
      double t = Double.isFinite(va) && Double.isFinite(vb) ? va / (va - vb) : 0.5;
      edgeX[e] = cx[a] + t * (cx[b] - cx[a]);
      edgeY[e] = cy[a] + t * (cy[b] - cy[a]);
    }
  }

  /** Splits a crossed cell into subcells, interpolating its border and evaluating its interior. */
  private void refine(
      int i,
      int j,
      double v0,
      double v1,
      double v2,
      double v3,
      long[] coarseIds,
      double[] coarseX,
      double[] coarseY) {
    double x0 = gx[i];
    double x1 = gx[i + 1];
    double y0 = gy[j];
    double y1 = gy[j + 1];
    for (int b = 0; b <= SUB; b++) {
      double y = b == SUB ? y1 : y0 + (y1 - y0) * b / SUB;
      for (int a = 0; a <= SUB; a++) {
        double v;
        if (b == 0) {
          v = v0 + (v1 - v0) * a / SUB;
        } else if (b == SUB) {
          v = v3 + (v2 - v3) * a / SUB;
        } else if (a == 0) {
          v = v0 + (v3 - v0) * b / SUB;
        } else if (a == SUB) {
          v = v1 + (v2 - v1) * b / SUB;
        } else {
          v = field.at(a == SUB ? x1 : x0 + (x1 - x0) * a / SUB, y);
        }
        subValues[b * (SUB + 1) + a] = v;
      }
    }
    long base = SUB_IDS + ((long) j * cols + i) * SUB_EDGES;
    for (int b = 0; b < SUB; b++) {
      double ya = y0 + (y1 - y0) * b / SUB;
      double yb = b + 1 == SUB ? y1 : y0 + (y1 - y0) * (b + 1) / SUB;
      for (int a = 0; a < SUB; a++) {
        double xa = x0 + (x1 - x0) * a / SUB;
        double xb = a + 1 == SUB ? x1 : x0 + (x1 - x0) * (a + 1) / SUB;
        double w0 = subValues[b * (SUB + 1) + a];
        double w1 = subValues[b * (SUB + 1) + a + 1];
        double w2 = subValues[(b + 1) * (SUB + 1) + a + 1];
        double w3 = subValues[(b + 1) * (SUB + 1) + a];
        int mask = mask(w0, w1, w2, w3);
        if (mask == 0 || mask == 15) {
          continue;
        }
        if (!region
            && !(Double.isFinite(w0)
                && Double.isFinite(w1)
                && Double.isFinite(w2)
                && Double.isFinite(w3))) {
          continue;
        }
        corners(xa, xb, ya, yb, w0, w1, w2, w3);
        edgeIds[0] = base + 2L * (b * (SUB + 1) + a);
        edgeIds[1] = base + 2L * (b * (SUB + 1) + a + 1) + 1;
        edgeIds[2] = base + 2L * ((b + 1) * (SUB + 1) + a);
        edgeIds[3] = base + 2L * (b * (SUB + 1) + a) + 1;
        crossings();
        // an edge on the cell's border takes the cell's crossing, which its neighbour shares
        border(b == 0, 0, coarseIds, coarseX, coarseY);
        border(a + 1 == SUB, 1, coarseIds, coarseX, coarseY);
        border(b + 1 == SUB, 2, coarseIds, coarseX, coarseY);
        border(a == 0, 3, coarseIds, coarseX, coarseY);
        segments(mask);
      }
    }
  }

  private void border(boolean on, int e, long[] coarseIds, double[] coarseX, double[] coarseY) {
    if (on) {
      edgeIds[e] = coarseIds[e];
      edgeX[e] = coarseX[e];
      edgeY[e] = coarseY[e];
    }
  }

  /** The segments of the current cell, from its mask and, for a saddle, its centre. */
  private void segments(int mask) {
    if (mask == 5 || mask == 10) {
      boolean centre = in(field.at(0.5 * (cx[0] + cx[2]), 0.5 * (cy[0] + cy[2])));
      // cut off the corners of the class the centre is not in, each by its two edges
      boolean cutInside = !centre;
      boolean evenInside = mask == 5;
      if (cutInside == evenInside) {
        segment(3, 0);
        segment(1, 2);
      } else {
        segment(0, 1);
        segment(2, 3);
      }
      return;
    }
    int first = -1;
    for (int e = 0; e < 4; e++) {
      int a = e == 2 ? 3 : e == 3 ? 0 : e;
      int b = e == 2 ? 2 : e == 3 ? 3 : e + 1;
      if (in(cv[a]) != in(cv[b])) {
        if (first < 0) {
          first = e;
        } else {
          segment(first, e);
          return;
        }
      }
    }
  }

  /**
   * The segment between the zeros on two edges, the negative side on its left: it runs from the
   * edge where a walk round the cell, counterclockwise from corner to corner, leaves the negative
   * side to the edge where it enters it. Decided by the corners' signs alone, so a cell of no width
   * is oriented too.
   */
  private void segment(int e, int f) {
    double px = edgeX[e];
    double py = edgeY[e];
    double qx = edgeX[f];
    double qy = edgeY[f];
    boolean forward = in(cv[e]) && !in(cv[(e + 1) % 4]);
    if (!region) {
      double mid = field.at(0.5 * (px + qx), 0.5 * (py + qy));
      double most =
          Math.max(
              Math.max(Math.abs(cv[0]), Math.abs(cv[1])),
              Math.max(Math.abs(cv[2]), Math.abs(cv[3])));
      if (!(Math.abs(mid) <= most)) {
        return;
      }
    }
    if (forward) {
      ids.add(new long[] {edgeIds[e], edgeIds[f]});
      points.add(new double[] {px, py, qx, qy});
    } else {
      ids.add(new long[] {edgeIds[f], edgeIds[e]});
      points.add(new double[] {qx, qy, px, py});
    }
  }

  /** Joins the segments end to start by the edge they meet on. */
  private List<Polyline> join() {
    int n = ids.size();
    Map<Long, Integer> byStart = new HashMap<>(2 * n);
    Map<Long, Integer> byEnd = new HashMap<>(2 * n);
    for (int s = 0; s < n; s++) {
      byStart.put(ids.get(s)[0], s);
      byEnd.put(ids.get(s)[1], s);
    }
    boolean[] used = new boolean[n];
    List<Polyline> out = new ArrayList<>();
    double[] xs = new double[16];
    double[] ys = new double[16];
    for (int s = 0; s < n; s++) {
      if (used[s]) {
        continue;
      }
      int first = s;
      for (int steps = 0; steps < n; steps++) {
        Integer previous = byEnd.get(ids.get(first)[0]);
        if (previous == null || used[previous] || previous == s) {
          break;
        }
        first = previous;
      }
      int size = 0;
      xs[size] = points.get(first)[0];
      ys[size] = points.get(first)[1];
      size++;
      int at = first;
      while (true) {
        used[at] = true;
        double x = points.get(at)[2];
        double y = points.get(at)[3];
        if (x != xs[size - 1] || y != ys[size - 1]) {
          if (size == xs.length) {
            xs = Arrays.copyOf(xs, 2 * size);
            ys = Arrays.copyOf(ys, 2 * size);
          }
          xs[size] = x;
          ys[size] = y;
          size++;
        }
        Integer next = byStart.get(ids.get(at)[1]);
        if (next == null || used[next]) {
          break;
        }
        at = next;
      }
      out.add(Polyline.unparametrised(Arrays.copyOf(xs, size), Arrays.copyOf(ys, size)));
    }
    return out;
  }
}
