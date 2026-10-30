package mathcanvas.canvas.view;

import static java.util.concurrent.TimeUnit.SECONDS;
import static mathcanvas.canvas.view.UiHarness.SCREENSHOTS;
import static mathcanvas.canvas.view.UiHarness.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testfx.util.WaitForAsyncUtils.asyncFx;
import static org.testfx.util.WaitForAsyncUtils.waitFor;
import static org.testfx.util.WaitForAsyncUtils.waitForFxEvents;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Window;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.sample.Samplers;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Definition;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;

/**
 * The first-week benchmark with real graphs (PLAN.md section 7, Phase 3; section 12, item 4): ten
 * function graphs in a 1200 by 800 view, sampled by the real sampler, and a slider {@code a}
 * dragged for 120 measured frames after 20 warm-up frames, twice: once recomputing what the
 * slider's change recomputes in the app (its reader {@code x³ − a·x}), and once resampling all ten
 * every frame, the worst case of a pan.
 *
 * <p>Each frame the test thread makes the sampler for the slider's new value, recomputes the canvas
 * and builds the content with its hit index (the worker's work in the app), hands it to the view
 * and waits for the frame that paints it. It reports, median and 95th percentile, the recompute,
 * the FX paint of the dirty layers, their sum, and the whole frame, as {@code CanvasBenchmarkTest}
 * does, with the vertices drawn; none includes the Prism render thread. Only the whole run is
 * asserted, against 60 s. The figures go to {@code build/ui-screenshots/graph-benchmark.txt} and
 * the drawing to {@code graph-benchmark.png}.
 */
@ExtendWith(ApplicationExtension.class)
class GraphBenchmarkTest {

  /** Six graphs from PLAN.md section 6's examples and the four the brief adds. */
  static final List<String> GRAPHS =
      List.of(
          "x^2 sin(x)",
          "x e^x",
          "(x + 1)^3",
          "x^4 - 1",
          "x^2 - 5x + 6",
          "x^2 + 1",
          "sin(x)/x",
          "tan(x)",
          "e^(-x^2)",
          "x^3 - a x");

  private static final int WARM_UP = 20;
  private static final int FRAMES = 120;
  private static final long BUDGET_NANOS = SECONDS.toNanos(60);

  private Stage stage;
  private CanvasView view;
  private final BlockingQueue<CanvasView.Frame> frames = new LinkedBlockingQueue<>();

  @Start
  void start(Stage stage) {
    this.stage = stage;
    view = new CanvasView();
    view.setFrameListener(frames::add);
    stage.setTitle("Graph benchmark");
    stage.setScene(new Scene(new StackPane(view), 1200, 800));
    stage.sizeToScene();
    stage.show();
  }

  private static CanvasState scene() {
    CanvasState s =
        CanvasState.EMPTY.add(
            i ->
                new Slider(
                    i,
                    Style.DEFAULT,
                    "a",
                    BigDecimal.valueOf(-4),
                    BigDecimal.valueOf(4),
                    new BigDecimal("0.01"),
                    BigDecimal.ZERO));
    for (int k = 0; k < GRAPHS.size(); k++) {
      String body = GRAPHS.get(k);
      Style style = new Style(k % Style.COLOURS, 2, false, true, false);
      s =
          s.add(
              i ->
                  new FunctionGraph(
                      i,
                      style,
                      Parser.parseExpression(body, Names.NONE),
                      Symbol.of("x"),
                      FunctionGraph.ALL));
    }
    return s;
  }

  @Test
  void tenGraphsFollowASliderDrag() throws Exception {
    long runStart = System.nanoTime();
    waitFor(10, SECONDS, () -> waitFor(10, SECONDS, asyncFx(() -> view.frames() > 0)));
    Viewport v = waitFor(10, SECONDS, asyncFx(view::getViewport));
    CanvasTheme theme = waitFor(10, SECONDS, asyncFx(view::theme));
    Window window = new Window(v.xMin(), v.xMax(), v.yMin(), v.yMax());
    Samplers samplers = new Samplers();
    CanvasState state =
        scene()
            .recomputeAll(
                sampler(samplers, BigDecimal.ZERO, v),
                window,
                Recomputes.standard(),
                Cancellation.NEVER);
    List<String> report = new ArrayList<>();
    report.add(
        String.format(
            Locale.ROOT,
            "graph-benchmark: %d graphs, %d frames after %d warm-up, %.0fx%.0f px",
            GRAPHS.size(),
            FRAMES,
            WARM_UP,
            view.getWidth(),
            view.getHeight()));
    state = drag(state, samplers, v, window, theme, false, report);
    drag(state, samplers, v, window, theme, true, report);
    report.add(
        "graph-benchmark: the Prism render thread is not included in any figure; compare"
            + " recompute + paint with the 16 ms reopen figure (PLAN.md section 12, item 4)");

    WritableImage image = waitFor(10, SECONDS, asyncFx(() -> stage.getScene().snapshot(null)));
    write(image, SCREENSHOTS.resolve("graph-benchmark.png"));
    long run = System.nanoTime() - runStart;
    report.add(String.format(Locale.ROOT, "graph-benchmark: whole run %.1f s", run / 1e9));
    String text = String.join("\n", report);
    System.out.println(text);
    Files.createDirectories(SCREENSHOTS);
    Files.writeString(
        SCREENSHOTS.resolve("graph-benchmark.txt"), text + "\n", StandardCharsets.UTF_8);
    assertThat(run).as("the whole run within 60 s").isLessThan(BUDGET_NANOS);
  }

  private static Sampler sampler(Samplers samplers, BigDecimal a, Viewport v) {
    Environment env = Environment.EMPTY.with("a", new Definition.Value(Num.of(Real.of(a))));
    return samplers.over(env, Settings.DEFAULT, v.width(), v.height());
  }

  /** One drag of the slider, measured; returns the state at its end. */
  private CanvasState drag(
      CanvasState state,
      Samplers samplers,
      Viewport v,
      Window window,
      CanvasTheme theme,
      boolean all,
      List<String> report)
      throws Exception {
    for (int i = 0; i < 5; i++) {
      waitForFxEvents();
    }
    frames.clear();
    long[] recompute = new long[FRAMES];
    long[] paint = new long[FRAMES];
    long[] frame = new long[FRAMES];
    long vertices = 0;
    Set<ObjectId> everything = new java.util.HashSet<>();
    state.objects().forEach(o -> everything.add(o.id()));
    for (int f = -WARM_UP; f < FRAMES; f++) {
      BigDecimal a =
          BigDecimal.valueOf(3 * Math.sin(2 * Math.PI * (f + WARM_UP) / (FRAMES + WARM_UP)))
              .setScale(2, RoundingMode.HALF_EVEN);
      long start = System.nanoTime();
      state =
          state.recompute(
              all ? everything : Set.of(),
              Set.of("a"),
              sampler(samplers, a, v),
              window,
              Recomputes.standard(),
              Cancellation.NEVER);
      PlotContent content = CanvasContent.build(state, Map.of(), theme).content();
      long built = System.nanoTime();
      asyncFx(() -> view.setContent(content));
      CanvasView.Frame painted = frames.poll(10, SECONDS);
      long end = System.nanoTime();
      assertThat(painted).as("frame %d painted within 10 s", f).isNotNull();
      // a pointer over the window may also repaint the overlay; the grid stays clean
      assertThat(painted.painted()).contains(Layer.OBJECTS).doesNotContain(Layer.GRID);
      if (f >= 0) {
        recompute[f] = built - start;
        paint[f] = painted.nanos().values().stream().mapToLong(Long::longValue).sum();
        frame[f] = end - start;
        if (f == 0) {
          for (var g : state.geometry().values()) {
            if (g instanceof Geometry.Curve c) {
              for (var b : c.branches()) {
                vertices += b.size();
              }
            }
          }
        }
      }
    }
    long[] work = new long[FRAMES];
    for (int f = 0; f < FRAMES; f++) {
      work[f] = recompute[f] + paint[f];
    }
    String what = all ? "all ten resampled" : "the slider's reader";
    report.add(
        String.format(Locale.ROOT, "graph-benchmark: %s, %d vertices drawn", what, vertices));
    report.add(line(what + ": recompute (sample + content)", recompute));
    report.add(line(what + ": paint (dirty layers, FX)", paint));
    report.add(line(what + ": recompute + paint", work));
    report.add(line(what + ": frame (end to end)", frame));
    return state;
  }

  private static String line(String what, long[] nanos) {
    long[] sorted = nanos.clone();
    Arrays.sort(sorted);
    double median = (sorted[(sorted.length - 1) / 2] + sorted[sorted.length / 2]) / 2e6;
    double p95 = sorted[(int) Math.ceil(0.95 * sorted.length) - 1] / 1e6;
    return String.format(
        Locale.ROOT, "graph-benchmark: %-50s median %6.2f ms, p95 %6.2f ms", what, median, p95);
  }
}
