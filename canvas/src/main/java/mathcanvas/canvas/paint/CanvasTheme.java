package mathcanvas.canvas.paint;

import java.util.List;

/**
 * The canvas's colours: the background, the axes, the major and minor grid, the text, and eight
 * series colours for plotted objects. The view reads them from CSS (its background, {@code
 * -canvas-axis} and the rest); {@link #LIGHT} is the light theme, for drawing without a view.
 *
 * @param background the plane behind everything, which an export fills first
 * @param axis the axes
 * @param grid the major grid lines
 * @param minorGrid the minor grid lines
 * @param text tick labels and other text
 * @param series the eight series colours
 */
public record CanvasTheme(
    Rgba background, Rgba axis, Rgba grid, Rgba minorGrid, Rgba text, List<Rgba> series) {

  /** The number of series colours. */
  public static final int SERIES = 8;

  /** The light theme, the same values as the view's default stylesheet. */
  public static final CanvasTheme LIGHT =
      new CanvasTheme(
          Rgba.rgb(0xffffff),
          Rgba.rgb(0x48484a),
          Rgba.rgb(0xd8d8dc),
          Rgba.rgb(0xededf0),
          Rgba.rgb(0x48484a),
          List.of(
              Rgba.rgb(0x1f77b4),
              Rgba.rgb(0xd62728),
              Rgba.rgb(0x2ca02c),
              Rgba.rgb(0x9467bd),
              Rgba.rgb(0xff7f0e),
              Rgba.rgb(0x17becf),
              Rgba.rgb(0x8c564b),
              Rgba.rgb(0xe377c2)));

  /**
   * Copies the series colours.
   *
   * @throws IllegalArgumentException if there are not eight
   */
  public CanvasTheme {
    series = List.copyOf(series);
    if (series.size() != SERIES) {
      throw new IllegalArgumentException("need " + SERIES + " series colours: " + series.size());
    }
  }
}
