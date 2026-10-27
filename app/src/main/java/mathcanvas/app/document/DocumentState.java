package mathcanvas.app.document;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.SequencedSet;
import java.util.function.UnaryOperator;

/**
 * The document at one moment: the worksheet cells in order and the document settings. Immutable;
 * every change makes a new state, so the undo stack keeps states rather than inverse operations.
 * There is always at least one cell, so there is always somewhere to type. Canvas objects join the
 * state in Phase 3.
 *
 * @param cells the cells in order, never empty, with distinct ids
 * @param settings the document settings
 * @param nextId the id the next new cell gets, above every cell's id
 */
public record DocumentState(List<Cell> cells, DocumentSettings settings, long nextId) {

  /** One blank cell, the default settings. */
  public static final DocumentState EMPTY =
      new DocumentState(List.of(Cell.blank(1)), DocumentSettings.DEFAULT, 2);

  /**
   * Copies the cells and checks the fields.
   *
   * @throws NullPointerException if an operand is null
   * @throws IllegalArgumentException if there is no cell, two cells share an id, or an id is not
   *     below {@code nextId}
   */
  public DocumentState {
    cells = List.copyOf(cells);
    Objects.requireNonNull(settings, "settings");
    if (cells.isEmpty()) {
      throw new IllegalArgumentException("a document has at least one cell");
    }
    if (cells.stream().mapToLong(Cell::id).distinct().count() != cells.size()) {
      throw new IllegalArgumentException("two cells share an id");
    }
    if (cells.stream().anyMatch(c -> c.id() >= nextId)) {
      throw new IllegalArgumentException("a cell id is not below nextId");
    }
  }

  /**
   * A state of new cells with the given texts, ids from 1 in order; each evaluated cell is pending,
   * so its output is computed again, and each other cell has none. A blank cell is never evaluated,
   * as {@link Command.Submit} leaves it, whatever was stored.
   *
   * @param inputs the texts and whether each was evaluated, in order; empty gives one blank cell
   * @param settings the settings
   * @return the state
   */
  public static DocumentState of(List<StoredCell> inputs, DocumentSettings settings) {
    List<Cell> cells = new ArrayList<>();
    for (StoredCell s : inputs) {
      long id = cells.size() + 1;
      boolean evaluated = s.evaluated() && !s.input().isBlank();
      cells.add(
          new Cell(
              id,
              s.input(),
              evaluated,
              evaluated ? Output.Pending.INSTANCE : Output.None.INSTANCE));
    }
    if (cells.isEmpty()) {
      cells.add(Cell.blank(1));
    }
    return new DocumentState(cells, settings, cells.size() + 1);
  }

  /**
   * What the session file keeps of a cell.
   *
   * @param input the text
   * @param evaluated whether it was submitted
   */
  public record StoredCell(String input, boolean evaluated) {

    /**
     * Checks the field.
     *
     * @throws NullPointerException if the input is null
     */
    public StoredCell {
      Objects.requireNonNull(input, "input");
    }
  }

  /**
   * Where a cell is.
   *
   * @param id the cell's id
   * @return its index, or empty when no cell has the id
   */
  public OptionalInt indexOf(long id) {
    for (int k = 0; k < cells.size(); k++) {
      if (cells.get(k).id() == id) {
        return OptionalInt.of(k);
      }
    }
    return OptionalInt.empty();
  }

  /**
   * The ids of the pending cells, in order.
   *
   * @return the ids
   */
  public SequencedSet<Long> pendingIds() {
    SequencedSet<Long> out = new LinkedHashSet<>();
    for (Cell c : cells) {
      if (c.output() instanceof Output.Pending) {
        out.add(c.id());
      }
    }
    return out;
  }

  /**
   * This state with one cell changed; unchanged when no cell has the id.
   *
   * @param id the cell's id
   * @param change the change
   * @return the state
   */
  public DocumentState withCell(long id, UnaryOperator<Cell> change) {
    OptionalInt k = indexOf(id);
    if (k.isEmpty()) {
      return this;
    }
    List<Cell> out = new ArrayList<>(cells);
    out.set(k.getAsInt(), change.apply(cells.get(k.getAsInt())));
    return new DocumentState(out, settings, nextId);
  }

  /**
   * This state with every evaluated cell from an index on made pending, since what it computed may
   * depend on something that changed before it.
   *
   * @param from the first index
   * @return the state
   */
  public DocumentState pendingFrom(int from) {
    List<Cell> out = new ArrayList<>(cells);
    for (int k = from; k < out.size(); k++) {
      Cell c = out.get(k);
      if (c.evaluated()) {
        out.set(k, c.withOutput(Output.Pending.INSTANCE));
      }
    }
    return new DocumentState(out, settings, nextId);
  }
}
