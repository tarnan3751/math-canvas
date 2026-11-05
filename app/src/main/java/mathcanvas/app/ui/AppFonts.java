package mathcanvas.app.ui;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import javafx.scene.text.Font;

/**
 * The bundled interface fonts, Roboto in its Regular and Medium weights and Roboto Mono Regular for
 * what a person types (Apache License 2.0, {@code legal/Roboto-LICENSE.txt}), which {@code app.css}
 * names by family. Loaded into JavaFX once, before the first scene is styled, which must happen
 * after the toolkit has started; the maths keeps STIX Two Math through {@code render}.
 */
final class AppFonts {

  /** The bundled faces, under {@code mathcanvas/app/fonts/}. */
  static final List<String> FACES =
      List.of("Roboto-Regular.ttf", "Roboto-Medium.ttf", "RobotoMono-Regular.ttf");

  private static boolean loaded;

  private AppFonts() {}

  /** Loads the bundled faces, once. */
  static synchronized void load() {
    if (loaded) {
      return;
    }
    for (String face : FACES) {
      String resource = "/mathcanvas/app/fonts/" + face;
      try (InputStream in = AppFonts.class.getResourceAsStream(resource)) {
        if (in == null) {
          throw new IllegalStateException("the interface font is missing: " + resource);
        }
        // the size only sets the returned instance's; the stylesheet sizes every use
        if (Font.loadFont(in, 14) == null) {
          throw new IllegalStateException("JavaFX could not load the interface font: " + resource);
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
    loaded = true;
  }
}
