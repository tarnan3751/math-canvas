package mathcanvas.app.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.print.Format;
import org.junit.jupiter.api.Test;

class CommandsTest {

  private static final Output DONE = new Output.Done(List.of(), Environment.EMPTY);

  /** Cells 1 to 3: evaluated and done, not evaluated, evaluated and done. */
  private static DocumentState three() {
    return new DocumentState(
        List.of(
            new Cell(1, "a := 2", true, DONE),
            new Cell(2, "draft", false, Output.None.INSTANCE),
            new Cell(3, "a + 1", true, DONE)),
        DocumentSettings.DEFAULT,
        4);
  }

  @Test
  void theEmptyDocumentHasOneBlankCell() {
    assertThat(DocumentState.EMPTY.cells()).containsExactly(Cell.blank(1));
    assertThat(DocumentState.EMPTY.nextId()).isEqualTo(2);
  }

  @Test
  void aNewWorksheetReplacesTheStateAndUndoBringsItBack() {
    DocumentState before = three();
    DocumentState after = before.cleared();
    assertThat(after.cells()).containsExactly(Cell.blank(4));
    assertThat(after.nextId()).isEqualTo(5);
    assertThat(after.settings()).isEqualTo(before.settings());
    assertThat(new Command.Replace(before, after).apply(before)).isEqualTo(after);
    CommandStack stack = new CommandStack(before, () -> 0L);
    assertThat(stack.execute(new Command.Replace(before, after))).isTrue();
    assertThat(stack.current()).isEqualTo(after);
    assertThat(stack.undo()).isTrue();
    assertThat(stack.current()).isEqualTo(before);
  }

  @Test
  void submittingSeveralCellsIsSubmittingEachInTurnInOneStep() {
    DocumentState before = three();
    DocumentState each = new Command.Submit(3).apply(new Command.Submit(2).apply(before));
    assertThat(new Command.SubmitAll(List.of(2L, 3L)).apply(before)).isEqualTo(each);
    CommandStack stack = new CommandStack(before, () -> 0L);
    assertThat(stack.execute(new Command.SubmitAll(List.of(2L, 3L)))).isTrue();
    assertThat(stack.undo()).isTrue();
    assertThat(stack.current()).isEqualTo(before);
    assertThatThrownBy(() -> new Command.Sequence(List.of(new Command.SubmitAll(List.of(1L)))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aStateChecksItsCells() {
    assertThatThrownBy(() -> new DocumentState(List.of(), DocumentSettings.DEFAULT, 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new DocumentState(
                    List.of(Cell.blank(1), Cell.blank(1)), DocumentSettings.DEFAULT, 2))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DocumentState(List.of(Cell.blank(5)), DocumentSettings.DEFAULT, 5))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void storedCellsBecomeNumberedCellsWithTheEvaluatedOnesPending() {
    DocumentState s =
        DocumentState.of(
            List.of(
                new DocumentState.StoredCell("1 + 1", true),
                new DocumentState.StoredCell("x", false),
                new DocumentState.StoredCell(" ", true)),
            DocumentSettings.DEFAULT);

    assertThat(s.cells())
        .containsExactly(
            new Cell(1, "1 + 1", true, Output.Pending.INSTANCE),
            new Cell(2, "x", false, Output.None.INSTANCE),
            new Cell(3, " ", false, Output.None.INSTANCE));
    assertThat(s.nextId()).isEqualTo(4);
    assertThat(DocumentState.of(List.of(), DocumentSettings.DEFAULT))
        .isEqualTo(DocumentState.EMPTY);
  }

  @Test
  void anEditChangesOnlyTheText() {
    DocumentState s = new Command.EditInput(3, "a + 2").apply(three());

    assertThat(s.cells().get(2)).isEqualTo(new Cell(3, "a + 2", true, DONE));
    assertThat(new Command.EditInput(9, "x").apply(three())).isEqualTo(three());
  }

  @Test
  void submittingMakesTheCellAndTheEvaluatedCellsAfterItPending() {
    DocumentState s = new Command.Submit(2).apply(three());

    assertThat(s.cells())
        .containsExactly(
            new Cell(1, "a := 2", true, DONE),
            new Cell(2, "draft", true, Output.Pending.INSTANCE),
            new Cell(3, "a + 1", true, Output.Pending.INSTANCE));
    assertThat(s.pendingIds()).containsExactly(2L, 3L);
  }

  @Test
  void aCancelledCellIsMadePendingOnlyByItsOwnSubmission() {
    DocumentState stopped =
        new DocumentState(
            List.of(
                new Cell(1, "a := 2", true, DONE),
                new Cell(2, "slow", true, Output.Cancelled.INSTANCE),
                new Cell(3, "a + 1", true, DONE)),
            DocumentSettings.DEFAULT,
            4);

    assertThat(new Command.Submit(1).apply(stopped).pendingIds()).containsExactly(1L, 3L);
    assertThat(new Command.Submit(2).apply(stopped).pendingIds()).containsExactly(2L, 3L);
    assertThat(
            new Command.SetSettings(
                    new DocumentSettings(10, AngleMode.RADIANS, NumberFormat.EXACT, 10))
                .apply(stopped)
                .pendingIds())
        .containsExactly(1L, 3L);
  }

  @Test
  void anAppendedCellGoesBeforeTheBlankLastCellOrAtTheEnd() {
    DocumentState s = new Command.Append("clear(a)").apply(DocumentState.EMPTY);

    assertThat(s.cells())
        .containsExactly(new Cell(2, "clear(a)", true, Output.Pending.INSTANCE), Cell.blank(1));
    DocumentState t = new Command.Append("1").apply(three());
    assertThat(t.cells())
        .extracting(Cell::input)
        .containsExactly("a := 2", "draft", "a + 1", "1", "");
    assertThat(t.pendingIds()).containsExactly(4L);
  }

  @Test
  void submittingTheLastCellAddsABlankCell() {
    DocumentState s = new Command.Submit(3).apply(three());

    assertThat(s.cells()).hasSize(4);
    assertThat(s.cells().get(3)).isEqualTo(Cell.blank(4));
    assertThat(s.nextId()).isEqualTo(5);
    assertThat(s.pendingIds()).containsExactly(3L);
  }

  @Test
  void submittingABlankCellMakesItNotEvaluatedAndAddsNothing() {
    DocumentState s = new Command.Submit(1).apply(new Command.EditInput(1, "  ").apply(three()));

    assertThat(s.cells().getFirst()).isEqualTo(new Cell(1, "  ", false, Output.None.INSTANCE));
    assertThat(s.pendingIds()).containsExactly(3L);
    assertThat(new Command.Submit(1).apply(DocumentState.EMPTY)).isEqualTo(DocumentState.EMPTY);
  }

  @Test
  void deletingAnEvaluatedCellMakesTheLaterOnesPending() {
    DocumentState s = new Command.DeleteCell(1).apply(three());

    assertThat(s.cells()).extracting(Cell::id).containsExactly(2L, 3L);
    assertThat(s.pendingIds()).containsExactly(3L);
    assertThat(new Command.DeleteCell(2).apply(three()).pendingIds()).isEmpty();
  }

  @Test
  void theOnlyCellIsNotDeleted() {
    assertThat(new Command.DeleteCell(1).apply(DocumentState.EMPTY)).isEqualTo(DocumentState.EMPTY);
  }

  @Test
  void newPrecisionOrAngleModeMakesEveryEvaluatedCellPending() {
    DocumentSettings degrees = new DocumentSettings(34, AngleMode.DEGREES, NumberFormat.EXACT, 10);
    DocumentState s = new Command.SetSettings(degrees).apply(three());

    assertThat(s.settings()).isEqualTo(degrees);
    assertThat(s.pendingIds()).containsExactly(1L, 3L);
  }

  @Test
  void aNewNumberFormatPrintsEveryEvaluatedCellAgain() {
    DocumentSettings decimal =
        new DocumentSettings(34, AngleMode.RADIANS, NumberFormat.DECIMAL, 10);
    DocumentState s = new Command.SetSettings(decimal).apply(three());

    assertThat(s.settings()).isEqualTo(decimal);
    assertThat(s.pendingIds()).containsExactly(1L, 3L);
    assertThat(decimal.format()).isEqualTo(Format.decimal(10));
    assertThat(new Command.SetSettings(DocumentSettings.DEFAULT).apply(three())).isEqualTo(three());
  }

  @Test
  void digitsThatChangeNothingShownReevaluateNothing() {
    DocumentSettings exact20 = new DocumentSettings(34, AngleMode.RADIANS, NumberFormat.EXACT, 20);
    DocumentState s = new Command.SetSettings(exact20).apply(three());

    assertThat(s.settings()).isEqualTo(exact20);
    assertThat(s.pendingIds()).isEmpty();
    DocumentSettings fraction =
        new DocumentSettings(34, AngleMode.RADIANS, NumberFormat.FRACTION, 10);
    DocumentState f = new Command.SetSettings(fraction).apply(three());
    assertThat(f.pendingIds()).containsExactly(1L, 3L);
    DocumentState f20 =
        new Command.SetSettings(
                new DocumentSettings(34, AngleMode.RADIANS, NumberFormat.FRACTION, 20))
            .apply(f.pendingFrom(0));
    assertThat(f20.pendingIds()).containsExactly(1L, 3L);
    DocumentSettings decimal20 =
        new DocumentSettings(34, AngleMode.RADIANS, NumberFormat.DECIMAL, 20);
    DocumentState d =
        new Command.SetSettings(decimal20)
            .apply(
                new DocumentState(
                    three().cells(),
                    new DocumentSettings(34, AngleMode.RADIANS, NumberFormat.DECIMAL, 10),
                    4));
    assertThat(d.pendingIds()).containsExactly(1L, 3L);
  }

  @Test
  void settingsCheckThePrecision() {
    assertThatThrownBy(() -> new DocumentSettings(0, AngleMode.RADIANS, NumberFormat.EXACT, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DocumentSettings(34, AngleMode.RADIANS, NumberFormat.EXACT, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(DocumentSettings.DEFAULT.engine().precision()).isEqualTo(34);
  }

  @Test
  void decimalFormatsEvaluateIntegralsNumericallyToTheirDigits() {
    for (NumberFormat f : NumberFormat.values()) {
      int expected = f == NumberFormat.DECIMAL || f == NumberFormat.SCIENTIFIC ? 12 : 0;
      DocumentSettings s = new DocumentSettings(34, AngleMode.DEGREES, f, 12);
      assertThat(s.engine().quadratureDigits()).as("%s", f).isEqualTo(expected);
      assertThat(s.engine().angleMode()).isEqualTo(AngleMode.DEGREES);
    }
    // the digits of the exact format change nothing the engine sees
    assertThat(new DocumentSettings(34, AngleMode.RADIANS, NumberFormat.EXACT, 5).engine())
        .isEqualTo(DocumentSettings.DEFAULT.engine());
  }
}
