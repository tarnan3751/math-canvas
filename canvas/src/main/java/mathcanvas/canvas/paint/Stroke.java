package mathcanvas.canvas.paint;

import java.util.List;

/**
 * How a path is stroked: a colour, a width in pixels, and a dash pattern in pixels (empty for a
 * solid line). Lines have round caps and joins.
 *
 * @param colour the colour
 * @param width the width in pixels, positive
 * @param dash alternating dash and gap lengths in pixels, each positive; empty for solid
 */
public record Stroke(Rgba colour, double width, List<Double> dash) {

  /**
   * Checks the width and the pattern, and copies the pattern.
   *
   * @throws IllegalArgumentException if the width or a dash length is not positive and finite
   */
  public Stroke {
    if (!(Double.isFinite(width) && width > 0)) {
      throw new IllegalArgumentException("bad stroke width: " + width);
    }
    dash = List.copyOf(dash);
    for (double d : dash) {
      if (!(Double.isFinite(d) && d > 0)) {
        throw new IllegalArgumentException("bad dash length: " + d);
      }
    }
  }

  /**
   * A solid stroke.
   *
   * @param colour the colour
   * @param width the width in pixels
   * @return the stroke
   */
  public static Stroke solid(Rgba colour, double width) {
    return new Stroke(colour, width, List.of());
  }

  /**
   * A dashed stroke.
   *
   * @param colour the colour
   * @param width the width in pixels
   * @param dash alternating dash and gap lengths in pixels
   * @return the stroke
   */
  public static Stroke dashed(Rgba colour, double width, double... dash) {
    return new Stroke(colour, width, java.util.Arrays.stream(dash).boxed().toList());
  }
}
