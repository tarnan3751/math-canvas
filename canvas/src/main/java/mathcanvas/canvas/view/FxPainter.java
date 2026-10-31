package mathcanvas.canvas.view;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javafx.geometry.Bounds;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.paint.Anchor;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.ScreenPainter;
import mathcanvas.canvas.paint.Stroke;
import mathcanvas.canvas.paint.TextStyle;

/**
 * The screen backend: the painter's calls on a JavaFX {@code Canvas}'s graphics context, in the
 * canvas's pixels. Horizontal and vertical two-point lines, the grid and the axes, are aligned with
 * the pixel grid, at a half pixel for an odd width and a whole one for an even width, so a 1-pixel
 * line covers one row or column of pixels instead of two half-tinted ones. FX thread only.
 *
 * <p>An open subpath of an opaque stroke is stroked in pieces of {@link #PIECE} segments, each its
 * own path, because the Prism render thread's cost of stroking a path grows with the device-pixel
 * area of its bounding box, which a long curve makes nearly the whole canvas; pieces have small
 * boxes. Neighbouring pieces share an end point and the round caps cover the joint, so no seam
 * shows; a dashed stroke carries its dash offset from piece to piece. A translucent stroke, whose
 * overlapping caps would darken the joints, and a closed subpath are stroked whole. The path is
 * therefore collected here and issued when it is stroked or filled.
 */
final class FxPainter extends ScreenPainter {

  /** The segments in a piece of a stroked subpath, chosen by measuring the benchmark. */
  static final int PIECE = 16;

  private final GraphicsContext gc;
  private double[] xs = new double[256];
  private double[] ys = new double[256];
  private int size;
  private int[] starts = new int[16];
  private boolean[] closed = new boolean[16];
  private int subpaths;
  private final Map<Rgba, Color> colours = new HashMap<>();
  private final Map<Double, Font> fonts = new HashMap<>();

  FxPainter(GraphicsContext gc, Viewport viewport) {
    super(viewport);
    this.gc = gc;
    gc.setLineCap(StrokeLineCap.ROUND);
    gc.setLineJoin(StrokeLineJoin.ROUND);
  }

  @Override
  protected void beginScreenPath() {
    size = 0;
    subpaths = 0;
  }

  @Override
  protected void moveToScreen(double x, double y) {
    if (subpaths == starts.length) {
      starts = Arrays.copyOf(starts, subpaths * 2);
      closed = Arrays.copyOf(closed, subpaths * 2);
    }
    starts[subpaths] = size;
    closed[subpaths] = false;
    subpaths++;
    lineToScreen(x, y);
  }

  @Override
  protected void lineToScreen(double x, double y) {
    if (size == xs.length) {
      xs = Arrays.copyOf(xs, size * 2);
      ys = Arrays.copyOf(ys, size * 2);
    }
    xs[size] = x;
    ys[size] = y;
    size++;
  }

  @Override
  protected void closeScreenSubpath() {
    closed[subpaths - 1] = true;
  }

  @Override
  protected void strokeScreenPath(Stroke stroke) {
    gc.setStroke(colour(stroke.colour()));
    gc.setLineWidth(stroke.width());
    double[] dash =
        stroke.dash().isEmpty()
            ? null
            : stroke.dash().stream().mapToDouble(Double::doubleValue).toArray();
    gc.setLineDashes(dash);
    boolean pieces = stroke.colour().opacity() == 1;
    for (int k = 0; k < subpaths; k++) {
      int from = starts[k];
      int to = k + 1 < subpaths ? starts[k + 1] : size;
      if (!pieces || closed[k] || to - from <= PIECE + 1) {
        gc.setLineDashOffset(0);
        gc.beginPath();
        trace(from, to, closed[k]);
        gc.stroke();
        continue;
      }
      double offset = 0;
      for (int start = from; start < to - 1; start += PIECE) {
        int end = Math.min(to, start + PIECE + 1);
        gc.setLineDashOffset(offset);
        gc.beginPath();
        trace(start, end, false);
        gc.stroke();
        if (dash != null) {
          for (int i = start + 1; i < end; i++) {
            offset += Math.hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1]);
          }
        }
      }
    }
    gc.setLineDashOffset(0);
  }

  @Override
  protected void fillScreenPath(Rgba colour) {
    gc.setFill(colour(colour));
    gc.beginPath();
    for (int k = 0; k < subpaths; k++) {
      trace(starts[k], k + 1 < subpaths ? starts[k + 1] : size, closed[k]);
    }
    gc.fill();
  }

  /** Adds points {@code from} to {@code to - 1} to the context's path as one subpath. */
  private void trace(int from, int to, boolean close) {
    gc.moveTo(xs[from], ys[from]);
    for (int i = from + 1; i < to; i++) {
      gc.lineTo(xs[i], ys[i]);
    }
    if (close) {
      gc.closePath();
    }
  }

  @Override
  protected void pointScreen(double x, double y, double radius, Rgba colour) {
    gc.setFill(colour(colour));
    gc.fillOval(x - radius, y - radius, 2 * radius, 2 * radius);
  }

  @Override
  protected void textScreen(String text, double x, double y, Anchor anchor, TextStyle style) {
    Font font = fonts.computeIfAbsent(style.size(), Font::font);
    if (style.background().isPresent()) {
      Text measured = new Text(text);
      measured.setFont(font);
      Bounds b = measured.getLayoutBounds();
      double[] at = TextStyle.corner(x, y, anchor, b.getWidth(), b.getHeight());
      double pad = TextStyle.PADDING;
      double[] shift =
          inside(at[0] - pad, at[1] - pad, b.getWidth() + 2 * pad, b.getHeight() + 2 * pad);
      x += shift[0];
      y += shift[1];
      gc.setFill(colour(style.background().get()));
      gc.fillRoundRect(
          at[0] - pad + shift[0],
          at[1] - pad + shift[1],
          b.getWidth() + 2 * pad,
          b.getHeight() + 2 * pad,
          2 * pad,
          2 * pad);
    }
    gc.setFont(font);
    gc.setFill(colour(style.colour()));
    gc.setTextAlign(
        switch (anchor.horizontal()) {
          case START -> TextAlignment.LEFT;
          case MIDDLE -> TextAlignment.CENTER;
          case END -> TextAlignment.RIGHT;
        });
    gc.setTextBaseline(
        switch (anchor.vertical()) {
          case TOP -> VPos.TOP;
          case MIDDLE -> VPos.CENTER;
          case BASELINE -> VPos.BASELINE;
          case BOTTOM -> VPos.BOTTOM;
        });
    gc.fillText(text, x, y);
  }

  @Override
  protected double snap(double coordinate, double width) {
    long pixels = Math.max(1, Math.round(width));
    return pixels % 2 == 1 ? Math.floor(coordinate) + 0.5 : Math.round(coordinate);
  }

  private Color colour(Rgba c) {
    return colours.computeIfAbsent(c, k -> new Color(k.red(), k.green(), k.blue(), k.opacity()));
  }
}
