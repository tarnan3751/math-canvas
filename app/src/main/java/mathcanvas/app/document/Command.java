package mathcanvas.app.document;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.CycleException;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;

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
   * Submits a cell for evaluation (Enter). A cell with text becomes evaluated and pending, even if
   * it was stopped, and so does every evaluated cell after it that was not stopped, since they may
   * depend on it; a blank cell becomes not evaluated. Submitting the last cell, when it has text,
   * adds a blank cell after it.
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
          new Cell(
              cell.id(),
              cell.input(),
              !blank,
              blank ? Output.None.INSTANCE : Output.Pending.INSTANCE));
      long nextId = state.nextId();
      if (!blank && k == cells.size() - 1) {
        cells.add(Cell.blank(nextId++));
      }
      return state.withCells(cells, nextId).pendingFrom(k);
    }
  }

  /**
   * Adds a cell with the given text, submitted, after the last cell with text: before a blank last
   * cell, which stays last, or else at the end with a blank cell after it. How a panel runs a
   * statement of its own, such as the inspector's {@code clear(name)}.
   *
   * @param text the text
   */
  record Append(String text) implements Command {

    /**
     * Checks the text.
     *
     * @throws NullPointerException if it is null
     * @throws IllegalArgumentException if it is blank
     */
    public Append {
      Objects.requireNonNull(text, "text");
      if (text.isBlank()) {
        throw new IllegalArgumentException("nothing to append");
      }
    }

    @Override
    public DocumentState apply(DocumentState state) {
      List<Cell> cells = new ArrayList<>(state.cells());
      long nextId = state.nextId();
      boolean blankLast = cells.getLast().input().isBlank();
      int at = blankLast ? cells.size() - 1 : cells.size();
      cells.add(at, new Cell(nextId++, text, true, Output.Pending.INSTANCE));
      if (!blankLast) {
        cells.add(Cell.blank(nextId++));
      }
      return state.withCells(cells, nextId);
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
      DocumentState out = state.withCells(cells, state.nextId());
      return removed.evaluated() ? out.pendingFrom(k) : out;
    }
  }

  /**
   * Replaces the document settings. A change that alters what is shown (the precision, the angle
   * mode, or the printers' format: the number format, and the digits of the decimal and scientific
   * formats) makes every evaluated cell that was not stopped pending, so the worker computes or
   * prints it again; a change that alters nothing shown (the digits of the exact or fraction
   * format, or the same settings) re-evaluates nothing.
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
      DocumentState out = state.withSettings(settings);
      DocumentSettings was = state.settings();
      // the digits of an exact or fraction format change nothing that is shown
      boolean shownTheSame =
          settings.engine().equals(was.engine()) && settings.format().equals(was.format());
      return shownTheSame ? out : out.pendingFrom(0);
    }
  }

  /**
   * Adds an object to the canvas, with the id the canvas gives next, and names it when it is a
   * point given a name, or the next free name ({@link DocumentState#nextPointName}) of the state it
   * is applied to. An object added in turn takes the canvas's next colour ({@link
   * CanvasState#nextColour}) when the command runs, so a redo gives the same colour.
   *
   * @param make builds the object from its id; it must use that id
   * @param name the point's name, the variable it binds in the cells; empty for no name, or for the
   *     next free name
   * @param nextName whether a point with no name given gets the next free name
   * @param inTurn whether the object takes the least-used series colour
   */
  record AddObject(
      Function<ObjectId, ? extends CanvasObject> make,
      Optional<String> name,
      boolean nextName,
      boolean inTurn)
      implements Command {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public AddObject {
      Objects.requireNonNull(make, "make");
      Objects.requireNonNull(name, "name");
    }

    /**
     * Adds an object, named when a name is given.
     *
     * @param make builds the object from its id
     * @param name the point's name; empty for no name
     */
    public AddObject(Function<ObjectId, ? extends CanvasObject> make, Optional<String> name) {
      this(make, name, false, false);
    }

    /**
     * Adds an object with no name.
     *
     * @param make builds the object from its id
     */
    public AddObject(Function<ObjectId, ? extends CanvasObject> make) {
      this(make, Optional.empty());
    }

    /**
     * Adds a point with the next free name of the state the command is applied to, so two points
     * added one after the other never ask for the same name, in the least-used series colour.
     *
     * @param make builds the point from its id
     * @return the command
     */
    public static AddObject named(Function<ObjectId, ? extends CanvasObject> make) {
      return new AddObject(make, Optional.empty(), true, true);
    }

    /**
     * Adds an object with no name in the least-used series colour.
     *
     * @param make builds the object from its id
     * @return the command
     */
    public static AddObject inTurn(Function<ObjectId, ? extends CanvasObject> make) {
      return new AddObject(make, Optional.empty(), false, true);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if the object is not given its id, a parent is missing or of
     *     the wrong kind, or the name cannot be given to it
     */
    @Override
    public DocumentState apply(DocumentState state) {
      ObjectId id = state.canvas().nextObjectId();
      int colour = state.canvas().nextColour();
      CanvasState canvas =
          state
              .canvas()
              .add(
                  inTurn
                      ? n -> {
                        CanvasObject o = make.apply(n);
                        Style s = o.style();
                        return o.withStyle(
                            new Style(
                                colour, s.width(), s.dashed(), s.visible(), s.labelVisible()));
                      }
                      : make);
      Optional<String> given =
          nextName && name.isEmpty() ? Optional.of(state.nextPointName()) : name;
      if (given.isEmpty()) {
        return state.withCanvas(canvas);
      }
      Map<ObjectId, String> names = new LinkedHashMap<>(state.names());
      names.put(id, given.get());
      return state.withCanvas(canvas, names);
    }
  }

  /**
   * Redefines an object, keeping its id, its place and its name.
   *
   * @param replacement the new definition, with the id of the object it replaces
   */
  record ReplaceObject(CanvasObject replacement) implements Command {

    /**
     * Checks the field.
     *
     * @throws NullPointerException if it is null
     */
    public ReplaceObject {
      Objects.requireNonNull(replacement, "replacement");
    }

    /**
     * {@inheritDoc}
     *
     * @throws CycleException if the new definition would depend on itself
     * @throws IllegalArgumentException if a parent is missing or of the wrong kind, a dependent
     *     needs the old kind, or the object's name no longer fits it
     */
    @Override
    public DocumentState apply(DocumentState state) {
      CanvasState canvas = state.canvas().replace(replacement);
      return canvas == state.canvas() ? state : state.withCanvas(canvas, state.names());
    }
  }

  /**
   * Removes an object and every object built on it.
   *
   * @param id the object
   */
  record RemoveObject(ObjectId id) implements Command {

    /**
     * Checks the field.
     *
     * @throws NullPointerException if it is null
     */
    public RemoveObject {
      Objects.requireNonNull(id, "id");
    }

    @Override
    public DocumentState apply(DocumentState state) {
      CanvasState canvas = state.canvas().remove(id);
      return canvas == state.canvas() ? state : state.withCanvas(canvas);
    }
  }

  /**
   * Moves a point, as a drag does: a free point to a position, or a point on a path to a parameter
   * of its path that the caller found by projecting the pointer onto it. Consecutive moves of one
   * point merge into one undo step. A point of another kind, or no object, is left alone.
   *
   * @param id the point
   * @param x the abscissa: a free point's new one, or where a point on a path was projected to
   * @param y the ordinate, likewise
   * @param parameter a point on a path's new parameter; ignored (NaN) for a free point
   */
  record MovePoint(ObjectId id, double x, double y, double parameter) implements Command {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if the id is null
     * @throws IllegalArgumentException if a coordinate is not finite
     */
    public MovePoint {
      Objects.requireNonNull(id, "id");
      if (!Double.isFinite(x) || !Double.isFinite(y)) {
        throw new IllegalArgumentException("a point moves to a finite position");
      }
    }

    /**
     * Moves a free point.
     *
     * @param id the point
     * @param x the abscissa
     * @param y the ordinate
     */
    public MovePoint(ObjectId id, double x, double y) {
      this(id, x, y, Double.NaN);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if a point on a path is given a parameter that is not finite
     */
    @Override
    public DocumentState apply(DocumentState state) {
      CanvasObject o = state.canvas().object(id).orElse(null);
      if (o instanceof FreePoint p) {
        return new ReplaceObject(p.at(x, y)).apply(state);
      }
      if (o instanceof PointOnPath p) {
        return new ReplaceObject(p.withParameter(p.kind(), parameter).landedAt(x, y)).apply(state);
      }
      return state;
    }

    @Override
    public Optional<Object> mergeKey() {
      return Optional.of(new ObjectKey(id));
    }
  }

  /**
   * Sets a slider's value, as a drag does: snapped to the nearest step from the minimum (half a
   * step rounding up) and clamped to the range. Consecutive values of one slider merge into one
   * undo step. Anything but a slider is left alone.
   *
   * @param id the slider
   * @param value the value asked for
   */
  record SetSlider(ObjectId id, BigDecimal value) implements Command {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public SetSlider {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(value, "value");
    }

    @Override
    public DocumentState apply(DocumentState state) {
      if (!(state.canvas().object(id).orElse(null) instanceof Slider s)) {
        return state;
      }
      return new ReplaceObject(s.withValue(snap(s, value))).apply(state);
    }

    /**
     * A value on a slider's grid: the minimum plus a whole number of steps, within the range.
     *
     * @param s the slider
     * @param value the value asked for
     * @return the value
     */
    public static BigDecimal snap(Slider s, BigDecimal value) {
      BigDecimal steps =
          value.subtract(s.min()).divide(s.step(), 0, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
      BigDecimal snapped = s.min().add(steps.multiply(s.step()));
      return snapped.compareTo(s.max()) > 0 ? s.max() : snapped;
    }

    @Override
    public Optional<Object> mergeKey() {
      return Optional.of(new ObjectKey(id));
    }
  }

  /**
   * Changes how an object is drawn.
   *
   * @param id the object
   * @param style the style
   */
  record SetStyle(ObjectId id, Style style) implements Command {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public SetStyle {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(style, "style");
    }

    @Override
    public DocumentState apply(DocumentState state) {
      return state
          .canvas()
          .object(id)
          .map(o -> new ReplaceObject(o.withStyle(style)).apply(state))
          .orElse(state);
    }
  }

  /**
   * The merge key of drags of one canvas object.
   *
   * @param id the object
   */
  record ObjectKey(ObjectId id) {}
}
