package mathcanvas.app.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Style;
import mathcanvas.engine.eval.AngleMode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class CommandStackProperties {

  /**
   * One step of a random session: a command on the cell or the canvas object at an index, an undo
   * or a redo.
   */
  private record Action(int kind, int cell, String text, long pauseMillis) {}

  @Provide
  Arbitrary<List<Action>> sessions() {
    Arbitrary<Action> action =
        Combinators.combine(
                Arbitraries.integers().between(0, 10),
                Arbitraries.integers().between(0, 5),
                Arbitraries.of("", "1", "x", "x^2", "  ", "a := 2"),
                Arbitraries.of(0L, 200L, 1500L))
            .as(Action::new);
    return action.list().ofMaxSize(40);
  }

  private static Command command(Action a, DocumentState s) {
    long id = s.cells().get(a.cell() % s.cells().size()).id();
    return switch (a.kind()) {
      case 0, 1 -> new Command.EditInput(id, a.text());
      case 2 -> new Command.Submit(id);
      case 3 -> new Command.DeleteCell(id);
      case 7 -> new Command.AddObject(o -> new FreePoint(o, Style.DEFAULT, a.cell(), 0));
      case 8 -> new Command.MovePoint(object(a, s), a.cell(), a.pauseMillis());
      case 9 -> new Command.RemoveObject(object(a, s));
      case 10 ->
          new Command.SetStyle(
              object(a, s),
              new Style(a.cell(), 1 + a.cell(), a.cell() % 3 == 0, a.cell() % 2 == 0, false));
      default ->
          new Command.SetSettings(
              new DocumentSettings(
                  10 + a.cell(),
                  AngleMode.values()[a.cell() % 3],
                  NumberFormat.values()[a.cell() % 4],
                  1 + a.cell()));
    };
  }

  /** The canvas object at the action's index, or an id no object has. */
  private static ObjectId object(Action a, DocumentState s) {
    List<CanvasObject> objects = s.canvas().objects();
    return objects.isEmpty() ? new ObjectId(1) : objects.get(a.cell() % objects.size()).id();
  }

  private static void run(CommandStack stack, AtomicLong now, Action a) {
    now.addAndGet(a.pauseMillis() * 1_000_000L);
    switch (a.kind()) {
      case 5 -> stack.undo();
      case 6 -> stack.redo();
      default -> stack.execute(command(a, stack.current()));
    }
  }

  @Property(tries = 300)
  void undoAfterRedoAndRedoAfterUndoRestoreExactly(@ForAll("sessions") List<Action> session) {
    AtomicLong now = new AtomicLong();
    CommandStack stack = new CommandStack(DocumentState.EMPTY, now::get);
    for (Action a : session) {
      run(stack, now, a);
      DocumentState here = stack.current();
      if (stack.undo()) {
        stack.redo();
        assertThat(stack.current()).isEqualTo(here);
      }
      if (stack.redo()) {
        stack.undo();
        assertThat(stack.current()).isEqualTo(here);
      }
    }
  }

  @Property(tries = 300)
  void undoingEverythingReturnsToTheStartAndRedoingEverythingToTheEnd(
      @ForAll("sessions") List<Action> session) {
    AtomicLong now = new AtomicLong();
    CommandStack stack = new CommandStack(DocumentState.EMPTY, now::get);
    List<DocumentState> seen = new ArrayList<>(List.of(DocumentState.EMPTY));
    for (Action a : session) {
      if (a.kind() != 5 && a.kind() != 6) {
        run(stack, now, a);
      }
      seen.add(stack.current());
    }
    DocumentState end = stack.current();
    while (stack.undo()) {
      assertThat(seen).contains(stack.current());
    }
    assertThat(stack.current()).isEqualTo(DocumentState.EMPTY);
    while (stack.redo()) {
      // every step comes back
    }
    assertThat(stack.current()).isEqualTo(end);
  }
}
