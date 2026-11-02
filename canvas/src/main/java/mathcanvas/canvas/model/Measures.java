package mathcanvas.canvas.model;

import java.util.List;
import java.util.Map;
import org.apache.commons.geometry.euclidean.twod.Line;
import org.apache.commons.geometry.euclidean.twod.Lines;
import org.apache.commons.geometry.euclidean.twod.Vector2D;

/**
 * The measurements' values and where their labels go: a distance between two points, or from a
 * point to the nearest point of a line, segment or ray (Apache Commons Geometry's projection onto
 * the line and its segments' and rays' nearest points), halfway along the shortest segment; the
 * area of a polygon (shoelace, unsigned) at its vertices' centroid, or of a circle at its centre;
 * the slope of a straight path at the middle of the part of it drawn, undefined while it is
 * vertical.
 */
final class Measures {

  /** Below this fraction of a direction's length its abscissa's change is none: vertical. */
  static final double VERTICAL = 1e-12;

  private Measures() {}

  /**
   * A distance's geometry.
   *
   * @param d the distance
   * @param parents its parents' geometry
   * @return the value with its label's place, or undefined
   */
  static Geometry distance(Distance d, Map<ObjectId, Geometry> parents) {
    Geometry.Point p = (Geometry.Point) parents.get(d.a());
    Geometry to = parents.get(d.b());
    Geometry.Point near = null;
    if (to instanceof Geometry.Point q) {
      near = q;
    } else if (to instanceof Geometry.Curve c
        && c.path() instanceof Path.Parametrised q
        && q.parametrisation() instanceof Parametrisation.Linear l) {
      near = nearest(p, l, q.lo(), q.hi());
    }
    if (near == null) {
      return Geometry.UNDEFINED;
    }
    double length = Math.hypot(near.x() - p.x(), near.y() - p.y());
    return Double.isFinite(length)
        ? new Geometry.Measure(length, Constructions.midpoint(p, near))
        : Geometry.UNDEFINED;
  }

  /** The point of a straight path nearest a point. */
  private static Geometry.Point nearest(
      Geometry.Point p, Parametrisation.Linear l, double lo, double hi) {
    Geometry.Point start = new Geometry.Point(l.px(), l.py());
    if (Constructions.direction(
            new Geometry.Curve(new Path.Parametrised(l, lo, hi, ParamKind.LINE), List.of()))
        .isEmpty()) {
      return start; // a segment of length zero: its one point
    }
    Line line =
        Lines.fromPointAndDirection(
            Vector2D.of(l.px(), l.py()),
            Vector2D.of(l.dx(), l.dy()),
            // exact: the guard above has decided the direction is not zero
            Constructions.EXACT);
    Vector2D at = Vector2D.of(p.x(), p.y());
    double a0 = line.abscissa(Vector2D.of(l.px() + lo * l.dx(), l.py() + lo * l.dy()));
    Vector2D near =
        Double.isInfinite(lo) && Double.isInfinite(hi)
            ? line.project(at)
            : Double.isInfinite(hi)
                ? Lines.rayFromLocation(line, a0).closest(at)
                : Lines.segmentFromLocations(
                        line,
                        a0,
                        line.abscissa(Vector2D.of(l.px() + hi * l.dx(), l.py() + hi * l.dy())))
                    .closest(at);
    return new Geometry.Point(near.getX(), near.getY());
  }

  /**
   * An area's geometry.
   *
   * @param of the polygon's or circle's geometry
   * @return the value with its label's place, or undefined
   */
  static Geometry area(Geometry of) {
    if (!(of instanceof Geometry.Curve c && c.path() instanceof Path.Parametrised q)) {
      return Geometry.UNDEFINED;
    }
    return switch (q.parametrisation()) {
      case Parametrisation.Circular k ->
          new Geometry.Measure(Math.PI * k.r() * k.r(), new Geometry.Point(k.cx(), k.cy()));
      case Parametrisation.Polygonal g -> {
        double cx = 0;
        double cy = 0;
        for (int k = 0; k < g.size(); k++) {
          cx += g.x(k) / g.size();
          cy += g.y(k) / g.size();
        }
        yield new Geometry.Measure(Constructions.area(g), new Geometry.Point(cx, cy));
      }
      case Parametrisation.Linear l -> Geometry.UNDEFINED;
      case Parametrisation.Expressed e -> Geometry.UNDEFINED;
      case Parametrisation.Transformed t -> Geometry.UNDEFINED;
    };
  }

  /**
   * A slope's geometry.
   *
   * @param of the straight path's geometry
   * @return the value with its label's place, or undefined
   */
  static Geometry slope(Geometry of) {
    if (!(of instanceof Geometry.Curve c
            && c.path() instanceof Path.Parametrised q
            && q.parametrisation() instanceof Parametrisation.Linear l)
        || Math.abs(l.dx()) <= VERTICAL * Math.hypot(l.dx(), l.dy())) {
      return Geometry.UNDEFINED;
    }
    double m = l.dy() / l.dx();
    Geometry.Point at = new Geometry.Point(l.px(), l.py());
    if (!c.branches().isEmpty()) {
      Polyline b = c.branches().getFirst();
      int n = b.size() - 1;
      at = new Geometry.Point((b.x(0) + b.x(n)) / 2, (b.y(0) + b.y(n)) / 2);
    }
    return Double.isFinite(m) ? new Geometry.Measure(m, at) : Geometry.UNDEFINED;
  }
}
