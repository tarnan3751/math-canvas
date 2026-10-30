package mathcanvas.canvas.tool;

import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;

/**
 * The marks the tools' previews share, in the theme's colours: a curve the tool would use, a point
 * it would place, a point it would use, and a line it would draw.
 */
final class Preview {

  /** How much wider than the curve its highlight is, in pixels. */
  static final double HALO_WIDTH = 8;

  static final double HALO_OPACITY = 0.3;

  /** The radius of a point the tool would place, in pixels. */
  static final double POINT_RADIUS = 4;

  /** The radius of the ring around a point the tool would use, in pixels. */
  static final double RING_RADIUS = 8;

  static final double FREE_OPACITY = 0.5;

  private Preview() {}

  /** The colour a new object is drawn in: the first series colour, as the default style's. */
  static Rgba colour(CanvasTheme theme) {
    return theme.series().getFirst();
  }

  /** Highlights a curve: its polylines drawn again, wide and faint, in the axis colour. */
  static void curve(Painter p, CanvasTheme theme, CanvasState canvas, ObjectId id) {
    if (!(canvas.geometryOf(id) instanceof Geometry.Curve c)) {
      return;
    }
    Stroke halo = Stroke.solid(theme.axis().withOpacity(HALO_OPACITY), HALO_WIDTH);
    for (Polyline b : c.branches()) {
      p.beginPath();
      p.polyline(b.xs(), b.ys(), b.size());
      p.stroke(halo);
    }
  }

  /**
   * Marks what a click would use: a ring around an existing point, a point on a curve in the new
   * object's colour, a faint point where a free one would go.
   */
  static void snap(Painter p, CanvasTheme theme, Snap snap) {
    switch (snap) {
      case Snap.Existing e -> p.point(e.x(), e.y(), RING_RADIUS, theme.axis().withOpacity(0.35));
      case Snap.OnPath o -> p.point(o.at().x(), o.at().y(), POINT_RADIUS, colour(theme));
      case Snap.Free f ->
          p.point(f.x(), f.y(), POINT_RADIUS, colour(theme).withOpacity(FREE_OPACITY));
    }
  }

  /** The line through a point with a slope, across twice the window's diagonal each way. */
  static void line(
      Painter p, CanvasTheme theme, double x, double y, double slope, double reach, double width) {
    if (!Double.isFinite(slope)) {
      return;
    }
    double n = Math.hypot(1, slope);
    double dx = 2 * reach / n;
    double dy = 2 * reach * slope / n;
    p.beginPath();
    p.moveTo(x - dx, y - dy);
    p.lineTo(x + dx, y + dy);
    p.stroke(Stroke.dashed(colour(theme), width, 3 * width, 2 * width));
  }
}
