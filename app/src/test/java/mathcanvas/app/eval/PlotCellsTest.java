package mathcanvas.app.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import mathcanvas.app.document.Cell;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.document.Output;
import mathcanvas.app.document.PlotSpec;
import mathcanvas.app.document.Result;
import mathcanvas.app.document.Welcome;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.ImplicitCurve;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Style;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.print.Format;
import mathcanvas.engine.print.UnicodePrinter;
import org.junit.jupiter.api.Test;

/**
 * The {@code plot} statement: the graphs a cell draws on the canvas, which it owns, so its next
 * submission replaces them and clearing or deleting it removes them, all in the submission's undo
 * step ({@code docs/language.md} §12.3).
 */
class PlotCellsTest {

  /** An executor that queues its tasks until the test runs them. */
  private static final class Queue implements Executor {
    final Deque<Runnable> tasks = new ArrayDeque<>();

    @Override
    public void execute(Runnable task) {
      tasks.add(task);
    }

    void runAll() {
      while (!tasks.isEmpty()) {
        tasks.poll().run();
      }
    }
  }

  private final Queue worker = new Queue();
  private final Queue ui = new Queue();
  private long now;
  private final Document doc =
      new Document(
          DocumentState.EMPTY, worker, ui, () -> now, new TestSampling(), CanvasJob.Content.NONE);

  private void settle() {
    while (!worker.tasks.isEmpty() || !ui.tasks.isEmpty()) {
      worker.runAll();
      ui.runAll();
    }
  }

  /** Types the text into a cell and submits it, as Enter does, then lets the jobs finish. */
  private void submit(long id, String text) {
    now += 2_000_000_000L; // beyond the merge interval: each edit is a step of its own
    doc.execute(new Command.EditInput(id, text));
    doc.execute(new Command.Submit(id));
    settle();
  }

  private long cell(int index) {
    return doc.state().cells().get(index).id();
  }

  private List<ObjectId> plots(int index) {
    return doc.state().plotsOf(cell(index));
  }

  private CanvasObject object(ObjectId id) {
    return doc.state().canvas().object(id).orElseThrow();
  }

  private String body(ObjectId id) {
    return switch (object(id)) {
      case FunctionGraph g -> "y = " + UnicodePrinter.INSTANCE.print(g.body());
      case ImplicitCurve c -> UnicodePrinter.INSTANCE.print(c.equation());
      default -> object(id).getClass().getSimpleName();
    };
  }

  private static Output.Done run(String text, boolean submitted) {
    List<Output> out = new ArrayList<>();
    EvaluationJob.run(
        List.of(new EvaluationJob.Input(1, text, Optional.empty(), submitted)),
        Environment.EMPTY,
        Cancellation.NEVER,
        false,
        Format.EXACT,
        new Inspections(),
        (id, o) -> out.add(o));
    return (Output.Done) out.getFirst();
  }

  private static List<String> texts(Output.Done d) {
    return d.results().stream()
        .map(
            r ->
                switch (r) {
                  case Result.Plotted p -> p.text();
                  case Result.Failed f -> "error: " + f.message();
                  default -> r.getClass().getSimpleName();
                })
        .toList();
  }

  @Test
  void aPlotStatementShowsEachGraphsEquationAndListsItsGraphs() {
    Output.Done d = run("f(x) := x^2 - 2x\nplot(f(x), x^2 + y^2 = 4)", true);

    assertThat(texts(d)).containsExactly("Defined", "y = x² - 2x", "x² + y² = 4");
    assertThat(d.plots()).extracting(PlotSpec::function).containsExactly(true, false);
    // the graph keeps the call, so it follows f
    assertThat(UnicodePrinter.INSTANCE.print(d.plots().getFirst().equation()))
        .isEqualTo("y = f(x)");
    assertThat(d.reads()).contains("f");
  }

  @Test
  void onlyASubmissionDrawsAndAQuietPlotShowsNothing() {
    assertThat(run("plot(x^2)", false).plots()).isEmpty();
    assertThat(texts(run("plot(x^2)", false))).containsExactly("y = x²");
    Output.Done quiet = run("plot(sin(x));", true);
    assertThat(quiet.results()).isEmpty();
    assertThat(quiet.plots()).hasSize(1);
  }

  @Test
  void aPlotOfNoGraphIsTheCellsErrorAndDrawsNothing() {
    String message = "error: `plot` needs an expression in x or an equation in x and y";
    assertThat(texts(run("plot(x < 1)", true)).getFirst()).startsWith(message);
    assertThat(texts(run("plot([1, 2])", true)).getFirst()).startsWith(message);
    assertThat(run("plot(x, x < 1)", true).plots()).isEmpty();
    // an equation true everywhere, in neither x nor y, or undefined draws nothing
    for (String nothing : List.of("plot(y)", "plot(1 = 1)", "plot(1/0)", "plot(x^2, 1/0)")) {
      Output.Done d = run(nothing, true);
      assertThat(texts(d).getFirst()).as(nothing).startsWith(message);
      assertThat(d.plots()).as(nothing).isEmpty();
    }
    // anywhere but a statement of its own, plot is the engine's, which refuses it
    assertThat(texts(run("1 + plot(x)", true)).getFirst())
        .isEqualTo("error: `plot` works only as a cell of its own in the app, as in `plot(x^2)`");
  }

  @Test
  void aCellOwnsItsGraphsAndRunningItAgainReplacesThem() {
    submit(cell(0), "plot(x^2)");
    ObjectId first = plots(0).getFirst();
    assertThat(body(first)).isEqualTo("y = x²");
    doc.execute(new Command.SetStyle(first, new Style(3, 2, false, false, false)));

    // the same text again: the same graph, its style kept
    doc.execute(new Command.Submit(cell(0)));
    settle();
    assertThat(plots(0)).containsExactly(first);

    // a new body replaces it in place, a second argument adds a graph
    submit(cell(0), "plot(x^3, x^2 + y^2 = 1)");
    assertThat(plots(0)).hasSize(2).startsWith(first);
    assertThat(body(first)).isEqualTo("y = x³");
    assertThat(object(first).style().visible()).isFalse();
    assertThat(body(plots(0).get(1))).isEqualTo("x² + y² = 1");

    // fewer arguments remove the rest; text that plots nothing removes them all
    ObjectId second = plots(0).get(1);
    submit(cell(0), "plot(x^3)");
    assertThat(plots(0)).containsExactly(first);
    assertThat(doc.state().canvas().object(second)).isEmpty();
    submit(cell(0), "1 + 1");
    assertThat(plots(0)).isEmpty();
    assertThat(doc.state().canvas().objects()).isEmpty();
  }

  @Test
  void eachArgumentKeepsItsGraphWhenAnEarlierOneWasDeleted() {
    submit(cell(0), "plot(x^2, x^3)");
    ObjectId first = plots(0).get(0);
    ObjectId second = plots(0).get(1);
    doc.execute(
        new Command.AddObject(
            id ->
                new PointOnPath(
                    id, Style.DEFAULT, second, ParamKind.X, 1, Double.NaN, Double.NaN)));
    ObjectId point = doc.state().canvas().objects().getLast().id();
    doc.execute(new Command.RemoveObject(first));
    settle();
    assertThat(plots(0)).containsExactly(second);

    doc.execute(new Command.Submit(cell(0)));
    settle();
    // x³ is still its own graph, with the point on it; x² is drawn anew in its own slot
    assertThat(plots(0)).hasSize(2).endsWith(second);
    assertThat(body(second)).isEqualTo("y = x³");
    assertThat(body(plots(0).getFirst())).isEqualTo("y = x²");
    assertThat(plots(0).getFirst()).isNotEqualTo(first);
    assertThat(doc.state().canvas().object(point)).isPresent();
  }

  @Test
  void theEquationShownKeepsXAndYFree() {
    submit(cell(0), "x := 3");
    submit(cell(1), "plot(x^2)");
    submit(cell(2), "y := 2");
    submit(cell(3), "plot(x^2 + y^2 = 4)");

    assertThat(texts((Output.Done) doc.state().cells().get(1).output())).containsExactly("y = x²");
    assertThat(texts((Output.Done) doc.state().cells().get(3).output()))
        .containsExactly("x² + y² = 4");
    assertThat(body(plots(1).getFirst())).isEqualTo("y = x²");
  }

  @Test
  void twoCellsWithOneBodyDrawTwoGraphs() {
    submit(cell(0), "plot(x^2)");
    submit(cell(1), "plot(x^2)");

    assertThat(doc.state().canvas().objects()).hasSize(2);
    assertThat(plots(0)).doesNotContainAnyElementsOf(plots(1));
  }

  @Test
  void clearingOrDeletingTheCellRemovesItsGraphs() {
    submit(cell(0), "plot(x^2)");
    submit(cell(1), "plot(x^3)");

    submit(cell(0), "");
    assertThat(plots(0)).isEmpty();
    assertThat(doc.state().canvas().objects()).hasSize(1);

    doc.execute(new Command.DeleteCell(cell(1)));
    settle();
    assertThat(doc.state().canvas().objects()).isEmpty();
    assertThat(doc.state().plots()).isEmpty();
  }

  @Test
  void aGraphDeletedOnTheCanvasComesBackOnlyWithTheNextSubmission() {
    submit(cell(0), "a := slider(0, 5, 1)");
    submit(cell(1), "plot(x + a)");
    ObjectId graph = plots(1).getFirst();
    doc.execute(new Command.RemoveObject(graph));
    settle();
    assertThat(plots(1)).isEmpty();

    // a slider's drag runs the cell again, which draws nothing
    ObjectId slider = doc.state().canvas().objects().getFirst().id();
    doc.execute(new Command.SetSlider(slider, java.math.BigDecimal.TWO));
    settle();
    assertThat(plots(1)).isEmpty();
    Cell c = doc.state().cells().get(1);
    assertThat(texts((Output.Done) c.output())).containsExactly("y = x + 2");

    doc.execute(new Command.Submit(cell(1)));
    settle();
    assertThat(plots(1)).hasSize(1);
  }

  @Test
  void theGraphsAreInTheSubmissionsUndoStep() {
    submit(cell(0), "plot(x^2)");
    ObjectId graph = plots(0).getFirst();

    doc.undo(); // the submission
    settle();
    assertThat(doc.state().canvas().objects()).isEmpty();
    assertThat(doc.state().plots()).isEmpty();

    doc.redo();
    settle();
    assertThat(plots(0)).containsExactly(graph);
    assertThat(body(graph)).isEqualTo("y = x²");

    submit(cell(0), "");
    assertThat(doc.state().canvas().objects()).isEmpty();
    doc.undo();
    settle();
    assertThat(plots(0)).containsExactly(graph);
  }

  @Test
  void theWelcomeWorksheetDrawsItsSliderAndGraphsAndOneUndoRestoresTheWorksheetBefore() {
    submit(cell(0), "1 + 1");
    DocumentState before = doc.state();
    doc.execute(new Command.Replace(before, Welcome.state(before.settings())));
    settle();

    // its cells are submissions: the slider and the two graphs are made
    assertThat(doc.state().canvas().objects())
        .extracting(o -> o.getClass().getSimpleName())
        .containsExactlyInAnyOrder("Slider", "FunctionGraph", "FunctionGraph");
    assertThat(doc.state().cells().stream().map(Cell::output))
        .noneMatch(o -> o instanceof Output.Pending);
    String steps = "steps(solve(f(x) = 0, x))";
    Cell solve =
        doc.state().cells().stream().filter(c -> c.input().equals(steps)).findFirst().orElseThrow();
    assertThat(((Result.Value) ((Output.Done) solve.output()).results().getFirst()).traced())
        .isTrue();

    doc.undo();
    settle();
    assertThat(doc.state().cells().getFirst().input()).isEqualTo("1 + 1");
    assertThat(doc.state().canvas().objects()).isEmpty();
  }
}
