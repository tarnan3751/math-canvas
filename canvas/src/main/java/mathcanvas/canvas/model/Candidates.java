package mathcanvas.canvas.model;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.canvas.model.Families.Candidate;
import mathcanvas.engine.Cancellation;

/**
 * The places a family of points has now inside the visible window, as its recompute finds them: the
 * intersections of two paths, the features of a graph, so a tool can create one member per place it
 * shows. A place is given by its index among all the family's candidates (the curves are sampled
 * over the window widened by half), which is the index a new member takes.
 */
public final class Candidates {

  /** The least scale: the window's diagonal, kept positive. */
  private static final double TINY = 1e-300;

  private Candidates() {}

  /**
   * The places two paths meet inside a window, as their family's recompute finds them: none when
   * they overlap.
   *
   * @param first the first path's geometry, the path with the lower id
   * @param second the second path's geometry
   * @param sampler evaluates the paths
   * @param window the visible window: the scale of the tolerances, and the places counted
   * @param cancellation checked per seed and per refinement step
   * @return the indexes, among all the candidates, of those inside the window, ascending
   */
  public static List<Integer> intersections(
      Geometry.Curve first,
      Geometry.Curve second,
      Sampler sampler,
      Window window,
      Cancellation cancellation) {
    double scale = Math.max(window.diagonal(), TINY);
    return inside(
        Intersections.candidates(first, second, List.of(), sampler, scale, cancellation)
            .orElse(List.of()),
        window);
  }

  /**
   * The places of one kind of feature a graph has inside a window, as its critical points'
   * recompute finds them: a multiple root counts once.
   *
   * @param graph the graph's geometry
   * @param feature which features
   * @param sampler evaluates the graph and its derivative
   * @param window the visible window: the scale of the tolerances, and the places counted
   * @param cancellation checked as it goes
   * @return the indexes, among all the candidates, of those inside the window, ascending
   */
  public static List<Integer> features(
      Geometry.Curve graph,
      Feature feature,
      Sampler sampler,
      Window window,
      Cancellation cancellation) {
    double scale = Math.max(window.diagonal(), TINY);
    return inside(
        CriticalPoints.candidates(Features.of(graph, feature, sampler, cancellation), scale),
        window);
  }

  /** The indexes of the candidates whose abscissa lies inside a window. */
  private static List<Integer> inside(List<Candidate> cs, Window window) {
    List<Integer> out = new ArrayList<>();
    for (int k = 0; k < cs.size(); k++) {
      double x = cs.get(k).x();
      if (x >= window.minX() && x <= window.maxX()) {
        out.add(k);
      }
    }
    return out;
  }
}
