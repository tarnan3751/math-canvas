package mathcanvas.app;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import mathcanvas.app.ui.WorksheetWindow;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The startup benchmark (PLAN.md section 7, Phase 11): the app opened on a data directory of its
 * own, as {@link WorksheetHarness} opens it, timed to three moments, each read at the first pulse
 * after it: the window <b>shown</b> (its first laid-out pulse), the first cell <b>ready</b> to type
 * (its field focused and skinned), and the <b>first result</b> of {@code 1 + 1} typed into it and
 * entered (the cell showing {@code 2}).
 *
 * <p>The first launch in the JVM is <b>cold</b>: no class of the app is loaded yet. It is timed
 * from the process start (the JVM's, so the test worker's start, JUnit and the toolkit's start are
 * in it) and from the call that opens the window. Five more launches follow in the same JVM,
 * <b>warm</b>, each timed from that call, with their median. {@code tools/measure.sh} runs this
 * test in five JVMs for five cold launches. Tagged {@code benchmark}, it runs only when asked
 * ({@code -Pbenchmarks}) and asserts no budget; the figures are printed and written to {@code
 * build/ui-screenshots/startup-benchmark.txt}.
 */
@Tag("benchmark")
@ExtendWith(ApplicationExtension.class)
class StartupBenchmarkTest {

  private static final Path REPORT = Path.of("build", "ui-screenshots", "startup-benchmark.txt");
  private static final int WARM = 5;

  /** The moments of one launch, in System.nanoTime, set on the FX thread. */
  private static final class Launch {
    volatile long shown;
    volatile long ready;
    volatile long result;
  }

  @Test
  void theWorksheetOpensAndAnswers() throws Exception {
    long sinceProcess =
        System.currentTimeMillis() - ManagementFactory.getRuntimeMXBean().getStartTime();
    long[] cold = launch(System.nanoTime());
    long[] coldFromProcess = new long[3];
    for (int m = 0; m < 3; m++) {
      coldFromProcess[m] = cold[m] + sinceProcess * 1_000_000;
    }
    long[][] warm = new long[3][WARM];
    for (int k = 0; k < WARM; k++) {
      long[] w = launch(System.nanoTime());
      for (int m = 0; m < 3; m++) {
        warm[m][k] = w[m];
      }
    }
    long[] warmMedian = new long[3];
    for (int m = 0; m < 3; m++) {
      long[] sorted = warm[m].clone();
      Arrays.sort(sorted);
      warmMedian[m] = sorted[WARM / 2];
    }

    List<String> lines = new ArrayList<>();
    lines.add(line("cold, since the process start", coldFromProcess));
    lines.add(line("cold, since the window is opened", cold));
    lines.add(line("warm, median of " + WARM + ", since opened", warmMedian));
    lines.add(
        "startup-benchmark: the process start is the JVM's, so the test worker's own start, JUnit"
            + " and the toolkit's start are in the first line; the app is opened on a data"
            + " directory with the welcome already shown, so on an empty worksheet.");
    String report = String.join("\n", lines);
    System.out.println(report);
    Files.createDirectories(REPORT.getParent());
    Files.writeString(REPORT, report + "\n", StandardCharsets.UTF_8);
  }

  /**
   * Opens the app, types {@code 1 + 1} into the first cell once it is ready, waits for its result
   * and closes the app.
   *
   * @param from the nanoTime the times are taken from, just before the window is opened
   * @return the nanoseconds from {@code from} to the window shown, the cell ready and the result
   */
  private static long[] launch(long from) throws Exception {
    Path directory = WorksheetHarness.newDirectory();
    Main main = new Main();
    Launch at = new Launch();
    Stage[] stage = new Stage[1];
    WorksheetWindow[] window = new WorksheetWindow[1];
    fx(
        () -> {
          stage[0] = new Stage();
          window[0] = main.start(stage[0], directory);
          stage[0]
              .getScene()
              .addPostLayoutPulseListener(
                  () -> {
                    long now = System.nanoTime();
                    if (at.shown == 0 && stage[0].isShowing()) {
                      at.shown = now;
                    }
                    if (at.ready == 0
                        && !window[0].cellViews().isEmpty()
                        && stage[0].getScene().getFocusOwner()
                            == window[0].cellViews().get(0).field()
                        && window[0].cellViews().get(0).field().getSkin() != null) {
                      at.ready = now;
                    }
                    if (at.ready != 0
                        && at.result == 0
                        && window[0].cellViews().get(0).outputTexts().equals(List.of("2"))) {
                      at.result = now;
                    }
                  });
        });
    WaitForAsyncUtils.waitFor(30, SECONDS, () -> at.ready != 0);
    fx(
        () -> {
          Node target = stage[0].getScene().getFocusOwner();
          assertThat(target).isInstanceOf(TextInputControl.class);
          // an empty worksheet: its one cell is the input the user types into first
          assertThat(window[0].cellViews()).hasSize(1);
          "1 + 1"
              .codePoints()
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
          for (var type : List.of(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED)) {
            Event.fireEvent(
                target, new KeyEvent(type, "", "", KeyCode.ENTER, false, false, false, false));
          }
        });
    WaitForAsyncUtils.waitFor(30, SECONDS, () -> at.result != 0);
    fx(
        () -> {
          main.stop();
          stage[0].close();
        });
    return new long[] {at.shown - from, at.ready - from, at.result - from};
  }

  private static void fx(Runnable r) throws Exception {
    WaitForAsyncUtils.waitFor(10, SECONDS, WaitForAsyncUtils.asyncFx(r));
  }

  private static String line(String what, long[] nanos) {
    return String.format(
        Locale.ROOT,
        "startup-benchmark: %-36s shown %8.1f ms  ready %8.1f ms  first result %8.1f ms",
        what,
        nanos[0] / 1e6,
        nanos[1] / 1e6,
        nanos[2] / 1e6);
  }
}
