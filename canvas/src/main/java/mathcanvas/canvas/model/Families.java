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
   * @param cs the candidates, ordered
   * @param pos positions along the first curve
   * @param scale the length the tolerances are relative to
   * @return each member's placement, empty for a member left undefined
   */
  static List<Optional<Placement>> place(
      List<Optional<Anchor>> anchors,
      List<Integer> indexes,
      List<Candidate> cs,
      Positions pos,
      double scale) {
    int[] slot = cs.isEmpty() ? filled(anchors.size()) : assign(anchors, indexes, cs, pos, scale);
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
   * @param cs the candidates, ordered
   * @param pos positions along the first curve
   * @param scale the length the tolerances are relative to
   * @return for each member the index of its candidate, or −1
   */
  static int[] assign(
      List<Optional<Anchor>> anchors,
      List<Integer> indexes,
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
            cost[i][j] = Math.hypot(c.x() - an.x(), c.y() - an.y());
            if (inCluster && !Double.isNaN(side[m])) {
              double off = pos.offset(cpos[cols[j]], apos[m]);
              if (Math.signum(off) != Math.signum(side[m])) {
                cost[i][j] += SIDE * scale;
              }
            }
          }
        }
        double[] score = new double[2];
        int[] match = monotone(cost, score);
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
        if (slot[m] < 0) {
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
   * The order-preserving matching of rows to columns with the most matches, then the least total
   * cost: a dynamic programme over the two sequences, O(rows · columns).
   *
   * @param cost the cost of matching each row to each column
   * @param score set to the number of matches and their total cost
   * @return each row's column, or −1; the matched columns increase with the rows
   */
  static int[] monotone(double[][] cost, double[] score) {
    int m = cost.length;
    int n = m == 0 ? 0 : cost[0].length;
    int[][] count = new int[m + 1][n + 1];
    double[][] total = new double[m + 1][n + 1];
    byte[][] move = new byte[m + 1][n + 1];
    for (int i = 0; i <= m; i++) {
      for (int j = 0; j <= n; j++) {
        if (i == 0 && j == 0) {
          continue;
        }
        int bc = -1;
        double bt = 0;
        byte bm = 0;
        if (i > 0) {
          bc = count[i - 1][j];
          bt = total[i - 1][j];
          bm = 1; // the row stays unmatched
        }
        if (j > 0 && better(count[i][j - 1], total[i][j - 1], bc, bt)) {
          bc = count[i][j - 1];
          bt = total[i][j - 1];
          bm = 2; // the column is skipped
        }
        if (i > 0 && j > 0) {
          int c = count[i - 1][j - 1] + 1;
          double t = total[i - 1][j - 1] + cost[i - 1][j - 1];
          if (better(c, t, bc, bt)) {
            bc = c;
            bt = t;
            bm = 3;
          }
        }
        count[i][j] = bc;
        total[i][j] = bt;
        move[i][j] = bm;
      }
    }
    int[] out = filled(m);
    for (int i = m, j = n; i > 0 && j >= 0 && !(i == 0 && j == 0); ) {
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
    score[0] = count[m][n];
    score[1] = total[m][n];
    return out;
  }

  private static boolean better(int c, double t, int bc, double bt) {
    return c > bc || (c == bc && t < bt);
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
