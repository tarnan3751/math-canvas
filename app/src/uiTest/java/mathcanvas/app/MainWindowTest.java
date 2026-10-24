package mathcanvas.app;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testfx.util.WaitForAsyncUtils.asyncFx;
import static org.testfx.util.WaitForAsyncUtils.waitFor;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.scene.control.Label;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import mathcanvas.engine.Version;
import mathcanvas.render.MathFont;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The Phase 0 window opens with the version in its title and the sample line set in the bundled
 * math font, and a screenshot of it is written to {@code build/ui-screenshots/main-window.png}.
 * Input-free: no clicks and no typing, so it needs no macOS Accessibility permission, and the
 * screenshot is a scene snapshot rather than a screen capture, so it needs no Screen Recording
 * permission either.
 */
@ExtendWith(ApplicationExtension.class)
class MainWindowTest {

  private static final Path SCREENSHOTS = Path.of("build", "ui-screenshots");
  private static final String SAMPLE = "∫ x² dx";

  private Stage stage;

  @Start
  void start(Stage stage) {
    this.stage = stage;
    new Main().start(stage);
  }

  @Test
  void theWindowShowsTheVersionAndTheSampleInTheMathFont() throws Exception {
    Window window = waitFor(10, SECONDS, asyncFx(this::readWindow));

    assertThat(window.title()).isEqualTo("math-canvas " + Version.current());
    assertThat(window.sampleTexts()).containsExactly(SAMPLE);
    assertThat(window.sampleFontFamily()).isEqualTo(window.mathFontFamily());

    Path screenshot = SCREENSHOTS.resolve("main-window.png");
    write(window.snapshot(), screenshot);
    assertThat(screenshot).isNotEmptyFile();
  }

  /** What the test reads from the scene, gathered on the FX thread. */
  private record Window(
      String title,
      List<String> sampleTexts,
      String sampleFontFamily,
      String mathFontFamily,
      WritableImage snapshot) {}

  private Window readWindow() {
    List<Label> samples =
        stage.getScene().getRoot().lookupAll(".label").stream()
            .filter(node -> node instanceof Label label && SAMPLE.equals(label.getText()))
            .map(Label.class::cast)
            .toList();
    String sampleFontFamily = samples.isEmpty() ? null : samples.getFirst().getFont().getFamily();
    return new Window(
        stage.getTitle(),
        samples.stream().map(Label::getText).toList(),
        sampleFontFamily,
        MathFont.family(),
        stage.getScene().snapshot(null));
  }

  private static void write(WritableImage image, Path file) throws IOException {
    int width = (int) image.getWidth();
    int height = (int) image.getHeight();
    int[] argb = new int[width * height];
    image
        .getPixelReader()
        .getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
    BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    out.setRGB(0, 0, width, height, argb, 0, width);
    Files.createDirectories(file.getParent());
    if (!ImageIO.write(out, "png", file.toFile())) {
      throw new IOException("no PNG writer");
    }
  }
}
