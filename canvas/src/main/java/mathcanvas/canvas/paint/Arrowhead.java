package mathcanvas.canvas.paint;

import mathcanvas.canvas.geom.Viewport;

/**
 * An arrowhead drawn through a {@link Painter}: a filled triangle of a fixed size in pixels,
 * whatever the zoom or the aspect (as a point's radius is), laid out in the painter's pixels and
 * drawn through world coordinates as everything else is.
 */
public final class Arrowhead {

  /** The width of an arrowhead's base as a fraction of its length. */
  public static final double BASE = 0.8;

  private Arrowhead() {}

  /**
   * Fills an arrowhead.
   *
   * @param p the painter
   * @param x the tip's world x
   * @param y the tip's world y
   * @param dx the direction's world x
   * @param dy the direction's world y
   * @param length the length from the base to the tip in pixels
   * @param colour the colour
   */
  public static void paint(
      Painter p, double x, double y, double dx, double dy, double length, Rgba colour) {
    Viewport w = p.viewport();
    double px = dx * w.pixelsPerUnitX();
    double py = -dy * w.pixelsPerUnitY();
    double n = Math.hypot(px, py);
    if (!(n > 0) || !Double.isFinite(n)) {
      return;
    }
    double ux = px / n;
    double uy = py / n;
    double bx = w.toScreenX(x) - ux * length;
    double by = w.toScreenY(y) - uy * length;
    double half = BASE * length / 2;
    p.beginPath();
    p.moveTo(x, y);
    p.lineTo(w.toWorldX(bx - uy * half), w.toWorldY(by + ux * half));
    p.lineTo(w.toWorldX(bx + uy * half), w.toWorldY(by - ux * half));
    p.closePath();
    p.fill(colour);
  }
}
