package mathcanvas.canvas.model;

/**
 * How an object is drawn: the colour as an index into the canvas's series palette, the stroke width
 * in pixels, whether its stroke is dashed, whether it is shown, and whether its label is.
 *
 * @param colour the palette index, 0 to {@value #COLOURS} − 1
 * @param width the stroke width in pixels, positive and finite
 * @param dashed whether the stroke is dashed
 * @param visible whether the object is drawn
 * @param labelVisible whether its label is drawn
 */
public record Style(
    int colour, double width, boolean dashed, boolean visible, boolean labelVisible) {

  /** The number of series colours in the palette. */
  public static final int COLOURS = 8;

  /** The first colour, two pixels wide, solid, visible, with no label. */
  public static final Style DEFAULT = new Style(0, 2, false, true, false);

  /**
   * Checks the fields.
   *
   * @throws IllegalArgumentException if the colour is outside the palette or the width is not a
   *     positive finite number
   */
  public Style {
    if (colour < 0 || colour >= COLOURS) {
      throw new IllegalArgumentException("a colour index is 0 to " + (COLOURS - 1));
    }
    if (!(width > 0) || Double.isInfinite(width)) {
      throw new IllegalArgumentException("a width is positive and finite");
    }
  }
}
