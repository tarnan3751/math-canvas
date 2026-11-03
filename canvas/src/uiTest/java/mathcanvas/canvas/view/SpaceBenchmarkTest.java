package mathcanvas.canvas.view;

import static java.util.concurrent.TimeUnit.SECONDS;
import static mathcanvas.canvas.view.UiHarness.SCREENSHOTS;
import static mathcanvas.canvas.view.UiHarness.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testfx.util.WaitForAsyncUtils.asyncFx;
import static org.testfx.util.WaitForAsyncUtils.waitFor;
import static org.testfx.util.WaitForAsyncUtils.waitForFxEvents;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.space.Camera;
import mathcanvas.canvas.space.SpaceScene;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The 3D view's benchmark (PLAN.md section 7, Phase 7, the acceptance criterion: interactive with a
 * 200 × 200 surface mesh): {@code z = x² − y²} on a 200 × 200 grid in a 1200 by 800 view, the
 * camera orbiting by 0.03 radians a frame for 120 measured frames after 20 warm-up frames, twice:
 * once drawing the coarse 50 × 50 mesh, as the view does while the camera moves, once the full
 * mesh, as it does at rest. Each frame the test thread sets the camera on the FX thread and waits
 * for the frame that paints it. It reports, median and 95th percentile, the FX thread's paint
 * (projecting, sorting and recording the quads) and the whole frame, which the Prism render thread
 * paces (the pulse logger measures that thread, as for the plane's benchmarks), against the 33 ms
 * target; the figures go to {@code build/ui-screenshots/space-benchmark.txt} and the drawing to
 * {@code space-benchmark.png}. Only the whole run is asserted, against 60 s.
 */
@ExtendWith(ApplicationExtension.class)
class SpaceBenchmarkTest {

  private static final int WARM_UP = 20;
  private static final int FRAMES = 120;

  private Stage stage;
  private SpaceView view;
  private final BlockingQueue<SpaceView.Frame> frames = new LinkedBlockingQueue<>();

  @Start
  void start(Stage stage) {
    this.stage = stage;
    view = new SpaceView();
    view.setFrameListener(frames::add);
    stage.setTitle("Space benchmark");
    stage.setScene(new Scene(new StackPane(view), 1200, 800));
    stage.sizeToScene();
    stage.show();
  }

  @Test
  void aFullMeshOrbits() throws Exception {
    long runStart = System.nanoTime();
    SpaceScene scene = SpaceViewTest.saddle(CanvasTheme.LIGHT, 200);
    waitFor(10, SECONDS, asyncFx(() -> view.sceneContentProperty().set(scene)));
    for (int i = 0; i < 5; i++) {
      waitForFxEvents();
    }
    List<String> lines = new ArrayList<>();
    lines.add(
        String.format(
            Locale.ROOT,
            "space-benchmark: z = x^2 - y^2 on 200 x 200, %d frames after %d warm-up, %.0fx%.0f px",
            FRAMES,
            WARM_UP,
            view.getWidth(),
            view.getHeight()));
    for (boolean coarse : new boolean[] {true, false}) {
      waitFor(10, SECONDS, asyncFx(() -> view.setMoving(coarse)));
      for (int i = 0; i < 5; i++) {
        waitForFxEvents();
      }
      frames.clear();
      long[] paint = new long[FRAMES];
      long[] frame = new long[FRAMES];
      for (int f = -WARM_UP; f < FRAMES; f++) {
        Camera c = Camera.DEFAULT.orbit(0.03 * (f + WARM_UP + 1), 0);
        long start = System.nanoTime();
        asyncFx(() -> view.setCamera(c));
        SpaceView.Frame painted = frames.poll(10, SECONDS);
        long end = System.nanoTime();
        assertThat(painted).as("frame %d painted within 10 s", f).isNotNull();
        if (f >= 0) {
          assertThat(painted.coarse()).isEqualTo(coarse);
          paint[f] = painted.nanos();
          frame[f] = end - start;
        }
      }
      String mesh = coarse ? "orbiting, 50 x 50" : "at rest, 200 x 200";
      lines.add(line(mesh + ": paint (FX thread)", paint));
      lines.add(line(mesh + ": whole frame", frame));
    }
    WritableImage image = waitFor(10, SECONDS, asyncFx(() -> stage.getScene().snapshot(null)));
    write(image, SCREENSHOTS.resolve("space-benchmark.png"));
    long run = System.nanoTime() - runStart;
    lines.add(
        "space-benchmark: the whole frame is paced by the Prism render thread; the pulse logger"
            + " measures it (JAVA_TOOL_OPTIONS=-Djavafx.pulseLogger=true, the Painting phase)."
            + " The target is 33 ms while orbiting.");
    lines.add(String.format(Locale.ROOT, "space-benchmark: whole run %.1f s", run / 1e9));
    String report = String.join("\n", lines);
    System.out.println(report);
    Files.createDirectories(SCREENSHOTS);
    Files.writeString(
        SCREENSHOTS.resolve("space-benchmark.txt"), report + "\n", StandardCharsets.UTF_8);
    assertThat(run).as("the whole run within 60 s").isLessThan(SECONDS.toNanos(60));
  }

  private static String line(String what, long[] nanos) {
    long[] sorted = nanos.clone();
    Arrays.sort(sorted);
    double median = (sorted[(sorted.length - 1) / 2] + sorted[sorted.length / 2]) / 2e6;
    double p95 = sorted[(int) Math.ceil(0.95 * sorted.length) - 1] / 1e6;
    return String.format(
        Locale.ROOT, "space-benchmark: %-40s median %6.2f ms, p95 %6.2f ms", what, median, p95);
  }
}
