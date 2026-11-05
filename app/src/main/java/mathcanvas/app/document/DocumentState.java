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
import mathcanvas.canvas.space.Camera;

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
 * keeps the viewport of the moment (the document carries it into the restored state); so is the 3D
 * view's camera.
 *
 * @param cells the cells in order, never empty, with distinct ids
 * @param settings the document settings
 * @param nextId the id the next new cell gets, above every cell's id
 * @param canvas the canvas objects and their geometry
 * @param names the name of each named point, in the order the points were named
 * @param viewport the canvas's viewport
 * @param camera the 3D view's camera, which is kept as the viewport is
 * @param plots the graphs each cell's {@code plot} statements drew, by the cell's id, one slot per
 *     argument in order: the cell owns them, so its next submission replaces them and its deletion
 *     removes them. A slot keeps the id of a graph deleted on the canvas (ids are never given
 *     twice), so the argument after it keeps its own graph; {@link #plotsOf} gives those on the
 *     canvas
 */
public record DocumentState(
    List<Cell> cells,
    DocumentSettings settings,
    long nextId,
    CanvasState canvas,
    Map<ObjectId, String> names,
    Viewport viewport,
    Camera camera,
    Map<Long, List<ObjectId>> plots) {

  /** The viewport of a new document: square, 40 pixels to a unit, centred on the origin. */
  public static final Viewport DEFAULT_VIEWPORT = Viewport.centred(0, 0, 1 / 40.0, 640, 480, true);

  /** One blank cell, the default settings, an empty canvas. */
  public static final DocumentState EMPTY =
      new DocumentState(List.of(Cell.blank(1)), DocumentSettings.DEFAULT, 2);

  /**
   * Copies the cells, the names and the graphs and checks the fields.
   *
   * @throws NullPointerException if an operand is null
   * @throws IllegalArgumentException if there is no cell, two cells share an id, an id is not below
   *     {@code nextId}, a name is not a variable name, names an object that is not a point of the
   *     canvas, or is given twice or to a slider's variable, or a cell's graphs are of a cell that
   *     is not in the document, or one is not an id the canvas gave, or is owned twice
   */
  public DocumentState {
    cells = List.copyOf(cells);
    Objects.requireNonNull(settings, "settings");
    Objects.requireNonNull(canvas, "canvas");
    Objects.requireNonNull(viewport, "viewport");
    Objects.requireNonNull(camera, "camera");
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
    Map<Long, List<ObjectId>> owned = new LinkedHashMap<>();
    Set<ObjectId> drawn = new HashSet<>();
    for (Map.Entry<Long, List<ObjectId>> e : plots.entrySet()) {
      long cell = e.getKey();
      if (cells.stream().noneMatch(c -> c.id() == cell)) {
        throw new IllegalArgumentException("no cell " + cell + " owns graphs");
      }
      for (ObjectId id : e.getValue()) {
        if (id.value() >= canvas.nextId() || !drawn.add(id)) {
          throw new IllegalArgumentException(id + " is not a graph of its own on the canvas");
        }
      }
      if (!e.getValue().isEmpty()) {
        owned.put(cell, List.copyOf(e.getValue()));
      }
    }
    plots = Collections.unmodifiableMap(owned);
  }

  /**
   * A state whose cells own no graphs.
   *
   * @param cells the cells in order, never empty, with distinct ids
   * @param settings the document settings
   * @param nextId the id the next new cell gets, above every cell's id
   * @param canvas the canvas objects and their geometry
   * @param names the name of each named point, in the order the points were named
   * @param viewport the canvas's viewport
   * @param camera the 3D view's camera
   */
  public DocumentState(
      List<Cell> cells,
      DocumentSettings settings,
      long nextId,
      CanvasState canvas,
      Map<ObjectId, String> names,
      Viewport viewport,
      Camera camera) {
    this(cells, settings, nextId, canvas, names, viewport, camera, Map.of());
  }

  /**
   * A state seen through the default camera.
   *
   * @param cells the cells in order, never empty, with distinct ids
   * @param settings the document settings
   * @param nextId the id the next new cell gets, above every cell's id
   * @param canvas the canvas objects and their geometry
   * @param names the name of each named point, in the order the points were named
   * @param viewport the canvas's viewport
   */
  public DocumentState(
      List<Cell> cells,
      DocumentSettings settings,
      long nextId,
      CanvasState canvas,
      Map<ObjectId, String> names,
      Viewport viewport) {
    this(cells, settings, nextId, canvas, names, viewport, Camera.DEFAULT);
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
    this(cells, settings, nextId, CanvasState.EMPTY, Map.of(), DEFAULT_VIEWPORT, Camera.DEFAULT);
  }

  /**
   * A new worksheet in place of this one: one blank cell, under the next id, and an empty canvas,
   * with the same settings, viewport and camera.
   *
   * @return the state
   */
  public DocumentState cleared() {
    return new DocumentState(
        List.of(Cell.blank(nextId)),
        settings,
        nextId + 1,
        CanvasState.EMPTY,
        Map.of(),
        viewport,
        camera);
  }

  /**
   * A state of new cells with the given texts, ids from 1 in order; each evaluated cell is pending,
   * so its output is computed again, and each other cell has none. A blank cell is never evaluated,
   * as {@link Command.Submit} leaves it, whatever was stored. The cells own no graphs: their canvas
   * is empty.
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
   * @param plots the graphs its {@code plot} statements drew, in order
   */
  public record StoredCell(String input, boolean evaluated, List<ObjectId> plots) {

    /**
     * Checks the fields and copies the graphs.
     *
     * @throws NullPointerException if the input or the graphs are null
     */
    public StoredCell {
      Objects.requireNonNull(input, "input");
      plots = List.copyOf(plots);
    }

    /**
     * A cell that drew no graph.
     *
     * @param input the text
     * @param evaluated whether it was submitted
     */
    public StoredCell(String input, boolean evaluated) {
      this(input, evaluated, List.of());
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
   * The name a new point gets: the first of {@code A} to {@code Z}, then {@code A_1} to {@code
   * Z_1}, and so on, that no point or slider has.
   *
   * @return the name
   */
  public String nextPointName() {
    Set<String> taken = new HashSet<>(names.values());
    for (CanvasObject o : canvas.objects()) {
      if (o instanceof Slider s) {
        taken.add(s.variable());
      }
    }
    for (int round = 0; ; round++) {
      for (char c = 'A'; c <= 'Z'; c++) {
        String name = round == 0 ? String.valueOf(c) : c + "_" + round;
        if (!taken.contains(name)) {
          return name;
        }
      }
    }
  }

  /**
   * This state with other cells.
   *
   * @param cells the cells
   * @param nextId the id the next new cell gets
   * @return the state
   */
  public DocumentState withCells(List<Cell> cells, long nextId) {
    return new DocumentState(
        cells, settings, nextId, canvas, names, viewport, camera, ownedBy(cells, canvas));
  }

  /**
   * This state with other document settings.
   *
   * @param settings the settings
   * @return the state
   */
  public DocumentState withSettings(DocumentSettings settings) {
    return new DocumentState(cells, settings, nextId, canvas, names, viewport, camera, plots);
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
    return new DocumentState(
        cells, settings, nextId, canvas, kept, viewport, camera, ownedBy(cells, canvas));
  }

  /**
   * This state with another canvas and other point names.
   *
   * @param canvas the canvas
   * @param names the names
   * @return the state
   */
  public DocumentState withCanvas(CanvasState canvas, Map<ObjectId, String> names) {
    return new DocumentState(
        cells, settings, nextId, canvas, names, viewport, camera, ownedBy(cells, canvas));
  }

  /**
   * This state with another viewport.
   *
   * @param viewport the viewport
   * @return the state
   */
  public DocumentState withViewport(Viewport viewport) {
    return new DocumentState(cells, settings, nextId, canvas, names, viewport, camera, plots);
  }

  // Phase 7d: the 3D view's camera.

  /**
   * This state seen through another camera in the 3D view.
   *
   * @param camera the camera
   * @return the state
   */
  public DocumentState withCamera(Camera camera) {
    return new DocumentState(cells, settings, nextId, canvas, names, viewport, camera, plots);
  }

  /**
   * The graphs a cell's {@code plot} statements drew that are on the canvas, in order.
   *
   * @param id the cell's id
   * @return the graphs' ids, none when it drew none
   */
  public List<ObjectId> plotsOf(long id) {
    return plotSlots(id).stream().filter(g -> canvas.object(g).isPresent()).toList();
  }

  /**
   * A cell's graphs one slot per {@code plot} argument, in order, a deleted graph's id kept in its
   * slot.
   *
   * @param id the cell's id
   * @return the slots, none when it drew none
   */
  public List<ObjectId> plotSlots(long id) {
    return plots.getOrDefault(id, List.of());
  }

  /**
   * This state with a cell owning other graphs.
   *
   * @param id the cell's id
   * @param graphs the graphs' ids, one slot per argument, in order; none to own none
   * @return the state
   * @throws IllegalArgumentException if a graph is not an id the canvas gave or another cell's
   */
  public DocumentState withPlots(long id, List<ObjectId> graphs) {
    Map<Long, List<ObjectId>> out = new LinkedHashMap<>(plots);
    out.put(id, graphs);
    return new DocumentState(cells, settings, nextId, canvas, names, viewport, camera, out);
  }

  /** The graphs still owned by cells of the document, of ids the canvas gave. */
  private Map<Long, List<ObjectId>> ownedBy(List<Cell> cells, CanvasState canvas) {
    if (plots.isEmpty()) {
      return plots;
    }
    Map<Long, List<ObjectId>> out = new LinkedHashMap<>();
    for (Cell c : cells) {
      List<ObjectId> kept = new ArrayList<>(plotSlots(c.id()));
      kept.removeIf(id -> id.value() >= canvas.nextId());
      if (!kept.isEmpty()) {
        out.put(c.id(), kept);
      }
    }
    return out;
  }
}
