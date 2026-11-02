package mathcanvas.canvas.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.number.RealNumeric;

/**
 * The standard {@link Recompute}: the geometry of every object kind (docs/reference/canvas.md,
 * **Object model**). Expressions are evaluated only through the sampler; lines, segments, vectors
 * and projections are plain arithmetic on the parents' points.
 */
public final class Recomputes {

  /**
   * The fraction of the window's size added on each side of what is sampled over the window, so a
   * pan by less than half a window does not leave a graph short, and an intersection near the edge
   * still has samples on both sides.
   */
  static final double MARGIN = 0.5;

  private static final Recompute STANDARD = Recomputes::recompute;

  private Recomputes() {}

  /**
   * The standard recompute.
   *
   * @return it
   */
  public static Recompute standard() {
    return STANDARD;
  }

  private static List<Recompute.Result> recompute(
      List<CanvasObject> group,
      Map<ObjectId, Geometry> parents,
      Sampler sampler,
      Window window,
      Cancellation cancellation) {
    if (group.getFirst() instanceof Intersection first) {
      List<Intersection> family = new ArrayList<>();
      for (CanvasObject o : group) {
        family.add((Intersection) o);
      }
      return Intersections.track(
          family,
          shown(group, parents),
          (Geometry.Curve) parents.get(first.first()),
          (Geometry.Curve) parents.get(first.second()),
          sampler,
          window,
          cancellation);
    }
    if (group.getFirst() instanceof CriticalPoint first) {
      List<CriticalPoint> family = new ArrayList<>();
      for (CanvasObject o : group) {
        family.add((CriticalPoint) o);
      }
      return CriticalPoints.track(
          family,
          shown(group, parents),
          (Geometry.Curve) parents.get(first.path()),
          sampler,
          window,
          cancellation);
    }
    return List.of(one(group.getFirst(), parents, sampler, window, cancellation));
  }

  /** The members of a family drawn after the previous recompute, as the state hands them in. */
  private static Set<ObjectId> shown(List<CanvasObject> group, Map<ObjectId, Geometry> geometry) {
    Set<ObjectId> out = new HashSet<>();
    for (CanvasObject o : group) {
      if (geometry.get(o.id()) instanceof Geometry.Point) {
        out.add(o.id());
      }
    }
    return out;
  }

  private static Recompute.Result one(
      CanvasObject object,
      Map<ObjectId, Geometry> parents,
      Sampler sampler,
      Window window,
      Cancellation cancellation) {
    Window wide = window.widened(MARGIN);
    return switch (object) {
      case FreePoint p -> new Recompute.Result(p, new Geometry.Point(p.x(), p.y()));
      case Text t -> new Recompute.Result(t, new Geometry.Point(t.x(), t.y()));
      case Slider s -> new Recompute.Result(s, new Geometry.Value(s.value().doubleValue()));
      case FunctionGraph f -> new Recompute.Result(f, graph(f, sampler, window, cancellation));
      case ParametricCurve p ->
          new Recompute.Result(
              p,
              bounded(
                  new Parametrisation.Parametric(p.x(), p.y(), p.parameter()),
                  p.domain(),
                  ParamKind.T,
                  sampler,
                  window,
                  cancellation));
      case PolarCurve p ->
          new Recompute.Result(
              p,
              bounded(
                  new Parametrisation.Polar(p.radius(), p.angle()),
                  p.domain(),
                  ParamKind.THETA,
                  sampler,
                  window,
                  cancellation));
      case ImplicitCurve c ->
          new Recompute.Result(
              c,
              new Geometry.Curve(
                  Path.POLYLINE_ONLY, sampler.contour(c.equation(), wide, cancellation)));
      case ShadedArea a -> new Recompute.Result(a, Areas.area(a, parents, sampler, cancellation));
      case RiemannSum s -> new Recompute.Result(s, Areas.riemann(s, parents, sampler));
      case InequalityRegion r ->
          new Recompute.Result(
              r, new Geometry.Region(sampler.region(r.inequality(), window, cancellation)));
      case Segment s ->
          new Recompute.Result(s, segment(point(parents, s.start()), point(parents, s.end())));
      case Line l -> new Recompute.Result(l, line(l, parents, sampler, wide));
      case Arrow a -> new Recompute.Result(a, Vectors.arrow(a, parents, sampler));
      case VectorSum v -> new Recompute.Result(v, Vectors.sum(v, parents));
      case Projection p -> new Recompute.Result(p, Vectors.projection(p, parents));
      case PointOnPath p ->
          pointOnPath(p, (Geometry.Curve) parents.get(p.path()), sampler, cancellation);
      case Intersection i -> throw new IllegalStateException("an intersection is a family");
      case CriticalPoint c -> throw new IllegalStateException("a critical point is a family");
    };
  }

  private static Geometry.Point point(Map<ObjectId, Geometry> parents, ObjectId id) {
    return (Geometry.Point) parents.get(id);
  }

  /**
   * A domain's end as a double: ±∞ for {@code ∞} and {@code -∞}, else the sampler's value. The ends
   * are sampled whether open or closed: at double precision the difference is a sample.
   */
  private static double end(Expr e, Sampler sampler) {
    if (e instanceof Constant c && c.kind() == Constant.Kind.INFINITY) {
      return Double.POSITIVE_INFINITY;
    }
    if (e instanceof Product p
        && p.factors().size() == 2
        && p.factors().contains(Constant.INFINITY)
        && p.factors().stream()
            .anyMatch(
                f -> f instanceof Num n && n.value() instanceof RealNumeric r && r.signum() < 0)) {
      return Double.NEGATIVE_INFINITY;
    }
    return sampler.value(e, Map.of());
  }

  private static Geometry graph(
      FunctionGraph f, Sampler sampler, Window window, Cancellation cancellation) {
    Window wide = window.widened(MARGIN);
    double lo = end(f.domain().lower(), sampler);
    double hi = end(f.domain().upper(), sampler);
    if (!(lo <= hi)) {
      return Geometry.UNDEFINED;
    }
    Path.Parametrised path =
        new Path.Parametrised(
            new Parametrisation.Graph(f.body(), f.variable()), lo, hi, ParamKind.X);
    double a = Math.max(lo, wide.minX());
    double b = Math.min(hi, wide.maxX());
    List<Polyline> branches =
        a <= b
            ? sampler.sample(
                (Parametrisation.Expressed) path.parametrisation(), a, b, window, cancellation)
            : List.of();
    return new Geometry.Curve(path, branches);
  }

  private static Geometry bounded(
      Parametrisation.Expressed p,
      Interval domain,
      ParamKind kind,
      Sampler sampler,
      Window window,
      Cancellation cancellation) {
    double lo = end(domain.lower(), sampler);
    double hi = end(domain.upper(), sampler);
    if (!Double.isFinite(lo) || !Double.isFinite(hi) || lo > hi) {
      return Geometry.UNDEFINED;
    }
    return new Geometry.Curve(
        new Path.Parametrised(p, lo, hi, kind), sampler.sample(p, lo, hi, window, cancellation));
  }

  private static Geometry segment(Geometry.Point a, Geometry.Point b) {
    Parametrisation.Linear l =
        new Parametrisation.Linear(a.x(), a.y(), b.x() - a.x(), b.y() - a.y());
    return new Geometry.Curve(
        new Path.Parametrised(l, 0, 1, ParamKind.LINE),
        List.of(
            new Polyline(
                new double[] {0, 1}, new double[] {a.x(), b.x()}, new double[] {a.y(), b.y()})));
  }

  private static Geometry line(
      Line line, Map<ObjectId, Geometry> parents, Sampler sampler, Window wide) {
    Parametrisation.Linear l =
        switch (line.definition()) {
          case Line.Through t -> {
            Geometry.Point a = point(parents, t.a());
            Geometry.Point b = point(parents, t.b());
            double scale =
                Math.max(1, Math.max(Math.hypot(a.x(), a.y()), Math.hypot(b.x(), b.y())));
            if (Math.hypot(b.x() - a.x(), b.y() - a.y()) <= 1e-12 * scale) {
              yield null;
            }
            yield new Parametrisation.Linear(a.x(), a.y(), b.x() - a.x(), b.y() - a.y());
          }
          case Line.PointSlope s -> {
            Geometry.Point p = point(parents, s.point());
            double m = sampler.value(s.slope(), Map.of(s.at(), p.x()));
            yield Double.isFinite(m) ? new Parametrisation.Linear(p.x(), p.y(), 1, m) : null;
          }
        };
    if (l == null) {
      return Geometry.UNDEFINED;
    }
    Path.Parametrised path =
        new Path.Parametrised(
            l, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, ParamKind.LINE);
    return new Geometry.Curve(path, clip(l, wide).map(List::of).orElse(List.of()));
  }

  /** The part of a line inside a window (Liang–Barsky), as a two-vertex polyline. */
  private static Optional<Polyline> clip(Parametrisation.Linear l, Window w) {
    double t0 = Double.NEGATIVE_INFINITY;
    double t1 = Double.POSITIVE_INFINITY;
    double[][] sides = {
      {-l.dx(), l.px() - w.minX()},
      {l.dx(), w.maxX() - l.px()},
      {-l.dy(), l.py() - w.minY()},
      {l.dy(), w.maxY() - l.py()}
    };
    for (double[] side : sides) {
      double p = side[0];
      double q = side[1];
      if (p == 0) {
        if (q < 0) {
          return Optional.empty();
        }
      } else if (p < 0) {
        t0 = Math.max(t0, q / p);
      } else {
        t1 = Math.min(t1, q / p);
      }
    }
    if (!(t0 <= t1)) {
      return Optional.empty();
    }
    Sample a = l.at(t0);
    Sample b = l.at(t1);
    return Optional.of(
        new Polyline(
            new double[] {t0, t1}, new double[] {a.x(), b.x()}, new double[] {a.y(), b.y()}));
  }

  /**
   * A point on a path (docs/reference/canvas.md, **Object model**, rules P1 to P4; P4, an undefined
   * path, is the graph's): at its parameter when the path's parameter is of its kind; else its last
   * position projected, once onto a path redefined with another kind of parameter, at every
   * recompute onto a path with none.
   */
  private static Recompute.Result pointOnPath(
      PointOnPath p, Geometry.Curve c, Sampler sampler, Cancellation cancellation) {
    if (p.at().isPresent()) {
      // at an expression: its value is the parameter, in the path's own kind
      double t = sampler.value(p.at().get(), Map.of());
      if (!(c.path() instanceof Path.Parametrised q) || !Double.isFinite(t)) {
        return new Recompute.Result(p, Geometry.UNDEFINED);
      }
      PointOnPath placed = p.withParameter(q.kind(), t);
      Sample at = Paths.point(q, t, sampler);
      return at.isFinite()
          ? new Recompute.Result(
              placed.landedAt(at.x(), at.y()), new Geometry.Point(at.x(), at.y()))
          : new Recompute.Result(placed, Geometry.UNDEFINED);
    }
    if (c.path() instanceof Path.Parametrised q && q.kind() == p.kind()) {
      Sample at = Paths.point(q, p.parameter(), sampler);
      return at.isFinite()
          ? new Recompute.Result(p.landedAt(at.x(), at.y()), new Geometry.Point(at.x(), at.y()))
          : new Recompute.Result(p, Geometry.UNDEFINED);
    }
    if (Double.isNaN(p.lastX())) {
      return new Recompute.Result(p, Geometry.UNDEFINED);
    }
    Optional<Paths.Projection> on = Paths.project(c, p.lastX(), p.lastY(), sampler, cancellation);
    if (on.isEmpty()) {
      return new Recompute.Result(p, Geometry.UNDEFINED);
    }
    Paths.Projection r = on.get();
    ParamKind kind = c.path() instanceof Path.Parametrised q ? q.kind() : ParamKind.NONE;
    double t = kind == ParamKind.NONE ? Double.NaN : r.t();
    return new Recompute.Result(
        p.withParameter(kind, t).landedAt(r.x(), r.y()), new Geometry.Point(r.x(), r.y()));
  }
}
