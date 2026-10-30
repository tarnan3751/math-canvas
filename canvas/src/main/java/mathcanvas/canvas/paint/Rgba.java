package mathcanvas.canvas.paint;

/**
 * A colour with opacity, each component from 0 to 1.
 *
 * @param red the red component
 * @param green the green component
 * @param blue the blue component
 * @param opacity the opacity, 1 being opaque
 */
public record Rgba(double red, double green, double blue, double opacity) {

  /**
   * Checks the components.
   *
   * @throws IllegalArgumentException if a component is outside [0, 1]
   */
  public Rgba {
    for (double c : new double[] {red, green, blue, opacity}) {
      if (!(c >= 0 && c <= 1)) {
        throw new IllegalArgumentException("colour component outside [0, 1]: " + c);
      }
    }
  }

  /**
   * An opaque colour from {@code 0xRRGGBB}.
   *
   * @param rgb the colour
   * @return the colour
   */
  public static Rgba rgb(int rgb) {
    return new Rgba(
        ((rgb >> 16) & 0xff) / 255.0, ((rgb >> 8) & 0xff) / 255.0, (rgb & 0xff) / 255.0, 1);
  }

  /**
   * This colour with another opacity.
   *
   * @param newOpacity the opacity
   * @return the colour
   */
  public Rgba withOpacity(double newOpacity) {
    return new Rgba(red, green, blue, newOpacity);
  }

  /**
   * The colour without its opacity, as CSS and SVG write it.
   *
   * @return {@code #rrggbb}
   */
  public String hex() {
    return String.format(
        java.util.Locale.ROOT, "#%02x%02x%02x", byteOf(red), byteOf(green), byteOf(blue));
  }

  private static int byteOf(double c) {
    return (int) Math.round(c * 255);
  }
}
