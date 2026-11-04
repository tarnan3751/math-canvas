package mathcanvas.app.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import mathcanvas.app.document.Cell;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.DocumentSettings;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.document.NumberFormat;
import mathcanvas.app.document.Output;
import mathcanvas.app.document.Result;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.space.Camera;
import mathcanvas.engine.eval.AngleMode;
import org.junit.jupiter.api.Test;

/** The document on executors run by hand: the worker and the FX thread are two queues. */
class DocumentTest {

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
  private final Document doc = new Document(DocumentState.EMPTY, worker, ui, () -> now);
  private final List<Document.Change> changes = new ArrayList<>();

  {
    doc.addListener(changes::add);
  }

  private void settle() {
    while (!worker.tasks.isEmpty() || !ui.tasks.isEmpty()) {
      worker.runAll();
      ui.runAll();
    }
  }

  private long lastId() {
    return doc.state().cells().getLast().id();
  }

  private void enter(String text) {
    long id = lastId();
    doc.execute(new Command.EditInput(id, text));
    doc.execute(new Command.Submit(id));
  }

  private List<String> shown() {
    List<String> out = new ArrayList<>();
    for (Cell c : doc.state().cells()) {
      switch (c.output()) {
        case Output.Done d ->
            out.add(
                String.join(
                    "; ",
                    d.results().stream()
                        .map(
                            r ->
                                switch (r) {
                                  case Result.Value v -> v.text();
                                  case Result.Defined def -> def.text();
                                  case Result.Printed p -> "print " + p.text();
                                  case Result.Plotted p -> "plot " + p.text();
                                  case Result.Note n -> "# " + n.text();
                                  case Result.Function fn -> fn.text();
                                  case Result.Ran _ -> "(ran)";
                                  case Result.Failed f -> "error";
                                })
                        .toList()));
        case Output.Pending p -> out.add("pending");
        case Output.Cancelled x -> out.add("cancelled");
        case Output.None n -> out.add("-");
      }
    }
    return out;
  }

  @Test
  void aSubmittedCellIsPendingUntilItsResultArrives() {
    enter("1/3 + 1/6");

    assertThat(doc.busyProperty().get()).isTrue();
    assertThat(shown()).containsExactly("pending", "-");
    settle();
    assertThat(shown()).containsExactly("1/2", "-");
    assertThat(doc.busyProperty().get()).isFalse();
  }

  @Test
  void editingAnEarlierCellReevaluatesTheCellsAfterIt() {
    enter("a := 2");
    enter("a + 1");
    settle();
    assertThat(shown()).containsExactly("a := 2", "3", "-");

    long first = doc.state().cells().getFirst().id();
    doc.execute(new Command.EditInput(first, "a := 10"));
    assertThat(doc.busyProperty().get()).isFalse();
    doc.execute(new Command.Submit(first));
    assertThat(shown()).containsExactly("pending", "pending", "-");
    settle();
    assertThat(shown()).containsExactly("a := 10", "11", "-");
  }

  @Test
  void aStaleResultIsDropped() {
    enter("a := 2");
    enter("a + 1");
    // the first job's work is queued; a new submission supersedes it before it runs
    long first = doc.state().cells().getFirst().id();
    doc.execute(new Command.EditInput(first, "a := 5"));
    doc.execute(new Command.Submit(first));
    worker.tasks.pollFirst().run(); // the superseded job: its results are queued on the ui
    ui.runAll();
    assertThat(shown()).containsExactly("pending", "pending", "-");
    settle();
    assertThat(shown()).containsExactly("a := 5", "6", "-");
  }

  @Test
  void typingDoesNotRestartARunningJob() {
    enter("2 + 2");
    int jobs = worker.tasks.size();
    doc.execute(new Command.EditInput(lastId(), "x"));
    assertThat(worker.tasks).hasSize(jobs);
    settle();
    assertThat(shown()).containsExactly("4", "-");
  }

  @Test
  void aStoppedCellStaysCancelledUntilItIsSubmittedAgain() {
    enter("a := 3");
    settle();
    enter("expand((x + y + z + w + 1)^30)");
    enter("a + 1");
    doc.stop();
    settle();
    assertThat(shown()).containsExactly("a := 3", "cancelled", "cancelled", "-");
    assertThat(doc.busyProperty().get()).isFalse();

    // a new cell evaluates alone, on what finished before it; the stopped cells stay stopped
    enter("a + 2");
    assertThat(shown()).containsExactly("a := 3", "cancelled", "cancelled", "pending", "-");
    settle();
    assertThat(shown()).containsExactly("a := 3", "cancelled", "cancelled", "5", "-");

    // submitting a stopped cell runs it, and the cells after it that were not stopped
    long second = doc.state().cells().get(1).id();
    doc.execute(new Command.EditInput(second, "b := a + 1"));
    doc.execute(new Command.Submit(second));
    assertThat(shown()).containsExactly("a := 3", "pending", "cancelled", "pending", "-");
    settle();
    assertThat(shown()).containsExactly("a := 3", "b := a + 1", "cancelled", "5", "-");
  }

  @Test
  void undoAndRedoRestoreStatesAndReevaluateWhatIsPending() {
    enter("a := 2");
    enter("a + 1");
    settle();
    doc.undo();
    // the state before the submission was recorded while the first cell was pending
    assertThat(shown()).containsExactly("pending", "-");
    settle();
    assertThat(shown()).containsExactly("a := 2", "-");
    assertThat(doc.state().cells().get(1).input()).isEqualTo("a + 1");
    doc.redo();
    // the redone state is the one undo left, results included
    assertThat(shown()).containsExactly("a := 2", "3", "-");
    assertThat(doc.busyProperty().get()).isFalse();
    assertThat(doc.canUndoProperty().get()).isTrue();
    assertThat(doc.canRedoProperty().get()).isFalse();
  }

  @Test
  void newSettingsReevaluateEveryEvaluatedCell() {
    enter("sin(30)");
    settle();
    doc.execute(
        new Command.SetSettings(
            new DocumentSettings(34, AngleMode.DEGREES, NumberFormat.EXACT, 10)));
    settle();
    assertThat(shown()).containsExactly("1/2", "-");
  }

  @Test
  void startEvaluatesALoadedDocument() {
    Document loaded =
        new Document(
            DocumentState.of(
                List.of(
                    new DocumentState.StoredCell("b := 4", true),
                    new DocumentState.StoredCell("b^2", true)),
                DocumentSettings.DEFAULT),
            worker,
            ui,
            () -> now);
    loaded.start();
    settle();
    assertThat(loaded.state().cells().get(1).output()).isInstanceOf(Output.Done.class);
    assertThat(
            ((Result.Value)
                    ((Output.Done) loaded.state().cells().get(1).output()).results().getFirst())
                .text())
        .isEqualTo("16");
  }

  @Test
  void listenersHearEveryChangeResultsIncluded() {
    enter("1");
    int before = changes.size();
    settle();
    assertThat(changes.size()).isGreaterThan(before);
    assertThat(changes.getLast().after()).isEqualTo(doc.state());
  }

  @Test
  void aViewportChangeIsSavedButNotAnUndoStepAndAnUndoKeepsIt() {
    doc.execute(
        new Command.AddObject(id -> new FreePoint(id, Style.DEFAULT, 1, 2), Optional.of("A")));
    Viewport panned = DocumentState.DEFAULT_VIEWPORT.pan(30, 10);
    changes.clear();

    doc.setViewport(panned);
    doc.setViewport(panned);

    assertThat(changes).hasSize(1);
    assertThat(changes.getFirst().after().viewport()).isEqualTo(panned);
    assertThat(doc.canUndoProperty().get()).isTrue();
    doc.undo();
    assertThat(doc.state().canvas().objects()).isEmpty();
    assertThat(doc.state().viewport()).isEqualTo(panned);
    Viewport zoomed = panned.zoom(2, 0, 0);
    doc.setViewport(zoomed);
    doc.redo();
    assertThat(doc.state().names()).containsValue("A");
    assertThat(doc.state().viewport()).isEqualTo(zoomed);
    assertThat(doc.canRedoProperty().get()).isFalse();
  }

  // Phase 7d: the 3D view's camera.

  @Test
  void aCameraChangeIsSavedButNotAnUndoStepAndAnUndoKeepsIt() {
    doc.execute(new Command.AddObject(id -> new FreePoint(id, Style.DEFAULT, 1, 2)));
    Camera turned = Camera.DEFAULT.orbit(0.5, 0.1);
    changes.clear();

    doc.setCamera(turned);
    doc.setCamera(turned);

    assertThat(changes).hasSize(1);
    assertThat(changes.getFirst().after().camera()).isEqualTo(turned);
    doc.undo();
    assertThat(doc.state().canvas().objects()).isEmpty();
    assertThat(doc.state().camera()).isEqualTo(turned);
    Camera zoomed = turned.zoom(2);
    doc.setCamera(zoomed);
    doc.redo();
    assertThat(doc.state().canvas().objects()).hasSize(1);
    assertThat(doc.state().camera()).isEqualTo(zoomed);
  }

  @Test
  void theCanvasFrameFollowsTheCanvas() {
    assertThat(doc.canvasFrameProperty().get()).isEqualTo(CanvasFrame.EMPTY);

    doc.execute(new Command.AddObject(id -> new FreePoint(id, Style.DEFAULT, 1, 2)));

    assertThat(doc.canvasFrameProperty().get().state()).isEqualTo(doc.state().canvas());
  }

  private Slider sliderOf(String name) {
    return doc.state().canvas().objects().stream()
        .filter(o -> o instanceof Slider sl && sl.variable().equals(name))
        .map(o -> (Slider) o)
        .findFirst()
        .orElseThrow();
  }

  @Test
  void aProgramsComputedValuesFollowTheSliderItReadsIntoTheCellsThatReadThem() {
    // s is computed by a loop that reads a; the last cell reads only s, which no longer reads a
    enter("a := slider(0, 5, 1)");
    enter("s := 0\nfor k in 1..3 do s := s + a k end");
    enter("s + 1");
    settle();
    assertThat(shown()).containsExactly("a := slider(0, 5, 1)", "s := 0; (ran)", "1", "-");
    Slider a = sliderOf("a");
    doc.execute(new Command.SetSlider(a.id(), new BigDecimal("2")));
    settle();
    assertThat(shown()).containsExactly("a := slider(0, 5, 1)", "s := 0; (ran)", "13", "-");
  }

  @Test
  void aReaderOfComputedValuesSeesThemThroughTheCellsBetween() {
    // a finished cell between the program and its reader stored the old s: it runs again, and
    // so does the reader after it; the cell after them that reads nothing changed does not
    enter("a := slider(0, 5, 1)");
    enter("s := 0\nfor k in 1..3 do s := s + a k end");
    enter("q := 7");
    enter("s + 1");
    enter("2 + 2");
    settle();
    assertThat(shown())
        .containsExactly("a := slider(0, 5, 1)", "s := 0; (ran)", "q := 7", "1", "4", "-");
    Output unrelated = doc.state().cells().get(4).output();
    doc.execute(new Command.SetSlider(sliderOf("a").id(), new BigDecimal("2")));
    settle();
    assertThat(shown())
        .containsExactly("a := slider(0, 5, 1)", "s := 0; (ran)", "q := 7", "13", "4", "-");
    assertThat(doc.state().cells().get(4).output()).isSameAs(unrelated);
  }

  @Test
  void aSecondProgramReadingTheFirstFollowsItToo() {
    enter("a := slider(0, 5, 1)");
    enter("s := 0\nfor k in 1..3 do s := s + a k end");
    enter("if true then w := 2 s end");
    enter("w + 100");
    settle();
    assertThat(shown())
        .containsExactly("a := slider(0, 5, 1)", "s := 0; (ran)", "(ran)", "100", "-");
    doc.execute(new Command.SetSlider(sliderOf("a").id(), new BigDecimal("1")));
    settle();
    assertThat(shown())
        .containsExactly("a := slider(0, 5, 1)", "s := 0; (ran)", "(ran)", "112", "-");
  }

  @Test
  void aCellNeverRunBetweenTheProgramAndItsReaderIsSkipped() {
    enter("a := slider(0, 5, 1)");
    enter("s := 0\nfor k in 1..3 do s := s + a k end");
    doc.execute(new Command.EditInput(lastId(), "s := 100"));
    doc.execute(new Command.Append("s + 1"));
    settle();
    assertThat(shown()).containsExactly("a := slider(0, 5, 1)", "s := 0; (ran)", "-", "1", "-");
    doc.execute(new Command.SetSlider(sliderOf("a").id(), new BigDecimal("2")));
    settle();
    assertThat(shown()).containsExactly("a := slider(0, 5, 1)", "s := 0; (ran)", "-", "13", "-");
  }

  @Test
  void aSliderCellCreatesTheSliderAndADragReevaluatesOnlyItsReaders() {
    enter("a := slider(0, 5, 0.1)");
    enter("expand((x + 1)^2)");
    enter("2a");
    settle();
    assertThat(shown()).containsExactly("a := slider(0, 5, 0.1)", "x² + 2x + 1", "0", "-");
    Slider a = sliderOf("a");
    assertThat(a.value()).isEqualByComparingTo("0");
    assertThat(a.max()).isEqualByComparingTo("5");
    Output unrelated = doc.state().cells().get(1).output();
    Output definition = doc.state().cells().get(0).output();

    for (String v : List.of("1", "1.5", "2.04")) {
      now += 100_000_000L;
      doc.execute(new Command.SetSlider(a.id(), new BigDecimal(v)));
    }
    assertThat(shown()).containsExactly("a := slider(0, 5, 0.1)", "x² + 2x + 1", "pending", "-");
    settle();
    assertThat(shown()).containsExactly("a := slider(0, 5, 0.1)", "x² + 2x + 1", "4", "-");
    assertThat(doc.state().cells().get(1).output()).isSameAs(unrelated);
    assertThat(doc.state().cells().get(0).output()).isSameAs(definition);

    // the drag was one step
    doc.undo();
    settle();
    assertThat(shown()).containsExactly("a := slider(0, 5, 0.1)", "x² + 2x + 1", "0", "-");
    // undoing the cell's submission takes the slider away
    while (doc.state().cells().getFirst().evaluated()) {
      doc.undo();
    }
    settle();
    assertThat(doc.state().canvas().objects()).isEmpty();
  }

  @Test
  void aSliderDefinedLaterIsSeenByTheCellsBeforeIt() {
    enter("a + 1");
    settle();
    assertThat(shown()).containsExactly("a + 1", "-");

    enter("a := slider(1, 2, 1)");
    settle();

    assertThat(shown()).containsExactly("2", "a := slider(1, 2, 1)", "-");
    assertThat(doc.busyProperty().get()).isFalse();
  }

  @Test
  void aSliderOutlivesItsCellAndANewRangeKeepsItsValue() {
    enter("a := slider(0, 10, 1)");
    enter("a");
    settle();
    doc.execute(new Command.SetSlider(sliderOf("a").id(), new BigDecimal(7)));
    settle();
    assertThat(shown()).containsExactly("a := slider(0, 10, 1)", "7", "-");

    long first = doc.state().cells().getFirst().id();
    doc.execute(new Command.EditInput(first, "a := slider(0, 5, 2)"));
    doc.execute(new Command.Submit(first));
    settle();
    assertThat(sliderOf("a").value()).isEqualByComparingTo("5");
    assertThat(shown()).containsExactly("a := slider(0, 5, 2)", "5", "-");

    doc.execute(new Command.DeleteCell(first));
    settle();
    assertThat(sliderOf("a").value()).isEqualByComparingTo("5");
    assertThat(shown()).containsExactly("5", "-");
  }

  @Test
  void aNamedPointIsAVariableAndMovingItReevaluatesItsReaders() {
    doc.execute(
        new Command.AddObject(id -> new FreePoint(id, Style.DEFAULT, 1.5, -2), Optional.of("P")));
    enter("nth(P, 1)");
    enter("Q");
    settle();
    assertThat(shown()).containsExactly("1.5", "Q", "-");

    ObjectId p = doc.state().names().keySet().iterator().next();
    doc.execute(new Command.MovePoint(p, 3, 4));
    assertThat(shown()).containsExactly("pending", "Q", "-");
    settle();
    assertThat(shown()).containsExactly("3.0", "Q", "-");

    doc.execute(new Command.RemoveObject(p));
    settle();
    assertThat(shown()).containsExactly("nth(P, 1)", "Q", "-");
  }

  @Test
  void aReaderThatAssumesReevaluatesEverythingAfterIt() {
    enter("a := slider(0, 5, 1)");
    enter("assume(y > a)");
    enter("7");
    settle();
    Output seven = doc.state().cells().get(2).output();

    doc.execute(new Command.SetSlider(sliderOf("a").id(), BigDecimal.ONE));

    assertThat(shown()).containsExactly("a := slider(0, 5, 1)", "pending", "pending", "-");
    settle();
    assertThat(doc.state().cells().get(2).output()).isNotSameAs(seven);
  }

  @Test
  void aSliderDeletedOnTheCanvasStaysDeletedWhenItsCellRunsAgain() {
    enter("a := slider(0, 5, 1)\n2a");
    settle();
    doc.execute(new Command.SetSlider(sliderOf("a").id(), new BigDecimal(3)));
    settle();
    assertThat(shown()).containsExactly("a := slider(0, 5, 1); 6", "-");

    // the cell reads a, so the deletion runs it again: it binds the minimum and makes nothing
    doc.execute(new Command.RemoveObject(sliderOf("a").id()));
    settle();
    assertThat(doc.state().canvas().objects()).isEmpty();
    assertThat(shown()).containsExactly("a := slider(0, 5, 1); 0", "-");

    // nor does a run for new settings
    doc.execute(
        new Command.SetSettings(
            new DocumentSettings(20, AngleMode.DEGREES, NumberFormat.EXACT, 10)));
    settle();
    assertThat(doc.state().canvas().objects()).isEmpty();

    // submitting the cell again makes it
    long first = doc.state().cells().getFirst().id();
    doc.execute(new Command.Submit(first));
    settle();
    assertThat(sliderOf("a").value()).isEqualByComparingTo("0");
  }

  @Test
  void undoOfALaterSubmissionBeforeDeliveryKeepsTheSliderCellsSubmission() {
    enter("1");
    settle();
    long id = doc.state().cells().getLast().id();
    doc.execute(new Command.EditInput(id, "a := slider(0, 5, 1)"));
    doc.execute(new Command.Submit(id));
    // before the worker runs, the next cell is submitted and that submission undone
    long next = doc.state().cells().getLast().id();
    now += 2_000_000_000L;
    doc.execute(new Command.EditInput(next, "7"));
    doc.execute(new Command.Submit(next));
    doc.undo();
    settle();

    assertThat(sliderOf("a").min()).isEqualByComparingTo("0");
    assertThat(((Output.Done) doc.state().cells().get(1).output()).after().definition("a"))
        .isEmpty();
    enter("a + 1");
    settle();
    assertThat(shown().get(shown().size() - 2)).isEqualTo("1");
  }
}
