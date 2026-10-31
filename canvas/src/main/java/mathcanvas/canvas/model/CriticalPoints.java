package mathcanvas.canvas.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import mathcanvas.canvas.model.Families.Candidate;
import mathcanvas.engine.Cancellation;

/**
 * The critical points of one graph and one feature, tracked as a family ({@link Families}): the
 * candidates are the graph's features found now ({@link Features}) over the polylines it was
 * sampled to (the window widened by half), so a feature that leaves them is undefined until it
 * comes back. Features within the sum of their spreads and the merge distance of each other are one
 * multiple root, as is a root where the graph touches the axis: a tangency, which several members
 * may share. A graph's domain is never closed, so no rotation of the order is tried.
 */
final class CriticalPoints {

  /** The least scale: the window's diagonal, kept positive. */
  private static final double TINY = 1e-300;

  private CriticalPoints() {}

  /**
   * Tracks a family to its places on the graph's new geometry.
   *
   * @param family the critical points of one graph and one feature, in drawing order
   * @param shown the members defined after the previous recompute
   * @param graph the graph's geometry
   * @param sampler evaluates the graph and its derivative
   * @param window gives the scale of the tolerances
   * @param cancellation checked as it goes
   * @return one result per member, in order
   */
  static List<Recompute.Result> track(
      List<CriticalPoint> family,
      Set<ObjectId> shown,
      Geometry.Curve graph,
      Sampler sampler,
      Window window,
      Cancellation cancellation) {
    double scale = Math.max(window.diagonal(), TINY);
    Feature feature = family.getFirst().feature();
    List<Candidate> cs = candidates(Features.of(graph, feature, sampler, cancellation), scale);
    List<Optional<Anchor>> anchors = new ArrayList<>();
    List<Integer> indexes = new ArrayList<>();
    List<Boolean> defined = new ArrayList<>();
    for (CriticalPoint p : family) {
      anchors.add(p.anchor());
      indexes.add(p.index());
      defined.add(shown.contains(p.id()));
    }
    List<Optional<Families.Placement>> placed =
        Families.place(
            anchors,
            indexes,
            defined,
            cs,
            new Families.Positions(graph, sampler, scale, cancellation),
            scale);
    List<Recompute.Result> out = new ArrayList<>();
    for (int m = 0; m < family.size(); m++) {
      CriticalPoint p = family.get(m);
      out.add(
          placed
              .get(m)
              .map(
                  q ->
                      new Recompute.Result(
                          p.trackedTo(q.index(), q.anchor()),
                          new Geometry.Point(q.anchor().x(), q.anchor().y())))
              .orElse(new Recompute.Result(p, Geometry.UNDEFINED)));
    }
    return out;
  }

  /**
   * The features as candidates, in order: those within the sum of their spreads and the merge
   * distance of the one before are merged into it, a multiple root.
   */
  static List<Candidate> candidates(List<Features.Found> found, double scale) {
    List<Candidate> out = new ArrayList<>();
    Features.Found last = null;
    for (Features.Found f : found) {
      if (last != null
          && f.t() - last.t()
              <= f.spread()
                  + last.spread()
                  + Families.tolerance(Families.MERGE, scale, f.x(), f.y())) {
        Candidate m = out.getLast();
        out.set(
            out.size() - 1,
            new Candidate(
                m.x(), m.y(), m.t1(), Double.NaN, true, Math.max(m.spread(), f.spread())));
        continue;
      }
      out.add(new Candidate(f.x(), f.y(), f.t(), Double.NaN, f.multiple(), f.spread()));
      last = f;
    }
    return out;
  }
}
