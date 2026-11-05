package mathcanvas.canvas.hit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import mathcanvas.canvas.geom.Viewport;

/**
 * World-coordinate hit testing over points, polylines and areas: the nearest point or polyline to a
 * query, measured in pixels, within a tolerance, else an area within it. Picking is mathematical,
 * the nearest point on a curve found by projecting onto its segments, not by the shape of what was
 * drawn.
 *
 * <p>Distances are in pixels through the viewport's scale on each axis, so with the aspect unlocked
 * a pixel circle around the query, not a world circle, is what counts; the projection onto a
 * segment is done in pixel space for the same reason. A point within the tolerance wins over any
 * polyline, and a polyline over any area; among points, among polylines or among areas, the nearest
 * wins, ties going to the lower item and then the lower segment.
 *
 * <p>An area (a histogram's bar, a box plot's box) is a rectangle in world coordinates, reaching a
 * number of pixels above and below its world extent so that a box drawn a fixed number of pixels
 * thick is hit where it is drawn; a query inside it is at distance 0 and one outside at its
 * distance in pixels from the rectangle.
 *
 * <p>The index is a static R-tree bulk-loaded by sort-tile-recursive packing over the segments' and
 * points' bounding boxes, sixteen to a node. A uniform grid was the alternative; a sampled
 * asymptote's segment spanning 10¹⁰ units either crowds every other segment into one cell or lands
 * in thousands, while packing needs no tuning to the distribution. Segments with a non-finite end
 * (the gaps of a sampled function) are left out, and an entry whose distance in pixels overflows to
 * a value that is not a number is never a hit. Immutable once built, so a worker builds it with the
 * polylines and hands both to the view; rebuilt whenever they change.
 */
public final class HitIndex {

  /** The default tolerance, in pixels. */
  public static final double DEFAULT_TOLERANCE = 6;

  /** An index of nothing. */
  public static final HitIndex EMPTY = new Builder().build();

  private static final int NODE = 16;

  // Entries (level 0), in packed order: a point has segment -1 and its coordinates in x0, y0; an
  // area has segment -2, its corners in x0, y0 and x1, y1 and its reach in pixels in pad.
  private static final int AREA = -2;
  private final int[] item;
  private final int[] segment;
  private final double[] x0;
  private final double[] y0;
  private final double[] x1;
  private final double[] y1;
  private final double[] pad;
  // the greatest pad, by which a query reaches further in y to find the areas it is within
  private final double maxPad;
  // Levels of nodes above the entries, from the entries' parents up to the top level (at most
  // sixteen nodes, scanned by every query): node i's children are the entries or nodes
  // start[i] .. end[i] - 1 of the level below.
  private final List<Level> levels;

  private record Level(
      double[] minX, double[] minY, double[] maxX, double[] maxY, int[] start, int[] end) {
    int size() {
      return start.length;
    }
  }

  private HitIndex(
      int[] item,
      int[] segment,
      double[] x0,
      double[] y0,
      double[] x1,
      double[] y1,
      double[] pad,
      List<Level> levels) {
    this.item = item;
    this.segment = segment;
    this.x0 = x0;
    this.y0 = y0;
    this.x1 = x1;
    this.y1 = y1;
    this.pad = pad;
    double most = 0;
    for (double p : pad) {
      most = Math.max(most, p);
    }
    this.maxPad = most;
    this.levels = levels;
  }

  /**
   * A builder.
   *
   * @return an empty builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * The number of indexed points, segments and areas.
   *
   * @return the count
   */
  public int size() {
    return item.length;
  }

  /**
   * The nearest point within the tolerance, or else the nearest polyline within it, or else the
   * nearest area.
   *
   * @param x the query's world x
   * @param y the query's world y
   * @param viewport the viewport whose scale turns world distances into pixels
   * @param tolerance the largest distance in pixels
   * @return the hit, or empty when nothing is within the tolerance
   */
  public Optional<Hit> nearest(double x, double y, Viewport viewport, double tolerance) {
    Query q = new Query(x, y, viewport.pixelsPerUnitX(), viewport.pixelsPerUnitY(), tolerance);
    for (int node = 0; node < levels.getLast().size(); node++) {
      q.descend(levels.size() - 1, node);
    }
    return q.best();
  }

  /** The same query by a scan of every entry, without the tree: the tests' reference. */
  Optional<Hit> nearestByScan(double x, double y, Viewport viewport, double tolerance) {
    Query q = new Query(x, y, viewport.pixelsPerUnitX(), viewport.pixelsPerUnitY(), tolerance);
    for (int e = 0; e < item.length; e++) {
      q.consider(e);
    }
    return q.best();
  }

  private final class Query {
    final double qx;
    final double qy;
    final double sx;
    final double sy;
    final double tolerance;
    final double minX;
    final double maxX;
    final double minY;
    final double maxY;
    Hit pointBest;
    Hit lineBest;
    Hit areaBest;

    Query(double qx, double qy, double sx, double sy, double tolerance) {
      this.qx = qx;
      this.qy = qy;
      this.sx = sx;
      this.sy = sy;
      this.tolerance = tolerance;
      this.minX = qx - tolerance / sx;
      this.maxX = qx + tolerance / sx;
      this.minY = qy - (tolerance + maxPad) / sy;
      this.maxY = qy + (tolerance + maxPad) / sy;
    }

    Optional<Hit> best() {
      return Optional.ofNullable(
          pointBest != null ? pointBest : lineBest != null ? lineBest : areaBest);
    }

    void descend(int level, int node) {
      Level l = levels.get(level);
      if (l.minX[node] > maxX
          || l.maxX[node] < minX
          || l.minY[node] > maxY
          || l.maxY[node] < minY) {
        return;
      }
      for (int c = l.start[node]; c < l.end[node]; c++) {
        if (level == 0) {
          consider(c);
        } else {
          descend(level - 1, c);
        }
      }
    }

    void consider(int e) {
      Hit h =
          segment[e] == AREA
              ? areaHit(item[e], x0[e], y0[e], x1[e], y1[e], pad[e], qx, qy, sx, sy)
              : segment[e] < 0
                  ? pointHit(item[e], x0[e], y0[e], qx, qy, sx, sy)
                  : segmentHit(item[e], segment[e], x0[e], y0[e], x1[e], y1[e], qx, qy, sx, sy);
      // A distance that is not a number (a segment whose pixel extent overflows) is no hit.
      if (!(h.distance() <= tolerance)) {
        return;
      }
      if (h.kind() == Hit.Kind.POINT) {
        if (pointBest == null || before(h, pointBest)) {
          pointBest = h;
        }
      } else if (h.kind() == Hit.Kind.POLYLINE) {
        if (lineBest == null || before(h, lineBest)) {
          lineBest = h;
        }
      } else if (areaBest == null || before(h, areaBest)) {
        areaBest = h;
      }
    }
  }

  /** Nearer first, then the lower item, then the lower segment. */
  static boolean before(Hit a, Hit b) {
    if (a.distance() != b.distance()) {
      return a.distance() < b.distance();
    }
    if (a.item() != b.item()) {
      return a.item() < b.item();
    }
    return a.segment() < b.segment();
  }

  /** The distance in pixels from a query to a point, at pixel scales {@code sx}, {@code sy}. */
  static Hit pointHit(int item, double px, double py, double qx, double qy, double sx, double sy) {
    double d = Math.hypot((qx - px) * sx, (qy - py) * sy);
    return new Hit(item, Hit.Kind.POINT, -1, 0, px, py, d);
  }

  /** The nearest point of a segment to a query, projected in pixel space. */
  static Hit segmentHit(
      int item,
      int segment,
      double ax,
      double ay,
      double bx,
      double by,
      double qx,
      double qy,
      double sx,
      double sy) {
    double dx = (bx - ax) * sx;
    double dy = (by - ay) * sy;
    double wx = (qx - ax) * sx;
    double wy = (qy - ay) * sy;
    double length2 = dx * dx + dy * dy;
    double t = length2 == 0 ? 0 : Math.clamp((wx * dx + wy * dy) / length2, 0.0, 1.0);
    double d = Math.hypot(wx - t * dx, wy - t * dy);
    return new Hit(item, Hit.Kind.POLYLINE, segment, t, ax + t * (bx - ax), ay + t * (by - ay), d);
  }

  /**
   * The nearest point of an area to a query, in pixels: 0 inside it. The area is the rectangle from
   * ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}), {@code x0 ≤ x1} and {@code y0 ≤ y1},
   * reaching {@code pad} pixels further above and below.
   */
  static Hit areaHit(
      int item,
      double x0,
      double y0,
      double x1,
      double y1,
      double pad,
      double qx,
      double qy,
      double sx,
      double sy) {
    double x = Math.clamp(qx, x0, x1);
    double y = Math.clamp(qy, y0 - pad / sy, y1 + pad / sy);
    double d = Math.hypot((qx - x) * sx, (qy - y) * sy);
    return new Hit(item, Hit.Kind.AREA, -1, 0, x, y, d);
  }

  /** Collects points, polylines and areas, then packs them. Not thread-safe. */
  public static final class Builder {
    private final List<double[]> entries = new ArrayList<>();

    private Builder() {}

    /**
     * Adds a point.
     *
     * @param item the point's number, returned in its hits
     * @param x the world x
     * @param y the world y
     * @return this builder
     */
    public Builder point(int item, double x, double y) {
      if (Double.isFinite(x) && Double.isFinite(y)) {
        entries.add(new double[] {item, -1, x, y, x, y, 0});
      }
      return this;
    }

    /**
     * Adds the segments of a polyline, leaving out those with a non-finite end.
     *
     * @param item the polyline's number, returned in its hits
     * @param xs the world xs
     * @param ys the world ys
     * @param n the number of points to read from each array
     * @return this builder
     */
    public Builder polyline(int item, double[] xs, double[] ys, int n) {
      for (int i = 0; i + 1 < n; i++) {
        if (Double.isFinite(xs[i])
            && Double.isFinite(ys[i])
            && Double.isFinite(xs[i + 1])
            && Double.isFinite(ys[i + 1])) {
          entries.add(new double[] {item, i, xs[i], ys[i], xs[i + 1], ys[i + 1], 0});
        }
      }
      return this;
    }

    /**
     * Adds an area: a rectangle, hit anywhere inside it, reaching a number of pixels further above
     * and below; left out when a corner is not finite.
     *
     * @param item the area's number, returned in its hits
     * @param x0 one world x of the rectangle
     * @param y0 one world y
     * @param x1 the other world x
     * @param y1 the other world y
     * @param pad how many pixels the area reaches above and below the rectangle, at least 0
     * @return this builder
     */
    public Builder area(int item, double x0, double y0, double x1, double y1, double pad) {
      if (Double.isFinite(x0)
          && Double.isFinite(y0)
          && Double.isFinite(x1)
          && Double.isFinite(y1)
          && pad >= 0) {
        entries.add(
            new double[] {
              item,
              AREA,
              Math.min(x0, x1),
              Math.min(y0, y1),
              Math.max(x0, x1),
              Math.max(y0, y1),
              pad
            });
      }
      return this;
    }

    /**
     * Packs the index.
     *
     * @return the index
     */
    public HitIndex build() {
      int n = entries.size();
      double[] minX = new double[n];
      double[] minY = new double[n];
      double[] maxX = new double[n];
      double[] maxY = new double[n];
      for (int e = 0; e < n; e++) {
        double[] r = entries.get(e);
        minX[e] = Math.min(r[2], r[4]);
        maxX[e] = Math.max(r[2], r[4]);
        minY[e] = Math.min(r[3], r[5]);
        maxY[e] = Math.max(r[3], r[5]);
      }
      int[] order = strOrder(minX, minY, maxX, maxY, n);
      int[] item = new int[n];
      int[] segment = new int[n];
      double[] x0 = new double[n];
      double[] y0 = new double[n];
      double[] x1 = new double[n];
      double[] y1 = new double[n];
      double[] pad = new double[n];
      for (int k = 0; k < n; k++) {
        double[] r = entries.get(order[k]);
        item[k] = (int) r[0];
        segment[k] = (int) r[1];
        x0[k] = r[2];
        y0[k] = r[3];
        x1[k] = r[4];
        y1[k] = r[5];
        pad[k] = r[6];
      }
      minX = permute(minX, order);
      minY = permute(minY, order);
      maxX = permute(maxX, order);
      maxY = permute(maxY, order);
      List<Level> levels = new ArrayList<>();
      int count = n;
      while (true) {
        Level level = group(minX, minY, maxX, maxY, count);
        if (level.size() <= NODE) {
          levels.add(level);
          break;
        }
        // Order these nodes for packing into the level above; each keeps its own children.
        level =
            reorder(level, strOrder(level.minX, level.minY, level.maxX, level.maxY, level.size()));
        levels.add(level);
        minX = level.minX;
        minY = level.minY;
        maxX = level.maxX;
        maxY = level.maxY;
        count = level.size();
      }
      return new HitIndex(item, segment, x0, y0, x1, y1, pad, List.copyOf(levels));
    }
  }

  /** Groups consecutive runs of up to sixteen children into nodes with their bounding boxes. */
  private static Level group(
      double[] minX, double[] minY, double[] maxX, double[] maxY, int count) {
    int nodes = (count + NODE - 1) / NODE;
    double[] nMinX = new double[nodes];
    double[] nMinY = new double[nodes];
    double[] nMaxX = new double[nodes];
    double[] nMaxY = new double[nodes];
    int[] start = new int[nodes];
    int[] end = new int[nodes];
    for (int p = 0; p < nodes; p++) {
      start[p] = p * NODE;
      end[p] = Math.min(count, start[p] + NODE);
      nMinX[p] = Double.POSITIVE_INFINITY;
      nMinY[p] = Double.POSITIVE_INFINITY;
      nMaxX[p] = Double.NEGATIVE_INFINITY;
      nMaxY[p] = Double.NEGATIVE_INFINITY;
      for (int c = start[p]; c < end[p]; c++) {
        nMinX[p] = Math.min(nMinX[p], minX[c]);
        nMinY[p] = Math.min(nMinY[p], minY[c]);
        nMaxX[p] = Math.max(nMaxX[p], maxX[c]);
        nMaxY[p] = Math.max(nMaxY[p], maxY[c]);
      }
    }
    return new Level(nMinX, nMinY, nMaxX, nMaxY, start, end);
  }

  /** A level's nodes in a new order, each keeping its own children. */
  private static Level reorder(Level level, int[] order) {
    int[] start = new int[order.length];
    int[] end = new int[order.length];
    for (int k = 0; k < order.length; k++) {
      start[k] = level.start[order[k]];
      end[k] = level.end[order[k]];
    }
    return new Level(
        permute(level.minX, order),
        permute(level.minY, order),
        permute(level.maxX, order),
        permute(level.maxY, order),
        start,
        end);
  }

  /**
   * Sort-tile-recursive order: by centre x into vertical slices of about √(count / 16) × 16 boxes,
   * each slice by centre y.
   */
  private static int[] strOrder(
      double[] minX, double[] minY, double[] maxX, double[] maxY, int count) {
    Integer[] order = new Integer[count];
    for (int i = 0; i < count; i++) {
      order[i] = i;
    }
    Arrays.sort(order, Comparator.comparingDouble(i -> minX[i] / 2 + maxX[i] / 2));
    int leaves = (count + NODE - 1) / NODE;
    int slices = (int) Math.ceil(Math.sqrt(leaves));
    int perSlice = slices * NODE;
    for (int from = 0; from < count; from += perSlice) {
      Arrays.sort(
          order,
          from,
          Math.min(count, from + perSlice),
          Comparator.comparingDouble(i -> minY[i] / 2 + maxY[i] / 2));
    }
    int[] out = new int[count];
    for (int i = 0; i < count; i++) {
      out[i] = order[i];
    }
    return out;
  }

  private static double[] permute(double[] values, int[] order) {
    double[] out = new double[order.length];
    for (int k = 0; k < order.length; k++) {
      out[k] = values[order[k]];
    }
    return out;
  }
}
