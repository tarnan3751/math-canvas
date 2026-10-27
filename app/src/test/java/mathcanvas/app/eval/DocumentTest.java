package mathcanvas.app.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;
import mathcanvas.app.document.Cell;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.DocumentSettings;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.document.NumberFormat;
import mathcanvas.app.document.Output;
import mathcanvas.app.document.Result;
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
        new Command.SetSettings(new DocumentSettings(34, AngleMode.DEGREES, NumberFormat.EXACT)));
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
}
