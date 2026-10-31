package mathcanvas.canvas.paint;

import java.util.Objects;
import java.util.Optional;

/**
 * How text is drawn: the platform's sans-serif font at a size in pixels, in a colour, optionally on
 * a small rounded box of a background colour, so it reads over a filled region.
 *
 * @param size the font size in pixels, positive
 * @param colour the colour
 * @param background the box's colour, or empty for none
 */
public record TextStyle(double size, Rgba colour, Optional<Rgba> background) {

  /** The padding of a background box around its text, in pixels. */
  public static final double PADDING = 3;

  /**
   * Checks the size.
   *
   * @throws IllegalArgumentException if the size is not positive and finite
   */
  public TextStyle {
    if (!(Double.isFinite(size) && size > 0)) {
      throw new IllegalArgumentException("bad font size: " + size);
    }
    Objects.requireNonNull(colour, "colour");
    Objects.requireNonNull(background, "background");
  }

  /**
   * Text with no background.
   *
   * @param size the font size in pixels, positive
   * @param colour the colour
   */
  public TextStyle(double size, Rgba colour) {
    this(size, colour, Optional.empty());
  }

  /**
   * The left and top of a text's box, from its anchor point and its measured width and height: the
   * box the text occupies, before the padding.
   *
   * @param x the anchor's abscissa in pixels
   * @param y the anchor's ordinate in pixels
   * @param anchor which point of the text sits there
   * @param width the text's width in pixels
   * @param height the text's height in pixels
   * @return the left and the top
   */
  public static double[] corner(double x, double y, Anchor anchor, double width, double height) {
    double left =
        switch (anchor.horizontal()) {
          case START -> x;
          case MIDDLE -> x - width / 2;
          case END -> x - width;
        };
    double top =
        switch (anchor.vertical()) {
          case TOP -> y;
          case MIDDLE -> y - height / 2;
          case BASELINE -> y - 0.8 * height;
          case BOTTOM -> y - height;
        };
    return new double[] {left, top};
  }
}
