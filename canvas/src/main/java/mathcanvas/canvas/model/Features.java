package mathcanvas.canvas.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;

/**
 * Finds the roots and the local extrema of a function graph from its sampled polylines: a root
 * where the sampled ordinate changes sign between two samples (or is zero at one sample between two
 * that are not), an extremum where it turns, each refined on the compiled evaluator: by bisection
 * to a double's resolution, by golden section to 1e-12 of the abscissa's magnitude.
 *
 * <p>A root is refined by bisection on the function. An extremum is refined by bisection on the
 * derivative, the engine's derivative of the graph's body ({@link Derivative}, evaluated and
 * compiled by the sampler like any expression), between the samples either side of the turning
 * sample; where the derivative has no sign change there (a corner, as {@code abs(x)} at 0, or a
 * derivative the engine leaves undefined) by a golden-section search on the function instead. A
 * root where the graph touches the axis without crossing it ({@code x²} at 0) is the extremum there
 * when its refined ordinate is zero to within 1e-12 of the largest sampled ordinate. A sign change
 * across a pole is no root: the refined ordinate is then larger than either sample's. A run of two
 * or more zero samples is an interval where the graph is zero ({@code 0·x}, {@code floor(x)} on [0,
 * 1)), not a set of roots: it gives none, nor does a turn whose bracket holds one.
 *
 * <p>Plain {@code double} arithmetic on what the sampler gives; only function graphs have features.
 * A domain's end and a branch's end are never extrema.
 */
public final class Features {

  /**
   * The precision of an abscissa refined by golden section, relative to its magnitude; bisection
   * goes on to a double's resolution, so a root's ordinate is the function's rounding there.
   */
  public static final double PRECISION = 1e-12;

  /** Enough halvings to reach a double's resolution from any bracket. */
  private static final int STEPS = 2100;

  private static final double INV_PHI = (Math.sqrt(5) - 1) / 2;

  private Features() {}

  /**
   * A feature found.
   *
   * @param t the parameter, the graph's abscissa
   * @param x the abscissa
   * @param y the ordinate
   */
  public record Found(double t, double x, double y) {}

  /**
   * The features of one kind of a curve, in increasing order of the abscissa.
   *
   * @param curve the curve: a function graph's, else there are none
   * @param feature which features
   * @param sampler evaluates the graph and its derivative
   * @param cancellation checked every 1,024 samples and at every refinement
   * @return the features, possibly none
   */
  public static List<Found> of(
      Geometry.Curve curve, Feature feature, Sampler sampler, Cancellation cancellation) {
    if (!(curve.path() instanceof Path.Parametrised p
        && p.parametrisation() instanceof Parametrisation.Graph g)) {
      return List.of();
    }
    List<Found> out = new ArrayList<>();
    Function f = t -> sampler.point(g, t).y();
    Expr d = Derivative.of(g.body(), g.variable(), 1);
    Function slope = t -> sampler.value(d, Map.of(g.variable(), t));
    for (Polyline b : curve.branches()) {
      switch (feature) {
        case ROOT -> roots(b, f, slope, out, cancellation);
        case MIN, MAX -> {
          List<Turn> turns = new ArrayList<>();
          extrema(b, feature == Feature.MAX ? 1 : -1, f, slope, turns, cancellation);
          turns.forEach(e -> out.add(e.found()));
        }
      }
    }
    out.sort((a, b) -> Double.compare(a.t(), b.t()));
    return out;
  }

  /** A function of the abscissa. */
  @FunctionalInterface
  private interface Function {
    double at(double t);
  }

  private static void roots(
      Polyline b, Function f, Function slope, List<Found> out, Cancellation cancellation) {
    double largest = 0;
    for (int k = 0; k < b.size(); k++) {
      if (Double.isFinite(b.y(k))) {
        largest = Math.max(largest, Math.abs(b.y(k)));
      }
    }
    for (int k = 0; k < b.size(); k++) {
      if ((k & 1023) == 0) {
        cancellation.check();
      }
      double y = b.y(k);
      if (y == 0) {
        if (!zeroRun(b, k)) {
          out.add(new Found(b.t(k), b.x(k), 0));
        }
        continue;
      }
      if (k + 1 < b.size() && b.y(k + 1) != 0 && Math.signum(y) * Math.signum(b.y(k + 1)) < 0) {
        double t = bisect(f, b.t(k), b.t(k + 1), cancellation);
        double v = f.at(t);
        if (Math.abs(v) <= Math.max(Math.abs(y), Math.abs(b.y(k + 1)))) {
          out.add(new Found(t, t, v));
        }
      }
    }
    // a touching root: a minimum at or just above zero, or a maximum at or just below, with no
    // crossing in its bracket
    for (int sign : new int[] {1, -1}) {
      List<Turn> turns = new ArrayList<>();
      extrema(b, sign, f, slope, turns, cancellation);
      for (Turn e : turns) {
        double y = -sign * e.found().y();
        if (y >= 0
            && y <= PRECISION * largest
            && !crossed(out, e.lo(), e.hi())
            && !zeroRunWithin(b, e.lo(), e.hi())) {
          out.add(e.found());
        }
      }
    }
  }

  /**
   * Whether a zero sample is in a run of two or more: an interval where the graph is zero, not a
   * set of roots.
   */
  private static boolean zeroRun(Polyline b, int k) {
    return k > 0 && b.y(k - 1) == 0 || k + 1 < b.size() && b.y(k + 1) == 0;
  }

  /** Whether a run of zero samples lies within a bracket. */
  private static boolean zeroRunWithin(Polyline b, double lo, double hi) {
    for (int k = 0; k + 1 < b.size(); k++) {
      if (b.y(k) == 0 && b.y(k + 1) == 0 && b.t(k + 1) >= lo && b.t(k) <= hi) {
        return true;
      }
    }
    return false;
  }

  /** Whether a root found lies within a bracket. */
  private static boolean crossed(List<Found> roots, double lo, double hi) {
    return roots.stream().anyMatch(r -> r.t() >= lo && r.t() <= hi);
  }

  /**
   * An extremum with the bracket it was refined in.
   *
   * @param found the extremum
   * @param lo the bracket's least parameter
   * @param hi the bracket's greatest parameter
   */
  private record Turn(Found found, double lo, double hi) {}

  /**
   * The local maxima ({@code sign} 1) or minima (−1) of a polyline's interior: a sample at least as
   * high as one neighbour and higher than the other, a plateau counted once at its first sample.
   */
  private static void extrema(
      Polyline b, int sign, Function f, Function slope, List<Turn> out, Cancellation cancellation) {
    for (int k = 1; k + 1 < b.size(); k++) {
      if ((k & 1023) == 0) {
        cancellation.check();
      }
      double before = sign * b.y(k - 1);
      double at = sign * b.y(k);
      int j = k + 1;
      while (j + 1 < b.size() && sign * b.y(j) == at) {
        j++;
      }
      double after = sign * b.y(j);
      if (!(at > before && at > after)) {
        continue;
      }
      double lo = b.t(k - 1);
      double hi = b.t(j);
      double t;
      double s0 = slope.at(lo);
      double s1 = slope.at(hi);
      if (Math.signum(s0) * Math.signum(s1) < 0) {
        t = bisect(slope, lo, hi, cancellation);
      } else {
        t = golden(t0 -> -sign * f.at(t0), lo, hi, cancellation);
      }
      double y = f.at(t);
      if (Double.isFinite(y)) {
        out.add(new Turn(new Found(t, t, y), lo, hi));
      }
      k = j - 1;
    }
  }

  /** Whether a bracket is as narrow as the precision, or as a double allows. */
  private static boolean narrow(double lo, double hi) {
    double mid = lo + (hi - lo) / 2;
    return hi - lo <= PRECISION * Math.max(Math.abs(lo), Math.abs(hi)) || mid <= lo || mid >= hi;
  }

  /** Whether a bracket's midpoint is one of its ends: as narrow as a double allows. */
  private static boolean resolved(double lo, double hi) {
    double mid = lo + (hi - lo) / 2;
    return mid <= lo || mid >= hi;
  }

  /** A sign change of a function between two parameters, by bisection, to a double's resolution. */
  private static double bisect(Function f, double lo, double hi, Cancellation cancellation) {
    double flo = f.at(lo);
    for (int step = 0; step < STEPS && !resolved(lo, hi); step++) {
      cancellation.check();
      double mid = lo + (hi - lo) / 2;
      double fm = f.at(mid);
      if (fm == 0) {
        return mid;
      }
      if (!Double.isFinite(fm)) {
        break;
      }
      if (Math.signum(fm) == Math.signum(flo)) {
        lo = mid;
        flo = fm;
      } else {
        hi = mid;
      }
    }
    return lo + (hi - lo) / 2;
  }

  /** The minimum of a function between two parameters, by golden-section search. */
  private static double golden(Function f, double lo, double hi, Cancellation cancellation) {
    double a = lo;
    double b = hi;
    double c = b - INV_PHI * (b - a);
    double d = a + INV_PHI * (b - a);
    double fc = f.at(c);
    double fd = f.at(d);
    for (int step = 0; step < STEPS && !narrow(a, b); step++) {
      cancellation.check();
      if (fc < fd || Double.isNaN(fd)) {
        b = d;
        d = c;
        fd = fc;
        c = b - INV_PHI * (b - a);
        fc = f.at(c);
      } else {
        a = c;
        c = d;
        fc = fd;
        d = a + INV_PHI * (b - a);
        fd = f.at(d);
      }
      if (c >= d) {
        break;
      }
    }
    return a + (b - a) / 2;
  }
}
