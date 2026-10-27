package mathcanvas.app.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import mathcanvas.engine.eval.Environment;
import org.junit.jupiter.api.Test;

class CommandStackTest {

  private final AtomicLong now = new AtomicLong();
  private final CommandStack stack = new CommandStack(DocumentState.EMPTY, now::get);

  private void type(String text) {
    stack.execute(new Command.EditInput(1, text));
  }

  private String text() {
    return stack.current().cells().getFirst().input();
  }

  private void advanceMillis(long ms) {
    now.addAndGet(ms * 1_000_000L);
  }

  @Test
  void editsOfOneCellWithinTheIntervalAreOneStep() {
    type("1");
    advanceMillis(300);
    type("1/");
    advanceMillis(1000);
    type("1/3");

    assertThat(stack.undo()).isTrue();
    assertThat(text()).isEmpty();
    assertThat(stack.canUndo()).isFalse();
  }

  @Test
  void editsThatComeBackToWhereTheStepBeganRemoveIt() {
    type("1");
    type("");

    assertThat(stack.current().cells().getFirst().input()).isEmpty();
    stack.undo();
    assertThat(stack.current()).isEqualTo(DocumentState.EMPTY);
    assertThat(stack.canUndo()).isFalse();
    type("2");
    stack.undo();
    assertThat(text()).isEmpty();
  }

  @Test
  void aPauseLongerThanTheIntervalStartsAStep() {
    type("1");
    advanceMillis(1001);
    type("12");

    stack.undo();
    assertThat(text()).isEqualTo("1");
    stack.undo();
    assertThat(text()).isEmpty();
  }

  @Test
  void anotherCommandEndsTheStep() {
    type("1");
    stack.execute(new Command.Submit(1));
    type("2");
    stack.execute(new Command.EditInput(2, "x"));
    stack.execute(new Command.EditInput(1, "3"));

    stack.undo();
    assertThat(text()).isEqualTo("2");
    stack.undo();
    assertThat(stack.current().cells().get(1).input()).isEmpty();
    stack.undo();
    assertThat(text()).isEqualTo("1");
  }

  @Test
  void anUndoOrEndMergeEndsTheStep() {
    type("1");
    type("12");
    stack.undo();
    stack.redo();
    type("123");
    stack.undo();
    assertThat(text()).isEqualTo("12");

    stack.endMerge();
    type("1234");
    type("12345");
    stack.undo();
    assertThat(text()).isEqualTo("12");
  }

  @Test
  void undoAndRedoGoBackAndForthInOrder() {
    type("a");
    stack.execute(new Command.Submit(1));
    stack.execute(new Command.EditInput(2, "b"));
    DocumentState last = stack.current();

    stack.undo();
    stack.undo();
    assertThat(stack.current().cells()).hasSize(1);
    stack.redo();
    assertThat(stack.current().cells()).hasSize(2);
    stack.redo();
    assertThat(stack.current()).isEqualTo(last);
    assertThat(stack.redo()).isFalse();
  }

  @Test
  void aCommandClearsTheRedoSteps() {
    type("a");
    stack.undo();
    assertThat(stack.canRedo()).isTrue();
    type("b");
    assertThat(stack.canRedo()).isFalse();
  }

  @Test
  void aCommandThatChangesNothingIsNotRecorded() {
    assertThat(stack.execute(new Command.DeleteCell(1))).isFalse();
    assertThat(stack.execute(new Command.EditInput(42, "x"))).isFalse();
    assertThat(stack.canUndo()).isFalse();
  }

  @Test
  void theOldestStepsAreDroppedPastTheCap() {
    for (int k = 0; k <= CommandStack.MAX_UNDO + 10; k++) {
      advanceMillis(2000);
      type(Integer.toString(k));
    }
    int undone = 0;
    while (stack.undo()) {
      undone++;
    }
    assertThat(undone).isEqualTo(CommandStack.MAX_UNDO);
    assertThat(text()).isEqualTo("10");
  }

  @Test
  void anAmendRecordsNoStepAndKeepsTheHistory() {
    type("a");
    stack.execute(new Command.Submit(1));
    Output done = new Output.Done(List.of(), Environment.EMPTY);
    stack.amend(s -> s.withCell(1, c -> c.withOutput(done)));

    assertThat(stack.current().cells().getFirst().output()).isEqualTo(done);
    stack.undo();
    assertThat(stack.current().cells()).hasSize(1);
    assertThat(stack.current().cells().getFirst().output()).isEqualTo(Output.None.INSTANCE);
    stack.redo();
    assertThat(stack.current().cells().getFirst().output()).isEqualTo(done);
  }
}
