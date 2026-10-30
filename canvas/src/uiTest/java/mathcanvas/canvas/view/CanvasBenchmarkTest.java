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
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Rgba;
import mathcanvas.canvas.paint.Stroke;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The first-week painting benchmark (PLAN.md section 7, Phase 3; section 12, item 4): ten polylines
 * of 2,000 points each in a 1200 by 800 view, and a simulated slider drag that moves every curve
 * and the slider's handle each frame, for 120 measured frames after 20 unmeasured warm-up frames.
 *
 * <p>Each frame the test thread recomputes the ten curves and builds their content with its hit
 * index (the work a worker does in the app), hands it to the view on the FX thread, and waits for
 * the frame that paints it. It reports, median and 95th percentile, the recompute, the FX paint of
 * the dirty layers (the objects and the overlay; the grid stays clean, which every measured frame
 * checks), and beside them the whole frame from the start of the recompute to the frame painted.
 * The whole frame is what a drag feels, and it is paced by the Prism render thread: the FX thread's
 * paint only records canvas commands, the render thread rasterises them afterwards, and a pulse
 * waits for the previous frame's rendering before it hands over the next. No public API measures
 * the render thread; JavaFX's pulse logger does ({@code
 * JAVA_TOOL_OPTIONS=-Djavafx.pulseLogger=true}, its {@code Painting} phase), and the report says
 * so. The 16 ms figure of section 12 is read against the whole frame and that phase, not asserted;
 * only the whole run is, against the 30 s budget. The figures are printed and written to {@code
 * build/ui-screenshots/canvas-benchmark.txt}, the drawing to {@code canvas-benchmark.png}.
 */
@ExtendWith(ApplicationExtension.class)
class CanvasBenchmarkTest {

  private static final int CURVES = 10;
  private static final int POINTS = 2_000;
  private static final int WARM_UP = 20;
  private static final int FRAMES = 120;
  private static final long BUDGET_NANOS = SECONDS.toNanos(30);

  private Stage stage;
  private CanvasView view;
  private final BlockingQueue<CanvasView.Frame> frames = new LinkedBlockingQueue<>();
  private volatile double handle;

  @Start
  void start(Stage stage) {
    this.stage = stage;
    view = new CanvasView();
    view.setFrameListener(frames::add);
    view.overlayProperty()
        .set(p -> p.point(handle, -6, 7, CanvasTheme.LIGHT.series().get(CanvasTheme.SERIES - 1)));
    stage.setTitle("Canvas benchmark");
    stage.setScene(new Scene(new StackPane(view), 1200, 800));
    stage.sizeToScene();
    stage.show();
  }

  /** The ten curves at a slider value: sines of different frequencies, shifted by the slider. */
  private static PlotContent curves(double slider) {
    List<PlotContent.Curve> curves = new ArrayList<>(CURVES);
    for (int k = 0; k < CURVES; k++) {
      double[] xs = new double[POINTS];
      double[] ys = new double[POINTS];
      double frequency = 0.5 + 0.25 * k;
      double amplitude = 1 + 0.3 * k;
      for (int i = 0; i < POINTS; i++) {
        double x = -16 + 32.0 * i / (POINTS - 1);
        xs[i] = x;
        ys[i] = amplitude * Math.sin(frequency * (x - slider)) + 0.4 * (k - CURVES / 2.0);
      }
      Rgba colour = CanvasTheme.LIGHT.series().get(k % CanvasTheme.SERIES);
      curves.add(new PlotContent.Curve(xs, ys, Stroke.solid(colour, 2)));
    }
    return PlotContent.of(curves, List.of());
  }

  @Test
  void tenCurvesFollowASliderDrag() throws Exception {
    long runStart = System.nanoTime();
    waitFor(10, SECONDS, () -> waitFor(10, SECONDS, asyncFx(() -> view.frames() > 0)));
    for (int i = 0; i < 5; i++) {
      waitForFxEvents();
    }
    frames.clear();

    long[] recompute = new long[FRAMES];
    long[] paint = new long[FRAMES];
    long[] frame = new long[FRAMES];
    for (int f = -WARM_UP; f < FRAMES; f++) {
      double slider = 4 * Math.sin(2 * Math.PI * (f + WARM_UP) / (FRAMES + WARM_UP));
      long start = System.nanoTime();
      PlotContent content = curves(slider);
      long built = System.nanoTime();
      asyncFx(
          () -> {
            handle = slider;
            view.setContent(content);
            view.invalidate(Layer.OVERLAY);
          });
      CanvasView.Frame painted = frames.poll(10, SECONDS);
      long end = System.nanoTime();
      assertThat(painted).as("frame %d painted within 10 s", f).isNotNull();
      // A warm-up frame may still carry the window's first layout, which repaints every layer.
      if (f >= 0) {
        assertThat(painted.painted()).isEqualTo(EnumSet.of(Layer.OBJECTS, Layer.OVERLAY));
        recompute[f] = built - start;
        paint[f] = painted.nanos().values().stream().mapToLong(Long::longValue).sum();
        frame[f] = end - start;
      }
    }
    WritableImage image = waitFor(10, SECONDS, asyncFx(() -> stage.getScene().snapshot(null)));
    write(image, SCREENSHOTS.resolve("canvas-benchmark.png"));
    long run = System.nanoTime() - runStart;

    String report =
        String.join(
            "\n",
            String.format(
                Locale.ROOT,
                "canvas-benchmark: %d polylines x %d points, %d frames after %d warm-up, %.0fx%.0f"
                    + " px",
                CURVES,
                POINTS,
                FRAMES,
                WARM_UP,
                view.getWidth(),
                view.getHeight()),
            line("recompute (sample + hit index, test thread)", recompute),
            line("paint (record dirty layers, FX thread)", paint),
            line("whole frame (recompute to painted)", frame),
            "canvas-benchmark: the whole frame is paced by the Prism render thread, which"
                + " rasterises the recorded commands after the FX thread and is in no figure above;"
                + " the pulse logger measures it (JAVA_TOOL_OPTIONS=-Djavafx.pulseLogger=true,"
                + " the Painting phase). Read the 16 ms figure (PLAN.md section 12, item 4) against"
                + " the whole frame and that phase, not against recompute or paint.",
            String.format(Locale.ROOT, "canvas-benchmark: whole run %.1f s", run / 1e9));
    System.out.println(report);
    Files.createDirectories(SCREENSHOTS);
    Files.writeString(
        SCREENSHOTS.resolve("canvas-benchmark.txt"), report + "\n", StandardCharsets.UTF_8);
    assertThat(run).as("the whole run within 30 s").isLessThan(BUDGET_NANOS);
  }

  private static String line(String what, long[] nanos) {
    long[] sorted = nanos.clone();
    Arrays.sort(sorted);
    double median = (sorted[(sorted.length - 1) / 2] + sorted[sorted.length / 2]) / 2e6;
    double p95 = sorted[(int) Math.ceil(0.95 * sorted.length) - 1] / 1e6;
    return String.format(
        Locale.ROOT, "canvas-benchmark: %-44s median %6.2f ms, p95 %6.2f ms", what, median, p95);
  }
}
