package mathcanvas.canvas.space;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.paint.Anchor;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import mathcanvas.canvas.paint.TextStyle;

/** A painter that records the fills, strokes and texts, for the scene's tests. */
final class Recorder implements Painter {

  final Viewport viewport;
  final List<Rgba> fills = new ArrayList<>();
  final List<Stroke> strokes = new ArrayList<>();
  final List<String> texts = new ArrayList<>();

  /** The centre of each filled path, in the painter's world. */
  final List<double[]> centres = new ArrayList<>();

  /**
   * Every fill and stroke in the order painted: its stroke (null for a fill), a fill's colour, its
   * centre and, for a path of two points, both ends {@code {x0, y0, x1, y1}}.
   */
  record Event(Stroke stroke, Rgba fill, double[] centre, double[] ends) {}

  /** The fills and strokes in the order painted. */
  final List<Event> events = new ArrayList<>();

  private double sx;
  private double sy;
  private int n;
  private final double[] ends = new double[4];

  Recorder(double width, double height) {
    viewport = new Viewport(0, width, 0, height, width, height, false);
  }

  @Override
  public Viewport viewport() {
    return viewport;
  }

  @Override
  public void beginPath() {
    sx = 0;
    sy = 0;
    n = 0;
  }

  @Override
  public void moveTo(double x, double y) {
    lineTo(x, y);
  }

  @Override
  public void lineTo(double x, double y) {
    if (n < 2) {
      ends[2 * n] = x;
      ends[2 * n + 1] = y;
    }
    sx += x;
    sy += y;
    n++;
  }

  @Override
  public void polyline(double[] xs, double[] ys, int count) {
    for (int k = 0; k < count; k++) {
      lineTo(xs[k], ys[k]);
    }
  }

  @Override
  public void closePath() {}

  @Override
  public void stroke(Stroke stroke) {
    strokes.add(stroke);
    events.add(
        new Event(stroke, null, new double[] {sx / n, sy / n}, n == 2 ? ends.clone() : null));
  }

  @Override
  public void fill(Rgba colour) {
    fills.add(colour);
    centres.add(new double[] {sx / n, sy / n});
    events.add(new Event(null, colour, new double[] {sx / n, sy / n}, null));
  }

  @Override
  public void point(double x, double y, double radius, Rgba colour) {}

  @Override
  public void text(
      String text, double x, double y, double dx, double dy, Anchor anchor, TextStyle style) {
    texts.add(text);
  }
}
