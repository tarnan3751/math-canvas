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
import java.util.stream.Stream;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import mathcanvas.app.ui.ThemeBinding;
import mathcanvas.app.ui.WorksheetWindow;
import mathcanvas.engine.Version;
import mathcanvas.render.MathFont;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The Phase 2 acceptance tests of the worksheet: input and evaluation, recall, undo and redo, the
 * theme in both colours, cancelling a slow statement with the window responsive, and the session
 * kept across a restart. Each test starts the app on a data directory of its own under {@code
 * mathcanvas.dataDir}.
 *
 * <p>Input is synthetic: key events are fired at the focused node on the FX thread and buttons are
 * fired, never through the robot, so no test needs a macOS permission. Screenshots are scene
 * snapshots under {@code build/ui-screenshots/}.
 */
@ExtendWith(ApplicationExtension.class)
class WorksheetWindowTest {

  private static final Path SCREENSHOTS = Path.of("build", "ui-screenshots");
  private static final boolean MAC =
      System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("mac");

  /** About 4.5 s of expansion uncancelled; the engine checks it once per term of the product. */
  private static final String SLOW = "expand((x + y + z + w + 1)^30)";

  private Stage stage;
  private Path directory;
  private Main main;
  private WorksheetWindow window;

  @Start
  void start(Stage stage) {
    this.stage = stage;
    this.directory = newDirectory();
    this.main = new Main();
    this.window = main.start(stage, directory);
  }

  @AfterEach
  void stop() throws Exception {
    fx(() -> main.stop());
  }

  private static Path newDirectory() {
    String base = System.getProperty("mathcanvas.dataDir", "build/ui-test-data");
    return Path.of(base).toAbsolutePath().resolve("worksheet-" + UUID.randomUUID());
  }

  // --- driving the window on the FX thread

  private static void fx(Runnable r) throws Exception {
    WaitForAsyncUtils.waitFor(10, SECONDS, WaitForAsyncUtils.asyncFx(r));
  }

  private static <T> T get(Callable<T> c) throws Exception {
    return WaitForAsyncUtils.waitFor(10, SECONDS, WaitForAsyncUtils.asyncFx(c));
  }

  private static void waitUntil(long seconds, Callable<Boolean> onFx) throws Exception {
    WaitForAsyncUtils.waitFor(seconds, SECONDS, () -> get(onFx));
  }

  /**
   * Types text into the focused field, a key-typed event per character, once the field has its
   * skin: a field added by the last key has none until the next pulse, and without its skin's
   * behaviour a key-typed event inserts nothing.
   */
  private void type(String text) throws Exception {
    waitUntil(
        10, () -> stage.getScene().getFocusOwner() instanceof TextField f && f.getSkin() != null);
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

  private void press(KeyCode code) throws Exception {
    press(code, false, false, false);
  }

  /** Presses a key at the focused node, with Shortcut (Cmd on macOS, else Ctrl), Shift or Alt. */
  private void press(KeyCode code, boolean shortcut, boolean shift, boolean alt) throws Exception {
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

  private void enter(String text) throws Exception {
    type(text);
    press(KeyCode.ENTER);
  }

  private void click(String id) throws Exception {
    fx(() -> ((Button) stage.getScene().lookup("#" + id)).fire());
  }

  private List<String> outputs(int cell) throws Exception {
    return get(() -> window.cellViews().get(cell).outputTexts());
  }

  private void waitForOutput(int cell, String... lines) throws Exception {
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

  private String input(int cell) throws Exception {
    return get(() -> window.cellViews().get(cell).field().getText());
  }

  private boolean busyOnFx() {
    return !stage.getScene().lookup("#stop").isDisabled();
  }

  // --- the tests

  @Test
  void anExpressionEvaluatesAndTheNextCellTakesTheFocus() throws Exception {
    enter("1/3 + 1/6");
    waitForOutput(0, "1/2");

    assertThat(get(() -> window.cellViews().size())).isEqualTo(2);
    assertThat(get(() -> stage.getScene().getFocusOwner() == window.cellViews().get(1).field()))
        .isTrue();
    assertThat(get(stage::getTitle)).isEqualTo("math-canvas " + Version.current());
    assertThat(
            get(
                () ->
                    window.cellViews().getFirst().lookupAll(".cell-output").stream()
                        .map(n -> ((Label) n).getFont().getFamily())
                        .toList()))
        .containsExactly(MathFont.family());

    enter("f(x) := x^2 + 1");
    waitForOutput(1, "f(x) := x² + 1");
    enter("1 +");
    waitUntil(10, () -> window.cellViews().get(2).outputTexts().size() == 1);
    assertThat(outputs(2).getFirst()).endsWith("\n1 +\n   ^");
    // a long value is cut, with a note of the rest
    enter("expand((x + y + z + 1)^20)");
    waitUntil(
        30,
        () ->
            window.cellViews().get(3).outputTexts().size() == 1
                && window.cellViews().get(3).outputTexts().getFirst().length() > 1);
    assertThat(outputs(3).getFirst()).matches("(?s).{10000} … \\([0-9,]+ more characters\\)");
    screenshot("worksheet.png");
  }

  @Test
  void anEarlierInputIsRecalledIntoTheCurrentCell() throws Exception {
    enter("2 + 3");
    waitForOutput(0, "5");
    enter("7 * 6");
    waitForOutput(1, "42");

    press(KeyCode.UP, false, false, true);
    assertThat(input(2)).isEqualTo("7 * 6");
    press(KeyCode.UP, false, false, true);
    assertThat(input(2)).isEqualTo("2 + 3");
    press(KeyCode.DOWN, false, false, true);
    assertThat(input(2)).isEqualTo("7 * 6");
    press(KeyCode.UP, false, false, true);
    press(KeyCode.ENTER);
    waitForOutput(2, "5");
  }

  @Test
  void undoAndRedoGoThroughTheDocument() throws Exception {
    enter("a := 4");
    waitForOutput(0, "a := 4");
    enter("a + 1");
    waitForOutput(1, "5");

    press(KeyCode.Z, true, false, false);
    waitUntil(10, () -> window.cellViews().size() == 2);
    assertThat(input(1)).isEqualTo("a + 1");
    assertThat(outputs(1)).isEmpty();
    click("undo");
    assertThat(input(1)).isEmpty();

    click("redo");
    assertThat(input(1)).isEqualTo("a + 1");
    press(KeyCode.Z, true, true, false);
    waitForOutput(1, "5");
    assertThat(get(() -> window.cellViews().size())).isEqualTo(3);
  }

  @Test
  void theThemeSwitchesBetweenLightAndDark() throws Exception {
    enter("sqrt(8)");
    waitForOutput(0, "2√2");

    click("theme"); // system to light
    assertThat(get(() -> stage.getScene().getRoot().getStyleClass().contains(ThemeBinding.DARK)))
        .isFalse();
    WritableImage light = screenshot("worksheet-light.png");
    click("theme"); // light to dark
    assertThat(get(() -> stage.getScene().getRoot().getStyleClass().contains(ThemeBinding.DARK)))
        .isTrue();
    WritableImage dark = screenshot("worksheet-dark.png");

    int x = (int) light.getWidth() / 2;
    int y = (int) light.getHeight() - 80;
    assertThat(brightness(dark.getPixelReader().getArgb(x, y)))
        .isLessThan(brightness(light.getPixelReader().getArgb(x, y)));
  }

  @Test
  void aSlowStatementIsCancelledWithTheWindowResponsive() throws Exception {
    enter("b := 2");
    waitForOutput(0, "b := 2");
    enter(SLOW);
    waitUntil(10, this::busyOnFx);
    Thread.sleep(500); // well into the expansion, which takes seconds

    long asked = System.nanoTime();
    get(() -> true);
    long answered = System.nanoTime();
    assertThat(get(this::busyOnFx)).isTrue();
    // the FX thread answers while the worker expands; typically within milliseconds
    assertThat((answered - asked) / 1_000_000).isLessThan(5_000);

    press(KeyCode.ESCAPE);
    long stopped = System.nanoTime();
    waitUntil(30, () -> !busyOnFx());
    // the engine checks the expansion once per term of the product: typically 2 ms after Escape
    assertThat((System.nanoTime() - stopped) / 1_000_000).isLessThan(1_000);
    waitForOutput(1, "(cancelled)");
    assertThat(outputs(0)).containsExactly("b := 2");
    screenshot("worksheet-cancelled.png");

    // a new cell evaluates alone, on what finished before it; the stopped cell stays stopped
    enter("b + 1");
    waitForOutput(2, "3");
    assertThat(outputs(1)).containsExactly("(cancelled)");
  }

  @Test
  void theSessionAndTheThemeSurviveARestart() throws Exception {
    enter("c := 7");
    waitForOutput(0, "c := 7");
    enter("c * 6");
    waitForOutput(1, "42");
    click("theme"); // system to light
    fx(() -> main.stop());

    assertThat(directory.resolve("session.json")).exists();
    assertThat(Files.readString(directory.resolve("settings.json"))).contains("\"light\"");
    Main again = new Main();
    Stage[] second = new Stage[1];
    WorksheetWindow[] reopened = new WorksheetWindow[1];
    fx(
        () -> {
          second[0] = new Stage();
          reopened[0] = again.start(second[0], directory);
        });
    try {
      waitUntil(30, () -> reopened[0].cellViews().get(1).outputTexts().equals(List.of("42")));
      assertThat(get(() -> reopened[0].cellViews().get(0).field().getText())).isEqualTo("c := 7");
      assertThat(get(() -> reopened[0].themeProperty().get().name())).isEqualTo("LIGHT");
    } finally {
      fx(
          () -> {
            again.stop();
            second[0].close();
          });
    }
  }

  @Test
  void anUnreadableSessionIsSetAsideAndReported() throws Exception {
    Path other = newDirectory();
    Files.createDirectories(other);
    Files.writeString(other.resolve("session.json"), "{ this is not JSON");
    Main another = new Main();
    Stage[] second = new Stage[1];
    WorksheetWindow[] opened = new WorksheetWindow[1];
    fx(
        () -> {
          second[0] = new Stage();
          opened[0] = another.start(second[0], other);
        });
    try {
      assertThat(get(() -> opened[0].statusLine().getText()))
          .startsWith("session.json could not be read and was moved aside as session.unreadable-");
      assertThat(get(() -> opened[0].statusLine().getTooltip().getText())).isNotEmpty();
      try (Stream<Path> files = Files.list(other)) {
        assertThat(files.map(p -> p.getFileName().toString()).toList())
            .anyMatch(n -> n.startsWith("session.unreadable-"))
            .doesNotContain("session.json");
      }
    } finally {
      fx(
          () -> {
            another.stop();
            second[0].close();
          });
    }
  }

  // --- screenshots

  private WritableImage screenshot(String name) throws Exception {
    WritableImage image = get(() -> stage.getScene().snapshot(null));
    write(image, SCREENSHOTS.resolve(name));
    return image;
  }

  private static int brightness(int argb) {
    return ((argb >> 16) & 0xff) + ((argb >> 8) & 0xff) + (argb & 0xff);
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
    assertThat(file).isNotEmptyFile();
  }
}
