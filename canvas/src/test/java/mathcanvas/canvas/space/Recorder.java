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

  private double sx;
  private double sy;
  private int n;

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
  }

  @Override
  public void fill(Rgba colour) {
    fills.add(colour);
    centres.add(new double[] {sx / n, sy / n});
  }

  @Override
  public void point(double x, double y, double radius, Rgba colour) {}

  @Override
  public void text(
      String text, double x, double y, double dx, double dy, Anchor anchor, TextStyle style) {
    texts.add(text);
  }
}
