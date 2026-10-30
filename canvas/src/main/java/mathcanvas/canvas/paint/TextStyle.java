package mathcanvas.canvas.paint;

/**
 * How text is drawn: the platform's sans-serif font at a size in pixels, in a colour.
 *
 * @param size the font size in pixels, positive
 * @param colour the colour
 */
public record TextStyle(double size, Rgba colour) {

  /**
   * Checks the size.
   *
   * @throws IllegalArgumentException if the size is not positive and finite
   */
  public TextStyle {
    if (!(Double.isFinite(size) && size > 0)) {
      throw new IllegalArgumentException("bad font size: " + size);
    }
  }
}
