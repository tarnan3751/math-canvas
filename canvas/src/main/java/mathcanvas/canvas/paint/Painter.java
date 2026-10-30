package mathcanvas.canvas.paint;

import mathcanvas.canvas.geom.Viewport;

/**
 * Immediate-mode drawing of math objects (CLAUDE.md, **Architecture rules**): the only way they are
 * drawn, on the screen and into an SVG export alike. Every coordinate is in world units; the
 * backend maps it through its {@link #viewport()} and clips what falls far outside it. Widths,
 * radii, dashes, offsets and font sizes are in pixels.
 *
 * <p>A path is built with {@link #beginPath()}, {@link #moveTo}, {@link #lineTo}, {@link #polyline}
 * and {@link #closePath()}, then stroked, filled, or both; stroking or filling leaves the path in
 * place until the next {@link #beginPath()}. A filled region is a closed path. Points and text are
 * drawn at once and do not touch the path.
 */
public interface Painter {

  /**
   * The viewport the backend draws through.
   *
   * @return the viewport
   */
  Viewport viewport();

  /** Starts a new, empty path. */
  void beginPath();

  /**
   * Starts a subpath at a world point.
   *
   * @param x the world x
   * @param y the world y
   */
  void moveTo(double x, double y);

  /**
   * Extends the current subpath to a world point; starts one there when there is none.
   *
   * @param x the world x
   * @param y the world y
   */
  void lineTo(double x, double y);

  /**
   * Appends a sampled polyline to the path: a new subpath at its first point and after every point
   * that is not finite, which marks a gap (a discontinuity of a sampled function).
   *
   * @param xs the world xs
   * @param ys the world ys
   * @param n the number of points to read from each array
   */
  void polyline(double[] xs, double[] ys, int n);

  /** Closes the current subpath back to its first point. */
  void closePath();

  /**
   * Strokes the path.
   *
   * @param stroke the colour, width and dash
   */
  void stroke(Stroke stroke);

  /**
   * Fills the path, each subpath closed, by the non-zero winding rule.
   *
   * @param colour the colour
   */
  void fill(Rgba colour);

  /**
   * Draws a filled disc at a world point.
   *
   * @param x the world x
   * @param y the world y
   * @param radius the radius in pixels
   * @param colour the colour
   */
  void point(double x, double y, double radius, Rgba colour);

  /**
   * Draws a line of text at a world anchor moved by a pixel offset.
   *
   * @param text the text
   * @param x the anchor's world x
   * @param y the anchor's world y
   * @param dx the offset in pixels to the right
   * @param dy the offset in pixels down
   * @param anchor which point of the text sits at the anchor
   * @param style the font size and colour
   */
  void text(String text, double x, double y, double dx, double dy, Anchor anchor, TextStyle style);
}
