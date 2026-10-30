package mathcanvas.app.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.SequencedSet;
import java.util.Set;
import java.util.function.UnaryOperator;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Slider;

/**
 * The document at one moment: the worksheet cells in order, the document settings, the canvas with
 * the names of its points, and the canvas's viewport. Immutable; every change makes a new state, so
 * the undo stack keeps states rather than inverse operations. There is always at least one cell, so
 * there is always somewhere to type.
 *
 * <p>The worksheet and the canvas are two views of this one state. The canvas's geometry is derived
 * output, like a cell's: it enters through the stack's amend, never as an undo step. A point's name
 * is the variable it binds in the cells; a slider's name is its own variable. The viewport is part
 * of the state so that it is saved, but it is changed by amending, never by a command, and an undo
 * keeps the viewport of the moment (the document carries it into the restored state).
 *
 * @param cells the cells in order, never empty, with distinct ids
 * @param settings the document settings
 * @param nextId the id the next new cell gets, above every cell's id
 * @param canvas the canvas objects and their geometry
 * @param names the name of each named point, in the order the points were named
 * @param viewport the canvas's viewport
 */
public record DocumentState(
    List<Cell> cells,
    DocumentSettings settings,
    long nextId,
    CanvasState canvas,
    Map<ObjectId, String> names,
    Viewport viewport) {

  /** The viewport of a new document: square, 40 pixels to a unit, centred on the origin. */
  public static final Viewport DEFAULT_VIEWPORT = Viewport.centred(0, 0, 1 / 40.0, 640, 480, true);

  /** One blank cell, the default settings, an empty canvas. */
  public static final DocumentState EMPTY =
      new DocumentState(List.of(Cell.blank(1)), DocumentSettings.DEFAULT, 2);

  /**
   * Copies the cells and the names and checks the fields.
   *
   * @throws NullPointerException if an operand is null
   * @throws IllegalArgumentException if there is no cell, two cells share an id, an id is not below
   *     {@code nextId}, or a name is not a variable name, names an object that is not a point of
   *     the canvas, or is given twice or to a slider's variable
   */
  public DocumentState {
    cells = List.copyOf(cells);
    Objects.requireNonNull(settings, "settings");
    Objects.requireNonNull(canvas, "canvas");
    Objects.requireNonNull(viewport, "viewport");
    names = Collections.unmodifiableMap(new LinkedHashMap<>(names));
    if (cells.isEmpty()) {
      throw new IllegalArgumentException("a document has at least one cell");
    }
    if (cells.stream().mapToLong(Cell::id).distinct().count() != cells.size()) {
      throw new IllegalArgumentException("two cells share an id");
    }
    if (cells.stream().anyMatch(c -> c.id() >= nextId)) {
      throw new IllegalArgumentException("a cell id is not below nextId");
    }
    Set<String> taken = new HashSet<>();
    for (CanvasObject o : canvas.objects()) {
      if (o instanceof Slider s) {
        taken.add(s.variable());
      }
    }
    for (Map.Entry<ObjectId, String> e : names.entrySet()) {
      Objects.requireNonNull(e.getKey(), "id");
      Objects.requireNonNull(e.getValue(), "name");
      if (!canvas.object(e.getKey()).map(CanvasObject::isPoint).orElse(false)) {
        throw new IllegalArgumentException(e.getKey() + " is not a point of the canvas");
      }
      if (!Slider.isVariableName(e.getValue())) {
        throw new IllegalArgumentException("not a variable name: " + e.getValue());
      }
      if (!taken.add(e.getValue())) {
        throw new IllegalArgumentException("the name " + e.getValue() + " is taken");
      }
    }
  }

  /**
   * A state with an empty canvas at the default viewport. Package-private, so code that changes a
   * state goes through the withers, which keep the canvas.
   *
   * @param cells the cells in order, never empty, with distinct ids
   * @param settings the document settings
   * @param nextId the id the next new cell gets, above every cell's id
   */
  DocumentState(List<Cell> cells, DocumentSettings settings, long nextId) {
    this(cells, settings, nextId, CanvasState.EMPTY, Map.of(), DEFAULT_VIEWPORT);
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
    return withCells(out, nextId);
  }

  /**
   * This state with every evaluated cell from an index on made pending, since what it computed may
   * depend on something that changed before it; a cancelled cell stays cancelled, since the user
   * stopped it, until it is submitted again ({@link Command.Submit}).
   *
   * @param from the first index
   * @return the state
   */
  public DocumentState pendingFrom(int from) {
    List<Cell> out = new ArrayList<>(cells);
    for (int k = from; k < out.size(); k++) {
      Cell c = out.get(k);
      if (c.evaluated() && !(c.output() instanceof Output.Cancelled)) {
        out.set(k, c.withOutput(Output.Pending.INSTANCE));
      }
    }
    return withCells(out, nextId);
  }

  /**
   * This state with other cells.
   *
   * @param cells the cells
   * @param nextId the id the next new cell gets
   * @return the state
   */
  public DocumentState withCells(List<Cell> cells, long nextId) {
    return new DocumentState(cells, settings, nextId, canvas, names, viewport);
  }

  /**
   * This state with other document settings.
   *
   * @param settings the settings
   * @return the state
   */
  public DocumentState withSettings(DocumentSettings settings) {
    return new DocumentState(cells, settings, nextId, canvas, names, viewport);
  }

  /**
   * This state with another canvas; the names of points no longer on it are dropped.
   *
   * @param canvas the canvas
   * @return the state
   */
  public DocumentState withCanvas(CanvasState canvas) {
    Map<ObjectId, String> kept = new LinkedHashMap<>(names);
    kept.keySet().removeIf(id -> canvas.object(id).isEmpty());
    return new DocumentState(cells, settings, nextId, canvas, kept, viewport);
  }

  /**
   * This state with another canvas and other point names.
   *
   * @param canvas the canvas
   * @param names the names
   * @return the state
   */
  public DocumentState withCanvas(CanvasState canvas, Map<ObjectId, String> names) {
    return new DocumentState(cells, settings, nextId, canvas, names, viewport);
  }

  /**
   * This state with another viewport.
   *
   * @param viewport the viewport
   * @return the state
   */
  public DocumentState withViewport(Viewport viewport) {
    return new DocumentState(cells, settings, nextId, canvas, names, viewport);
  }
}
