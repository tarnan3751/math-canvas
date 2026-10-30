package mathcanvas.app;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextInputControl;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import mathcanvas.app.ui.WorksheetWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

/**
 * What every worksheet UI test shares: the app started on a data directory of its own under {@code
 * mathcanvas.dataDir} and stopped after each test, and the means of driving it. Input is synthetic:
 * key events are fired at the focused node on the FX thread and buttons are fired, never through
 * the robot, so no test needs a macOS permission. Screenshots are scene snapshots under {@code
 * build/ui-screenshots/}.
 */
@ExtendWith(ApplicationExtension.class)
abstract class WorksheetHarness {

  static final Path SCREENSHOTS = Path.of("build", "ui-screenshots");
  static final boolean MAC =
      System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("mac");

  protected Stage stage;
  protected Path directory;
  protected Main main;
  protected WorksheetWindow window;

  /**
   * Opens the app on a stage of its own (TestFX's {@code @Start} is not inherited, so the harness
   * makes the stage itself; the extension still starts the toolkit).
   */
  @BeforeEach
  void open() throws Exception {
    directory = newDirectory();
    main = new Main();
    fx(
        () -> {
          stage = new Stage();
          window = main.start(stage, directory);
        });
  }

  @AfterEach
  void close() throws Exception {
    fx(
        () -> {
          main.stop();
          stage.close();
        });
  }

  protected static Path newDirectory() {
    String base = System.getProperty("mathcanvas.dataDir", "build/ui-test-data");
    return Path.of(base).toAbsolutePath().resolve("worksheet-" + UUID.randomUUID());
  }

  // --- driving the window on the FX thread

  protected static void fx(Runnable r) throws Exception {
    WaitForAsyncUtils.waitFor(10, SECONDS, WaitForAsyncUtils.asyncFx(r));
  }

  protected static <T> T get(Callable<T> c) throws Exception {
    return WaitForAsyncUtils.waitFor(10, SECONDS, WaitForAsyncUtils.asyncFx(c));
  }

  protected static void waitUntil(long seconds, Callable<Boolean> onFx) throws Exception {
    WaitForAsyncUtils.waitFor(seconds, SECONDS, () -> get(onFx));
  }

  /**
   * Types text into the focused field, a key-typed event per character, once the field has its
   * skin: a field added by the last key has none until the next pulse, and without its skin's
   * behaviour a key-typed event inserts nothing.
   */
  protected void type(String text) throws Exception {
    waitUntil(
        10,
        () ->
            stage.getScene().getFocusOwner() instanceof TextInputControl f && f.getSkin() != null);
    fx(
        () -> {
          Node target = stage.getScene().getFocusOwner();
          text.codePoints()
              .forEach(
                  cp ->
                      Event.fireEvent(
                          target,
                          new KeyEvent(
                              KeyEvent.KEY_TYPED,
                              Character.toString(cp),
                              "",
                              KeyCode.UNDEFINED,
                              false,
                              false,
                              false,
                              false)));
        });
  }

  protected void press(KeyCode code) throws Exception {
    press(code, false, false, false);
  }

  /** Presses a key at the focused node, with Shortcut (Cmd on macOS, else Ctrl), Shift or Alt. */
  protected void press(KeyCode code, boolean shortcut, boolean shift, boolean alt)
      throws Exception {
    fx(
        () -> {
          Node target = stage.getScene().getFocusOwner();
          for (var type : List.of(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED)) {
            Event.fireEvent(
                target,
                new KeyEvent(type, "", "", code, shift, shortcut && !MAC, alt, shortcut && MAC));
          }
        });
  }

  protected void enter(String text) throws Exception {
    type(text);
    press(KeyCode.ENTER);
  }

  protected void click(String id) throws Exception {
    fx(() -> ((Button) stage.getScene().lookup("#" + id)).fire());
  }

  protected List<String> outputs(int cell) throws Exception {
    return get(() -> window.cellViews().get(cell).outputTexts());
  }

  protected void waitForOutput(int cell, String... lines) throws Exception {
    try {
      waitUntil(
          30,
          () ->
              window.cellViews().size() > cell
                  && window.cellViews().get(cell).outputTexts().equals(List.of(lines)));
    } catch (TimeoutException e) {
      // say what the window shows instead
      assertThat(
              get(
                  () ->
                      window.cellViews().stream()
                          .map(v -> v.field().getText() + " -> " + v.outputTexts())
                          .toList()))
          .as("cell %d shows %s", cell, List.of(lines))
          .isEmpty();
    }
  }

  protected String input(int cell) throws Exception {
    return get(() -> window.cellViews().get(cell).field().getText());
  }

  protected boolean busyOnFx() {
    return !stage.getScene().lookup("#stop").isDisabled();
  }

  // --- screenshots

  protected WritableImage screenshot(String name) throws Exception {
    WritableImage image = get(() -> stage.getScene().snapshot(null));
    write(image, SCREENSHOTS.resolve(name));
    return image;
  }

  protected static int brightness(int argb) {
    return ((argb >> 16) & 0xff) + ((argb >> 8) & 0xff) + (argb & 0xff);
  }

  protected static void write(WritableImage image, Path file) throws IOException {
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
    assertThat(file).isNotEmptyFile();
  }
}
