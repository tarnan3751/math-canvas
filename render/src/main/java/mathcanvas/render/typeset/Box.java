package mathcanvas.render.typeset;

import java.util.List;
import java.util.Optional;
import mathcanvas.engine.rule.Path;

/**
 * A positioned box of a typeset expression. Coordinates are pixels with y down, from the left end
 * of the root's baseline: a box spans {@code x} to {@code x + width} across and {@code baseline -
 * ascent} to {@code baseline + descent} down. A group's box contains its children's. A box carries
 * the path of the subexpression it shows, or null.
 */
public sealed interface Box permits Box.Group, Box.GlyphBox, Box.RuleBox, Box.TextBox {

  /**
   * Returns the left edge.
   *
   * @return pixels
   */
  double x();

  /**
   * Returns the baseline.
   *
   * @return pixels, down from the root's baseline
   */
  double baseline();

  /**
   * Returns the width.
   *
   * @return pixels
   */
  double width();

  /**
   * Returns the height above the baseline.
   *
   * @return pixels
   */
  double ascent();

  /**
   * Returns the depth below the baseline.
   *
   * @return pixels
   */
  double descent();

  /**
   * Returns the path of the subexpression the box shows.
   *
   * @return the path, or null
   */
  Path path();

  /**
   * Returns the children.
   *
   * @return the children, empty for a leaf
   */
  default List<Box> children() {
    return List.of();
  }

  /**
   * Returns the top edge.
   *
   * @return pixels
   */
  default double top() {
    return baseline() - ascent();
  }

  /**
   * Returns the bottom edge.
   *
   * @return pixels
   */
  default double bottom() {
    return baseline() + descent();
  }

  /**
   * Returns whether a point is in the box, edges included.
   *
   * @param px the x
   * @param py the y
   * @return whether it is
   */
  default boolean contains(double px, double py) {
    return px >= x() && px <= x() + width() && py >= top() && py <= bottom();
  }

  /**
   * Returns the path of the deepest box with a path that contains a point.
   *
   * @param px the x
   * @param py the y
   * @return the path, or empty when no box with a path contains the point
   */
  default Optional<Path> hitTest(double px, double py) {
    return Optional.ofNullable(hit(this, px, py));
  }

  private static Path hit(Box box, double px, double py) {
    if (!box.contains(px, py)) {
      return null;
    }
    for (Box child : box.children()) {
      Path p = hit(child, px, py);
      if (p != null) {
        return p;
      }
    }
    return box.path();
  }

  /**
   * Returns the outermost box with a path.
   *
   * @param path the path
   * @return the box, or empty
   */
  default Optional<Box> find(Path path) {
    if (path.equals(path())) {
      return Optional.of(this);
    }
    for (Box child : children()) {
      Optional<Box> found = child.find(path);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }

  /**
   * Boxes side by side or stacked.
   *
   * @param x the left edge
   * @param baseline the baseline
   * @param width the width
   * @param ascent the height
   * @param descent the depth
   * @param path the path, or null
   * @param children the children
   */
  record Group(
      double x,
      double baseline,
      double width,
      double ascent,
      double descent,
      Path path,
      List<Box> children)
      implements Box {

    /** Copies the children. */
    public Group {
      children = List.copyOf(children);
    }
  }

  /**
   * A glyph of the font, its origin at {@code (x, baseline)}: an outline point {@code (u, v)} in
   * font units is drawn at {@code (x + u·scale, baseline − v·scale)}. Its box is its advance across
   * and its outline's bounds up and down.
   *
   * @param x the origin's x
   * @param baseline the origin's y
   * @param width the advance
   * @param ascent the height
   * @param descent the depth
   * @param path the path, or null
   * @param glyph the glyph
   * @param scale pixels per font unit
   */
  record GlyphBox(
      double x,
      double baseline,
      double width,
      double ascent,
      double descent,
      Path path,
      int glyph,
      double scale)
      implements Box {}

  /**
   * A filled rectangle, the box itself: a fraction bar, a radical's bar.
   *
   * @param x the left edge
   * @param baseline the baseline
   * @param width the width
   * @param ascent the height above the baseline
   * @param descent the depth below it
   * @param path the path, or null
   */
  record RuleBox(double x, double baseline, double width, double ascent, double descent, Path path)
      implements Box {}

  /**
   * Text the font has no glyph for, set by the platform's text renderer at a size.
   *
   * @param x the origin's x
   * @param baseline the origin's y
   * @param width the measured width
   * @param ascent the measured height
   * @param descent the measured depth
   * @param path the path, or null
   * @param text the text
   * @param size the font size in pixels
   */
  record TextBox(
      double x,
      double baseline,
      double width,
      double ascent,
      double descent,
      Path path,
      String text,
      double size)
      implements Box {}
}
