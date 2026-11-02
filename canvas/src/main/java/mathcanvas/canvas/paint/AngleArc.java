package mathcanvas.canvas.paint;

import mathcanvas.canvas.geom.Viewport;

/**
 * The mark of an angle drawn through a {@link Painter}: a wedge of a fixed radius in pixels at the
 * vertex, filled faintly and edged with an arc, from one arm's direction to the other's, whatever
 * the zoom or the aspect (as an arrowhead's size is), laid out in the painter's pixels and drawn
 * through world coordinates as everything else is, with its label written beyond the arc on its
 * bisector as the screen shows it, so it stays there under any aspect.
 */
public final class AngleArc {

  /** The arc's points per half turn. */
  private static final int STEPS = 32;

  private AngleArc() {}

  /**
   * Draws an angle's mark.
   *
   * @param p the painter
   * @param x the vertex's world x
   * @param y the vertex's world y
   * @param start the first arm's world direction, in radians
   * @param sweep the angle from it counterclockwise to the second arm in the world, 0 to π
   * @param radius the radius in pixels
   * @param stroke the arc's stroke
   * @param fill the wedge's fill
   * @param label the label, empty for none
   * @param gap the label's distance beyond the arc, in pixels
   * @param style the label's style
   */
  public static void paint(
      Painter p,
      double x,
      double y,
      double start,
      double sweep,
      double radius,
      Stroke stroke,
      Rgba fill,
      String label,
      double gap,
      TextStyle style) {
    Viewport w = p.viewport();
    double a = screenAngle(w, start);
    double b = screenAngle(w, start + sweep);
    // the screen's y goes down, so the world's counterclockwise sweep is the shorter way from a to
    // b
    double turn = Math.atan2(Math.sin(b - a), Math.cos(b - a));
    int n = Math.max(2, (int) Math.ceil(STEPS * Math.abs(turn) / Math.PI));
    double cx = w.toScreenX(x);
    double cy = w.toScreenY(y);
    double[] xs = new double[n + 1];
    double[] ys = new double[n + 1];
    for (int k = 0; k <= n; k++) {
      double t = a + turn * k / n;
      xs[k] = w.toWorldX(cx + radius * Math.cos(t));
      ys[k] = w.toWorldY(cy + radius * Math.sin(t));
    }
    p.beginPath();
    p.moveTo(x, y);
    for (int k = 0; k <= n; k++) {
      p.lineTo(xs[k], ys[k]);
    }
    p.closePath();
    p.fill(fill);
    p.beginPath();
    p.polyline(xs, ys, xs.length);
    p.stroke(stroke);
    if (!label.isEmpty()) {
      double mid = a + turn / 2;
      double r = radius + gap;
      p.text(
          label,
          x,
          y,
          r * Math.cos(mid),
          r * Math.sin(mid),
          new Anchor(Anchor.Horizontal.MIDDLE, Anchor.Vertical.MIDDLE),
          style);
    }
  }

  /** A world direction's angle on the screen, y down. */
  private static double screenAngle(Viewport w, double angle) {
    return Math.atan2(-Math.sin(angle) * w.pixelsPerUnitY(), Math.cos(angle) * w.pixelsPerUnitX());
  }
}
