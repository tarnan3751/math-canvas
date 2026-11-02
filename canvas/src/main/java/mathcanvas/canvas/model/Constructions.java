package mathcanvas.canvas.model;

import java.util.List;
import java.util.Optional;
import org.apache.commons.geometry.euclidean.twod.AffineTransformMatrix2D;
import org.apache.commons.geometry.euclidean.twod.Lines;
import org.apache.commons.geometry.euclidean.twod.Vector2D;
import org.apache.commons.numbers.core.Precision;

/**
 * The plane geometry of the constructions, in {@code double}, over Apache Commons Geometry where it
 * has the predicate (lines through points, their directions and perpendiculars, angles between
 * vectors, rotations) and our own where it has none (the circle through three points, the tangents
 * from a point). Every degenerate configuration (coincident points, collinear points for a circle,
 * a zero radius, a tangent from inside, too few distinct vertices) is an empty result, never an
 * exception: our own guard decides it, and the library is then called under {@link #EXACT}, so it
 * never refuses an input the guard let through (a guard under one precision and a library call
 * under a larger one would throw on a defined configuration).
 *
 * <p>The precision is relative to the coordinates, {@link #EPSILON} of the largest magnitude
 * involved (at least 1), never to the window, so whether a construction is defined does not change
 * with a zoom. The tools use these functions for their previews, so a preview is the object it
 * adds.
 */
public final class Constructions {

  /** Two values within this much of the largest coordinate involved, or of 1, are one. */
  public static final double EPSILON = 1e-12;

  /** The precision of the library calls made after our own guard: only zero is zero. */
  static final Precision.DoubleEquivalence EXACT = Precision.doubleEquivalenceOfEpsilon(0);

  private Constructions() {}

  /** A vector's direction, or null for one of length zero or not finite. */
  private static Vector2D.Unit unit(Vector2D d) {
    return d.normalizeOrNull();
  }

  /** The precision for some points: {@link #EPSILON} of their largest coordinate, or of 1. */
  static Precision.DoubleEquivalence precision(Geometry.Point... points) {
    double scale = 1;
    for (Geometry.Point p : points) {
      scale = Math.max(scale, Math.max(Math.abs(p.x()), Math.abs(p.y())));
    }
    return Precision.doubleEquivalenceOfEpsilon(EPSILON * scale);
  }

  private static Vector2D v(Geometry.Point p) {
    return Vector2D.of(p.x(), p.y());
  }

  private static Geometry.Point point(Vector2D v) {
    return new Geometry.Point(v.getX(), v.getY());
  }

  /**
   * Whether two points are one under the precision.
   *
   * @param a a point
   * @param b another
   * @return true when they coincide
   */
  public static boolean coincide(Geometry.Point a, Geometry.Point b) {
    Precision.DoubleEquivalence p = precision(a, b);
    return p.eq(a.x(), b.x()) && p.eq(a.y(), b.y());
  }

  /**
   * The point halfway between two.
   *
   * @param a a point
   * @param b another
   * @return the midpoint
   */
  public static Geometry.Point midpoint(Geometry.Point a, Geometry.Point b) {
    return point(v(a).lerp(v(b), 0.5));
  }

  /**
   * The line through two points, from the first in the direction of the second.
   *
   * @param a the point at 0
   * @param b the point at 1
   * @return the parametrisation, empty while they coincide
   */
  public static Optional<Parametrisation.Linear> through(Geometry.Point a, Geometry.Point b) {
    if (coincide(a, b)) {
      return Optional.empty();
    }
    return Optional.of(new Parametrisation.Linear(a.x(), a.y(), b.x() - a.x(), b.y() - a.y()));
  }

  /**
   * The unit direction of a straight path (a line, a segment, a ray).
   *
   * @param path the path's geometry
   * @return the direction, empty when it is not straight or has no direction (a segment of length
   *     zero)
   */
  static Optional<Vector2D> direction(Geometry path) {
    if (path instanceof Geometry.Curve c
        && c.path() instanceof Path.Parametrised p
        && p.parametrisation() instanceof Parametrisation.Linear l) {
      Vector2D d = Vector2D.of(l.dx(), l.dy());
      Vector2D.Unit u = d.normalizeOrNull();
      Precision.DoubleEquivalence eq =
          precision(new Geometry.Point(l.px(), l.py()), new Geometry.Point(l.dx(), l.dy()));
      return u == null || eq.eqZero(d.norm()) ? Optional.empty() : Optional.of(u);
    }
    return Optional.empty();
  }

  /**
   * The line through a point perpendicular to a straight path.
   *
   * @param point the point, at 0
   * @param to the path's geometry
   * @return the parametrisation with a unit direction, empty when the path has no direction
   */
  public static Optional<Parametrisation.Linear> perpendicular(Geometry.Point point, Geometry to) {
    return direction(to).map(d -> linear(point, d.orthogonal()));
  }

  /**
   * The line through a point parallel to a straight path.
   *
   * @param point the point, at 0
   * @param to the path's geometry
   * @return the parametrisation with a unit direction, empty when the path has no direction
   */
  public static Optional<Parametrisation.Linear> parallel(Geometry.Point point, Geometry to) {
    return direction(to).map(d -> linear(point, d));
  }

  private static Parametrisation.Linear linear(Geometry.Point p, Vector2D d) {
    return new Parametrisation.Linear(p.x(), p.y(), d.getX(), d.getY());
  }

  /**
   * The perpendicular bisector of two points, through their midpoint.
   *
   * @param a a point
   * @param b another
   * @return the parametrisation with a unit direction, empty while they coincide
   */
  public static Optional<Parametrisation.Linear> bisector(Geometry.Point a, Geometry.Point b) {
    Vector2D.Unit d = unit(v(b).subtract(v(a)));
    if (coincide(a, b) || d == null) {
      return Optional.empty();
    }
    return Optional.of(linear(midpoint(a, b), d.orthogonal()));
  }

  /**
   * The bisector of the angle at a vertex: through the vertex along the difference of the arms'
   * unit vectors turned a quarter, which is along their sum (or against it) and, unlike the sum,
   * never vanishes or turns round as the angle passes through a straight one, so the line's
   * parameter, and the identity of what is intersected along it, stays continuous there; along the
   * first arm where the arms coincide (a zero angle).
   *
   * @param a the first arm's point
   * @param vertex the vertex
   * @param c the second arm's point
   * @return the parametrisation with a unit direction, empty while an arm has length zero
   */
  public static Optional<Parametrisation.Linear> angleBisector(
      Geometry.Point a, Geometry.Point vertex, Geometry.Point c) {
    if (coincide(a, vertex) || coincide(c, vertex)) {
      return Optional.empty();
    }
    Vector2D.Unit u = unit(v(a).subtract(v(vertex)));
    Vector2D.Unit w = unit(v(c).subtract(v(vertex)));
    if (u == null || w == null) {
      return Optional.empty();
    }
    Vector2D.Unit d = unit(u.subtract(w));
    return Optional.of(linear(vertex, d == null ? u : d.orthogonal()));
  }

  /**
   * A circle as a path's parametrisation.
   *
   * @param path a path's geometry
   * @return its circle, empty when it is not one
   */
  public static Optional<Parametrisation.Circular> circle(Geometry path) {
    return path instanceof Geometry.Curve c
            && c.path() instanceof Path.Parametrised p
            && p.parametrisation() instanceof Parametrisation.Circular k
        ? Optional.of(k)
        : Optional.empty();
  }

  /**
   * The circle about a centre with a radius.
   *
   * @param center the centre
   * @param r the radius
   * @return the circle, empty unless the radius is a positive number under the precision
   */
  public static Optional<Parametrisation.Circular> circle(Geometry.Point center, double r) {
    if (!Double.isFinite(r) || !precision(center).gt(r, 0)) {
      return Optional.empty();
    }
    return Optional.of(new Parametrisation.Circular(center.x(), center.y(), r, 0, 1));
  }

  /**
   * The circle through three points, about the intersection of two perpendicular bisectors.
   *
   * @param a a point
   * @param b another
   * @param c a third
   * @return the circle, empty while the points are collinear (two coincident included)
   */
  public static Optional<Parametrisation.Circular> circle(
      Geometry.Point a, Geometry.Point b, Geometry.Point c) {
    Precision.DoubleEquivalence eq = precision(a, b, c);
    if (coincide(a, b) || coincide(b, c) || coincide(a, c)) {
      return Optional.empty();
    }
    // c's distance from the line ab, under the precision of the three points (the line itself
    // built exactly: the guard above has already refused a and b as one)
    if (eq.eqZero(Lines.fromPoints(v(a), v(b), EXACT).offset(v(c)))) {
      return Optional.empty();
    }
    // the centre solves |P − a|² = |P − b|² = |P − c|², relative to a for the rounding
    double bx = b.x() - a.x();
    double by = b.y() - a.y();
    double cx = c.x() - a.x();
    double cy = c.y() - a.y();
    double d = 2 * (bx * cy - by * cx);
    double b2 = bx * bx + by * by;
    double c2 = cx * cx + cy * cy;
    double ux = (cy * b2 - by * c2) / d;
    double uy = (bx * c2 - cx * b2) / d;
    return circle(new Geometry.Point(a.x() + ux, a.y() + uy), Math.hypot(ux, uy));
  }

  /**
   * A tangent to a circle through a point: from outside, index 0 touches counterclockwise from the
   * point's direction as seen from the centre and index 1 clockwise; from a point on the circle,
   * both are the tangent there. Each index runs from the point toward its point of contact, a unit
   * direction, so on the circle index 0 runs counterclockwise round it and index 1 clockwise, the
   * limits of their directions from outside: each line and its parameter move continuously as the
   * point comes onto the circle.
   *
   * @param circle the circle
   * @param p the point, at 0
   * @param index 0 or 1
   * @return the tangent with a unit direction, empty from a point inside the circle
   */
  public static Optional<Parametrisation.Linear> tangent(
      Parametrisation.Circular circle, Geometry.Point p, int index) {
    Geometry.Point c = new Geometry.Point(circle.cx(), circle.cy());
    Vector2D cp = v(p).subtract(v(c));
    double d = cp.norm();
    double r = circle.r();
    Precision.DoubleEquivalence eq = precision(c, p, new Geometry.Point(r, r));
    if (eq.lt(d, r)) {
      return Optional.empty();
    }
    Vector2D.Unit radial = unit(cp);
    Vector2D.Unit along =
        eq.eq(d, r) && radial != null
            ? radial.orthogonal()
            : unit(
                touch(c, r, Math.atan2(cp.getY(), cp.getX()), Math.acos(r / d), index)
                    .subtract(v(p)));
    if (along == null) {
      return Optional.empty();
    }
    return Optional.of(linear(p, index == 0 ? along : eq.eq(d, r) ? along.negate() : along));
  }

  /**
   * Whether a point is on a circle under the precision the tangents use: from such a point the two
   * tangents are one line.
   *
   * @param circle the circle
   * @param p the point
   * @return true on the circle
   */
  public static boolean onCircle(Parametrisation.Circular circle, Geometry.Point p) {
    Geometry.Point c = new Geometry.Point(circle.cx(), circle.cy());
    double r = circle.r();
    return precision(c, p, new Geometry.Point(r, r))
        .eq(Math.hypot(p.x() - c.x(), p.y() - c.y()), r);
  }

  /** The point of contact of a tangent from outside, on its index's side. */
  private static Vector2D touch(Geometry.Point c, double r, double phi, double alpha, int index) {
    double at = index == 0 ? phi + alpha : phi - alpha;
    return Vector2D.of(c.x() + r * Math.cos(at), c.y() + r * Math.sin(at));
  }

  /**
   * The vertices of a regular polygon on the side from {@code a} to {@code b}, counterclockwise, so
   * the polygon lies to the left of that side.
   *
   * @param a the first vertex
   * @param b the second vertex
   * @param n the number of sides
   * @return the vertices, from {@code a}, empty while {@code a} and {@code b} coincide
   */
  public static Optional<List<Geometry.Point>> regular(Geometry.Point a, Geometry.Point b, int n) {
    Vector2D side = v(b).subtract(v(a));
    Vector2D.Unit along = unit(side);
    if (coincide(a, b) || along == null) {
      return Optional.empty();
    }
    double toCentre = side.norm() / (2 * Math.tan(Math.PI / n));
    Vector2D centre = v(a).lerp(v(b), 0.5).add(along.orthogonal().multiply(toCentre));
    AffineTransformMatrix2D turn = AffineTransformMatrix2D.createRotation(centre, 2 * Math.PI / n);
    Geometry.Point[] out = new Geometry.Point[n];
    out[0] = a;
    out[1] = b;
    Vector2D at = v(b);
    for (int k = 2; k < n; k++) {
      at = turn.apply(at);
      out[k] = point(at);
    }
    return Optional.of(List.of(out));
  }

  /**
   * The angle at a vertex between the arms to two points, never reflex, on the smaller side.
   *
   * @param a the first arm's point
   * @param vertex the vertex
   * @param c the second arm's point
   * @return the angle, empty while an arm has length zero
   */
  public static Optional<Geometry.Angle> angle(
      Geometry.Point a, Geometry.Point vertex, Geometry.Point c) {
    if (coincide(a, vertex) || coincide(c, vertex)) {
      return Optional.empty();
    }
    Vector2D u = v(a).subtract(v(vertex));
    Vector2D w = v(c).subtract(v(vertex));
    double sweep = Math.min(u.angle(w), Math.PI);
    Vector2D first = u.signedArea(w) >= 0 ? u : w;
    return Optional.of(new Geometry.Angle(vertex, Math.atan2(first.getY(), first.getX()), sweep));
  }

  /**
   * Whether points are fewer than three distinct ones under the precision.
   *
   * @param points the points
   * @return true when fewer than three are distinct
   */
  public static boolean fewerThanThree(List<Geometry.Point> points) {
    int distinct = 0;
    for (int k = 0; k < points.size() && distinct < 3; k++) {
      boolean seen = false;
      for (int j = 0; j < k && !seen; j++) {
        seen = coincide(points.get(j), points.get(k));
      }
      if (!seen) {
        distinct++;
      }
    }
    return distinct < 3;
  }

  /**
   * A polygon's area by the shoelace formula, unsigned: the magnitude of the net signed area, so a
   * polygon that crosses itself counts its lobes with their signs (a bowtie's two equal lobes give
   * 0).
   *
   * @param g the polygon
   * @return the area
   */
  public static double area(Parametrisation.Polygonal g) {
    double twice = 0;
    for (int k = 0; k < g.size(); k++) {
      twice += g.x(k) * g.y(k + 1) - g.x(k + 1) * g.y(k);
    }
    return Math.abs(twice) / 2;
  }

  /**
   * A polygon's perimeter.
   *
   * @param g the polygon
   * @return the sum of its sides' lengths
   */
  public static double perimeter(Parametrisation.Polygonal g) {
    double sum = 0;
    for (int k = 0; k < g.size(); k++) {
      sum += Math.hypot(g.x(k + 1) - g.x(k), g.y(k + 1) - g.y(k));
    }
    return sum;
  }
}
