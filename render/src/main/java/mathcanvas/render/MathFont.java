package mathcanvas.render;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import javafx.scene.text.Font;
import mathcanvas.render.font.OpenTypeFont;

/**
 * The bundled math font, STIX Two Math (SIL Open Font License 1.1, its text beside the font in
 * {@code fonts/OFL.txt}). The typesetting depends on its metrics, so it is the one math font on
 * every platform. It is loaded into JavaFX once, on first use, which must happen after the toolkit
 * has started.
 */
public final class MathFont {

  private MathFont() {}

  /**
   * Returns the font family name under which JavaFX registered the bundled font.
   *
   * @return the family name
   */
  public static String family() {
    return Loaded.FAMILY;
  }

  /**
   * Returns the bundled font at a size.
   *
   * @param size the size in points
   * @return the font
   */
  public static Font at(double size) {
    return Font.font(Loaded.FAMILY, size);
  }

  private static final class Loaded {
    static final String FAMILY = load();

    private Loaded() {}

    private static String load() {
      // the bytes the metrics were read from, rather than the resource read a second time
      try (InputStream in = OpenTypeFont.stixFile()) {
        // The size only sets the returned instance's; at() derives every other size.
        Font font = Font.loadFont(in, 12);
        if (font == null) {
          throw new IllegalStateException("JavaFX could not load the math font");
        }
        return font.getFamily();
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
