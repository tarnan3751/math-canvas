package mathcanvas.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The window's icons: every size the build copies from {@code app/icon} decodes to that size. */
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
}
