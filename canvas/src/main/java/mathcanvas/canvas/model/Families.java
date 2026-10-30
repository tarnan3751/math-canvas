package mathcanvas.canvas.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.Cancellation;

/**
 * The tracking of a family of points on a curve, the intersections of one ordered pair of paths
 * ({@link Intersections}): given the places the members can take now, the candidates, which member
 * takes which, keeping their order along the first curve (docs/reference/canvas.md, <b>Object
 * model</b>), and the anchor each member keeps.
 */
final class Families {

  /**
   * The distance, relative to {@code max(scale, |coordinates|)}, within which two points are one.
   */
  static final double MERGE = 1e-7;

  /**
   * The cost, relative to the scale, of matching a member of a cluster to a candidate on the other
   * side from the one it approached from: enough to break a tie between coincident anchors, too
   * little to outweigh any distance.
   */
  static final double SIDE = 1e-9;

  private Families() {}

  /**
   * A place a family's member can take: where two curves meet, or a feature of a graph.
   *
   * @param x the abscissa
   * @param y the ordinate
   * @param t1 the parameter on the first curve, or NaN
   * @param t2 the parameter on the second curve, or NaN
   * @param tangent whether the curves touch there rather than cross
   * @param spread how far the refinement may be from the root: twice the distance it moved after
   *     the gap fell within the root tolerance, large at a multiple root, about zero at a simple
   *     one
   */
  record Candidate(double x, double y, double t1, double t2, boolean tangent, double spread) {}

  /**
   * Where a member is placed.
   *
   * @param index its index from now on
   * @param anchor its anchor from now on
   */
  record Placement(int index, Anchor anchor) {}

  /**
   * Places a family's members on the candidates ({@link #assign}). A member placed on a simple root
   * takes the candidate's index and records it as its approach; on a tangency it keeps its index
   * and its approach, unless it is new.
   *
   * @param anchors each member's anchor, in the family's order
   * @param indexes each member's index
   * @param shown whether each member was defined after the previous recompute
   * @param cs the candidates, ordered
   * @param pos positions along the first curve
   * @param scale the length the tolerances are relative to
   * @return each member's placement, empty for a member left undefined
   */
  static List<Optional<Placement>> place(
      List<Optional<Anchor>> anchors,
      List<Integer> indexes,
      List<Boolean> shown,
      List<Candidate> cs,
      Positions pos,
      double scale) {
    int[] slot =
        cs.isEmpty() ? filled(anchors.size()) : assign(anchors, indexes, shown, cs, pos, scale);
    List<Optional<Placement>> out = new ArrayList<>();
    for (int m = 0; m < anchors.size(); m++) {
      if (slot[m] < 0) {
        out.add(Optional.empty());
        continue;
      }
      Candidate c = cs.get(slot[m]);
      Optional<Anchor> was = anchors.get(m);
      Optional<Approach> approach =
          c.tangent()
              ? was.flatMap(Anchor::approach)
              : Optional.of(new Approach(finite(c.t1()), c.x(), c.y()));
      int index = c.tangent() && was.isPresent() ? indexes.get(m) : slot[m];
      out.add(
          Optional.of(
              new Placement(
                  index,
                  new Anchor(
                      c.x(), c.y(), finite(c.t1()), finite(c.t2()), c.tangent(), approach))));
    }
    return out;
  }

  private static int[] filled(int n) {
    int[] out = new int[n];
    Arrays.fill(out, -1);
    return out;
  }

  private static double finite(double t) {
    return Double.isFinite(t) ? t : Double.NaN;
  }

  static double tolerance(double factor, double scale, double x, double y) {
    return factor * Math.max(scale, Math.max(Math.abs(x), Math.abs(y)));
  }

  /**
   * Positions along the first curve: its parameter, or on a curve with no parameter the arc length
   * along its polylines (branch after branch, a point projected onto the nearest segment); with the
   * period when the curve is closed.
   */
  static final class Positions {

    final Geometry.Curve curve;
    final Sampler sampler;
    final Cancellation cancellation;
    final double period;

    /** The least and the greatest position the curve was sampled over, NaN with no samples. */
    final double lo;

    final double hi;

    /** The length of the curve's samples, branch after branch. */
    final double length;

    /** The points at those positions: the ends of the sampled range. */
    final Sample loEnd;

    final Sample hiEnd;

    private final double[][] cumulative;
    private final double[] offsets;

    Positions(Geometry.Curve curve, Sampler sampler, double scale, Cancellation cancellation) {
      this.curve = curve;
      this.sampler = sampler;
      this.cancellation = cancellation;
      List<Polyline> bs = curve.branches();
      cumulative = new double[bs.size()][];
      offsets = new double[bs.size()];
      double total = 0;
      for (int b = 0; b < bs.size(); b++) {
        Polyline p = bs.get(b);
        cumulative[b] = new double[p.size()];
        for (int k = 1; k < p.size(); k++) {
          cumulative[b][k] =
              cumulative[b][k - 1] + Math.hypot(p.x(k) - p.x(k - 1), p.y(k) - p.y(k - 1));
        }
        offsets[b] = total;
        total += cumulative[b][p.size() - 1];
      }
      double rangeLo = Double.NaN;
      double rangeHi = Double.NaN;
      Sample loPoint = Sample.UNDEFINED;
      Sample hiPoint = Sample.UNDEFINED;
      if (curve.path() instanceof Path.Parametrised) {
        for (Polyline p : bs) {
          for (int k = 0; k < p.size(); k++) {
            if (!(p.t(k) >= rangeLo)) {
              rangeLo = p.t(k);
              loPoint = new Sample(p.x(k), p.y(k));
            }
            if (!(p.t(k) <= rangeHi)) {
              rangeHi = p.t(k);
              hiPoint = new Sample(p.x(k), p.y(k));
            }
          }
        }
      } else if (!bs.isEmpty()) {
        Polyline a = bs.getFirst();
        Polyline z = bs.getLast();
        rangeLo = 0;
        rangeHi = total;
        loPoint = new Sample(a.x(0), a.y(0));
        hiPoint = new Sample(z.x(z.size() - 1), z.y(z.size() - 1));
      }
      length = total;
      lo = rangeLo;
      hi = rangeHi;
      loEnd = loPoint;
      hiEnd = hiPoint;
      if (curve.path() instanceof Path.Parametrised) {
        period = Families.period(curve, sampler, scale);
      } else if (bs.size() == 1) {
        Polyline p = bs.getFirst();
        int last = p.size() - 1;
        boolean closed =
            last > 0
                && Math.hypot(p.x(last) - p.x(0), p.y(last) - p.y(0))
                    <= tolerance(MERGE, scale, p.x(0), p.y(0));
        period = closed ? total : Double.NaN;
      } else {
        period = Double.NaN;
      }
    }

    /** The position of a point with a known parameter (NaN when it has none). */
    double of(double t, double x, double y) {
      if (curve.path() instanceof Path.Parametrised) {
        if (!Double.isNaN(t)) {
          return t;
        }
        return Paths.project(curve, x, y, sampler, cancellation)
            .map(Paths.Projection::t)
            .orElse(Double.NaN);
      }
      double best = Double.POSITIVE_INFINITY;
      double at = Double.NaN;
      List<Polyline> bs = curve.branches();
      for (int b = 0; b < bs.size(); b++) {
        Polyline p = bs.get(b);
        for (int k = 0; k + 1 < p.size(); k++) {
          double dx = p.x(k + 1) - p.x(k);
          double dy = p.y(k + 1) - p.y(k);
          double d2 = dx * dx + dy * dy;
          double w = d2 == 0 ? 0 : Math.clamp(((x - p.x(k)) * dx + (y - p.y(k)) * dy) / d2, 0, 1);
          double d = Math.hypot(p.x(k) + w * dx - x, p.y(k) + w * dy - y);
          if (d < best) {
            best = d;
            at = offsets[b] + cumulative[b][k] + w * (cumulative[b][k + 1] - cumulative[b][k]);
          }
        }
      }
      return at;
    }

    /** A position's offset from a reference, cyclic on a closed curve. */
    double offset(double s, double ref) {
      return Families.offset(s, ref, period);
    }

    /**
     * Whether a position lies outside the range the curve was sampled over, where a family's place
     * can have gone: never on a closed curve, which is sampled whole.
     */
    boolean outside(double s) {
      return !(Double.isFinite(period) && period > 0) && (s < lo || s > hi);
    }

    /**
     * The world distance from a point to the nearer end of the sampled range where the window cut
     * the curve: ∞ on a closed curve, on a curve with no parameter, and at an end of the path's own
     * domain (a segment's end, a graph's domain), where places do not come and go with the window.
     */
    double toEdge(double x, double y) {
      if (Double.isFinite(period) && period > 0
          || !loEnd.isFinite()
          || !(curve.path() instanceof Path.Parametrised p)) {
        return Double.POSITIVE_INFINITY;
      }
      double d = Double.POSITIVE_INFINITY;
      if (lo > p.lo()) {
        d = Math.hypot(x - loEnd.x(), y - loEnd.y());
      }
      if (hi < p.hi()) {
        d = Math.min(d, Math.hypot(x - hiEnd.x(), y - hiEnd.y()));
      }
      return d;
    }

    /** A position brought into one period, for ordering on a closed curve. */
    double wrapped(double s) {
      return Double.isFinite(period) && period > 0 ? s - period * Math.floor(s / period) : s;
    }
  }

  /**
   * Matches a family's members to the candidates, keeping their order along the first curve.
   *
   * <ol>
   *   <li>Members whose anchors coincide within the merge distance are a cluster, ordered by the
   *       side each approached from (the offset of its approach from the cluster's position), or by
   *       index without sides; the cluster is one block in the order.
   *   <li>The members with an anchor, ordered by their anchors' positions, are matched to the
   *       candidates ordered by theirs by an order-preserving matching: as many matches as there
   *       can be, then the least total distance, a cluster member charged {@link #SIDE} of the
   *       scale for a candidate on the other side from the one it approached from. On a closed
   *       curve every rotation of the candidates is tried.
   *   <li>A member left over shares the nearest tangency, every candidate being taken.
   *   <li>A member with no anchor takes the candidate at its index if it is free, else it shares
   *       the tangency nearest its index, if there is one.
   * </ol>
   *
   * @param anchors each member's anchor, in the family's order
   * @param indexes each member's index
   * @param shown whether each member was defined after the previous recompute
   * @param cs the candidates, ordered
   * @param pos positions along the first curve
   * @param scale the length the tolerances are relative to
   * @return for each member the index of its candidate, or −1
   */
  static int[] assign(
      List<Optional<Anchor>> anchors,
      List<Integer> indexes,
      List<Boolean> shown,
      List<Candidate> cs,
      Positions pos,
      double scale) {
    int k = anchors.size();
    int n = cs.size();
    int[] slot = filled(k);
    double[] cpos = new double[n];
    for (int c = 0; c < n; c++) {
      cpos[c] = pos.of(cs.get(c).t1(), cs.get(c).x(), cs.get(c).y());
    }
    // the anchored members, with their positions, clusters and sides
    List<Integer> anchored = new ArrayList<>();
    double[] apos = new double[k];
    for (int m = 0; m < k; m++) {
      Optional<Anchor> an = anchors.get(m);
      if (an.isPresent()) {
        anchored.add(m);
        apos[m] = pos.of(an.get().t1(), an.get().x(), an.get().y());
      }
    }
    if (!anchored.isEmpty()) {
      int[] cluster = new int[k];
      Arrays.fill(cluster, -1);
      double[] side = new double[k];
      List<List<Integer>> blocks = new ArrayList<>();
      for (int m : anchored) {
        if (cluster[m] >= 0) {
          continue;
        }
        Anchor an = anchors.get(m).orElseThrow();
        double tol = tolerance(MERGE, scale, an.x(), an.y());
        List<Integer> block = new ArrayList<>(List.of(m));
        cluster[m] = blocks.size();
        for (int o : anchored) {
          Anchor ao = anchors.get(o).orElseThrow();
          if (cluster[o] < 0 && Math.hypot(ao.x() - an.x(), ao.y() - an.y()) <= tol) {
            cluster[o] = blocks.size();
            block.add(o);
          }
        }
        boolean sides = block.size() > 1;
        for (int o : block) {
          Optional<Approach> ap = anchors.get(o).orElseThrow().approach();
          double sp = ap.map(a -> pos.of(a.t(), a.x(), a.y())).orElse(Double.NaN);
          side[o] = pos.offset(sp, apos[m]);
          sides &= !Double.isNaN(side[o]);
        }
        if (sides) {
          block.sort(
              Comparator.comparingDouble((Integer o) -> side[o])
                  .thenComparingInt(o -> indexes.get(o)));
        } else {
          block.sort(Comparator.comparingInt(o -> indexes.get(o)));
          for (int o : block) {
            side[o] = Double.NaN;
          }
        }
        blocks.add(block);
      }
      blocks.sort(Comparator.comparingDouble(b -> pos.wrapped(apos[b.getFirst()])));
      List<Integer> seq = new ArrayList<>();
      for (List<Integer> b : blocks) {
        seq.addAll(b);
      }
      Integer[] order = new Integer[n];
      for (int c = 0; c < n; c++) {
        order[c] = c;
      }
      Arrays.sort(order, Comparator.comparingDouble(c -> pos.wrapped(cpos[c])));
      double penalty = penalty(order, cs, pos, scale);
      int rotations = Double.isFinite(pos.period) ? Math.max(n, 1) : 1;
      int[] best = null;
      double[] bestScore = null;
      for (int r = 0; r < rotations; r++) {
        int[] cols = new int[n];
        for (int j = 0; j < n; j++) {
          cols[j] = order[(j + r) % n];
        }
        double[][] cost = new double[seq.size()][n];
        for (int i = 0; i < seq.size(); i++) {
          int m = seq.get(i);
          Anchor an = anchors.get(m).orElseThrow();
          boolean inCluster = blocks.get(cluster[m]).size() > 1;
          for (int j = 0; j < n; j++) {
            Candidate c = cs.get(cols[j]);
            // a member undefined after the last recompute re-attaches at the penalty besides
            cost[i][j] = Math.hypot(c.x() - an.x(), c.y() - an.y()) + (shown.get(m) ? 0 : penalty);
            if (inCluster && !Double.isNaN(side[m])) {
              double off = pos.offset(cpos[cols[j]], apos[m]);
              if (Math.signum(off) != Math.signum(side[m])) {
                cost[i][j] += SIDE * scale;
              }
            }
          }
        }
        // a member whose anchor left the sampled range may vanish, and a candidate near its edge be
        // new, each at the penalty; any other member is matched if it can be (the edge rule)
        boolean[] free = new boolean[seq.size()];
        for (int i = 0; i < seq.size(); i++) {
          int m = seq.get(i);
          free[i] = leaving(anchors.get(m).orElseThrow(), apos[m], pos, penalty);
        }
        double[] skip = new double[n];
        for (int j = 0; j < n; j++) {
          Candidate c = cs.get(cols[j]);
          skip[j] = penalty;
        }
        double[] score = new double[2];
        int[] match = match(cost, free, penalty, skip, score);
        if (best == null
            || score[0] > bestScore[0]
            || (score[0] == bestScore[0] && score[1] < bestScore[1])) {
          best = new int[match.length];
          for (int i = 0; i < match.length; i++) {
            best[i] = match[i] < 0 ? -1 : cols[match[i]];
          }
          bestScore = score;
        }
      }
      for (int i = 0; i < seq.size(); i++) {
        slot[seq.get(i)] = best[i];
      }
      // a member left over shares the nearest tangency, all candidates being taken
      for (int m : anchored) {
        if (slot[m] < 0 && !leaving(anchors.get(m).orElseThrow(), apos[m], pos, penalty)) {
          Anchor an = anchors.get(m).orElseThrow();
          double near = Double.POSITIVE_INFINITY;
          for (int c = 0; c < n; c++) {
            double d = Math.hypot(cs.get(c).x() - an.x(), cs.get(c).y() - an.y());
            if (cs.get(c).tangent() && d < near) {
              near = d;
              slot[m] = c;
            }
          }
        }
      }
    }
    Set<Integer> taken = new HashSet<>();
    for (int s : slot) {
      taken.add(s);
    }
    for (int m = 0; m < k; m++) {
      int index = indexes.get(m);
      if (anchors.get(m).isPresent()) {
        continue;
      }
      if (index < n && taken.add(index)) {
        slot[m] = index;
      } else {
        // its candidate is taken or missing: it may share the tangency nearest its index
        for (int c = 0; c < n; c++) {
          if (cs.get(c).tangent()
              && (slot[m] < 0 || Math.abs(c - index) < Math.abs(slot[m] - index))) {
            slot[m] = c;
          }
        }
      }
    }
    return slot;
  }

  /**
   * Whether a member may be leaving the family's places: its anchor lies outside the range the
   * curve was sampled over, or within the penalty of the range's end, where a place goes out
   * between one frame and the next.
   */
  private static boolean leaving(Anchor an, double position, Positions pos, double penalty) {
    return pos.outside(position) || pos.toEdge(an.x(), an.y()) <= penalty;
  }

  /**
   * The penalty: what a member free to leave costs unmatched, a candidate costs new, and a member
   * undefined after the last recompute costs besides its distance to re-attach. Half the smallest
   * world distance between adjacent candidates (across the seam on a closed curve), at most an
   * eighth of the sampled range's length, which alone sets it with fewer than two candidates; never
   * below the merge distance.
   */
  private static double penalty(Integer[] order, List<Candidate> cs, Positions pos, double scale) {
    int n = order.length;
    double eighth = pos.length / 8;
    if (n < 2) {
      return Math.max(eighth, MERGE * scale);
    }
    double gap = Double.POSITIVE_INFINITY;
    int pairs = Double.isFinite(pos.period) && pos.period > 0 ? n : n - 1;
    for (int j = 0; j < pairs; j++) {
      Candidate a = cs.get(order[j]);
      Candidate b = cs.get(order[(j + 1) % n]);
      gap = Math.min(gap, Math.hypot(b.x() - a.x(), b.y() - a.y()));
    }
    return Math.max(Math.min(gap / 2, eighth), MERGE * scale);
  }

  /**
   * The order-preserving matching of rows to columns that leaves the fewest rows unmatched that
   * must be matched, then costs least: a match its cost, a free row unmatched the penalty, a column
   * skipped its own skip cost. A dynamic programme over the two sequences, O(rows · columns).
   *
   * @param cost the cost of matching each row to each column
   * @param free whether each row may stay unmatched at the penalty
   * @param penalty the cost of a free row unmatched
   * @param skip the cost of each column skipped
   * @param score set to minus the number of rows unmatched that are not free, and the total cost
   * @return each row's column, or −1; the matched columns increase with the rows
   */
  static int[] match(
      double[][] cost, boolean[] free, double penalty, double[] skip, double[] score) {
    int m = cost.length;
    int n = skip.length;
    int[][] forced = new int[m + 1][n + 1];
    double[][] total = new double[m + 1][n + 1];
    byte[][] move = new byte[m + 1][n + 1];
    for (int i = 0; i <= m; i++) {
      for (int j = 0; j <= n; j++) {
        if (i == 0 && j == 0) {
          continue;
        }
        int bf = Integer.MAX_VALUE;
        double bt = Double.POSITIVE_INFINITY;
        byte bm = 0;
        if (i > 0) {
          bf = forced[i - 1][j] + (free[i - 1] ? 0 : 1);
          bt = total[i - 1][j] + (free[i - 1] ? penalty : 0);
          bm = 1; // the row stays unmatched
        }
        if (j > 0) {
          int f = forced[i][j - 1];
          double t = total[i][j - 1] + skip[j - 1];
          if (f < bf || f == bf && t < bt) {
            bf = f;
            bt = t;
            bm = 2; // the column is skipped
          }
        }
        if (i > 0 && j > 0) {
          int f = forced[i - 1][j - 1];
          double t = total[i - 1][j - 1] + cost[i - 1][j - 1];
          if (f < bf || f == bf && t < bt) {
            bf = f;
            bt = t;
            bm = 3;
          }
        }
        forced[i][j] = bf;
        total[i][j] = bt;
        move[i][j] = bm;
      }
    }
    int[] out = filled(m);
    for (int i = m, j = n; i > 0 || j > 0; ) {
      switch (move[i][j]) {
        case 1 -> i--;
        case 2 -> j--;
        default -> {
          out[i - 1] = j - 1;
          i--;
          j--;
        }
      }
    }
    score[0] = -forced[m][n];
    score[1] = total[m][n];
    return out;
  }

  /** The offset of a parameter from a reference, in (−period/2, period/2] on a closed curve. */
  static double offset(double t, double ref, double period) {
    double d = t - ref;
    if (Double.isFinite(period) && period > 0) {
      d -= period * Math.ceil(d / period - 0.5);
    }
    return d;
  }

  /**
   * The period of a closed parametrised curve, whose two ends are one point within the merge
   * distance; NaN for any other.
   */
  static double period(Geometry.Curve c, Sampler sampler, double scale) {
    if (!(c.path() instanceof Path.Parametrised p)
        || !Double.isFinite(p.lo())
        || !Double.isFinite(p.hi())
        || !(p.hi() > p.lo())) {
      return Double.NaN;
    }
    Sample a = Paths.point(p, p.lo(), sampler);
    Sample b = Paths.point(p, p.hi(), sampler);
    if (!a.isFinite() || !b.isFinite()) {
      return Double.NaN;
    }
    return Math.hypot(a.x() - b.x(), a.y() - b.y()) <= tolerance(MERGE, scale, a.x(), a.y())
        ? p.hi() - p.lo()
        : Double.NaN;
  }
}
