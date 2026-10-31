package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A root, a local minimum or a local maximum of a function graph, tracked by continuity as the
 * graph changes: the critical points of one graph and one feature are a family, matched to the
 * features found at each recompute by the rules intersections follow (docs/reference/canvas.md,
 * <b>Object model</b>), so each keeps its identity as a slider moves, becomes undefined when its
 * feature vanishes, and re-attaches when it returns.
 *
 * <p>The index is its place among the features, by abscissa. A new critical point, with no anchor
 * yet, takes the feature at its index; after that the index follows the feature it is matched to
 * while that is simple, and is kept at a multiple root.
 *
 * @param id the id
 * @param style the style
 * @param path the function graph's id
 * @param feature which feature it is
 * @param index the place among the features, at least 0
 * @param anchor where it last was, empty until it first is defined
 */
public record CriticalPoint(
    ObjectId id, Style style, ObjectId path, Feature feature, int index, Optional<Anchor> anchor)
    implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a reference is null
   * @throws IllegalArgumentException if the index is negative
   */
  public CriticalPoint {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(feature, "feature");
    Objects.requireNonNull(anchor, "anchor");
    if (index < 0) {
      throw new IllegalArgumentException("a critical point's index is at least 0");
    }
  }

  /**
   * A new critical point, with no anchor.
   *
   * @param id the id
   * @param style the style
   * @param path the function graph's id
   * @param feature which feature
   * @param index the place among the features
   * @return the critical point
   */
  public static CriticalPoint of(
      ObjectId id, Style style, ObjectId path, Feature feature, int index) {
    return new CriticalPoint(id, style, path, feature, index, Optional.empty());
  }

  @Override
  public List<ObjectId> parents() {
    return List.of(path);
  }

  @Override
  public Set<String> reads() {
    return Set.of();
  }

  @Override
  public CriticalPoint withStyle(Style style) {
    return new CriticalPoint(id, style, path, feature, index, anchor);
  }

  /**
   * This critical point tracked to a new place.
   *
   * @param index the index
   * @param anchor the anchor
   * @return the critical point
   */
  public CriticalPoint trackedTo(int index, Anchor anchor) {
    return new CriticalPoint(id, style, path, feature, index, Optional.of(anchor));
  }
}
