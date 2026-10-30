package mathcanvas.canvas.paint;

import java.util.function.ToDoubleFunction;
import mathcanvas.canvas.geom.Ticks;
import mathcanvas.canvas.geom.Viewport;

/**
 * Draws the coordinate plane's grid, axes and tick labels through a {@link Painter}, so the screen
 * and an SVG export draw the same grid: the minor grid, then the major grid at the labelled ticks
 * ({@link Ticks}), then the axes, then the labels.
 *
 * <p>The x labels hang below the x axis, centred on their ticks, and the y labels sit left of the y
 * axis, centred on theirs; a label of zero is written once, below and left of the origin. When an
 * axis is off screen, or so near an edge that its labels would leave the view, its labels are
 * pinned inside that edge.
 */
public final class GridPainter {

  /** The label font size in pixels. */
  public static final double LABEL_SIZE = 12;

  private static final double GAP = 4;
  private static final double Y_LABEL_ROOM = 48;
  private static final double LINE_WIDTH = 1;
  private static final double EDGE = 12;

  private GridPainter() {}

  /**
   * Draws the grid, the axes and the labels, label widths estimated from their length ({@link
   * Ticks#estimate}).
   *
   * @param p the painter
   * @param theme the colours
   */
  public static void paint(Painter p, CanvasTheme theme) {
    paint(p, theme, Ticks.estimate(LABEL_SIZE));
  }

  /**
   * Draws the grid, the axes and the labels, the x axis's step wide enough for its labels as
   * measured (the y axis's labels, one line each, fit any step).
   *
   * @param p the painter
   * @param theme the colours
   * @param labelWidth a label's width in pixels at {@link #LABEL_SIZE}
   */
  public static void paint(Painter p, CanvasTheme theme, ToDoubleFunction<String> labelWidth) {
    Viewport v = p.viewport();
    Ticks tx = Ticks.forAxis(v.xMin(), v.xMax(), v.width(), labelWidth);
    Ticks ty = Ticks.forAxis(v.yMin(), v.yMax(), v.height(), label -> LABEL_SIZE);

    p.beginPath();
    minorLines(p, tx, v.xMin(), v.xMax(), v.yMin(), v.yMax(), true);
    minorLines(p, ty, v.yMin(), v.yMax(), v.xMin(), v.xMax(), false);
    p.stroke(Stroke.solid(theme.minorGrid(), LINE_WIDTH));

    p.beginPath();
    for (long i = tx.first(v.xMin()); i <= tx.last(v.xMax()); i++) {
      if (i != 0) {
        line(p, tx.value(i), v.yMin(), v.yMax(), true);
      }
    }
    for (long i = ty.first(v.yMin()); i <= ty.last(v.yMax()); i++) {
      if (i != 0) {
        line(p, ty.value(i), v.xMin(), v.xMax(), false);
      }
    }
    p.stroke(Stroke.solid(theme.grid(), LINE_WIDTH));

    boolean xAxis = v.yMin() <= 0 && 0 <= v.yMax();
    boolean yAxis = v.xMin() <= 0 && 0 <= v.xMax();
    p.beginPath();
    if (xAxis) {
      line(p, 0, v.xMin(), v.xMax(), false);
    }
    if (yAxis) {
      line(p, 0, v.yMin(), v.yMax(), true);
    }
    p.stroke(Stroke.solid(theme.axis(), LINE_WIDTH));

    TextStyle style = new TextStyle(LABEL_SIZE, theme.text());
    xLabels(p, tx, style);
    yLabels(p, ty, style);
    if (xAxis && yAxis) {
      p.text("0", 0, 0, -GAP, GAP, new Anchor(Anchor.Horizontal.END, Anchor.Vertical.TOP), style);
    }
  }

  private static void minorLines(
      Painter p, Ticks t, double min, double max, double from, double to, boolean vertical) {
    double minor = t.minorStep();
    long perMajor = t.mantissa() == 2 ? 2 : 5;
    for (long j = (long) Math.ceil(min / minor); j <= (long) Math.floor(max / minor); j++) {
      if (j % perMajor != 0) {
        line(p, j * minor, from, to, vertical);
      }
    }
  }

  private static void line(Painter p, double at, double from, double to, boolean vertical) {
    if (vertical) {
      p.moveTo(at, from);
      p.lineTo(at, to);
    } else {
      p.moveTo(from, at);
      p.lineTo(to, at);
    }
  }

  private static void xLabels(Painter p, Ticks t, TextStyle style) {
    Viewport v = p.viewport();
    double axis = v.toScreenY(0);
    double y;
    double dy;
    Anchor.Vertical vertical;
    if (axis < 0) {
      y = v.yMax();
      dy = GAP;
      vertical = Anchor.Vertical.TOP;
    } else if (axis > v.height() - LABEL_SIZE - 2 * GAP) {
      y = v.yMin();
      dy = -GAP;
      vertical = Anchor.Vertical.BOTTOM;
    } else {
      y = 0;
      dy = GAP;
      vertical = Anchor.Vertical.TOP;
    }
    Anchor anchor = new Anchor(Anchor.Horizontal.MIDDLE, vertical);
    for (long i = t.first(v.xMin()); i <= t.last(v.xMax()); i++) {
      double sx = v.toScreenX(t.value(i));
      if (i != 0 && sx >= EDGE && sx <= v.width() - EDGE) {
        p.text(t.label(i), t.value(i), y, 0, dy, anchor, style);
      }
    }
  }

  private static void yLabels(Painter p, Ticks t, TextStyle style) {
    Viewport v = p.viewport();
    double axis = v.toScreenX(0);
    double x;
    double dx;
    Anchor.Horizontal horizontal;
    if (axis > v.width()) {
      x = v.xMax();
      dx = -GAP;
      horizontal = Anchor.Horizontal.END;
    } else if (axis >= Y_LABEL_ROOM) {
      x = 0;
      dx = -GAP;
      horizontal = Anchor.Horizontal.END;
    } else if (axis >= 0) {
      x = 0;
      dx = GAP;
      horizontal = Anchor.Horizontal.START;
    } else {
      x = v.xMin();
      dx = GAP;
      horizontal = Anchor.Horizontal.START;
    }
    Anchor anchor = new Anchor(horizontal, Anchor.Vertical.MIDDLE);
    for (long i = t.first(v.yMin()); i <= t.last(v.yMax()); i++) {
      double sy = v.toScreenY(t.value(i));
      if (i != 0 && sy >= EDGE && sy <= v.height() - EDGE) {
        p.text(t.label(i), x, t.value(i), dx, 0, anchor, style);
      }
    }
  }
}
