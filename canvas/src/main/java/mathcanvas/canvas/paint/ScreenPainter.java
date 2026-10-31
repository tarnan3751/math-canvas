package mathcanvas.canvas.paint;

import java.util.Arrays;
import mathcanvas.canvas.geom.Viewport;

/**
 * The part every backend shares: it keeps the path in world coordinates, clips it to the viewport
 * grown by {@link #MARGIN} pixels on every side, and hands only the clipped part, mapped to pixels,
 * to the backend's screen-space calls. Clipping is done in world coordinates, with differences
 * taken on halved values, so any finite point clips along its own segment, however far away: a
 * sampled asymptote at 10¹⁷, a construction line between points 10³⁰⁰ away or a spike to 10³⁰⁸
 * never reaches the backend as a huge or infinite pixel coordinate, and never changes direction.
 * Only a non-finite world value is a gap. Strokes are clipped segment by segment (Liang–Barsky), a
 * subpath leaving and re-entering the window becoming two; fills are clipped as polygons
 * (Sutherland–Hodgman), so a region keeps its shape. A clipped end is the segment's own end where
 * no edge cut it, and otherwise where it crosses the edge that did, interpolated from the segment's
 * point nearest the window, so neither end's distance costs it precision. A point or a text whose
 * anchor falls outside the grown window is not drawn.
 *
 * <p>Not thread-safe: a painter draws one frame on one thread.
 */
public abstract class ScreenPainter implements Painter {

  /** How far beyond the viewport, in pixels, drawing is kept before it is clipped. */
  public static final double MARGIN = 64;

  private final Viewport viewport;
  private final double xMin;
  private final double yMax;
  private final double pixelsPerUnitX;
  private final double pixelsPerUnitY;
  // The grown window in world units: x from left to right, y from low to high.
  private final double left;
  private final double right;
  private final double low;
  private final double high;
  private final double centreX;
  private final double centreY;
  private final double pixelLeft;
  private final double pixelTop;
  private final double pixelRight;
  private final double pixelBottom;

  private double[] xs = new double[256];
  private double[] ys = new double[256];
  private int size;
  private int[] starts = new int[16];
  private boolean[] closed = new boolean[16];
  private int subpaths;
  private boolean open;

  private double clipT0;
  private double clipT1;
  private int clipEdge0;
  private int clipEdge1;
  private double pointX;
  private double pointY;
  private double anchorX;
  private double anchorY;
  private double directionX;
  private double directionY;

  /**
   * A painter drawing through a viewport.
   *
   * @param viewport the viewport
   */
  protected ScreenPainter(Viewport viewport) {
    this.viewport = viewport;
    this.xMin = viewport.xMin();
    this.yMax = viewport.yMax();
    this.pixelsPerUnitX = viewport.pixelsPerUnitX();
    this.pixelsPerUnitY = viewport.pixelsPerUnitY();
    this.left = viewport.xMin() - MARGIN / pixelsPerUnitX;
    this.right = viewport.xMax() + MARGIN / pixelsPerUnitX;
    this.low = viewport.yMin() - MARGIN / pixelsPerUnitY;
    this.high = viewport.yMax() + MARGIN / pixelsPerUnitY;
    this.centreX = viewport.xMin() / 2 + viewport.xMax() / 2;
    this.centreY = viewport.yMin() / 2 + viewport.yMax() / 2;
    this.pixelLeft = -MARGIN;
    this.pixelTop = -MARGIN;
    this.pixelRight = viewport.width() + MARGIN;
    this.pixelBottom = viewport.height() + MARGIN;
  }

  @Override
  public final Viewport viewport() {
    return viewport;
  }

  @Override
  public final void beginPath() {
    size = 0;
    subpaths = 0;
    open = false;
  }

  @Override
  public final void moveTo(double x, double y) {
    open = false;
    lineTo(x, y);
  }

  @Override
  public final void lineTo(double x, double y) {
    if (!Double.isFinite(x) || !Double.isFinite(y)) {
      open = false;
      return;
    }
    if (!open) {
      if (subpaths == starts.length) {
        starts = Arrays.copyOf(starts, subpaths * 2);
        closed = Arrays.copyOf(closed, subpaths * 2);
      }
      starts[subpaths] = size;
      closed[subpaths] = false;
      subpaths++;
      open = true;
    }
    if (size == xs.length) {
      xs = Arrays.copyOf(xs, size * 2);
      ys = Arrays.copyOf(ys, size * 2);
    }
    xs[size] = x;
    ys[size] = y;
    size++;
  }

  @Override
  public final void polyline(double[] px, double[] py, int n) {
    open = false;
    for (int i = 0; i < n; i++) {
      lineTo(px[i], py[i]);
    }
  }

  @Override
  public final void closePath() {
    if (subpaths > 0 && open) {
      closed[subpaths - 1] = true;
    }
    open = false;
  }

  @Override
  public final void stroke(Stroke stroke) {
    beginScreenPath();
    for (int k = 0; k < subpaths; k++) {
      strokeSubpath(k, stroke.width());
    }
    strokeScreenPath(stroke);
  }

  @Override
  public final void fill(Rgba colour) {
    beginScreenPath();
    for (int k = 0; k < subpaths; k++) {
      fillSubpath(k);
    }
    fillScreenPath(colour);
  }

  @Override
  public final void point(double x, double y, double radius, Rgba colour) {
    double sx = screenX(x);
    double sy = screenY(y);
    if (insidePixels(sx, sy)) {
      pointScreen(sx, sy, radius, colour);
    }
  }

  @Override
  public final void text(
      String text, double x, double y, double dx, double dy, Anchor anchor, TextStyle style) {
    double sx = screenX(x) + dx;
    double sy = screenY(y) + dy;
    if (insidePixels(sx, sy)) {
      textScreen(text, sx, sy, anchor, style);
    }
  }

  /** Starts the backend's path; the subpaths of one stroke or fill follow. */
  protected abstract void beginScreenPath();

  /**
   * Starts a subpath.
   *
   * @param x pixels from the left
   * @param y pixels from the top
   */
  protected abstract void moveToScreen(double x, double y);

  /**
   * Extends the subpath.
   *
   * @param x pixels from the left
   * @param y pixels from the top
   */
  protected abstract void lineToScreen(double x, double y);

  /** Closes the subpath. */
  protected abstract void closeScreenSubpath();

  /**
   * Strokes the backend's path.
   *
   * @param stroke the stroke
   */
  protected abstract void strokeScreenPath(Stroke stroke);

  /**
   * Fills the backend's path.
   *
   * @param colour the colour
   */
  protected abstract void fillScreenPath(Rgba colour);

  /**
   * Draws a disc.
   *
   * @param x the centre, pixels from the left
   * @param y the centre, pixels from the top
   * @param radius the radius in pixels
   * @param colour the colour
   */
  protected abstract void pointScreen(double x, double y, double radius, Rgba colour);

  /**
   * Draws text.
   *
   * @param text the text
   * @param x the anchor, pixels from the left
   * @param y the anchor, pixels from the top
   * @param anchor which point of the text sits there
   * @param style the size and colour
   */
  protected abstract void textScreen(
      String text, double x, double y, Anchor anchor, TextStyle style);

  /**
   * Where the constant coordinate of a horizontal or vertical two-point line goes, for a backend
   * that aligns such lines with its pixel grid; the identity by default.
   *
   * @param coordinate the coordinate in pixels
   * @param width the stroke width in pixels
   * @return the coordinate to draw at
   */
  protected double snap(double coordinate, double width) {
    return coordinate;
  }

  private void strokeSubpath(int k, double width) {
    int from = starts[k];
    int to = k + 1 < subpaths ? starts[k + 1] : size;
    int n = to - from;
    if (n < 2) {
      return;
    }
    if (n == 2 && !closed[k]) {
      double x0 = xs[from];
      double y0 = ys[from];
      double x1 = xs[from + 1];
      double y1 = ys[from + 1];
      if (clip(x0, y0, x1, y1)) {
        clippedStart(x0, y0, x1, y1);
        double ax = screenX(pointX);
        double ay = screenY(pointY);
        clippedEnd(x0, y0, x1, y1);
        double bx = screenX(pointX);
        double by = screenY(pointY);
        if (x0 == x1) {
          ax = bx = snap(ax, width);
        } else if (y0 == y1) {
          ay = by = snap(ay, width);
        }
        moveToScreen(ax, ay);
        lineToScreen(bx, by);
      }
      return;
    }
    boolean allInside = true;
    for (int i = from; i < to && allInside; i++) {
      allInside = inside(xs[i], ys[i]);
    }
    if (allInside) {
      moveToScreen(screenX(xs[from]), screenY(ys[from]));
      for (int i = from + 1; i < to; i++) {
        lineToScreen(screenX(xs[i]), screenY(ys[i]));
      }
      if (closed[k]) {
        closeScreenSubpath();
      }
      return;
    }
    boolean penThere = false;
    int segments = closed[k] ? n : n - 1;
    for (int s = 0; s < segments; s++) {
      int i = from + s;
      int j = s + 1 < n ? i + 1 : from;
      if (!clip(xs[i], ys[i], xs[j], ys[j])) {
        penThere = false;
        continue;
      }
      if (!penThere || clipEdge0 >= 0) {
        clippedStart(xs[i], ys[i], xs[j], ys[j]);
        moveToScreen(screenX(pointX), screenY(pointY));
      }
      clippedEnd(xs[i], ys[i], xs[j], ys[j]);
      lineToScreen(screenX(pointX), screenY(pointY));
      penThere = clipEdge1 < 0;
    }
  }

  private void fillSubpath(int k) {
    int from = starts[k];
    int to = k + 1 < subpaths ? starts[k + 1] : size;
    int n = to - from;
    if (n < 3) {
      return;
    }
    double[] px = Arrays.copyOfRange(xs, from, to);
    double[] py = Arrays.copyOfRange(ys, from, to);
    for (int edge = 0; edge < 4 && n > 0; edge++) {
      double[] qx = new double[2 * n];
      double[] qy = new double[2 * n];
      int m = 0;
      for (int i = 0; i < n; i++) {
        int j = (i + 1) % n;
        boolean inI = keeps(edge, px[i], py[i]);
        boolean inJ = keeps(edge, px[j], py[j]);
        if (inI) {
          qx[m] = px[i];
          qy[m] = py[i];
          m++;
        }
        if (inI != inJ) {
          crossingPoint(edge, px[i], py[i], px[j], py[j]);
          qx[m] = pointX;
          qy[m] = pointY;
          m++;
        }
      }
      px = qx;
      py = qy;
      n = m;
    }
    if (n < 3) {
      return;
    }
    moveToScreen(screenX(px[0]), screenY(py[0]));
    for (int i = 1; i < n; i++) {
      lineToScreen(screenX(px[i]), screenY(py[i]));
    }
    closeScreenSubpath();
  }

  private double screenX(double x) {
    return (x - xMin) * pixelsPerUnitX;
  }

  private double screenY(double y) {
    return (yMax - y) * pixelsPerUnitY;
  }

  private boolean keeps(int edge, double x, double y) {
    return switch (edge) {
      case 0 -> x >= left;
      case 1 -> x <= right;
      case 2 -> y >= low;
      default -> y <= high;
    };
  }

  /**
   * The start of the clipped segment into {@code pointX}, {@code pointY}: its own first end when no
   * edge cut it, otherwise where it crosses the edge that did. The edge, not the parameter,
   * decides, since a parameter of 1 − 10⁻³⁰⁰ rounds to 1.
   */
  private void clippedStart(double x0, double y0, double x1, double y1) {
    if (clipEdge0 < 0) {
      pointX = x0;
      pointY = y0;
    } else {
      crossingPoint(clipEdge0, x0, y0, x1, y1);
    }
  }

  /** The end of the clipped segment, as {@link #clippedStart} for the second end. */
  private void clippedEnd(double x0, double y0, double x1, double y1) {
    if (clipEdge1 < 0) {
      pointX = x1;
      pointY = y1;
    } else {
      crossingPoint(clipEdge1, x0, y0, x1, y1);
    }
  }

  /**
   * Where a segment crosses an edge's line, into {@code pointX}, {@code pointY}: the coordinate
   * along the edge's normal is the edge itself, and the other is interpolated from the segment's
   * point nearest the window's centre ({@link #anchor}), so neither end's distance costs it
   * precision. The point is inside the grown window, so nothing here overflows.
   */
  private void crossingPoint(int edge, double x0, double y0, double x1, double y1) {
    anchor(x0, y0, x1, y1);
    if (edge < 2) {
      double e = edge == 0 ? left : right;
      pointX = e;
      pointY = anchorY + (e - anchorX) * (directionY / directionX);
    } else {
      double e = edge == 2 ? low : high;
      pointY = e;
      pointX = anchorX + (e - anchorY) * (directionX / directionY);
    }
  }

  /**
   * The segment's point nearest the window's centre into {@code anchorX}, {@code anchorY}, and its
   * direction, scaled, into {@code directionX}, {@code directionY}. The coordinates are first
   * scaled by a power of two (exactly) to below 2 in magnitude. When the centre projects beyond an
   * end, the anchor is that end itself. Otherwise it is the foot of the perpendicular from the
   * centre to the line {@code dy·x − dx·y = x0·y1 − y0·x1}, whose constant is computed with fused
   * multiply-adds as a compensated difference of products: when the line passes near the window,
   * the constant is small and comes out nearly exact however far the ends are (y = x/2 between
   * ±1e300 is not rounded to y = 0), and the direction's own rounding moves the line only by a
   * relative error of the window's own coordinates.
   */
  private void anchor(double x0, double y0, double x1, double y1) {
    double largest =
        Math.max(
            Math.max(Math.abs(x0), Math.abs(y0)),
            Math.max(Math.max(Math.abs(x1), Math.abs(y1)), Math.max(Math.abs(centreX), 1)));
    double scale = Math.scalb(1.0, Math.getExponent(largest));
    double sx0 = x0 / scale;
    double sy0 = y0 / scale;
    double sx1 = x1 / scale;
    double sy1 = y1 / scale;
    double cx = centreX / scale;
    double cy = centreY / scale;
    double dx = sx1 - sx0;
    double dy = sy1 - sy0;
    directionX = dx;
    directionY = dy;
    if ((cx - sx0) * dx + (cy - sy0) * dy <= 0) {
      anchorX = x0;
      anchorY = y0;
      return;
    }
    if ((cx - sx1) * dx + (cy - sy1) * dy >= 0) {
      anchorX = x1;
      anchorY = y1;
      return;
    }
    double product = sy0 * sx1;
    double constant = Math.fma(sx0, sy1, -product) - Math.fma(sy0, sx1, -product);
    double offset = (dy * cx - dx * cy - constant) / (dx * dx + dy * dy);
    anchorX = (cx - offset * dy) * scale;
    anchorY = (cy + offset * dx) * scale;
  }

  private boolean inside(double x, double y) {
    return x >= left && x <= right && y >= low && y <= high;
  }

  private boolean insidePixels(double x, double y) {
    return x >= pixelLeft && x <= pixelRight && y >= pixelTop && y <= pixelBottom;
  }

  /**
   * Liang–Barsky in world units: whether part of a segment is inside the grown window, and which
   * edge, if any, cut each end ({@code clipEdge0}, {@code clipEdge1}). Parameters run along the
   * segment from its point nearest the window ({@link #anchor}), so the edges' parameters are small
   * and exact enough to tell apart even when both ends are 10³⁰⁵ away, where parameters from an end
   * would all round to the same value.
   */
  private boolean clip(double x0, double y0, double x1, double y1) {
    anchor(x0, y0, x1, y1);
    boolean byX = Math.abs(directionX) >= Math.abs(directionY);
    clipT0 = byX ? (x0 - anchorX) / directionX : (y0 - anchorY) / directionY;
    clipT1 = byX ? (x1 - anchorX) / directionX : (y1 - anchorY) / directionY;
    clipEdge0 = -1;
    clipEdge1 = -1;
    return clipAxis(directionX, anchorX, left, right, 0)
        && clipAxis(directionY, anchorY, low, high, 2)
        && clipT0 <= clipT1;
  }

  /**
   * One axis of Liang–Barsky: the parameters where the segment crosses {@code from} and {@code to}
   * (edges {@code edge} and {@code edge + 1}) narrow the clipped range.
   */
  private boolean clipAxis(double d, double at, double from, double to, int edge) {
    if (d == 0) {
      return at >= from && at <= to;
    }
    double sFrom = (from - at) / d;
    double sTo = (to - at) / d;
    double enter = Math.min(sFrom, sTo);
    double leave = Math.max(sFrom, sTo);
    int enterEdge = sFrom <= sTo ? edge : edge + 1;
    if (enter > clipT0) {
      clipT0 = enter;
      clipEdge0 = enterEdge;
    }
    if (leave < clipT1) {
      clipT1 = leave;
      clipEdge1 = enterEdge == edge ? edge + 1 : edge;
    }
    return true;
  }

  /**
   * How far a box at {@code (left, top)} in pixels must move to lie inside the view, as {@code {dx,
   * dy}}: a value label's box is clamped to the view's edges, so a region mostly off the screen
   * still shows its value; a box larger than the view keeps its left and top edges in view.
   *
   * @param left the box's left
   * @param top the box's top
   * @param width the box's width
   * @param height the box's height
   * @return the shift
   */
  protected final double[] inside(double left, double top, double width, double height) {
    double dx = Math.min(0, viewport.width() - (left + width));
    double dy = Math.min(0, viewport.height() - (top + height));
    dx = Math.max(dx, -left);
    dy = Math.max(dy, -top);
    return new double[] {dx, dy};
  }
}
