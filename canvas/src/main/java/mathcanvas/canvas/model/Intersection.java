package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One intersection of two paths, tracked by continuity: at each recompute the intersections of one
 * ordered pair of paths, a family, are matched to the candidates together, each to its own, so each
 * keeps its identity as the paths move, through tangency and through intervals where the paths do
 * not meet (docs/reference/canvas.md, **Object model**). Families are keyed by the ordered pair, so
 * tools create an intersection with the lower object id first.
 *
 * <p>The index is the intersection's place among the candidates, ordered by the parameter on the
 * first path (then the second; by position for a path with no parameter). A new intersection, with
 * no anchor yet, takes the candidate at its index; after that the index follows the candidate it is
 * matched to while that is a simple root, is kept at a tangency, and orders members whose anchors
 * coincide when they have no side to go by.
 *
 * @param id the id
 * @param style the style
 * @param first the first path's id
 * @param second the second path's id
 * @param index the place among the candidates, at least 0
 * @param anchor where it last was, empty until it first is defined
 */
public record Intersection(
    ObjectId id, Style style, ObjectId first, ObjectId second, int index, Optional<Anchor> anchor)
    implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a reference is null
   * @throws IllegalArgumentException if the index is negative or the two paths are one
   */
  public Intersection {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(first, "first");
    Objects.requireNonNull(second, "second");
    Objects.requireNonNull(anchor, "anchor");
    if (index < 0) {
      throw new IllegalArgumentException("an intersection index is at least 0");
    }
    if (first.equals(second)) {
      throw new IllegalArgumentException("an intersection is of two different paths");
    }
  }

  /**
   * A new intersection, with no anchor.
   *
   * @param id the id
   * @param style the style
   * @param first the first path's id
   * @param second the second path's id
   * @param index the place among the candidates
   * @return the intersection
   */
  public static Intersection of(
      ObjectId id, Style style, ObjectId first, ObjectId second, int index) {
    return new Intersection(id, style, first, second, index, Optional.empty());
  }

  @Override
  public List<ObjectId> parents() {
    return List.of(first, second);
  }

  @Override
  public Set<String> reads() {
    return Set.of();
  }

  @Override
  public Intersection withStyle(Style style) {
    return new Intersection(id, style, first, second, index, anchor);
  }

  /**
   * This intersection tracked to a new place.
   *
   * @param index the index
   * @param anchor the anchor
   * @return the intersection
   */
  public Intersection trackedTo(int index, Anchor anchor) {
    return new Intersection(id, style, first, second, index, Optional.of(anchor));
  }
}
