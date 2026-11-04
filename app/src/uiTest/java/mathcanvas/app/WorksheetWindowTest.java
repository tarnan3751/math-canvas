package mathcanvas.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import mathcanvas.app.ui.ThemeBinding;
import mathcanvas.app.ui.WorksheetWindow;
import mathcanvas.engine.Version;
import org.junit.jupiter.api.Test;

/**
 * The Phase 2 acceptance tests of the worksheet: input and evaluation, recall, undo and redo, the
 * theme in both colours, cancelling a slow statement with the window responsive, and the session
 * kept across a restart ({@link WorksheetHarness} starts and drives the app).
 */
class WorksheetWindowTest extends WorksheetHarness {

  /** About 4.5 s of expansion uncancelled; the engine checks it once per term of the product. */
  private static final String SLOW = "expand((x + y + z + w + 1)^30)";

  // --- the tests

  @Test
  void anExpressionEvaluatesAndTheNextCellTakesTheFocus() throws Exception {
    enter("1/3 + 1/6");
    waitForOutput(0, "1/2");

    assertThat(get(() -> window.cellViews().size())).isEqualTo(2);
    assertThat(get(() -> stage.getScene().getFocusOwner() == window.cellViews().get(1).field()))
        .isTrue();
    assertThat(get(stage::getTitle)).isEqualTo(AppInfo.name() + " " + Version.current());
    assertThat(get(() -> stage.getIcons().size())).isEqualTo(6);
    assertThat(get(() -> window.cellViews().getFirst().lookupAll(".cell-math").size()))
        .isEqualTo(1);

    enter("f(x) := x^2 + 1");
    waitForOutput(1, "f(x) := x² + 1");
    enter("1 +");
    // the pending placeholder is also one output text; wait for the error itself
    waitUntil(
        10,
        () ->
            window.cellViews().get(2).outputTexts().size() == 1
                && window.cellViews().get(2).outputTexts().getFirst().endsWith("\n1 +\n   ^"));
    assertThat(outputs(2).getFirst()).endsWith("\n1 +\n   ^");
    // a long value is cut, with a note of the rest
    enter("expand((x + y + z + 1)^30)"); // over the typesetting cap: capped text instead
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
    enter("f(x) := x^2/(2 pi) + sqrt(x + 1)");
    enter("diff(f(x), x)");
    enter("[[1, 2], [3, 4]]^-1");
    enter("steps(factor(x^4 - 1))");
    waitUntil(30, () -> window.cellViews().size() == 6 && !busyOnFx());
    fx(
        () ->
            window
                .cellViews()
                .get(4)
                .lookupAll(".cell-steps")
                .forEach(n -> ((TitledPane) n).setExpanded(true)));
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-inspector")).fire());

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
}
