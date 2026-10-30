package mathcanvas.canvas.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntConsumer;
import mathcanvas.canvas.model.Families.Candidate;
import mathcanvas.engine.Cancellation;

/**
 * Intersections of two curves, and which candidate each member of an intersection family is
 * (docs/reference/canvas.md, **Object model**).
 *
 * <p><b>Candidates.</b> Seeds are every crossing of the two curves' sampled segments, every vertex
 * of either curve whose distance to the other is a local minimum within a segment's length and
 * below its neighbours by more than rounding (where a tangency hides between samples), at most one
 * per sampling step, and each member's anchor and approach. When both curves have a parameter each
 * seed is refined by a damped Gauss–Newton descent on {@code P₁(t₁) − P₂(t₂)} through the sampler,
 * within the seed's own segments first and then the domains, run until it stops improving and
 * abandoned after {@link #STALLED} steps in a row that shrink the gap by less than a tenth. It is a
 * root when the gap is within {@link #ROOT} of the scale (the window's diagonal) or of the rounding
 * of the coordinates, unless it shows two curves that only approach each other (stopped on the edge
 * of its range, still taking long steps, or a tangency band as long as an overlap with the gap not
 * closing), and a tangency when the tangents are parallel within {@link #TANGENT}. A tangency's
 * spread is its band: the length along the first curve over which the gap stays within the
 * tolerance, the same from every seed. Candidates within {@link Families#MERGE} of {@code
 * max(scale, |coordinates|)} are one root found twice; two tangencies whose bands overlap, or
 * between which the gap stays within the tolerance, are one multiple root. They are ordered by the
 * parameter on the first curve then the second, or by position when a curve has no parameter (its
 * candidates are the crossings of the samples, unrefined). Two curves overlap when they coincide to
 * about double precision ({@link #OVERLAP} of the coordinates) over a stretch of at least {@link
 * #OVERLAP_SPAN} of the scale: two parallel segments sharing such a stretch, or a run of three or
 * more consecutive vertices of one on the other spanning it whose separation at its ends is within
 * ten times its least (a contact of high order grows toward its ends).
 *
 * <p><b>Matching</b> keeps order along the first curve ({@link Families#assign}): between
 * tangencies the roots of a continuously deformed pair cannot exchange their order, so identity is
 * order, not distance.
 */
final class Intersections {

  /**
   * The gap, relative to the scale, within which two curves count as together: a tangency's band,
   * the stretch between two tangencies that are one root, and where a stalled refinement gives up.
   */
  static final double ROOT = 1e-9;

  /** The sine of the angle between the tangents at or below which a root is a tangency. */
  static final double TANGENT = 1e-6;

  /**
   * The largest distance from the other curve, relative to the vertex's own coordinates (not the
   * scale), of a vertex in an overlapping run: coincident to about double precision, so a contact
   * of high order is not an overlap (x⁹ against the axis comes within it only for |x| below about
   * 0.024).
   */
  static final double OVERLAP = 1e-13;

  /** The least span, relative to the scale, of a run of coincident vertices that is an overlap. */
  static final double OVERLAP_SPAN = 1e-2;

  /** Beyond this many seeds, seeds are clustered at the sampling step. */
  static final int MAX_SEEDS = 4096;

  /** The least scale: the window's diagonal, kept positive. */
  private static final double TINY = 1e-300;

  private static final int ITERATIONS = 100;

  /** Consecutive steps that shrink the gap by less than a tenth after which a seed is abandoned. */
  static final int STALLED = 5;

  private Intersections() {}

  /**
   * Where a refinement starts, and the parameter ranges it is held to first: the crossing segments
   * of a crossing, the neighbouring segments of a near miss, the domains for an anchor.
   */
  private record Seed(
      double x, double y, double t1, double t2, double lo1, double hi1, double lo2, double hi2) {}

  /**
   * Tracks a family to its places on the parents' new geometry.
   *
   * @param family the intersections of one ordered pair, in drawing order
   * @param shown the members defined after the previous recompute
   * @param a the first path's geometry
   * @param b the second path's geometry
   * @param sampler evaluates the paths
   * @param window gives the scale of the tolerances
   * @param cancellation checked per seed and per refinement step
   * @return one result per member, in order
   */
  static List<Recompute.Result> track(
      List<Intersection> family,
      Set<ObjectId> shown,
      Geometry.Curve a,
      Geometry.Curve b,
      Sampler sampler,
      Window window,
      Cancellation cancellation) {
    double scale = Math.max(window.diagonal(), TINY);
    List<Anchor> anchors = new ArrayList<>();
    List<Optional<Anchor>> held = new ArrayList<>();
    List<Integer> indexes = new ArrayList<>();
    List<Boolean> defined = new ArrayList<>();
    for (Intersection i : family) {
      i.anchor().ifPresent(anchors::add);
      held.add(i.anchor());
      indexes.add(i.index());
      defined.add(shown.contains(i.id()));
    }
    List<Candidate> cs = candidates(a, b, anchors, sampler, scale, cancellation).orElse(List.of());
    List<Optional<Families.Placement>> placed =
        Families.place(
            held,
            indexes,
            defined,
            cs,
            new Families.Positions(a, sampler, scale, cancellation),
            scale);
    List<Recompute.Result> out = new ArrayList<>();
    for (int m = 0; m < family.size(); m++) {
      Intersection i = family.get(m);
      out.add(
          placed
              .get(m)
              .map(
                  p ->
                      new Recompute.Result(
                          i.trackedTo(p.index(), p.anchor()),
                          new Geometry.Point(p.anchor().x(), p.anchor().y())))
              .orElse(new Recompute.Result(i, Geometry.UNDEFINED)));
    }
    return out;
  }

  /**
   * The gap within which two curves count as together, for a tangency's band and for whether two
   * tangencies are one: {@link #ROOT} of the scale, never below the rounding of the coordinates.
   */
  private static double rootTolerance(double scale, double x, double y) {
    return Math.max(ROOT * scale, 1e-14 * Math.max(Math.abs(x), Math.abs(y)));
  }

  /**
   * The candidates, merged and ordered; empty when the curves overlap.
   *
   * @param a the first curve
   * @param b the second curve
   * @param anchors the family's anchors, seeds of their own
   * @param sampler evaluates the curves
   * @param scale the length the tolerances are relative to
   * @param cancellation checked per seed and per refinement step
   * @return the candidates, or empty for an overlap
   */
  static Optional<List<Candidate>> candidates(
      Geometry.Curve a,
      Geometry.Curve b,
      List<Anchor> anchors,
      Sampler sampler,
      double scale,
      Cancellation cancellation) {
    Path.Parametrised pa = a.path() instanceof Path.Parametrised p ? p : null;
    Path.Parametrised pb = b.path() instanceof Path.Parametrised p ? p : null;
    boolean refine = pa != null && pb != null;
    Seeds seeds = new Seeds(a, b, scale, cancellation);
    if (!seeds.collect(refine)) {
      return Optional.empty();
    }
    List<Seed> all = seeds.clustered();
    if (refine) {
      for (Anchor an : anchors) {
        if (!Double.isNaN(an.t1()) && !Double.isNaN(an.t2())) {
          all.add(
              new Seed(
                  an.x(),
                  an.y(),
                  Math.clamp(an.t1(), pa.lo(), pa.hi()),
                  Math.clamp(an.t2(), pb.lo(), pb.hi()),
                  pa.lo(),
                  pa.hi(),
                  pb.lo(),
                  pb.hi()));
        }
        // and where it approached from: a root beside a multiple one, closer than a sampling
        // step, is found from the side the member came from
        if (an.approach().isPresent() && !Double.isNaN(an.approach().get().t())) {
          Approach ap = an.approach().get();
          double t2 =
              Paths.project(b, ap.x(), ap.y(), sampler, cancellation)
                  .map(Paths.Projection::t)
                  .orElse(Double.NaN);
          if (!Double.isNaN(t2)) {
            all.add(
                new Seed(
                    ap.x(),
                    ap.y(),
                    Math.clamp(ap.t(), pa.lo(), pa.hi()),
                    Math.clamp(t2, pb.lo(), pb.hi()),
                    pa.lo(),
                    pa.hi(),
                    pb.lo(),
                    pb.hi()));
          }
        }
      }
    }
    List<Candidate> roots = new ArrayList<>();
    double h1 = refine ? step(a) : Double.NaN;
    double h2 = refine ? step(b) : Double.NaN;
    for (Seed s : all) {
      cancellation.check();
      if (!refine) {
        roots.add(new Candidate(s.x(), s.y(), s.t1(), s.t2(), false, 0));
        continue;
      }
      // within the seed's own segments first, from the seed and then from the ends of its
      // segments (Newton's step from inside a segment that holds an extremum points away from a
      // root beyond it), so two roots between neighbouring samples are each found from their own
      // crossing; if that fails, over the whole domains
      double lo1 = Math.max(s.lo1(), pa.lo());
      double hi1 = Math.min(s.hi1(), pa.hi());
      double lo2 = Math.max(s.lo2(), pb.lo());
      double hi2 = Math.min(s.hi2(), pb.hi());
      Optional<Candidate> r = Optional.empty();
      if (lo1 <= hi1 && lo2 <= hi2) {
        double[][] starts = {{s.t1(), s.t2()}, {hi1, hi2}, {lo1, lo2}, {lo1, hi2}, {hi1, lo2}};
        for (int k = 0; k < starts.length && r.isEmpty(); k++) {
          r =
              refine(
                  pa,
                  pb,
                  starts[k][0],
                  starts[k][1],
                  lo1,
                  hi1,
                  lo2,
                  hi2,
                  h1,
                  h2,
                  sampler,
                  scale,
                  cancellation);
        }
      }
      if (r.isEmpty() && (lo1 > pa.lo() || hi1 < pa.hi() || lo2 > pb.lo() || hi2 < pb.hi())) {
        r =
            refine(
                pa,
                pb,
                Math.clamp(s.t1(), pa.lo(), pa.hi()),
                Math.clamp(s.t2(), pb.lo(), pb.hi()),
                pa.lo(),
                pa.hi(),
                pb.lo(),
                pb.hi(),
                h1,
                h2,
                sampler,
                scale,
                cancellation);
        // over the whole domains a seed between two curves that only approach each other can
        // run off after them: such a root is kept only near the samples
        if (r.isPresent() && !seeds.near(r.get().x(), r.get().y())) {
          r = Optional.empty();
        }
      }
      r.ifPresent(roots::add);
    }
    Comparator<Candidate> order =
        refine
            ? Comparator.comparingDouble(Candidate::t1).thenComparingDouble(Candidate::t2)
            : Comparator.comparingDouble(Candidate::x).thenComparingDouble(Candidate::y);
    roots.sort(order);
    // one root found from several seeds is one candidate; two tangencies whose bands overlap, or
    // between which the gap stays within the root tolerance, are one multiple root
    List<Candidate> merged = new ArrayList<>();
    for (Candidate r : roots) {
      int same = -1;
      boolean multiple = false;
      for (int k = 0; k < merged.size() && same < 0; k++) {
        Candidate m = merged.get(k);
        double d = Math.hypot(m.x() - r.x(), m.y() - r.y());
        if (d <= Families.tolerance(Families.MERGE, scale, r.x(), r.y())) {
          same = k;
        } else if (m.tangent()
            && r.tangent()
            && (d <= (m.spread() + r.spread()) / 2
                || connected(pa, pb, m, r, h2, sampler, scale, cancellation))) {
          same = k;
          multiple = true;
        }
      }
      if (same < 0) {
        merged.add(r);
      } else {
        Candidate m = merged.get(same);
        boolean tangent = m.tangent() || r.tangent() || multiple;
        merged.set(
            same,
            new Candidate(m.x(), m.y(), m.t1(), m.t2(), tangent, Math.max(m.spread(), r.spread())));
      }
    }
    return Optional.of(merged);
  }

  /**
   * Whether the gap stays within the root tolerance all along the first curve between two roots.
   */
  private static boolean connected(
      Path.Parametrised pa,
      Path.Parametrised pb,
      Candidate m,
      Candidate r,
      double h2,
      Sampler sampler,
      double scale,
      Cancellation cancellation) {
    for (int k = 1; k < 16; k++) {
      cancellation.check();
      double w = k / 16.0;
      double t1 = m.t1() + w * (r.t1() - m.t1());
      double t2 = m.t2() + w * (r.t2() - m.t2());
      Sample p = Paths.point(pa, t1, sampler);
      if (!p.isFinite()
          || gapAt(pa, pb, t1, t2, h2, sampler) > rootTolerance(scale, p.x(), p.y())) {
        return false;
      }
    }
    return true;
  }

  /** A curve's typical parameter step between samples, for the difference quotients. */
  private static double step(Geometry.Curve c) {
    double sum = 0;
    int n = 0;
    for (Polyline p : c.branches()) {
      for (int k = 0; k + 1 < p.size(); k++) {
        double d = Math.abs(p.t(k + 1) - p.t(k));
        if (Double.isFinite(d) && d > 0) {
          sum += d;
          n++;
        }
      }
    }
    return n == 0 ? Double.NaN : sum / n;
  }

  /**
   * Refines a seed by a damped Gauss–Newton (Levenberg–Marquardt) descent on the gap between the
   * two curves, run until a step no longer shrinks it, so a double root is approached as far as
   * double precision allows.
   */
  private static Optional<Candidate> refine(
      Path.Parametrised pa,
      Path.Parametrised pb,
      double t1,
      double t2,
      double lo1,
      double hi1,
      double lo2,
      double hi2,
      double h1,
      double h2,
      Sampler sampler,
      double scale,
      Cancellation cancellation) {
    t1 = Math.clamp(t1, lo1, hi1);
    t2 = Math.clamp(t2, lo2, hi2);
    Sample p = Paths.point(pa, t1, sampler);
    Sample q = Paths.point(pb, t2, sampler);
    if (!p.isFinite() || !q.isFinite()) {
      return Optional.empty();
    }
    double fx = p.x() - q.x();
    double fy = p.y() - q.y();
    double gap = Math.hypot(fx, fy);
    double lambda = 1e-3;
    int slow = 0;
    double lastStep = 0;
    double[] d1 = derivative(pa, t1, h1, sampler);
    double[] d2 = derivative(pb, t2, h2, sampler);
    for (int it = 0; it < ITERATIONS && gap > 0 && d1 != null && d2 != null; it++) {
      cancellation.check();
      // J = [d1, -d2]; solve (JᵀJ + λ diag JᵀJ) δ = −Jᵀ F
      double a11 = d1[0] * d1[0] + d1[1] * d1[1];
      double a22 = d2[0] * d2[0] + d2[1] * d2[1];
      double a12 = -(d1[0] * d2[0] + d1[1] * d2[1]);
      double g1 = -(d1[0] * fx + d1[1] * fy);
      double g2 = d2[0] * fx + d2[1] * fy;
      boolean improved = false;
      for (int tries = 0; tries < 30 && !improved; tries++) {
        double m11 = a11 * (1 + lambda) + Double.MIN_NORMAL;
        double m22 = a22 * (1 + lambda) + Double.MIN_NORMAL;
        double det = m11 * m22 - a12 * a12;
        if (det == 0 || !Double.isFinite(det)) {
          lambda *= 4;
          continue;
        }
        double s1 = Math.clamp(t1 + (g1 * m22 - a12 * g2) / det, lo1, hi1);
        double s2 = Math.clamp(t2 + (m11 * g2 - a12 * g1) / det, lo2, hi2);
        Sample np = Paths.point(pa, s1, sampler);
        Sample nq = Paths.point(pb, s2, sampler);
        double ngap =
            np.isFinite() && nq.isFinite()
                ? Math.hypot(np.x() - nq.x(), np.y() - nq.y())
                : Double.POSITIVE_INFINITY;
        if (ngap < gap) {
          lastStep = Math.hypot(np.x() - p.x(), np.y() - p.y());
          // a seed whose gap shrinks by less than a tenth, step after step, above the tolerance
          // has stalled at a near miss: no root there
          slow = ngap > 0.9 * gap && ngap > rootTolerance(scale, np.x(), np.y()) ? slow + 1 : 0;
          t1 = s1;
          t2 = s2;
          p = np;
          fx = np.x() - nq.x();
          fy = np.y() - nq.y();
          gap = ngap;
          lambda = Math.max(lambda / 3, 1e-12);
          improved = true;
        } else {
          lambda *= 4;
        }
      }
      if (!improved || slow >= STALLED) {
        break;
      }
      d1 = derivative(pa, t1, h1, sampler);
      d2 = derivative(pb, t2, h2, sampler);
    }
    double tol = rootTolerance(scale, p.x(), p.y());
    if (gap > tol) {
      return Optional.empty();
    }
    // two curves that only approach each other draw the refinement after them: it stops on the
    // edge of its range, or still taking long steps, with the gap small but not closing
    double e1 = 1e-6 * (hi1 - lo1);
    double e2 = 1e-6 * (hi2 - lo2);
    boolean atEdge =
        (t1 - lo1 <= e1 && lo1 > pa.lo())
            || (hi1 - t1 <= e1 && hi1 < pa.hi())
            || (t2 - lo2 <= e2 && lo2 > pb.lo())
            || (hi2 - t2 <= e2 && hi2 < pb.hi());
    if ((atEdge && gap > 1e-6 * tol) || lastStep > 1e3 * tol) {
      return Optional.empty();
    }
    boolean tangent = false;
    if (d1 != null && d2 != null) {
      double cross = d1[0] * d2[1] - d1[1] * d2[0];
      double norms = Math.hypot(d1[0], d1[1]) * Math.hypot(d2[0], d2[1]);
      tangent = norms > 0 && Math.abs(cross) <= TANGENT * norms;
    }
    double spread = tangent ? band(pa, pb, t1, t2, h2, sampler, scale, cancellation) : 0;
    // two curves running alongside each other within the tolerance without meeting: a band as long
    // as an overlap's stretch, and a gap that never closes (a root of high order closes it)
    if (spread >= OVERLAP_SPAN * scale && gap > 1e-3 * tol) {
      return Optional.empty();
    }
    return Optional.of(new Candidate(p.x(), p.y(), t1, t2, tangent, spread));
  }

  /**
   * The gap from the first curve's point at {@code t1} to the second curve: the distance to its
   * nearest point, found by a few Gauss–Newton steps along the second curve from {@code t2}.
   */
  private static double gapAt(
      Path.Parametrised pa,
      Path.Parametrised pb,
      double t1,
      double t2,
      double h2,
      Sampler sampler) {
    Sample p = Paths.point(pa, t1, sampler);
    if (!p.isFinite()) {
      return Double.POSITIVE_INFINITY;
    }
    double best = Double.POSITIVE_INFINITY;
    for (int k = 0; k < 8; k++) {
      Sample q = Paths.point(pb, t2, sampler);
      if (!q.isFinite()) {
        break;
      }
      double dx = q.x() - p.x();
      double dy = q.y() - p.y();
      best = Math.min(best, Math.hypot(dx, dy));
      double[] d = derivative(pb, t2, h2, sampler);
      double n2 = d == null ? 0 : d[0] * d[0] + d[1] * d[1];
      if (n2 == 0) {
        break;
      }
      double next = Math.clamp(t2 - (dx * d[0] + dy * d[1]) / n2, pb.lo(), pb.hi());
      if (next == t2) {
        break;
      }
      t2 = next;
    }
    return best;
  }

  /**
   * The band of a tangency: the length along the first curve over which the gap to the second stays
   * within the root tolerance, found by doubling a step each way from the root and then bisecting,
   * so every seed that reaches a multiple root reports the same band, wherever it started.
   */
  private static double band(
      Path.Parametrised pa,
      Path.Parametrised pb,
      double t1,
      double t2,
      double h2,
      Sampler sampler,
      double scale,
      Cancellation cancellation) {
    Sample at = Paths.point(pa, t1, sampler);
    double tol = rootTolerance(scale, at.x(), at.y());
    double length = 0;
    for (int dir = -1; dir <= 1; dir += 2) {
      double in = 0;
      double out = Double.NaN;
      double step = 1e-9 * Math.max(1, Math.abs(t1));
      for (int k = 0; k < 80; k++) {
        cancellation.check();
        double t = t1 + dir * step;
        if (!pa.contains(t) || gapAt(pa, pb, t, t2, h2, sampler) > tol) {
          out = step;
          break;
        }
        in = step;
        step *= 2;
      }
      if (Double.isNaN(out)) {
        continue;
      }
      for (int k = 0; k < 30; k++) {
        double mid = (in + out) / 2;
        double t = t1 + dir * mid;
        if (pa.contains(t) && gapAt(pa, pb, t, t2, h2, sampler) <= tol) {
          in = mid;
        } else {
          out = mid;
        }
      }
      Sample end = Paths.point(pa, t1 + dir * in, sampler);
      if (end.isFinite()) {
        length += Math.hypot(end.x() - at.x(), end.y() - at.y());
      }
    }
    return length;
  }

  /**
   * The tangent vector by a central difference over a thousandth of the sampling step, or a
   * second-order one-sided difference at a domain's end; exact for a line.
   */
  private static double[] derivative(Path.Parametrised p, double t, double step, Sampler sampler) {
    if (p.parametrisation() instanceof Parametrisation.Linear l) {
      return new double[] {l.dx(), l.dy()};
    }
    double h =
        Double.isNaN(step)
            ? 1e-6 * Math.max(1, Math.abs(t))
            : Math.max(1e-3 * step, 1e-12 * Math.abs(t));
    if (t - h >= p.lo() && t + h <= p.hi()) {
      Sample a = Paths.point(p, t - h, sampler);
      Sample b = Paths.point(p, t + h, sampler);
      return a.isFinite() && b.isFinite()
          ? new double[] {(b.x() - a.x()) / (2 * h), (b.y() - a.y()) / (2 * h)}
          : null;
    }
    double d = t - h < p.lo() ? h : -h;
    if (!p.contains(t + 2 * d)) {
      return null;
    }
    Sample a = Paths.point(p, t, sampler);
    Sample b = Paths.point(p, t + d, sampler);
    Sample c = Paths.point(p, t + 2 * d, sampler);
    if (!a.isFinite() || !b.isFinite() || !c.isFinite()) {
      return null;
    }
    return new double[] {
      (-3 * a.x() + 4 * b.x() - c.x()) / (2 * d), (-3 * a.y() + 4 * b.y() - c.y()) / (2 * d)
    };
  }

  /** A uniform grid over a curve's segments, each segment {t0, x0, y0, t1, x1, y1}. */
  private static final class Grid {

    final List<double[]> segs;
    final int n;
    final double minX;
    final double minY;
    final double cw;
    final double ch;
    final List<List<Integer>> cells = new ArrayList<>();
    final int[] seen;
    int stamp;

    Grid(Geometry.Curve c) {
      segs = new ArrayList<>();
      for (Polyline p : c.branches()) {
        for (int k = 0; k + 1 < p.size(); k++) {
          segs.add(new double[] {p.t(k), p.x(k), p.y(k), p.t(k + 1), p.x(k + 1), p.y(k + 1)});
        }
      }
      seen = new int[segs.size()];
      double x0 = Double.POSITIVE_INFINITY;
      double y0 = Double.POSITIVE_INFINITY;
      double x1 = Double.NEGATIVE_INFINITY;
      double y1 = Double.NEGATIVE_INFINITY;
      for (double[] s : segs) {
        x0 = Math.min(x0, Math.min(s[1], s[4]));
        x1 = Math.max(x1, Math.max(s[1], s[4]));
        y0 = Math.min(y0, Math.min(s[2], s[5]));
        y1 = Math.max(y1, Math.max(s[2], s[5]));
      }
      n = segs.isEmpty() ? 1 : Math.clamp((int) Math.sqrt(segs.size()), 1, 256);
      minX = x0;
      minY = y0;
      cw = Math.max((x1 - x0) / n, Double.MIN_NORMAL);
      ch = Math.max((y1 - y0) / n, Double.MIN_NORMAL);
      for (int k = 0; k < n * n; k++) {
        cells.add(new ArrayList<>());
      }
      for (int j = 0; j < segs.size(); j++) {
        double[] s = segs.get(j);
        int fj = j;
        cover(
            Math.min(s[1], s[4]),
            Math.max(s[1], s[4]),
            Math.min(s[2], s[5]),
            Math.max(s[2], s[5]),
            k -> cells.get(k).add(fj));
      }
    }

    private void cover(double x0, double x1, double y0, double y1, IntConsumer cell) {
      int a = cell(x0, minX, cw);
      int b = cell(x1, minX, cw);
      int c = cell(y0, minY, ch);
      int d = cell(y1, minY, ch);
      for (int cx = a; cx <= b; cx++) {
        for (int cy = c; cy <= d; cy++) {
          cell.accept(cy * n + cx);
        }
      }
    }

    private int cell(double v, double min, double size) {
      double k = Math.floor((v - min) / size);
      return (int) Math.clamp(k, 0, n - 1);
    }

    /** Each segment whose box may meet a rectangle, once. */
    void near(double x0, double x1, double y0, double y1, IntConsumer segment) {
      if (segs.isEmpty()) {
        return;
      }
      stamp++;
      cover(
          x0,
          x1,
          y0,
          y1,
          k -> {
            for (int j : cells.get(k)) {
              if (seen[j] != stamp) {
                seen[j] = stamp;
                segment.accept(j);
              }
            }
          });
    }
  }

  /** The seeds from the sampled segments and vertices. */
  private static final class Seeds {

    final Geometry.Curve a;
    final Geometry.Curve b;
    final double scale;
    final Cancellation cancellation;
    final List<Seed> crossings = new ArrayList<>();
    final List<Seed> near = new ArrayList<>();
    double lengthSum;
    int lengths;

    Seeds(Geometry.Curve a, Geometry.Curve b, double scale, Cancellation cancellation) {
      this.a = a;
      this.b = b;
      this.scale = scale;
      this.cancellation = cancellation;
    }

    /** Collects the seeds; false when the curves overlap. */
    boolean collect(boolean nearMisses) {
      Grid ga = new Grid(a);
      Grid gb = new Grid(b);
      for (int i = 0; i < ga.segs.size(); i++) {
        if ((i & 1023) == 0) {
          cancellation.check();
        }
        double[] s = ga.segs.get(i);
        lengthSum += Math.hypot(s[4] - s[1], s[5] - s[2]);
        lengths++;
        boolean[] overlap = {false};
        gb.near(
            Math.min(s[1], s[4]),
            Math.max(s[1], s[4]),
            Math.min(s[2], s[5]),
            Math.max(s[2], s[5]),
            j -> overlap[0] |= !pair(s, gb.segs.get(j)));
        if (overlap[0]) {
          return false;
        }
      }
      return vertices(a, gb, false, nearMisses) && vertices(b, ga, true, nearMisses);
    }

    /**
     * Seeds where a curve's vertex distance to the other curve is a local minimum within a
     * segment's length; false when a run of vertices lies on the other curve (an overlap).
     */
    private boolean vertices(Geometry.Curve c, Grid other, boolean second, boolean nearMisses) {
      for (Polyline p : c.branches()) {
        int n = p.size();
        double[] d = new double[n];
        double[] at = new double[n];
        double[] r = new double[n];
        double[][] span = new double[n][2];
        for (int k = 0; k < n; k++) {
          if ((k & 1023) == 0) {
            cancellation.check();
          }
          double before = k > 0 ? Math.hypot(p.x(k) - p.x(k - 1), p.y(k) - p.y(k - 1)) : 0;
          double after = k + 1 < n ? Math.hypot(p.x(k + 1) - p.x(k), p.y(k + 1) - p.y(k)) : 0;
          r[k] = Math.max(before, after);
          double x = p.x(k);
          double y = p.y(k);
          double[] best = {Double.POSITIVE_INFINITY, Double.NaN, Double.NaN, Double.NaN};
          other.near(
              x - r[k],
              x + r[k],
              y - r[k],
              y + r[k],
              j -> {
                double[] s = other.segs.get(j);
                double dx = s[4] - s[1];
                double dy = s[5] - s[2];
                double d2 = dx * dx + dy * dy;
                double w = d2 == 0 ? 0 : Math.clamp(((x - s[1]) * dx + (y - s[2]) * dy) / d2, 0, 1);
                double dist = Math.hypot(s[1] + w * dx - x, s[2] + w * dy - y);
                if (dist < best[0]) {
                  best[0] = dist;
                  best[1] = s[0] + w * (s[3] - s[0]);
                  double width = Math.abs(s[3] - s[0]);
                  best[2] = Math.min(s[0], s[3]) - width;
                  best[3] = Math.max(s[0], s[3]) + width;
                }
              });
          d[k] = best[0];
          at[k] = best[1];
          span[k][0] = best[2];
          span[k][1] = best[3];
        }
        // an overlap: three or more consecutive vertices on the other curve, spanning a stretch,
        // whose separation is about as small at the run's ends as anywhere in it (a contact of high
        // order grows from its middle to its ends; two coincident curves differ by rounding)
        int start = -1;
        for (int k = 0; k <= n; k++) {
          boolean on = k < n && d[k] <= OVERLAP * Math.max(Math.abs(p.x(k)), Math.abs(p.y(k)));
          if (on && start < 0) {
            start = k;
          } else if (!on && start >= 0) {
            int end = k - 1;
            if (end - start >= 2
                && Math.hypot(p.x(end) - p.x(start), p.y(end) - p.y(start))
                    >= OVERLAP_SPAN * scale) {
              double least = Double.POSITIVE_INFINITY;
              double size = 0;
              for (int j = start; j <= end; j++) {
                least = Math.min(least, d[j]);
                size = Math.max(size, Math.max(Math.abs(p.x(j)), Math.abs(p.y(j))));
              }
              double floor = Math.max(least, 1e-15 * size);
              if (Math.max(d[start], d[end]) <= 10 * floor) {
                return false;
              }
            }
            start = -1;
          }
        }
        if (!nearMisses) {
          continue;
        }
        // a minimum below its neighbours by more than rounding (not a ripple of rounding along two
        // near-parallel curves), at most one per sampling step
        double lastX = Double.NaN;
        double lastY = Double.NaN;
        for (int k = 0; k < n; k++) {
          double tol = OVERLAP * Math.max(Math.abs(p.x(k)), Math.abs(p.y(k)));
          boolean minimum =
              Double.isFinite(d[k])
                  && d[k] <= r[k]
                  && (k == 0 || d[k] < d[k - 1] - tol)
                  && (k + 1 == n || d[k] < d[k + 1] - tol);
          if (minimum && !(Math.hypot(p.x(k) - lastX, p.y(k) - lastY) <= r[k])) {
            lastX = p.x(k);
            lastY = p.y(k);
            double lo = Math.min(p.t(Math.max(k - 1, 0)), p.t(Math.min(k + 1, n - 1)));
            double hi = Math.max(p.t(Math.max(k - 1, 0)), p.t(Math.min(k + 1, n - 1)));
            near.add(
                second
                    ? new Seed(p.x(k), p.y(k), at[k], p.t(k), span[k][0], span[k][1], lo, hi)
                    : new Seed(p.x(k), p.y(k), p.t(k), at[k], lo, hi, span[k][0], span[k][1]));
          }
        }
      }
      return true;
    }

    /** Whether a point lies within the two curves' samples' bounds, widened by their size. */
    boolean near(double x, double y) {
      double x0 = Double.POSITIVE_INFINITY;
      double x1 = Double.NEGATIVE_INFINITY;
      double y0 = Double.POSITIVE_INFINITY;
      double y1 = Double.NEGATIVE_INFINITY;
      for (Geometry.Curve c : List.of(a, b)) {
        for (Polyline p : c.branches()) {
          for (int k = 0; k < p.size(); k++) {
            x0 = Math.min(x0, p.x(k));
            x1 = Math.max(x1, p.x(k));
            y0 = Math.min(y0, p.y(k));
            y1 = Math.max(y1, p.y(k));
          }
        }
      }
      double w = Math.max(x1 - x0, y1 - y0);
      return x >= x0 - w && x <= x1 + w && y >= y0 - w && y <= y1 + w;
    }

    /** The seeds, crossings first, clustered at the sampling step when there are too many. */
    List<Seed> clustered() {
      List<Seed> all = new ArrayList<>(crossings);
      all.addAll(near);
      if (all.size() <= MAX_SEEDS) {
        return all;
      }
      double h = lengths == 0 ? scale : Math.max(lengthSum / lengths, Double.MIN_NORMAL);
      Set<List<Long>> cells = new HashSet<>();
      List<Seed> out = new ArrayList<>();
      for (Seed c : all) {
        if (cells.add(List.of((long) Math.floor(c.x() / h), (long) Math.floor(c.y() / h)))) {
          out.add(c);
        }
      }
      return out;
    }

    /** Seeds a crossing of two segments; false when they overlap. */
    private boolean pair(double[] s, double[] o) {
      double ax = s[1];
      double ay = s[2];
      double dax = s[4] - ax;
      double day = s[5] - ay;
      double bx = o[1];
      double by = o[2];
      double dbx = o[4] - bx;
      double dby = o[5] - by;
      double la = Math.hypot(dax, day);
      double lb = Math.hypot(dbx, dby);
      double cross = dax * dby - day * dbx;
      double ex = bx - ax;
      double ey = by - ay;
      if (Math.abs(cross) <= 1e-12 * la * lb) {
        // parallel: an overlap when coincident to double precision over a stretch of at least
        // OVERLAP_SPAN of the scale (two lines; a sampled curve's short segments are left to the
        // run of vertices)
        double tol =
            OVERLAP
                * Math.max(
                    Math.max(Math.abs(ax), Math.abs(ay)), Math.max(Math.abs(bx), Math.abs(by)));
        if (la > 0 && Math.abs(ex * day - ey * dax) / la <= tol) {
          double u0 = (ex * dax + ey * day) / (la * la);
          double u1 = u0 + (dbx * dax + dby * day) / (la * la);
          double lo = Math.max(0, Math.min(u0, u1));
          double hi = Math.min(1, Math.max(u0, u1));
          if ((hi - lo) * la >= OVERLAP_SPAN * scale) {
            return false;
          }
        }
        if (cross == 0) {
          return true;
        }
      }
      double u = (ex * dby - ey * dbx) / cross;
      double v = (ex * day - ey * dax) / cross;
      if (u >= 0 && u <= 1 && v >= 0 && v <= 1) {
        crossings.add(
            new Seed(
                ax + u * dax,
                ay + u * day,
                s[0] + u * (s[3] - s[0]),
                o[0] + v * (o[3] - o[0]),
                Math.min(s[0], s[3]),
                Math.max(s[0], s[3]),
                Math.min(o[0], o[3]),
                Math.max(o[0], o[3])));
      }
      return true;
    }
  }
}
