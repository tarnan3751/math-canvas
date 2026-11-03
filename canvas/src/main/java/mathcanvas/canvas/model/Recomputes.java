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
 * **Object model**). Expressions are evaluated only through the sampler; lines, segments, rays,
 * circles, polygons, angles, vectors and projections are plain arithmetic on the parents' points
 * ({@link Constructions}); fields are marks at a grid ({@link FieldGrid}), contour plots levels
 * traced by the sampler ({@link Contours}); the statistical plots are counted and summarised from
 * their data ({@link Statistics}).
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
      case Ray r ->
          new Recompute.Result(
              r, ray(point(parents, r.start()), point(parents, r.through()), wide));
      case Circle c -> new Recompute.Result(c, circle(c, parents, sampler, window, wide));
      case Midpoint m ->
          new Recompute.Result(
              m, Constructions.midpoint(point(parents, m.a()), point(parents, m.b())));
      case Polygon p -> new Recompute.Result(p, polygon(p, parents, sampler));
      case Angle a ->
          new Recompute.Result(
              a,
              Constructions.angle(
                      point(parents, a.a()), point(parents, a.vertex()), point(parents, a.c()))
                  .<Geometry>map(g -> g)
                  .orElse(Geometry.UNDEFINED));
      case Arrow a -> new Recompute.Result(a, Vectors.arrow(a, parents, sampler));
      case VectorSum v -> new Recompute.Result(v, Vectors.sum(v, parents));
      case Projection p -> new Recompute.Result(p, Vectors.projection(p, parents));
      case Transform t -> new Recompute.Result(t, Images.of(t, parents, sampler, window, wide));
      case Distance d -> new Recompute.Result(d, Measures.distance(d, parents));
      case Area a -> new Recompute.Result(a, Measures.area(parents.get(a.of())));
      case Slope s -> new Recompute.Result(s, Measures.slope(parents.get(s.of())));
      case Locus l -> throw new IllegalStateException("a locus is traced by the canvas state");
      case VectorField v ->
          new Recompute.Result(v, FieldGrid.vectors(v, sampler, window, cancellation));
      case SlopeField s ->
          new Recompute.Result(s, FieldGrid.slopes(s, sampler, window, cancellation));
      case SolutionCurve s ->
          new Recompute.Result(s, solution(s, parents, sampler, wide, cancellation));
      case ContourPlot c ->
          new Recompute.Result(c, Contours.of(c, sampler, window, wide, cancellation));
      case Histogram h -> new Recompute.Result(h, Statistics.histogram(h, sampler, cancellation));
      case BarChart b -> new Recompute.Result(b, Statistics.barChart(b, sampler, cancellation));
      case BoxPlot b -> new Recompute.Result(b, Statistics.boxPlot(b, sampler, cancellation));
      case ScatterPlot p -> new Recompute.Result(p, Statistics.scatter(p, sampler, cancellation));
      case SpaceObject s -> new Recompute.Result(s, Spaces.of(s, parents, sampler, cancellation));
      case PointOnPath p ->
          pointOnPath(p, (Geometry.Curve) parents.get(p.path()), sampler, cancellation);
      case Intersection i -> throw new IllegalStateException("an intersection is a family");
      case CriticalPoint c -> throw new IllegalStateException("a critical point is a family");
    };
  }

  /**
   * A solution curve: the slope field's slope integrated through the point over the widened window,
   * undefined where the slope has no value at the point.
   */
  private static Geometry solution(
      SolutionCurve s,
      Map<ObjectId, Geometry> parents,
      Sampler sampler,
      Window wide,
      Cancellation cancellation) {
    Geometry.Point p = point(parents, s.point());
    if (!(parents.get(s.field()) instanceof Geometry.Field f) || f.slope().isEmpty()) {
      return Geometry.UNDEFINED;
    }
    List<Polyline> lines = sampler.solution(f.slope().get(), p.x(), p.y(), wide, cancellation);
    return lines.isEmpty() ? Geometry.UNDEFINED : new Geometry.Curve(Path.POLYLINE_ONLY, lines);
  }

  private static Geometry.Point point(Map<ObjectId, Geometry> parents, ObjectId id) {
    return (Geometry.Point) parents.get(id);
  }

  /**
   * A domain's end as a double: ±∞ for {@code ∞} and {@code -∞}, else the sampler's value. The ends
   * are sampled whether open or closed: at double precision the difference is a sample.
   */
  static double end(Expr e, Sampler sampler) {
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
    Optional<Parametrisation.Linear> l =
        switch (line.definition()) {
          case Line.Through t ->
              Constructions.through(point(parents, t.a()), point(parents, t.b()));
          case Line.PointSlope s -> {
            Geometry.Point p = point(parents, s.point());
            double m = sampler.value(s.slope(), Map.of(s.at(), p.x()));
            yield Double.isFinite(m)
                ? Optional.of(new Parametrisation.Linear(p.x(), p.y(), 1, m))
                : Optional.empty();
          }
          case Line.Perpendicular p ->
              Constructions.perpendicular(point(parents, p.point()), parents.get(p.to()));
          case Line.Parallel p ->
              Constructions.parallel(point(parents, p.point()), parents.get(p.to()));
          case Line.PerpendicularBisector b ->
              Constructions.bisector(point(parents, b.a()), point(parents, b.b()));
          case Line.AngleBisector b ->
              Constructions.angleBisector(
                  point(parents, b.a()), point(parents, b.vertex()), point(parents, b.c()));
          case Line.CircleTangent t ->
              Constructions.circle(parents.get(t.circle()))
                  .flatMap(c -> Constructions.tangent(c, point(parents, t.point()), t.index()));
        };
    if (l.isEmpty()) {
      return Geometry.UNDEFINED;
    }
    Path.Parametrised path =
        new Path.Parametrised(
            l.get(), Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, ParamKind.LINE);
    return new Geometry.Curve(path, clip(l.get(), path.lo(), wide).map(List::of).orElse(List.of()));
  }

  /** A ray from a point through another, drawn clipped to the widened window. */
  private static Geometry ray(Geometry.Point a, Geometry.Point b, Window wide) {
    Optional<Parametrisation.Linear> l = Constructions.through(a, b);
    if (l.isEmpty()) {
      return Geometry.UNDEFINED;
    }
    Path.Parametrised path =
        new Path.Parametrised(l.get(), 0, Double.POSITIVE_INFINITY, ParamKind.LINE);
    return new Geometry.Curve(path, clip(l.get(), 0, wide).map(List::of).orElse(List.of()));
  }

  private static Geometry circle(
      Circle circle, Map<ObjectId, Geometry> parents, Sampler sampler, Window window, Window wide) {
    Optional<Parametrisation.Circular> c =
        switch (circle.definition()) {
          case Circle.CenterPoint d -> {
            Geometry.Point o = point(parents, d.center());
            Geometry.Point p = point(parents, d.through());
            yield Constructions.circle(o, Math.hypot(p.x() - o.x(), p.y() - o.y()));
          }
          case Circle.CenterRadius d ->
              Constructions.circle(point(parents, d.center()), sampler.value(d.radius(), Map.of()));
          case Circle.ThreePoints d ->
              Constructions.circle(
                  point(parents, d.a()), point(parents, d.b()), point(parents, d.c()));
        };
    return c.<Geometry>map(
            k ->
                new Geometry.Curve(
                    new Path.Parametrised(k, 0, 2 * Math.PI, ParamKind.ANGLE),
                    Shapes.circle(k, window, wide)))
        .orElse(Geometry.UNDEFINED);
  }

  private static Geometry polygon(
      Polygon polygon, Map<ObjectId, Geometry> parents, Sampler sampler) {
    List<Geometry.Point> vertices =
        switch (polygon.definition()) {
          case Polygon.Vertices v -> {
            List<Geometry.Point> out = new ArrayList<>();
            for (ObjectId id : v.points()) {
              out.add(point(parents, id));
            }
            yield out;
          }
          case Polygon.Regular r -> {
            double n = sampler.value(r.n(), Map.of());
            yield n >= 3 && n <= Polygon.MAX_SIDES && n == Math.rint(n)
                ? Constructions.regular(point(parents, r.a()), point(parents, r.b()), (int) n)
                    .orElse(List.of())
                : List.of();
          }
        };
    if (Constructions.fewerThanThree(vertices)) {
      return Geometry.UNDEFINED;
    }
    double[] xs = new double[vertices.size()];
    double[] ys = new double[vertices.size()];
    for (int k = 0; k < xs.length; k++) {
      xs[k] = vertices.get(k).x();
      ys[k] = vertices.get(k).y();
    }
    Parametrisation.Polygonal g = new Parametrisation.Polygonal(xs, ys);
    return new Geometry.Curve(
        new Path.Parametrised(g, 0, xs.length, ParamKind.PERIMETER), List.of(Shapes.polygon(g)));
  }

  /**
   * The part of a line from a parameter on (−∞ for a whole line, 0 for a ray) inside a window
   * (Liang–Barsky), as a two-vertex polyline.
   */
  static Optional<Polyline> clip(Parametrisation.Linear l, double from, Window w) {
    double t0 = from;
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
