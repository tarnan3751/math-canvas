package mathcanvas.app.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mathcanvas.app.document.Output;
import mathcanvas.app.document.Result;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.print.Format;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The cell-latency benchmark (PLAN.md section 7, Phase 11): the time {@link EvaluationJob} takes,
 * on the test thread and with no window, for twenty typical inputs, each printed and typeset as the
 * worker does before the FX thread shows it. Nineteen are one cell each, run from an empty
 * environment; the twentieth is a slider drag, the three cells that read the slider's variable
 * {@code a} run again with the canvas binding it, as the document runs them while the slider moves.
 *
 * <p>The list runs once unmeasured (the first run, in a JVM that has just loaded the engine, is
 * reported apart as {@code first}), then five times measured, and each input's median, minimum and
 * maximum are reported. Every input must finish without a failure, so a broken input is never timed
 * as an error. It opens no window and asserts no budget: it lives with the UI tests so that {@code
 * ./gradlew build} and the gate never run it, and is tagged {@code benchmark}, so an ordinary UI
 * run leaves it out too ({@code tools/measure.sh} runs it, with {@code -Pbenchmarks}). The figures
 * are printed and written to {@code build/ui-screenshots/cell-latency.txt}.
 */
@Tag("benchmark")
class CellLatencyBenchmarkTest {

  private static final Path REPORT = Path.of("build", "ui-screenshots", "cell-latency.txt");
  private static final int RUNS = 5;

  private static final String MATRIX = "[[2, 1, 0, 3], [1, 4, 2, 0], [0, 2, 5, 1], [3, 0, 1, 6]]";

  /** The single-cell inputs: a label and the cell's text. */
  private static final List<String[]> CELLS =
      List.of(
          new String[] {"arithmetic", "2^100 + 3/7"},
          new String[] {"simplify trig", "simplify(sin(x)^2 + cos(x)^2)"},
          new String[] {"simplify rational", "simplify((x^2 - 1)/(x - 1))"},
          new String[] {"factor", "factor(x^6 - 1)"},
          new String[] {"expand", "expand((x + 2y)^6)"},
          new String[] {"diff", "diff(sin(x^2) e^x, x)"},
          new String[] {"integrate by parts", "integrate(x e^x, x)"},
          new String[] {"integrate twice by parts", "integrate(x^2 sin(x), x)"},
          new String[] {"definite integral", "integrate(1/(x^2 + 1), x, 0, 1)"},
          new String[] {"solve cubic", "solve(x^3 - 6x^2 + 11x - 6 = 0, x)"},
          new String[] {"det 4x4", "det(" + MATRIX + ")"},
          new String[] {"inverse 4x4", "inverse(" + MATRIX + ")"},
          new String[] {"dsolve", "dsolve(y'(x) + y(x) = x, y(x))"},
          new String[] {"limit", "limit((1 - cos(x))/x^2, x, 0)"},
          new String[] {"taylor", "taylor(e^x sin(x), x, 0, 6)"},
          new String[] {"steps diff", "steps(diff(x^2 sin(x), x))"},
          new String[] {"steps integrate", "steps(integrate(x cos(x), x))"},
          new String[] {"steps solve", "steps(solve(x^2 - 5x + 6 = 0, x))"},
          new String[] {"plot", "plot(sin(x) + x/2)"});

  /** The cells a slider drag runs again, each reading the slider's variable. */
  private static final List<String> DRAGGED =
      List.of("a x^2 + 1", "diff(a sin(a x), x)", "integrate(a x, x, 0, a)");

  private static final String DRAG = "slider drag, 3 cells";

  @Test
  void typicalInputs() throws Exception {
    int inputs = CELLS.size() + 1;
    long[] first = new long[inputs];
    long[][] measured = new long[inputs][RUNS];
    for (int run = -1; run < RUNS; run++) {
      for (int k = 0; k < CELLS.size(); k++) {
        long t = cell(CELLS.get(k)[1]);
        if (run < 0) {
          first[k] = t;
        } else {
          measured[k][run] = t;
        }
      }
      long t = drag(run + 2);
      if (run < 0) {
        first[inputs - 1] = t;
      } else {
        measured[inputs - 1][run] = t;
      }
    }

    List<String> lines = new ArrayList<>();
    lines.add(
        String.format(
            Locale.ROOT,
            "cell-latency: %d inputs, one unmeasured run, then %d measured; ms",
            inputs,
            RUNS));
    for (int k = 0; k < inputs; k++) {
      String label = k < CELLS.size() ? CELLS.get(k)[0] : DRAG;
      long[] sorted = measured[k].clone();
      Arrays.sort(sorted);
      lines.add(
          String.format(
              Locale.ROOT,
              "cell-latency: %-26s first %8.2f  median %8.2f  min %8.2f  max %8.2f",
              label,
              first[k] / 1e6,
              sorted[RUNS / 2] / 1e6,
              sorted[0] / 1e6,
              sorted[RUNS - 1] / 1e6));
    }
    String report = String.join("\n", lines);
    System.out.println(report);
    Files.createDirectories(REPORT.getParent());
    Files.writeString(REPORT, report + "\n", StandardCharsets.UTF_8);
  }

  /** Runs one cell from an empty environment; returns its time in nanoseconds. */
  private static long cell(String text) {
    return time(List.of(new EvaluationJob.Input(1, text)), CanvasBindings.NONE);
  }

  /**
   * Runs the dragged cells with the slider at {@code tenths} tenths, as cells the user did not
   * submit; returns their time in nanoseconds.
   */
  private static long drag(int tenths) {
    List<EvaluationJob.Input> cells = new ArrayList<>();
    for (int k = 0; k < DRAGGED.size(); k++) {
      cells.add(new EvaluationJob.Input(k + 1, DRAGGED.get(k), Optional.empty(), false));
    }
    Expr a = CanvasBindings.exact(BigDecimal.valueOf(tenths, 1));
    return time(cells, new CanvasBindings(Map.of("a", a), Map.of(), Set.of()));
  }

  private static long time(List<EvaluationJob.Input> cells, CanvasBindings canvas) {
    List<Output> outputs = new ArrayList<>();
    long start = System.nanoTime();
    boolean finished =
        EvaluationJob.run(
            cells,
            Environment.EMPTY,
            canvas,
            Cancellation.NEVER,
            false,
            Format.EXACT,
            new Inspections(),
            (id, o) -> outputs.add(o));
    long nanos = System.nanoTime() - start;
    assertThat(finished).isTrue();
    assertThat(outputs).hasSize(cells.size());
    for (Output o : outputs) {
      assertThat(o).isInstanceOf(Output.Done.class);
      assertThat(((Output.Done) o).results())
          .as("%s", cells)
          .isNotEmpty()
          .noneMatch(r -> r instanceof Result.Failed);
    }
    return nanos;
  }
}
