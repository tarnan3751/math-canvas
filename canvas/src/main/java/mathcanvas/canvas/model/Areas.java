package mathcanvas.canvas.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.compile.Quadrature;

/**
 * The geometry of the shaded areas and the Riemann sums ({@link ShadedArea}, {@link RiemannSum}): a
 * {@link Geometry.ValuedRegion}, its rings built from the parent graphs' sampled polylines (an
 * area) or from the heights the sampler gives (a sum), its value from the sampler's quadrature, so
 * the model evaluates no expression itself.
 */
public final class Areas {

  /** Why a shaded area or a Riemann sum has no value. */
  public enum Problem {
    /** The bounds are not numbers with {@code from < to}, or lie outside a graph's domain. */
    BOUNDS,
    /** The number of pieces is not a whole number from 1 to {@link RiemannSum#MAX_PIECES}. */
    PIECES,
    /** An area's integral did not converge. */
    NO_VALUE,
    /** A Riemann sum's height is not a finite number. */
    HEIGHT
  }

  private Areas() {}

  /**
   * Why an area or a sum is undefined, for its row; the graphs' geometry as the last recompute left
   * it.
   *
   * @param o a shaded area or a Riemann sum
   * @param geometry the geometry of its parents
   * @param sampler the sampler
   * @return the problem, or empty when there is none or the object is of another kind
   */
  public static Optional<Problem> problem(
      CanvasObject o, Map<ObjectId, Geometry> geometry, Sampler sampler) {
    if (o instanceof ShadedArea a) {
      Optional<Bounds> b =
          bounds(sampler.value(a.from(), Map.of()), sampler.value(a.to(), Map.of()));
      return b.isEmpty()
              || !inside(geometry.get(a.upper()), b.get())
              || a.lower().isPresent() && !inside(geometry.get(a.lower().get()), b.get())
          ? Optional.of(Problem.BOUNDS)
          : Optional.of(Problem.NO_VALUE);
    }
    if (o instanceof RiemannSum s) {
      Optional<Bounds> b =
          bounds(sampler.value(s.from(), Map.of()), sampler.value(s.to(), Map.of()));
      if (b.isEmpty() || !inside(geometry.get(s.graph()), b.get())) {
        return Optional.of(Problem.BOUNDS);
      }
      return pieces(sampler.value(s.n(), Map.of())) == 0
          ? Optional.of(Problem.PIECES)
          : Optional.of(Problem.HEIGHT);
    }
    return Optional.empty();
  }

  private record Bounds(double from, double to) {}

  private static Optional<Bounds> bounds(double from, double to) {
    return Double.isFinite(from) && Double.isFinite(to) && from < to
        ? Optional.of(new Bounds(from, to))
        : Optional.empty();
  }

  /** The graph's parametrisation when its domain holds the bounds. */
  private static Optional<Path.Parametrised> graph(Geometry g, Bounds b) {
    return g instanceof Geometry.Curve c
            && c.path() instanceof Path.Parametrised p
            && p.parametrisation() instanceof Parametrisation.Graph
            && p.contains(b.from())
            && p.contains(b.to())
        ? Optional.of(p)
        : Optional.empty();
  }

  private static boolean inside(Geometry g, Bounds b) {
    return graph(g, b).isPresent();
  }

  /** {@code n} as a whole number of pieces, or 0 when it is not one in range. */
  private static int pieces(double n) {
    return n >= 1 && n <= RiemannSum.MAX_PIECES && n == Math.rint(n) ? (int) n : 0;
  }

  /**
   * A shaded area: the integral of the upper graph less the lower one over {@code [from, to]}, and
   * the ring between their polylines over the part of it they were sampled on (the x-axis below,
   * with no lower graph). Undefined where the bounds are not numbers with {@code from < to} inside
   * both domains, or the integral does not converge (a pole or a gap between them). Where the two
   * sides cross, the region is one ring per lobe between the crossings, so no ring crosses itself.
   */
  static Geometry area(
      ShadedArea a, Map<ObjectId, Geometry> parents, Sampler sampler, Cancellation cancellation) {
    Optional<Bounds> b = bounds(sampler.value(a.from(), Map.of()), sampler.value(a.to(), Map.of()));
    if (b.isEmpty()) {
      return Geometry.UNDEFINED;
    }
    Optional<Path.Parametrised> upper = graph(parents.get(a.upper()), b.get());
    Optional<Path.Parametrised> lower =
        a.lower().isEmpty() ? Optional.empty() : graph(parents.get(a.lower().get()), b.get());
    if (upper.isEmpty() || a.lower().isPresent() && lower.isEmpty()) {
      return Geometry.UNDEFINED;
    }
    double from = b.get().from();
    double to = b.get().to();
    Quadrature.Estimate top = sampler.integral(graphOf(upper.get()), from, to, cancellation);
    Quadrature.Estimate bottom =
        lower.isEmpty()
            ? new Quadrature.Estimate(0, 0, true)
            : sampler.integral(graphOf(lower.get()), from, to, cancellation);
    double value = top.value() - bottom.value();
    if (!top.converged() || !bottom.converged() || !Double.isFinite(value)) {
      return Geometry.UNDEFINED;
    }
    Geometry.Curve up = (Geometry.Curve) parents.get(a.upper());
    Optional<Geometry.Curve> down = a.lower().map(id -> (Geometry.Curve) parents.get(id));
    // the ring over the part of [from, to] every side was sampled on
    double lo = Math.max(from, sampledMin(up));
    double hi = Math.min(to, sampledMax(up));
    if (down.isPresent()) {
      lo = Math.max(lo, sampledMin(down.get()));
      hi = Math.min(hi, sampledMax(down.get()));
    }
    List<Polyline> rings =
        lo < hi ? lobes(up, upper.get(), down, lower, lo, hi, sampler) : List.of();
    return new Geometry.ValuedRegion(rings, value);
  }

  /**
   * A Riemann sum: the heights the sampler gives at its pieces, a rectangle per piece (a trapezoid
   * for {@link Quadrature.Method#TRAPEZOID}), and their sum. Undefined where the bounds are not
   * numbers with {@code from < to} inside the domain, {@code n} is not a whole number in range, or
   * a height is not finite.
   */
  static Geometry riemann(RiemannSum s, Map<ObjectId, Geometry> parents, Sampler sampler) {
    Optional<Bounds> b = bounds(sampler.value(s.from(), Map.of()), sampler.value(s.to(), Map.of()));
    int n = pieces(sampler.value(s.n(), Map.of()));
    if (b.isEmpty() || n == 0) {
      return Geometry.UNDEFINED;
    }
    Optional<Path.Parametrised> graph = graph(parents.get(s.graph()), b.get());
    if (graph.isEmpty()) {
      return Geometry.UNDEFINED;
    }
    double from = b.get().from();
    double to = b.get().to();
    double[] h = sampler.riemannHeights(graphOf(graph.get()), from, to, n, s.method());
    for (double y : h) {
      if (!Double.isFinite(y)) {
        return Geometry.UNDEFINED;
      }
    }
    boolean trapezoid = s.method() == Quadrature.Method.TRAPEZOID;
    double width = (to - from) / n;
    List<Polyline> rings = new ArrayList<>(n);
    double sum = trapezoid ? 0.5 * (h[0] + h[n]) : 0;
    for (int i = 0; i < n; i++) {
      double x0 = from + i * (to - from) / n;
      double x1 = from + (i + 1) * (to - from) / n;
      double left = h[i];
      double right = trapezoid ? h[i + 1] : h[i];
      if (trapezoid) {
        sum += i > 0 ? h[i] : 0;
      } else {
        sum += h[i];
      }
      rings.add(
          closed(
              new ArrayList<>(
                  List.of(
                      new double[] {x0, 0},
                      new double[] {x1, 0},
                      new double[] {x1, right},
                      new double[] {x0, left}))));
    }
    double value = sum * width;
    return Double.isFinite(value) ? new Geometry.ValuedRegion(rings, value) : Geometry.UNDEFINED;
  }

  private static Parametrisation.Graph graphOf(Path.Parametrised p) {
    return (Parametrisation.Graph) p.parametrisation();
  }

  private static double sampledMin(Geometry.Curve c) {
    double min = Double.POSITIVE_INFINITY;
    for (Polyline b : c.branches()) {
      min = Math.min(min, b.x(0));
    }
    return min;
  }

  private static double sampledMax(Geometry.Curve c) {
    double max = Double.NEGATIVE_INFINITY;
    for (Polyline b : c.branches()) {
      max = Math.max(max, b.x(b.size() - 1));
    }
    return max;
  }

  /**
   * The rings of an area from {@code lo} to {@code hi}: at every abscissa either side was sampled
   * at (and at the ends, from the sampler), the upper side's ordinate and the lower's (0 for the
   * axis), read off the polylines between samples; where the difference changes sign the region is
   * cut at the crossing, by linear interpolation (or at a sample where the sides meet), and each
   * lobe is a ring along the upper side and back along the lower one.
   */
  private static List<Polyline> lobes(
      Geometry.Curve up,
      Path.Parametrised pu,
      Optional<Geometry.Curve> down,
      Optional<Path.Parametrised> pd,
      double lo,
      double hi,
      Sampler sampler) {
    TreeSet<Double> xs = new TreeSet<>();
    xs.add(lo);
    xs.add(hi);
    collect(up, lo, hi, xs);
    down.ifPresent(c -> collect(c, lo, hi, xs));
    List<Polyline> out = new ArrayList<>();
    List<double[]> top = new ArrayList<>();
    List<double[]> bottom = new ArrayList<>();
    double px = Double.NaN;
    double pu0 = Double.NaN;
    double pl0 = Double.NaN;
    for (double x : xs) {
      double u = x == lo || x == hi ? Paths.point(pu, x, sampler).y() : at(up, x);
      double l =
          down.isEmpty()
              ? 0
              : x == lo || x == hi ? Paths.point(pd.get(), x, sampler).y() : at(down.get(), x);
      if (!Double.isFinite(u) || !Double.isFinite(l)) {
        continue;
      }
      double d = u - l;
      double pd0 = pu0 - pl0;
      if (!top.isEmpty() && pd0 * d < 0) {
        double f = pd0 / (pd0 - d);
        double cx = px + f * (x - px);
        double cy = pu0 + f * (u - pu0);
        top.add(new double[] {cx, cy});
        out.add(ring(top, bottom));
        top = new ArrayList<>();
        bottom = new ArrayList<>();
        top.add(new double[] {cx, cy});
      }
      top.add(new double[] {x, u});
      bottom.add(new double[] {x, l});
      if (d == 0 && top.size() > 1) {
        // the sides meet at a sample: the lobe ends there and the next starts from it
        out.add(ring(top, bottom));
        top = new ArrayList<>();
        bottom = new ArrayList<>();
        top.add(new double[] {x, u});
      }
      px = x;
      pu0 = u;
      pl0 = l;
    }
    if (top.size() + bottom.size() >= 3) {
      out.add(ring(top, bottom));
    }
    return out;
  }

  private static void collect(Geometry.Curve c, double lo, double hi, Set<Double> xs) {
    for (Polyline b : c.branches()) {
      for (int k = 0; k < b.size(); k++) {
        double x = b.x(k);
        if (x > lo && x < hi && Double.isFinite(b.y(k))) {
          xs.add(x);
        }
      }
    }
  }

  /**
   * A curve's ordinate at an abscissa, read off its samples by linear interpolation; NaN off them.
   */
  private static double at(Geometry.Curve c, double x) {
    for (Polyline b : c.branches()) {
      for (int k = 0; k + 1 < b.size(); k++) {
        double x0 = b.x(k);
        double x1 = b.x(k + 1);
        if (x0 <= x && x <= x1) {
          return x1 == x0 ? b.y(k) : b.y(k) + (x - x0) / (x1 - x0) * (b.y(k + 1) - b.y(k));
        }
      }
    }
    return Double.NaN;
  }

  /** One lobe: along the upper side, back along the lower one. */
  private static Polyline ring(List<double[]> top, List<double[]> bottom) {
    List<double[]> all = new ArrayList<>(top);
    all.addAll(bottom.reversed());
    return closed(all);
  }

  /** A closed polyline through the vertices, back to the first. */
  private static Polyline closed(List<double[]> vertices) {
    int n = vertices.size();
    double[] xs = new double[n + 1];
    double[] ys = new double[n + 1];
    for (int k = 0; k <= n; k++) {
      double[] v = vertices.get(k % n);
      xs[k] = v[0];
      ys[k] = v[1];
    }
    return Polyline.unparametrised(xs, ys);
  }
}
