package mathcanvas.canvas.tool;

import java.util.List;
import java.util.Map;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Parametrisation;
import mathcanvas.canvas.model.Path;
import mathcanvas.canvas.model.Polyline;
import mathcanvas.canvas.model.Sample;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Window;
import mathcanvas.canvas.paint.AngleArc;
import mathcanvas.canvas.paint.Arrowhead;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import mathcanvas.canvas.paint.TextStyle;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;

/**
 * The marks the tools' previews share, in the theme's colours: a curve or a vector the tool would
 * use, a point it would place, a point it would use, and a line or an arrow it would draw.
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

  /** Highlights a vector: its shaft drawn again, wide and faint, in the axis colour. */
  static void vector(Painter p, CanvasTheme theme, CanvasState canvas, ObjectId id) {
    if (!(canvas.geometryOf(id) instanceof Geometry.Arrow a)) {
      return;
    }
    p.beginPath();
    p.moveTo(a.tail().x(), a.tail().y());
    p.lineTo(a.head().x(), a.head().y());
    p.stroke(Stroke.solid(theme.axis().withOpacity(HALO_OPACITY), HALO_WIDTH));
  }

  /** The arrow a tool would draw, dashed, in the new object's colour. */
  static void arrow(Painter p, CanvasTheme theme, double x0, double y0, double x1, double y1) {
    double width = 2;
    p.beginPath();
    p.moveTo(x0, y0);
    p.lineTo(x1, y1);
    p.stroke(Stroke.dashed(colour(theme), width, 3 * width, 2 * width));
    Arrowhead.paint(p, x1, y1, x1 - x0, y1 - y0, ARROWHEAD, colour(theme));
  }

  /** The length of a previewed arrow's head, in pixels: a two-pixel arrow's. */
  static final double ARROWHEAD = 12;

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

  /** The radius of a previewed angle's mark, in pixels, as the canvas draws one. */
  static final double ANGLE_RADIUS = 24;

  /**
   * The geometry a construction would add, dashed in the new object's colour: a curve's polylines
   * (a polygon's faintly filled), a point, an angle's mark.
   */
  static void geometry(Painter p, CanvasTheme theme, Geometry g) {
    double width = 2;
    Stroke dashed = Stroke.dashed(colour(theme), width, 3 * width, 2 * width);
    switch (g) {
      case Geometry.Curve c -> {
        for (Polyline b : c.branches()) {
          if (c.path() instanceof Path.Parametrised q
              && q.parametrisation() instanceof Parametrisation.Polygonal) {
            p.beginPath();
            p.polyline(b.xs(), b.ys(), b.size());
            p.fill(colour(theme).withOpacity(FILL_OPACITY));
          }
          p.beginPath();
          p.polyline(b.xs(), b.ys(), b.size());
          p.stroke(dashed);
        }
      }
      case Geometry.Point q -> p.point(q.x(), q.y(), POINT_RADIUS, colour(theme));
      case Geometry.Angle a ->
          AngleArc.paint(
              p,
              a.vertex().x(),
              a.vertex().y(),
              a.start(),
              a.sweep(),
              ANGLE_RADIUS,
              dashed,
              colour(theme).withOpacity(FILL_OPACITY),
              "",
              0,
              new TextStyle(12, colour(theme)));
      case Geometry.Undefined u -> {}
      case Geometry.Region r -> {}
      case Geometry.ValuedRegion r -> {}
      case Geometry.Value v -> {}
      case Geometry.Arrow a -> {}
      case Geometry.Measure m -> {}
      case Geometry.Field f -> {}
      case Geometry.Contours c -> {}
      case Geometry.Bins b -> {}
      case Geometry.Bars b -> {}
      case Geometry.Box b -> {}
      case Geometry.Scatter s -> {}
      case Geometry.Spatial s -> {}
    }
  }

  /** The opacity of a previewed polygon's or angle's fill. */
  static final double FILL_OPACITY = 0.15;

  /**
   * The sampler of a preview, which evaluates no expression: the constructions a tool previews are
   * plain arithmetic on points, and one that reads a number is previewed without it.
   */
  static final Sampler NO_SAMPLER =
      new Sampler() {
        @Override
        public double value(Expr e, Map<Symbol, Double> locals) {
          return Double.NaN;
        }

        @Override
        public Sample point(Parametrisation.Expressed p, double t) {
          return Sample.UNDEFINED;
        }

        @Override
        public List<Polyline> sample(
            Parametrisation.Expressed p,
            double lo,
            double hi,
            Window window,
            Cancellation cancellation) {
          return List.of();
        }

        @Override
        public List<Polyline> contour(Relation equation, Window window, Cancellation cancellation) {
          return List.of();
        }

        @Override
        public List<Polyline> region(
            Relation inequality, Window window, Cancellation cancellation) {
          return List.of();
        }
      };
}
