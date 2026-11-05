package mathcanvas.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The window's icons: every size the build copies from {@code app/icon} decodes to that size; every
 * symbol the window draws is in the icons resource as SVG path data.
 */
class IconTest {

  @Test
  void everyIconSizeDecodesToItsSize() throws IOException {
    assertThat(WorksheetWindow.ICON_SIZES).containsExactly(16, 32, 64, 128, 256, 512);
    for (int size : WorksheetWindow.ICON_SIZES) {
      try (InputStream in =
          WorksheetWindow.class.getResourceAsStream(
              "/mathcanvas/app/icon/math-canvas-" + size + ".png")) {
        assertThat(in).as("icon %d", size).isNotNull();
        BufferedImage image = ImageIO.read(in);
        assertThat(image).as("icon %d", size).isNotNull();
        assertThat(image.getWidth()).as("icon %d", size).isEqualTo(size);
        assertThat(image.getHeight()).as("icon %d", size).isEqualTo(size);
        assertThat(image.getColorModel().hasAlpha()).as("icon %d", size).isTrue();
      }
    }
  }

  @Test
  void everySymbolTheWindowDrawsHasItsPath() {
    for (String name :
        List.of(
            "add",
            "description",
            "show_chart",
            "dialpad",
            "history",
            "data_object",
            "radio_button_unchecked",
            "help",
            "settings",
            "undo",
            "redo",
            "play_arrow",
            "stop",
            "search",
            "view_in_ar")) {
      assertThat(Icons.path(name)).as(name).matches("[MmLlHhVvCcSsQqTtAaZz0-9 .,\\-]+");
    }
    assertThat(Icons.names()).hasSize(15);
  }
}
