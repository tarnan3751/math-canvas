package mathcanvas.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executor;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.document.Output;
import mathcanvas.app.eval.Document;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import org.junit.jupiter.api.Test;

/**
 * The worksheet shows a result the worker delivered with the next refresh, and every other change
 * at once ({@link WorksheetWindow#delivered}): the changes of a real document, as the window hears
 * them.
 */
class WorksheetDeliveredTest {

  /** An executor that queues its tasks until the test runs them. */
  private static final class Queue implements Executor {
    final Deque<Runnable> tasks = new ArrayDeque<>();

    @Override
    public void execute(Runnable task) {
      tasks.add(task);
    }
  }

  private final Queue worker = new Queue();
  private final Queue ui = new Queue();
  private final Document doc = new Document(DocumentState.EMPTY, worker, ui, () -> 0);
  private final List<Document.Change> changes = new ArrayList<>();

  {
    doc.addListener(changes::add);
  }

  private void settle() {
    while (!worker.tasks.isEmpty() || !ui.tasks.isEmpty()) {
      while (!worker.tasks.isEmpty()) {
        worker.tasks.poll().run();
      }
      while (!ui.tasks.isEmpty()) {
        ui.tasks.poll().run();
      }
    }
  }

  private void enter(String text) {
    long id = doc.state().cells().getLast().id();
    doc.execute(new Command.EditInput(id, text));
    doc.execute(new Command.Submit(id));
  }

  /** Whether each change heard since the last call is a delivery, in order. */
  private List<Boolean> heard() {
    List<Boolean> out =
        changes.stream().map(c -> WorksheetWindow.delivered(c.before(), c.after())).toList();
    changes.clear();
    return out;
  }

  private ObjectId slider() {
    return doc.state().canvas().objects().stream()
        .filter(o -> o instanceof Slider)
        .findFirst()
        .orElseThrow()
        .id();
  }

  @Test
  void aDeliveredResultWaitsForTheRefreshAndAPersonsChangesDoNot() {
    doc.start();
    settle();
    changes.clear();

    // typing and Enter are shown at once; the result the worker delivers waits
    enter("1 + 1");
    assertThat(heard()).doesNotContain(true);
    settle();
    assertThat(heard()).containsExactly(true);

    // Enter again on the same text only makes the cell pending: shown at once
    long first = doc.state().cells().getFirst().id();
    doc.execute(new Command.Submit(first));
    assertThat(doc.state().cells().getFirst().output()).isInstanceOf(Output.Pending.class);
    assertThat(heard()).containsExactly(false);
    settle();
    assertThat(heard()).containsExactly(true);

    // a slider moved makes its readers pending, at once; their new results wait
    enter("a := slider(0, 5, 1)");
    enter("a + 1");
    settle();
    changes.clear();
    doc.execute(new Command.SetSlider(slider(), new BigDecimal("2")));
    assertThat(heard()).containsExactly(false);
    settle();
    assertThat(heard()).contains(true);

    // a canvas edit, and its undo and redo, are shown at once
    doc.execute(new Command.AddObject(id -> new FreePoint(id, Style.DEFAULT, 1, 2)));
    assertThat(heard()).doesNotContain(true);
    doc.undo();
    assertThat(heard()).doesNotContain(true);
    doc.redo();
    assertThat(heard()).doesNotContain(true);
    settle();
    changes.clear();

    // an undo of a slider move too
    doc.execute(new Command.SetSlider(slider(), new BigDecimal("3")));
    settle();
    changes.clear();
    doc.undo();
    assertThat(heard()).containsExactly(false);
  }
}
