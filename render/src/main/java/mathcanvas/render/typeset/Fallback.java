package mathcanvas.render.typeset;

/**
 * Measures text the font has no glyph for, as the platform will draw it: the view measures with
 * JavaFX, a test with fixed metrics.
 */
public interface Fallback {

  /**
   * Measures a text.
   *
   * @param text the text
   * @param size the font size in pixels
   * @return its metrics
   */
  Metrics measure(String text, double size);

  /**
   * A text's metrics.
   *
   * @param width the width
   * @param ascent the height above the baseline
   * @param descent the depth below it
   */
  record Metrics(double width, double ascent, double descent) {}
}
