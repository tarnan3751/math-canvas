package mathcanvas.app.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * A change to the document: a function from one state to the next. Every mutation of the document
 * is a command run through the {@link CommandStack}, which keeps the states for undo and redo, so a
 * command needs no inverse. A command that does not apply (a cell that no longer exists) returns
 * the state unchanged, and the stack records nothing.
 */
public sealed interface Command {

  /**
   * The state after this command.
   *
   * @param state the state before
   * @return the state after
   */
  DocumentState apply(DocumentState state);

  /**
   * What makes consecutive commands one undo step: commands with equal keys run within the stack's
   * merge interval are merged. Empty for a command that is always its own step.
   *
   * @return the key
   */
  default Optional<Object> mergeKey() {
    return Optional.empty();
  }

  /**
   * Sets a cell's text: typing, or recalling an earlier input. Consecutive edits of one cell merge
   * into one undo step. The output stays until the cell is submitted again.
   *
   * @param id the cell
   * @param text the new text
   */
  record EditInput(long id, String text) implements Command {

    /**
     * Checks the text.
     *
     * @throws NullPointerException if it is null
     */
    public EditInput {
      Objects.requireNonNull(text, "text");
    }

    @Override
    public DocumentState apply(DocumentState state) {
      return state.withCell(id, c -> c.withInput(text));
    }

    @Override
    public Optional<Object> mergeKey() {
      return Optional.of(new EditKey(id));
    }
  }

  /**
   * The merge key of edits of one cell.
   *
   * @param id the cell
   */
  record EditKey(long id) {}

  /**
   * Submits a cell for evaluation (Enter). A cell with text becomes evaluated and pending, and so
   * does every evaluated cell after it, since they may depend on it; a blank cell becomes not
   * evaluated. Submitting the last cell, when it has text, adds a blank cell after it.
   *
   * @param id the cell
   */
  record Submit(long id) implements Command {

    @Override
    public DocumentState apply(DocumentState state) {
      OptionalInt at = state.indexOf(id);
      if (at.isEmpty()) {
        return state;
      }
      int k = at.getAsInt();
      Cell cell = state.cells().get(k);
      boolean blank = cell.input().isBlank();
      List<Cell> cells = new ArrayList<>(state.cells());
      cells.set(
          k,
          new Cell(cell.id(), cell.input(), !blank, blank ? Output.None.INSTANCE : cell.output()));
      long nextId = state.nextId();
      if (!blank && k == cells.size() - 1) {
        cells.add(Cell.blank(nextId++));
      }
      return new DocumentState(cells, state.settings(), nextId).pendingFrom(k);
    }
  }

  /**
   * Deletes a cell, unless it is the only one. The evaluated cells after it become pending when it
   * was evaluated, since they may have used what it defined.
   *
   * @param id the cell
   */
  record DeleteCell(long id) implements Command {

    @Override
    public DocumentState apply(DocumentState state) {
      OptionalInt at = state.indexOf(id);
      if (at.isEmpty() || state.cells().size() == 1) {
        return state;
      }
      int k = at.getAsInt();
      List<Cell> cells = new ArrayList<>(state.cells());
      Cell removed = cells.remove(k);
      DocumentState out = new DocumentState(cells, state.settings(), state.nextId());
      return removed.evaluated() ? out.pendingFrom(k) : out;
    }
  }

  /**
   * Replaces the document settings. A new precision or angle mode makes every evaluated cell
   * pending; a new number format changes only how results are shown.
   *
   * @param settings the settings
   */
  record SetSettings(DocumentSettings settings) implements Command {

    /**
     * Checks the settings.
     *
     * @throws NullPointerException if they are null
     */
    public SetSettings {
      Objects.requireNonNull(settings, "settings");
    }

    @Override
    public DocumentState apply(DocumentState state) {
      DocumentState out = new DocumentState(state.cells(), settings, state.nextId());
      return settings.engine().equals(state.settings().engine()) ? out : out.pendingFrom(0);
    }
  }
}
