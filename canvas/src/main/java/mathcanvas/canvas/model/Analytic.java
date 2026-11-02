package mathcanvas.canvas.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import mathcanvas.canvas.model.Families.Candidate;
import org.apache.commons.geometry.euclidean.twod.Line;
import org.apache.commons.geometry.euclidean.twod.Lines;
import org.apache.commons.geometry.euclidean.twod.Vector2D;
import org.apache.commons.geometry.euclidean.twod.shape.Circle;
import org.apache.commons.numbers.core.Precision;

/**
 * The intersections of two paths made of straight pieces and circles, in closed form: a circle, a
 * polygon's boundary, and a line, segment or ray against either. A line against a line is left to
 * the general candidates of {@link Intersections}, as it was before circles existed.
 *
 * <p>A line against a circle is Apache Commons Geometry's {@code Circle.intersections}, which gives
 * one point, the tangency, when the line's distance from the centre is the radius within the
 * precision; two circles are our own: they touch when the distance between their centres is the sum
 * or the difference of their radii within it. The precision is the root tolerance of {@link
 * Intersections} (relative to the window's diagonal, never below the coordinates' rounding), so a
 * tangency is the same notion here as for any other pair of curves, and its spread is its band, the
 * length over which the two curves stay within the tolerance: {@code 2√(tol/k)} with {@code k} the
 * difference of the curvatures halved. A circle whose radius is within the tolerance is a point,
 * touched by a line that passes within the tolerance of it. Two coincident circles, and a line
 * along a polygon's side over a stretch, overlap: no candidates at all. The candidates are ordered
 * by the parameter on the first path, then the second, and a point found twice (a polygon's vertex)
 * is one.
 */
final class Analytic {

  private Analytic() {}

  /** A straight piece of a path: {@code t = offset + s} for {@code s} from lo to hi. */
  private record Straight(
      double px, double py, double dx, double dy, double lo, double hi, double offset) {}

  /**
   * Whether two paths are both made of straight pieces and circles, and not both straight lines.
   *
   * @param a the first path
   * @param b the second
   * @return true when {@link #candidates} applies
   */
  static boolean applies(Path.Parametrised a, Path.Parametrised b) {
    return analytic(a) && analytic(b) && !(straight(a) && straight(b));
  }

  private static boolean analytic(Path.Parametrised p) {
    return p.parametrisation() instanceof Parametrisation.Linear
        || p.parametrisation() instanceof Parametrisation.Circular
        || p.parametrisation() instanceof Parametrisation.Polygonal;
  }

  private static boolean straight(Path.Parametrised p) {
    return p.parametrisation() instanceof Parametrisation.Linear;
  }

  /**
   * The candidates of two paths.
   *
   * @param a the first path
   * @param b the second
   * @param scale the length the tolerances are relative to
   * @return the candidates, ordered, or empty when the paths overlap
   */
  static Optional<List<Candidate>> candidates(
      Path.Parametrised a, Path.Parametrised b, double scale) {
    List<Candidate> found = new ArrayList<>();
    for (Object pa : pieces(a)) {
      for (Object pb : pieces(b)) {
        if (!pair(pa, pb, scale, found)) {
          return Optional.empty();
        }
      }
    }
    found.sort(Comparator.comparingDouble(Candidate::t1).thenComparingDouble(Candidate::t2));
    List<Candidate> merged = new ArrayList<>();
    for (Candidate c : found) {
      boolean same = false;
      for (Candidate m : merged) {
        same |=
            Math.hypot(m.x() - c.x(), m.y() - c.y())
                <= Families.tolerance(Families.MERGE, scale, c.x(), c.y());
      }
      if (!same) {
        merged.add(c);
      }
    }
    return Optional.of(merged);
  }

  /** A path's pieces: one straight piece, a circle, or a polygon's sides. */
  private static List<Object> pieces(Path.Parametrised p) {
    return switch (p.parametrisation()) {
      case Parametrisation.Linear l ->
          List.of(new Straight(l.px(), l.py(), l.dx(), l.dy(), p.lo(), p.hi(), 0));
      case Parametrisation.Circular c -> List.of(c);
      case Parametrisation.Polygonal g -> {
        List<Object> out = new ArrayList<>();
        for (int k = 0; k < g.size(); k++) {
          out.add(new Straight(g.x(k), g.y(k), g.x(k + 1) - g.x(k), g.y(k + 1) - g.y(k), 0, 1, k));
        }
        yield out;
      }
      case Parametrisation.Expressed e -> throw new IllegalArgumentException("not analytic");
    };
  }

  /** Adds the candidates of two pieces, the first's parameter first; false for an overlap. */
  private static boolean pair(Object a, Object b, double scale, List<Candidate> out) {
    if (a instanceof Straight s && b instanceof Straight t) {
      return straights(s, t, scale, out, false);
    }
    if (a instanceof Straight s && b instanceof Parametrisation.Circular c) {
      straightCircle(s, c, scale, out, false);
      return true;
    }
    if (a instanceof Parametrisation.Circular c && b instanceof Straight s) {
      straightCircle(s, c, scale, out, true);
      return true;
    }
    return circles((Parametrisation.Circular) a, (Parametrisation.Circular) b, scale, out);
  }

  private static double tolerance(double scale, double x, double y) {
    return Intersections.rootTolerance(scale, x, y);
  }

  /** Whether a piece's parameter is within its range, widened by the tolerance along it. */
  private static boolean within(Straight s, double u, double tol) {
    double slack = tol / Math.hypot(s.dx(), s.dy());
    return u >= s.lo() - slack && u <= s.hi() + slack;
  }

  private static double clamp(Straight s, double u) {
    return Math.min(Math.max(u, s.lo()), s.hi());
  }

  private static boolean straights(
      Straight a, Straight b, double scale, List<Candidate> out, boolean swapped) {
    double cross = a.dx() * b.dy() - a.dy() * b.dx();
    double la = Math.hypot(a.dx(), a.dy());
    double lb = Math.hypot(b.dx(), b.dy());
    if (la == 0 || lb == 0) {
      return true;
    }
    double tol = tolerance(scale, a.px(), a.py());
    double qx = b.px() - a.px();
    double qy = b.py() - a.py();
    if (Math.abs(cross) <= Intersections.TANGENT * la * lb) {
      // parallel: an overlap when they lie on one line over a stretch
      double off = Math.abs(qx * a.dy() - qy * a.dx()) / la;
      if (off > tol) {
        return true;
      }
      double s = (qx * a.dx() + qy * a.dy()) / (la * la);
      double k = (b.dx() * a.dx() + b.dy() * a.dy()) / (la * la);
      double s0 = s + k * b.lo();
      double s1 = s + k * b.hi();
      double from = Math.max(a.lo(), Math.min(s0, s1));
      double to = Math.min(a.hi(), Math.max(s0, s1));
      return !((to - from) * la >= Intersections.OVERLAP_SPAN * scale);
    }
    double u = (qx * b.dy() - qy * b.dx()) / cross;
    double v = (qx * a.dy() - qy * a.dx()) / cross;
    if (!within(a, u, tol) || !within(b, v, tol)) {
      return true;
    }
    u = clamp(a, u);
    v = clamp(b, v);
    double x = a.px() + u * a.dx();
    double y = a.py() + u * a.dy();
    double t1 = a.offset() + u;
    double t2 = b.offset() + v;
    out.add(new Candidate(x, y, swapped ? t2 : t1, swapped ? t1 : t2, false, 0));
    return true;
  }

  private static void straightCircle(
      Straight s,
      Parametrisation.Circular c,
      double scale,
      List<Candidate> out,
      boolean circleFirst) {
    double tol = tolerance(scale, c.cx(), c.cy());
    double ls = Math.hypot(s.dx(), s.dy());
    if (ls == 0) {
      return;
    }
    if (c.r() <= tol) {
      // a circle within the tolerance is a point, which the library would refuse as a circle:
      // touched where the line passes within the tolerance of it
      double u = ((c.cx() - s.px()) * s.dx() + (c.cy() - s.py()) * s.dy()) / (ls * ls);
      double x = s.px() + u * s.dx();
      double y = s.py() + u * s.dy();
      if (Math.hypot(x - c.cx(), y - c.cy()) <= tol && within(s, u, tol)) {
        double ts = s.offset() + clamp(s, u);
        double tc = c.parameterOf(x, y);
        out.add(new Candidate(x, y, circleFirst ? tc : ts, circleFirst ? ts : tc, true, 0));
      }
      return;
    }
    Precision.DoubleEquivalence eq = Precision.doubleEquivalenceOfEpsilon(tol);
    Line line =
        Lines.fromPointAndDirection(
            Vector2D.of(s.px(), s.py()),
            Vector2D.of(s.dx() / ls, s.dy() / ls),
            Precision.doubleEquivalenceOfEpsilon(0));
    List<Vector2D> hits = Circle.from(Vector2D.of(c.cx(), c.cy()), c.r(), eq).intersections(line);
    boolean tangent = hits.size() == 1;
    double spread = tangent ? 2 * Math.sqrt(2 * c.r() * tol) : 0;
    for (Vector2D q : hits) {
      double u = ((q.getX() - s.px()) * s.dx() + (q.getY() - s.py()) * s.dy()) / (ls * ls);
      if (!within(s, u, tol)) {
        continue;
      }
      double ts = s.offset() + clamp(s, u);
      double tc = c.parameterOf(q.getX(), q.getY());
      out.add(
          new Candidate(
              q.getX(), q.getY(), circleFirst ? tc : ts, circleFirst ? ts : tc, tangent, spread));
    }
  }

  private static boolean circles(
      Parametrisation.Circular a, Parametrisation.Circular b, double scale, List<Candidate> out) {
    double dx = b.cx() - a.cx();
    double dy = b.cy() - a.cy();
    double d = Math.hypot(dx, dy);
    double tol = tolerance(scale, a.cx(), a.cy());
    if (d <= tol) {
      // concentric: one circle twice is an overlap, two different radii never meet
      return Math.abs(a.r() - b.r()) > tol;
    }
    double ux = dx / d;
    double uy = dy / d;
    double outer = d - (a.r() + b.r());
    double inner = d - Math.abs(a.r() - b.r());
    if (Math.abs(outer) <= tol || Math.abs(inner) <= tol) {
      // touching: from outside, toward b; from inside, toward the smaller circle's side
      double side = Math.abs(outer) <= tol || a.r() >= b.r() ? 1 : -1;
      double x = a.cx() + side * a.r() * ux;
      double y = a.cy() + side * a.r() * uy;
      double k =
          Math.abs(outer) <= tol
              ? (1 / a.r() + 1 / b.r()) / 2
              : Math.abs(1 / a.r() - 1 / b.r()) / 2;
      out.add(
          new Candidate(
              x, y, a.parameterOf(x, y), b.parameterOf(x, y), true, 2 * Math.sqrt(tol / k)));
      return true;
    }
    if (outer > 0 || inner < 0) {
      return true;
    }
    double along = (d * d + a.r() * a.r() - b.r() * b.r()) / (2 * d);
    double h = Math.sqrt(Math.max(0, a.r() * a.r() - along * along));
    for (int sign = -1; sign <= 1; sign += 2) {
      double x = a.cx() + along * ux - sign * h * uy;
      double y = a.cy() + along * uy + sign * h * ux;
      out.add(new Candidate(x, y, a.parameterOf(x, y), b.parameterOf(x, y), false, 0));
    }
    return true;
  }
}
