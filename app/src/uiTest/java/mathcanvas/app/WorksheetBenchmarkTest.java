package mathcanvas.app;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import javafx.application.Platform;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import mathcanvas.app.document.Cell;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.DocumentSettings;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.document.Output;
import mathcanvas.app.document.Result;
import mathcanvas.app.eval.Document;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Slider;
import mathcanvas.engine.eval.AngleMode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testfx.util.WaitForAsyncUtils;

/**
 * The worksheet benchmark (PLAN.md section 7, Phase 11): a worksheet of one hundred cells, the
 * canvas shown, timed through five things a person does, each from the action on the FX thread to
 * the first pulse that shows its whole effect:
 *
 * <ul>
 *   <li><b>append</b>: a cell typed into the input bar and entered, until it shows its result;
 *   <li><b>re-run</b>: Enter in the fiftieth cell, until it shows its new result;
 *   <li><b>re-run every cell</b>: the angle mode switched, which makes every evaluated cell
 *       pending, until every cell shows its new result;
 *   <li><b>slider</b>: the slider {@code a}, which ten cells and five graphs read, set to a new
 *       value, until every cell shows its result, the canvas its frame and nothing is busy;
 *   <li><b>steps</b>: a cell's steps expander opened, until it is laid out open (closed again
 *       between runs, untimed);
 *   <li><b>scroll</b>: the worksheet scrolled from the top to the end in twenty steps, a pulse
 *       each.
 * </ul>
 *
 * <p>Beside each latency, the FX thread's CPU time over the same span, which the machine's other
 * load and the pulse's timing disturb less. Each runs once unmeasured, then five times measured;
 * the median, minimum and maximum are reported. Tagged {@code benchmark}, it runs only with {@code
 * -Pbenchmarks} and asserts no budget; the figures are printed and written to {@code
 * build/ui-screenshots/worksheet-benchmark.txt}.
 */
@Tag("benchmark")
class WorksheetBenchmarkTest extends WorksheetHarness {

  private static final Path REPORT = SCREENSHOTS.resolve("worksheet-benchmark.txt");
  private static final int RUNS = 5;
  private static final int SCROLL_STEPS = 20;
  private static final int MIDDLE = 50;
  private static final ThreadMXBean THREADS = ManagementFactory.getThreadMXBean();

  /** The FX thread's CPU time over the timed spans since it was last zeroed. */
  private long cpu;

  /** The ten cells that read the slider's variable. */
  private static final List<String> READERS =
      List.of(
          "a x^2 + 1",
          "diff(a sin(a x), x)",
          "integrate(a x, x, 0, a)",
          "expand((x + a)^3)",
          "a^2 + 2a + 1",
          "factor(x^2 - a^2)",
          "solve(x^2 = a, x)",
          "taylor(e^(a x), x, 0, 4)",
          "limit(sin(a x)/x, x, 0)",
          "simplify(a sin(x)^2 + a cos(x)^2)");

  /** The five graphs that read it. */
  private static final List<String> GRAPHS =
      List.of("plot(a sin(x))", "plot(x^2 + a)", "plot(a x)", "plot(cos(a x))", "plot(x^3 + a x)");

  private static final String STEPS = "steps(diff(x^2 sin(x), x))";

  /** The other cells, each with its number {@code k} in it, cycled to fill the hundred. */
  private static final List<String> OTHERS =
      List.of(
          "expand((x + k)^4)",
          "factor(x^2 - k^2)",
          "integrate(x^k, x)",
          "k! + 1/k",
          "diff(sin(k x), x)",
          "det([[k, 1], [2, 3]])",
          "simplify((x^2 - k^2)/(x - k))",
          "v_k := k^2 + 1",
          "solve(x^2 - k x = 0, x)",
          "2^k + 3/7",
          "inverse([[k, 1], [1, 2]])",
          "sum(j^2, j, 1, k)");

  @Test
  void aWorksheetOfAHundredCells() throws Exception {
    fx(() -> ((ToggleButton) stage.getScene().lookup("#toggle-canvas")).fire());
    List<DocumentState.StoredCell> cells = new ArrayList<>();
    cells.add(new DocumentState.StoredCell("a := slider(0, 5, 0.1)", true));
    READERS.forEach(t -> cells.add(new DocumentState.StoredCell(t, true)));
    GRAPHS.forEach(t -> cells.add(new DocumentState.StoredCell(t, true)));
    cells.add(new DocumentState.StoredCell(STEPS, true));
    for (int k = 0; cells.size() < 99; k++) {
      String text = OTHERS.get(k % OTHERS.size()).replace("k", Integer.toString(k + 2));
      cells.add(new DocumentState.StoredCell(text, true));
    }
    cells.add(new DocumentState.StoredCell("", false));
    fx(
        () -> {
          DocumentState before = document().state();
          document()
              .execute(new Command.Replace(before, DocumentState.of(cells, before.settings())));
        });
    waitUntil(120, this::settled);
    assertThat(get(() -> window.cellViews().size())).isEqualTo(100);
    assertThat(
            get(
                () ->
                    document().state().cells().stream()
                        .filter(c -> c.output() instanceof Output.Done d && failed(d))
                        .map(Cell::input)
                        .toList()))
        .isEmpty();
    ObjectId slider = slider();

    List<String> lines = new ArrayList<>();
    lines.add(
        String.format(
            Locale.ROOT,
            "worksheet-benchmark: 100 cells, the canvas shown; one unmeasured run, then %d"
                + " measured; ms",
            RUNS));

    long[] append = new long[RUNS + 1];
    long[] appendCpu = new long[RUNS + 1];
    for (int run = 0; run <= RUNS; run++) {
      cpu = 0;
      append[run] = append(run);
      appendCpu[run] = cpu;
    }
    lines.add(line("append a cell", append));
    lines.add(line("append a cell, FX thread CPU", appendCpu));

    long[] rerun = new long[RUNS + 1];
    long[] rerunCpu = new long[RUNS + 1];
    for (int run = 0; run <= RUNS; run++) {
      cpu = 0;
      rerun[run] = rerun();
      rerunCpu[run] = cpu;
    }
    lines.add(line("re-run the 50th cell", rerun));
    lines.add(line("re-run the 50th cell, FX thread CPU", rerunCpu));

    long[] all = new long[RUNS + 1];
    long[] allCpu = new long[RUNS + 1];
    for (int run = 0; run <= RUNS; run++) {
      cpu = 0;
      all[run] = rerunAll();
      allCpu[run] = cpu;
    }
    lines.add(line("re-run every cell", all));
    lines.add(line("re-run every cell, FX thread CPU", allCpu));

    long[] drag = new long[RUNS + 1];
    long[] dragCpu = new long[RUNS + 1];
    for (int run = 0; run <= RUNS; run++) {
      BigDecimal value = BigDecimal.valueOf(10 + run % 2 * 5 + run, 1);
      cpu = 0;
      drag[run] =
          timed(
              () -> document().execute(new Command.SetSlider(slider, value)),
              () ->
                  settled()
                      && document()
                          .canvasFrameProperty()
                          .get()
                          .state()
                          .equals(document().state().canvas()));
      dragCpu[run] = cpu;
    }
    lines.add(line("slider, 10 cells and 5 graphs", drag));
    lines.add(line("slider, 10 cells and 5 graphs, FX thread CPU", dragCpu));

    long[] steps = new long[RUNS + 1];
    long[] stepsCpu = new long[RUNS + 1];
    for (int run = 0; run <= RUNS; run++) {
      TitledPane pane = get(() -> stepsPane());
      cpu = 0;
      steps[run] =
          timed(
              () -> pane.setExpanded(true),
              () -> pane.isExpanded() && pane.getContent().isVisible() && pane.getHeight() > 60);
      stepsCpu[run] = cpu;
      fx(() -> pane.setExpanded(false));
      pulse();
    }
    lines.add(line("open a cell's steps", steps));
    lines.add(line("open a cell's steps, FX thread CPU", stepsCpu));

    long[] scroll = new long[RUNS + 1];
    long[] scrollCpu = new long[RUNS + 1];
    ScrollPane pane = get(this::scrollPane);
    for (int run = 0; run <= RUNS; run++) {
      fx(() -> pane.setVvalue(pane.getVmin()));
      pulse();
      long total = 0;
      cpu = 0;
      for (int k = 1; k <= SCROLL_STEPS; k++) {
        double to = pane.getVmin() + (pane.getVmax() - pane.getVmin()) * k / SCROLL_STEPS;
        total += timed(() -> pane.setVvalue(to), () -> pane.getVvalue() == to);
      }
      scroll[run] = total;
      scrollCpu[run] = cpu;
    }
    lines.add(line("scroll end to end, 20 steps", scroll));
    lines.add(line("scroll end to end, 20 steps, FX thread CPU", scrollCpu));

    String report = String.join("\n", lines);
    System.out.println(report);
    Files.createDirectories(REPORT.getParent());
    Files.writeString(REPORT, report + "\n", StandardCharsets.UTF_8);
  }

  private Document document() {
    return main.document();
  }

  /** Whether nothing runs and every evaluated cell shows its result. */
  private boolean settled() {
    return !document().busyProperty().get()
        && document().state().cells().stream().noneMatch(c -> c.output() instanceof Output.Pending)
        && window.canvasPane() != null;
  }

  private static boolean failed(Output.Done d) {
    return d.results().stream().anyMatch(r -> r instanceof Result.Failed);
  }

  private ObjectId slider() throws Exception {
    return get(
        () ->
            document().state().canvas().objects().stream()
                .filter(o -> o instanceof Slider s && s.variable().equals("a"))
                .findFirst()
                .orElseThrow()
                .id());
  }

  /** Types a cell into the input bar and enters it; the time until it shows its result. */
  private long append(int run) throws Exception {
    int before = get(() -> document().state().cells().size());
    fx(() -> window.cellViews().getLast().field().requestFocus());
    type("expand((x + " + (run + 1) + ")^5)");
    return timed(
        this::enterKey,
        () -> {
          List<Cell> c = document().state().cells();
          return c.size() == before + 1
              && c.get(before - 1).output() instanceof Output.Done
              && settled();
        });
  }

  /** Enter in the fiftieth cell; the time until it shows its new result. */
  private long rerun() throws Exception {
    Output was = get(() -> document().state().cells().get(MIDDLE - 1).output());
    fx(() -> window.cellViews().get(MIDDLE - 1).field().requestFocus());
    return timed(
        this::enterKey,
        () -> {
          Output now = document().state().cells().get(MIDDLE - 1).output();
          return now != was && now instanceof Output.Done && settled();
        });
  }

  /**
   * The angle mode switched, which makes every evaluated cell pending (Run all runs only the cells
   * that never ran or were stopped); the time until every cell shows its new result.
   */
  private long rerunAll() throws Exception {
    DocumentSettings was = get(() -> document().state().settings());
    DocumentSettings now =
        new DocumentSettings(
            was.precision(),
            was.angleMode() == AngleMode.RADIANS ? AngleMode.DEGREES : AngleMode.RADIANS,
            was.numberFormat(),
            was.numberDigits());
    return timed(() -> document().execute(new Command.SetSettings(now)), this::settled);
  }

  private TitledPane stepsPane() {
    for (var v : window.cellViews()) {
      if (v.field().getText().equals(STEPS)) {
        return (TitledPane) v.lookup(".cell-steps");
      }
    }
    throw new AssertionError("no steps cell");
  }

  private ScrollPane scrollPane() {
    Node n = window.cellViews().getFirst();
    while (!(n instanceof ScrollPane s && s.getStyleClass().contains("worksheet"))) {
      n = n.getParent();
      if (n == null) {
        throw new AssertionError("no worksheet scroll pane");
      }
    }
    return (ScrollPane) n;
  }

  /** Fires Enter at the focused node; on the FX thread. */
  private void enterKey() {
    Node target = stage.getScene().getFocusOwner();
    for (var type : List.of(KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED)) {
      Event.fireEvent(
          target, new KeyEvent(type, "", "", KeyCode.ENTER, false, false, false, false));
    }
  }

  /** Waits for one pulse. */
  private void pulse() throws Exception {
    timed(() -> {}, () -> true);
  }

  /**
   * Runs an action on the FX thread and times it to the first pulse, after a layout, at which the
   * condition holds; adds the FX thread's CPU time over the span to {@link #cpu}.
   */
  private long timed(Runnable action, BooleanSupplier done) throws Exception {
    long[] at = new long[4];
    Runnable[] listener = new Runnable[1];
    fx(
        () -> {
          listener[0] =
              () -> {
                if (at[1] == 0 && done.getAsBoolean()) {
                  at[1] = System.nanoTime();
                  at[3] = THREADS.getCurrentThreadCpuTime();
                }
              };
          stage.getScene().addPostLayoutPulseListener(listener[0]);
          at[0] = System.nanoTime();
          at[2] = THREADS.getCurrentThreadCpuTime();
          action.run();
          Platform.requestNextPulse();
        });
    WaitForAsyncUtils.waitFor(
        120,
        SECONDS,
        () ->
            get(
                () -> {
                  Platform.requestNextPulse();
                  return at[1] != 0;
                }));
    fx(() -> stage.getScene().removePostLayoutPulseListener(listener[0]));
    cpu += at[3] - at[2];
    return at[1] - at[0];
  }

  private static String line(String what, long[] nanos) {
    long[] measured = Arrays.copyOfRange(nanos, 1, nanos.length);
    Arrays.sort(measured);
    return String.format(
        Locale.ROOT,
        "worksheet-benchmark: %-32s first %8.2f  median %8.2f  min %8.2f  max %8.2f",
        what,
        nanos[0] / 1e6,
        measured[RUNS / 2] / 1e6,
        measured[0] / 1e6,
        measured[RUNS - 1] / 1e6);
  }
}
