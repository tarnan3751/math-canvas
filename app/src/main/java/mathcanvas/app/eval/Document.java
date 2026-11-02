package mathcanvas.app.eval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleUnaryOperator;
import java.util.function.LongSupplier;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import mathcanvas.app.document.Cell;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.CommandStack;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.document.Inspection;
import mathcanvas.app.document.Output;
import mathcanvas.app.document.SliderSpec;
import mathcanvas.app.text.Messages;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.model.Candidates;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.CriticalPoint;
import mathcanvas.canvas.model.CycleException;
import mathcanvas.canvas.model.Feature;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.Intersection;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Recomputes;
import mathcanvas.canvas.model.Sampler;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Window;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.view.PlotContent;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.CancelledException;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.EvaluationException;
import mathcanvas.engine.eval.Evaluator;
import mathcanvas.engine.eval.NumericForm;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.parse.ParseException;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.Format;

/**
 * The open document: its {@link CommandStack} and the evaluation of its cells. Every change goes
 * through {@link #execute}, {@link #undo} or {@link #redo}, and listeners hear of every change of
 * the state, results included.
 *
 * <p>Confined to one thread, the FX thread in the app: every method is called on it, and the
 * worker's results come back through the {@code ui} executor ({@code Platform::runLater}). Unit
 * tests pass executors they run by hand, so no toolkit starts.
 *
 * <p>Evaluation: whenever a cell is submitted, or a change alters which cells are pending, the
 * running job is cancelled and a new one evaluates the pending cells in order, on the environment
 * stored after the nearest finished cell before the first (or the empty environment with the
 * document's settings). Each job has a generation; a result of an older generation is dropped when
 * it arrives, so a superseded job can never overwrite a newer one. Results enter through {@link
 * CommandStack#amend}, recording no undo step. {@link #stop} cancels the running job, whose
 * remaining cells become cancelled; a cancelled cell is not run again until it is itself submitted,
 * and the cells after it are evaluated without it. A change of a canvas variable (a slider or a
 * named point, {@link CanvasBindings}) makes pending only the finished cells that read it.
 *
 * <p>The canvas: its geometry is derived output, recomputed by a {@link CanvasJob} on the same
 * worker. What changes is gathered and handed to the next job, which starts when the running one
 * ends; a job's result applies to each object whose record is what the job started from, and a
 * stopped job applies nothing. At {@link #start} the whole canvas is recomputed after the first
 * evaluation of the cells.
 */
public final class Document {

  /**
   * A change of the state.
   *
   * @param before the state before
   * @param after the state after
   */
  public record Change(DocumentState before, DocumentState after) {}

  /** A request to stop one job: a volatile flag. */
  private static final class Token implements Cancellation {
    private volatile boolean requested;

    @Override
    public boolean requested() {
      return requested;
    }
  }

  private final CommandStack stack;
  private final Executor worker;
  private final Executor ui;
  private final Inspections inspections = new Inspections();
  private final List<Consumer<Change>> listeners = new CopyOnWriteArrayList<>();
  private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(this, "busy", false);
  private final ReadOnlyBooleanWrapper canUndo = new ReadOnlyBooleanWrapper(this, "canUndo");
  private final ReadOnlyBooleanWrapper canRedo = new ReadOnlyBooleanWrapper(this, "canRedo");
  private final ReadOnlyObjectWrapper<CanvasFrame> canvasFrame =
      new ReadOnlyObjectWrapper<>(this, "canvasFrame", CanvasFrame.EMPTY);
  private CanvasTheme canvasTheme = CanvasTheme.LIGHT;
  private long generation;
  private boolean tracing;
  private Token token;
  // the cells the user submitted that have not had their output yet: only they make sliders
  private final Set<Long> submitted = new HashSet<>();
  private final CanvasJob.Sampling sampling;
  private final CanvasJob.Content content;
  private final AlgebraRows rows = new AlgebraRows();
  // what the next canvas job recomputes, gathered since the last one started
  private final Set<ObjectId> canvasDirty = new HashSet<>();
  // objects whose first recompute was stopped: left out until they change, or what they read does
  private final Set<ObjectId> canvasHeld = new HashSet<>();
  // the objects of the running canvas job's snapshot
  private Set<ObjectId> canvasJobIds = Set.of();
  private final Set<String> canvasChanged = new HashSet<>();
  private boolean canvasResample;
  private boolean contentStale;
  // false from start until the first evaluation ends, so the first recompute sees the cells
  private boolean canvasStarted = true;
  private Token canvasToken;
  private final Set<Token> entryTokens = new HashSet<>();
  private Environment canvasEnvironment;

  /**
   * A document. Its pending cells start evaluating at {@link #start}.
   *
   * @param initial the state
   * @param worker where jobs run: a single thread, never the FX thread
   * @param ui where results are delivered: the document's own thread
   * @param clock a monotonic clock in nanoseconds, for merging edits
   */
  public Document(DocumentState initial, Executor worker, Executor ui, LongSupplier clock) {
    this(initial, worker, ui, clock, CanvasJob.Sampling.UNAVAILABLE, CanvasJob.Content.NONE);
  }

  /**
   * A document with its canvas's sampling and content. Its pending cells start evaluating, and its
   * canvas is recomputed after them, at {@link #start}.
   *
   * @param initial the state
   * @param worker where jobs run: a single thread, never the FX thread
   * @param ui where results are delivered: the document's own thread
   * @param clock a monotonic clock in nanoseconds, for merging edits
   * @param sampling binds the canvas's sampler to the environment, one per document
   * @param content builds the canvas's drawn content
   */
  public Document(
      DocumentState initial,
      Executor worker,
      Executor ui,
      LongSupplier clock,
      CanvasJob.Sampling sampling,
      CanvasJob.Content content) {
    this.stack = new CommandStack(initial, clock);
    this.worker = Objects.requireNonNull(worker, "worker");
    this.ui = Objects.requireNonNull(ui, "ui");
    this.sampling = Objects.requireNonNull(sampling, "sampling");
    this.content = Objects.requireNonNull(content, "content");
    canvasDirty.addAll(ids(initial.canvas()));
  }

  /**
   * The current state.
   *
   * @return the state
   */
  public DocumentState state() {
    return stack.current();
  }

  /**
   * Adds a listener, told of every change after it happened.
   *
   * @param listener the listener
   */
  public void addListener(Consumer<Change> listener) {
    listeners.add(listener);
  }

  /**
   * Whether a job is running: an evaluation of cells or a canvas recompute, either of which {@link
   * #stop} stops.
   *
   * @return the property
   */
  public ReadOnlyBooleanProperty busyProperty() {
    return busy.getReadOnlyProperty();
  }

  /**
   * Whether there is a step to undo.
   *
   * @return the property
   */
  public ReadOnlyBooleanProperty canUndoProperty() {
    return canUndo.getReadOnlyProperty();
  }

  /**
   * Whether there is a step to redo.
   *
   * @return the property
   */
  public ReadOnlyBooleanProperty canRedoProperty() {
    return canRedo.getReadOnlyProperty();
  }

  /**
   * What the canvas side shows: the content and the algebra rows the worker last built for the
   * canvas. Set on this thread only.
   *
   * @return the property
   */
  public ReadOnlyObjectProperty<CanvasFrame> canvasFrameProperty() {
    return canvasFrame.getReadOnlyProperty();
  }

  /**
   * Sets the canvas's viewport: a pan, a zoom, a resize or the aspect lock. It changes the state
   * without an undo step, as scrolling would, and the listeners hear of it, so it is saved; the
   * objects sampled over the window are sampled again.
   *
   * @param viewport the viewport
   */
  public void setViewport(Viewport viewport) {
    Objects.requireNonNull(viewport, "viewport");
    DocumentState before = stack.current();
    if (before.viewport().equals(viewport)) {
      return;
    }
    stack.amend(s -> s.withViewport(viewport));
    noteCanvas(before);
    publish(before);
    scheduleCanvas();
  }

  /**
   * Sets the colours the canvas content is drawn in, the view's theme, so a theme change rebuilds
   * the content.
   *
   * @param theme the theme
   */
  public void setCanvasTheme(CanvasTheme theme) {
    if (!theme.equals(canvasTheme)) {
      canvasTheme = Objects.requireNonNull(theme, "theme");
      contentStale = true;
      scheduleCanvas();
    }
  }

  /**
   * Submits the text of an algebra panel row: parsed on the worker into an object that replaces the
   * target, or a new object when there is no target, through {@link Command.ReplaceObject} or
   * {@link Command.AddObject} on this thread. When the text does not parse, does not describe an
   * object, or the target changed meanwhile, nothing changes and {@code onError} is told why, on
   * this thread.
   *
   * @param target the object the row shows, or empty for the new-object row
   * @param text the text
   * @param onError told the message when the text is refused
   */
  public void submitEntry(Optional<ObjectId> target, String text, Consumer<String> onError) {
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(text, "text");
    Objects.requireNonNull(onError, "onError");
    DocumentState state = stack.current();
    Optional<CanvasObject> was = target.flatMap(id -> state.canvas().object(id));
    if (target.isPresent() && was.isEmpty()) {
      onError.accept(Messages.text("entry.changed"));
      return;
    }
    Environment env = environmentBefore(state, state.cells().size());
    if (was.isPresent() && was.get() instanceof PointOnPath p && p.kind() != ParamKind.NONE) {
      submitParameter(state, p, env, text, onError);
      return;
    }
    // stop() stops a reading too: an entry may evaluate a point's coordinates
    Token t = new Token();
    entryTokens.add(t);
    worker.execute(
        () -> {
          Entries.Read read;
          try {
            read = Entries.read(text, was, env, Set.copyOf(state.names().values()), t);
          } catch (CancelledException stopped) {
            read = null;
          }
          Entries.Read done = read;
          ui.execute(() -> entered(t, was, done, onError));
        });
  }

  /**
   * Adds every intersection of two paths inside the visible window now, one per place, as one step:
   * a new member of their family for each such place whose index no member has yet, the lower id
   * first ({@link Intersection}). The places are found on the worker ({@link
   * Candidates#intersections}) and the members added on this thread, unless the search was stopped
   * or a path changed meanwhile; {@code onAdded} is then told the last member added. When there is
   * nothing to add, {@code onRefused} is told why. Both are told on this thread.
   *
   * @param first the path with the lower id
   * @param second the other path
   * @param onAdded told the last member added
   * @param onRefused told the message when nothing is added
   */
  public void addIntersections(
      ObjectId first, ObjectId second, Consumer<ObjectId> onAdded, Consumer<String> onRefused) {
    addPlaces(
        List.of(first, second),
        (curves, sampler, window, c) ->
            List.of(Candidates.intersections(curves.get(0), curves.get(1), sampler, window, c)),
        (o, family) ->
            o instanceof Intersection i && i.first().equals(first) && i.second().equals(second)
                ? i.index()
                : -1,
        (id, family, index) -> Intersection.of(id, Style.DEFAULT, first, second, index),
        Messages.text("canvas.find.noIntersections"),
        onAdded,
        onRefused);
  }

  /**
   * Adds every feature of the given kinds a function graph has inside the visible window now, one
   * critical point per place, as one step, as {@link #addIntersections} adds intersections: a new
   * member of each feature's family for each such place whose index no member has yet.
   *
   * @param graph the graph
   * @param features the kinds: the roots, or the minima and the maxima
   * @param onAdded told the last member added
   * @param onRefused told the message when nothing is added
   */
  public void addFeatures(
      ObjectId graph,
      List<Feature> features,
      Consumer<ObjectId> onAdded,
      Consumer<String> onRefused) {
    List<Feature> kinds = List.copyOf(features);
    addPlaces(
        List.of(graph),
        (curves, sampler, window, c) -> {
          List<List<Integer>> out = new ArrayList<>();
          for (Feature f : kinds) {
            out.add(Candidates.features(curves.get(0), f, sampler, window, c));
          }
          return out;
        },
        (o, family) ->
            o instanceof CriticalPoint p
                    && p.path().equals(graph)
                    && p.feature() == kinds.get(family)
                ? p.index()
                : -1,
        (id, family, index) -> CriticalPoint.of(id, Style.DEFAULT, graph, kinds.get(family), index),
        kinds.contains(Feature.ROOT)
            ? Messages.text("canvas.find.noRoots")
            : Messages.text("canvas.find.noExtrema"),
        onAdded,
        onRefused);
  }

  /** The most places a search adds; beyond them it refuses: zoom in. */
  static final int MAX_PLACES = 50;

  /** Finds the places of one or more families inside the window on the worker, by index. */
  @FunctionalInterface
  private interface PlaceCount {
    List<List<Integer>> of(
        List<Geometry.Curve> curves, Sampler sampler, Window window, Cancellation c);
  }

  /** The index of an object as a member of a family, or −1 when it is none. */
  @FunctionalInterface
  private interface Membership {
    int index(CanvasObject o, int family);
  }

  /** Makes a new member of a family. */
  @FunctionalInterface
  private interface Member {
    CanvasObject make(ObjectId id, int family, int index);
  }

  /**
   * Finds the places of families over paths inside the visible window on the worker, under a token
   * {@link #stop} cancels, and adds, on this thread, a named member for each place no member has,
   * the new members of a family in one colour, the one the next drawn object would take ({@link
   * CanvasState#nextColour}) when the family is added, unless a path's record changed meanwhile
   * (its points' records a recompute rewrites; the paths' it does not), telling {@code onAdded} the
   * last one; tells {@code onRefused} {@code none} when there is no place at all, and refuses more
   * than {@link #MAX_PLACES} places in view, which would be a cloud of named points.
   */
  private void addPlaces(
      List<ObjectId> paths,
      PlaceCount count,
      Membership membership,
      Member member,
      String none,
      Consumer<ObjectId> onAdded,
      Consumer<String> onRefused) {
    DocumentState state = stack.current();
    List<Optional<CanvasObject>> records = new ArrayList<>();
    List<Geometry.Curve> curves = new ArrayList<>();
    for (ObjectId p : paths) {
      records.add(state.canvas().object(p));
      if (!(state.canvas().geometryOf(p) instanceof Geometry.Curve c)) {
        onRefused.accept(Messages.text("canvas.find.undefined"));
        return;
      }
      curves.add(c);
    }
    Environment env = environmentBefore(state, state.cells().size());
    Settings settings = state.settings().engine();
    Viewport view = state.viewport();
    Token t = new Token();
    entryTokens.add(t);
    worker.execute(
        () -> {
          List<List<Integer>> counted;
          try {
            counted =
                count.of(
                    curves,
                    sampling.over(env, settings, view.width(), view.height(), t),
                    window(view),
                    t);
          } catch (CancelledException stopped) {
            counted = null;
          }
          List<List<Integer>> done = counted;
          ui.execute(
              () -> {
                entryTokens.remove(t);
                if (done == null || t.requested) {
                  return;
                }
                CanvasState now = stack.current().canvas();
                for (int k = 0; k < paths.size(); k++) {
                  if (!now.object(paths.get(k)).equals(records.get(k))) {
                    onRefused.accept(Messages.text("entry.changed"));
                    return;
                  }
                }
                if (done.stream().mapToInt(List::size).sum() > MAX_PLACES) {
                  onRefused.accept(Messages.text("entry.tooMany"));
                  return;
                }
                List<Command> adds = new ArrayList<>();
                // the state each family's members are added to, after the families before it
                DocumentState at = stack.current();
                for (int f = 0; f < done.size(); f++) {
                  Set<Integer> taken = new HashSet<>();
                  for (CanvasObject o : now.objects()) {
                    taken.add(membership.index(o, f));
                  }
                  // a family's new members take one colour, the one the next drawn object would
                  int colour = at.canvas().nextColour();
                  int first = adds.size();
                  for (int index : done.get(f)) {
                    if (!taken.contains(index)) {
                      int family = f;
                      int place = index;
                      adds.add(
                          new Command.AddObject(
                              id -> {
                                CanvasObject o = member.make(id, family, place);
                                Style s = o.style();
                                return o.withStyle(
                                    new Style(
                                        colour,
                                        s.width(),
                                        s.dashed(),
                                        s.visible(),
                                        s.labelVisible()));
                              },
                              Optional.empty(),
                              true,
                              false));
                    }
                  }
                  at = new Command.Sequence(adds.subList(first, adds.size())).apply(at);
                }
                if (adds.isEmpty()) {
                  onRefused.accept(
                      done.stream().allMatch(List::isEmpty)
                          ? none
                          : Messages.text("canvas.find.present"));
                  return;
                }
                ObjectId last = new ObjectId(now.nextObjectId().value() + adds.size() - 1);
                execute(new Command.Sequence(adds));
                onAdded.accept(last);
              });
        });
  }

  /**
   * The coordinate entry of a point on a path, read on the worker ({@link Entries#onPath}) and run
   * unless it was stopped, or the point or its path changed meanwhile. A recompute rewrites the
   * point's record (where it last landed), so what is compared is its path and its kind of
   * parameter, and the path's record.
   */
  private void submitParameter(
      DocumentState state,
      PointOnPath point,
      Environment env,
      String text,
      Consumer<String> onError) {
    Optional<CanvasObject> path = state.canvas().object(point.path());
    Geometry geometry = state.canvas().geometryOf(point.path());
    Viewport view = state.viewport();
    Settings settings = state.settings().engine();
    Token t = new Token();
    entryTokens.add(t);
    worker.execute(
        () -> {
          Entries.Read read;
          try {
            read =
                Entries.onPath(
                    text,
                    point,
                    geometry,
                    env,
                    Set.copyOf(state.names().values()),
                    sampling.over(env, settings, view.width(), view.height(), t),
                    t);
          } catch (CancelledException stopped) {
            read = null;
          }
          Entries.Read done = read;
          ui.execute(
              () -> {
                entryTokens.remove(t);
                if (done == null || t.requested) {
                  return;
                }
                if (done.error() != null) {
                  onError.accept(done.error());
                  return;
                }
                CanvasState now = stack.current().canvas();
                if (!(now.object(point.id()).orElse(null) instanceof PointOnPath p
                        && p.path().equals(point.path())
                        && p.kind() == point.kind())
                    || !now.object(point.path()).equals(path)) {
                  onError.accept(Messages.text("entry.changed"));
                  return;
                }
                execute(done.command());
              });
        });
  }

  /**
   * Reads a number a canvas tool asks for, a bound or a centre: the text parsed, made canonical and
   * evaluated to a real number on the worker, in the environment after the last cell with the
   * canvas's variables, so it may read sliders; {@code onRead} is told the expression, which the
   * object keeps, and its value now, or {@code onError} why there is none, on this thread.
   *
   * @param text the text
   * @param onRead told the canonical expression and its value
   * @param onError told the message when the text is refused
   */
  public void readNumber(String text, BiConsumer<Expr, Double> onRead, Consumer<String> onError) {
    Objects.requireNonNull(text, "text");
    DocumentState state = stack.current();
    Environment env = environmentBefore(state, state.cells().size());
    Token t = new Token();
    entryTokens.add(t);
    worker.execute(
        () -> {
          Expr e = null;
          double value = Double.NaN;
          String error = null;
          try {
            e = Canonical.of(Parser.parseExpression(text, env.names()));
            Expr v = NumericForm.numeric(Evaluator.evaluate(e, env, false, t).value(), 17, t);
            if (v instanceof Num n && n.value() instanceof RealNumeric r) {
              value = r.toDouble();
            }
            if (!Double.isFinite(value)) {
              error = Messages.text("entry.number");
            }
          } catch (ParseException | EvaluationException x) {
            error = x.getMessage();
          } catch (CancelledException stopped) {
            error = null;
            e = null;
          }
          Expr read = e;
          double at = value;
          String refused = error;
          ui.execute(
              () -> {
                entryTokens.remove(t);
                if (t.requested || read == null && refused == null) {
                  return;
                }
                if (refused != null) {
                  onError.accept(refused);
                } else {
                  onRead.accept(read, at);
                }
              });
        });
  }

  /**
   * Reads an angle typed in the unit circle view, on the worker, in the environment after the last
   * cell and its angle mode ({@link TrigValues#read}); {@code onRead} is told the angle, or {@code
   * onError} why there is none, on this thread. Stop drops it.
   *
   * @param text the text
   * @param onRead told the angle
   * @param onError told the message when the text is refused
   */
  public void readAngle(String text, Consumer<TrigValues.Angle> onRead, Consumer<String> onError) {
    Objects.requireNonNull(text, "text");
    DocumentState state = stack.current();
    Environment env = environmentBefore(state, state.cells().size());
    Token t = new Token();
    entryTokens.add(t);
    worker.execute(
        () -> {
          TrigValues.Angle angle = null;
          String error = null;
          try {
            angle = TrigValues.read(text, env, t);
            if (angle == null) {
              error = Messages.text("entry.number");
            }
          } catch (ParseException | EvaluationException x) {
            error = x.getMessage();
          } catch (CancelledException stopped) {
            error = null;
          }
          TrigValues.Angle read = angle;
          String refused = error;
          ui.execute(
              () -> {
                entryTokens.remove(t);
                if (t.requested || read == null && refused == null) {
                  return;
                }
                if (refused != null) {
                  onError.accept(refused);
                } else {
                  onRead.accept(read);
                }
              });
        });
  }

  /**
   * The sine, cosine and tangent of an angle for the unit circle view, evaluated, printed and
   * typeset on the worker ({@link TrigValues#values}): exact at a special angle, otherwise decimals
   * at the document's digits; {@code onValues} is told them on this thread. Stop drops them.
   *
   * @param angle the angle
   * @param special the special angle's {@code k}, a multiple of 15°, or empty
   * @param onValues told the three values
   */
  public void trigValues(
      TrigValues.Angle angle, OptionalInt special, Consumer<List<TrigValues.Value>> onValues) {
    Objects.requireNonNull(angle, "angle");
    DocumentState state = stack.current();
    Environment env = environmentBefore(state, state.cells().size());
    int digits = state.settings().numberDigits();
    Token t = new Token();
    entryTokens.add(t);
    worker.execute(
        () -> {
          List<TrigValues.Value> values;
          try {
            values = TrigValues.values(angle, special, env, digits, t);
          } catch (CancelledException stopped) {
            values = null;
          }
          List<TrigValues.Value> done = values;
          ui.execute(
              () -> {
                entryTokens.remove(t);
                if (!t.requested && done != null) {
                  onValues.accept(done);
                }
              });
        });
  }

  /**
   * An entry read on the worker: run its command unless it was stopped or its object changed
   * meanwhile.
   */
  private void entered(
      Token t, Optional<CanvasObject> was, Entries.Read read, Consumer<String> onError) {
    entryTokens.remove(t);
    if (read == null || t.requested) {
      return;
    }
    if (read.error() != null) {
      onError.accept(read.error());
      return;
    }
    if (was.isPresent() && !stack.current().canvas().object(was.get().id()).equals(was)) {
      onError.accept(Messages.text("entry.changed"));
      return;
    }
    // submitting a held object's row again resumes it, even with the same text
    if (was.isPresent() && canvasHeld.remove(was.get().id())) {
      canvasDirty.add(was.get().id());
    }
    try {
      execute(read.command());
    } catch (CycleException e) {
      onError.accept(Messages.text("entry.cycle"));
    } catch (IllegalArgumentException e) {
      onError.accept(Messages.text("entry.invalid"));
    }
    scheduleCanvas();
  }

  /**
   * Starts evaluating the cells that are pending, as after loading, and then recomputes the whole
   * canvas with what they define.
   */
  public void start() {
    canvasStarted = false;
    canvasDirty.addAll(ids(stack.current().canvas()));
    restart();
    if (token == null) {
      canvasStarted = true;
      scheduleCanvas();
    }
  }

  /**
   * Runs a command.
   *
   * @param command the command
   */
  public void execute(Command command) {
    DocumentState before = stack.current();
    if (command instanceof Command.Submit s) {
      submitted.add(s.id());
    }
    boolean changed = stack.execute(command);
    boolean moved = changed && readersOfChanged(before);
    if (changed) {
      noteCanvas(before);
      publish(before);
    }
    // a submission always starts over, even of a cell already pending with older text, and so
    // does a canvas variable's change while cells are pending, which may have read its old value
    if (command instanceof Command.Submit
        || changed && !before.pendingIds().equals(stack.current().pendingIds())
        || moved && !stack.current().pendingIds().isEmpty()) {
      restart();
    }
    scheduleCanvas();
  }

  /** Undoes the last step. */
  public void undo() {
    DocumentState before = stack.current();
    if (stack.undo()) {
      // a submission whose cell is still pending stays one; the others are gone
      submitted.retainAll(stack.current().pendingIds());
      keepViewport(before.viewport());
      changed(before);
    }
  }

  /** Redoes the last undone step. */
  public void redo() {
    DocumentState before = stack.current();
    if (stack.redo()) {
      // a submission whose cell is still pending stays one; the others are gone
      submitted.retainAll(stack.current().pendingIds());
      keepViewport(before.viewport());
      changed(before);
    }
  }

  /** Carries the viewport of the moment into a state an undo or a redo restored. */
  private void keepViewport(Viewport viewport) {
    stack.amend(s -> s.withViewport(viewport));
  }

  /** Makes the next edit an undo step of its own. */
  public void endMerge() {
    stack.endMerge();
  }

  /**
   * Sets whether every value is traced, so it shows its steps; it applies from the next job.
   *
   * @param on whether to trace
   */
  public void setTracing(boolean on) {
    tracing = on;
  }

  /**
   * Stops the running jobs, if any: the evaluation's remaining cells become cancelled, and a canvas
   * recompute leaves the geometry it had not delivered.
   */
  public void stop() {
    stopCells();
    for (Token t : entryTokens) {
      t.requested = true;
    }
    if (canvasToken != null) {
      canvasToken.requested = true;
    }
    // what was gathered for the canvas waits for the objects' next change, as a stopped cell
    // does, keeping its old geometry. An object with none yet that the stopped job was drawing is
    // held: it waits for its own change, or a change of what it reads. One that no job has tried
    // yet (loaded while the cells run) stays gathered, so the next job draws it.
    boolean running = canvasToken != null;
    canvasDirty.clear();
    canvasChanged.clear();
    canvasResample = false;
    CanvasState canvas = stack.current().canvas();
    for (CanvasObject o : canvas.objects()) {
      if (!canvas.geometry().containsKey(o.id())) {
        (running && canvasJobIds.contains(o.id()) ? canvasHeld : canvasDirty).add(o.id());
      }
    }
  }

  private void stopCells() {
    if (token != null) {
      token.requested = true;
    }
  }

  private void changed(DocumentState before) {
    boolean moved = readersOfChanged(before);
    boolean restart =
        !before.pendingIds().equals(stack.current().pendingIds())
            || moved && !stack.current().pendingIds().isEmpty();
    noteCanvas(before);
    publish(before);
    if (restart) {
      restart();
    }
    scheduleCanvas();
  }

  private void publish(DocumentState before) {
    canUndo.set(stack.canUndo());
    canRedo.set(stack.canRedo());
    CanvasFrame frame = canvasFrame.get();
    if (!frame.state().equals(stack.current().canvas())) {
      // the content and the rows stay until the canvas job builds new ones
      canvasFrame.set(
          new CanvasFrame(
              stack.current().canvas(),
              frame.content(),
              frame.items(),
              frame.rows(),
              frame.slopes()));
    }
    Change change = new Change(before, stack.current());
    for (Consumer<Change> l : listeners) {
      l.accept(change);
    }
  }

  private void restart() {
    generation++;
    stopCells();
    token = null;
    DocumentState state = stack.current();
    int from = firstToRun(state);
    if (from < 0) {
      updateBusy();
      return;
    }
    Environment before = storedBefore(state, from);
    CanvasBindings canvas = CanvasBindings.of(state);
    List<EvaluationJob.Input> inputs = new ArrayList<>();
    // a cell goes on from the previous cell of the run unless a finished cell lies between them,
    // whose stored environment it then starts from
    boolean chained = true;
    for (int k = from; k < state.cells().size(); k++) {
      Cell c = state.cells().get(k);
      if (c.output() instanceof Output.Pending) {
        inputs.add(
            new EvaluationJob.Input(
                c.id(),
                c.input(),
                chained ? Optional.empty() : Optional.of(storedBefore(state, k)),
                submitted.contains(c.id())));
        chained = true;
      } else if (c.output() instanceof Output.Done) {
        chained = false;
      }
    }
    long gen = generation;
    boolean traced = tracing;
    Format format = state.settings().format();
    Token t = new Token();
    token = t;
    updateBusy();
    worker.execute(
        () -> {
          EvaluationJob.run(
              inputs,
              before,
              canvas,
              t,
              traced,
              format,
              inspections,
              (id, out) -> ui.execute(() -> deliver(gen, id, out)));
          ui.execute(() -> finished(gen, t));
        });
  }

  /** The first pending cell; -1 if none is. */
  private static int firstToRun(DocumentState state) {
    for (int k = 0; k < state.cells().size(); k++) {
      if (state.cells().get(k).output() instanceof Output.Pending) {
        return k;
      }
    }
    return -1;
  }

  /**
   * What the variables inspector shows after the last finished cell before an index.
   *
   * @param state the state
   * @param index the index
   * @return the inspection of the nearest finished cell before it, or nothing
   */
  public static Inspection inspectionBefore(DocumentState state, int index) {
    for (int k = index - 1; k >= 0; k--) {
      if (state.cells().get(k).output() instanceof Output.Done d) {
        return d.inspection();
      }
    }
    return Inspection.EMPTY;
  }

  /**
   * The environment a cell is evaluated in: the one stored after the nearest finished cell before
   * it, or the empty environment with the document's settings, with the canvas's variables bound
   * over it ({@link CanvasBindings#overlay}).
   *
   * @param state the state
   * @param index the cell's index
   * @return the environment
   */
  public static Environment environmentBefore(DocumentState state, int index) {
    return CanvasBindings.of(state).overlay(storedBefore(state, index));
  }

  /** The environment stored after the nearest finished cell before an index, without the canvas. */
  private static Environment storedBefore(DocumentState state, int index) {
    for (int k = index - 1; k >= 0; k--) {
      if (state.cells().get(k).output() instanceof Output.Done d) {
        return d.after();
      }
    }
    return Environment.EMPTY.withSettings(state.settings().engine());
  }

  /**
   * Makes pending the finished cells that read a canvas variable whose value changed since a state:
   * a slider moved, a point moved, either added or removed.
   *
   * @return whether a canvas variable changed
   */
  private boolean readersOfChanged(DocumentState before) {
    Set<String> changed = CanvasBindings.of(stack.current()).changedFrom(CanvasBindings.of(before));
    if (!changed.isEmpty()) {
      stack.amend(s -> readersPending(s, changed, s.cells().size()));
    }
    return !changed.isEmpty();
  }

  /**
   * A state with the finished cells before an index that read any of some names made pending. Only
   * those cells run again, each from the environment the cell before it stored, since a cell's
   * effect on the environment does not depend on the values it reads; a cell that runs {@code
   * assume} does, so it and every evaluated cell after it are made pending.
   *
   * @param state the state
   * @param names the changed names
   * @param upTo the index of the first cell left alone
   * @return the state
   */
  static DocumentState readersPending(DocumentState state, Set<String> names, int upTo) {
    DocumentState out = state;
    for (int k = 0; k < upTo; k++) {
      Cell c = state.cells().get(k);
      if (c.output() instanceof Output.Done d && !Collections.disjoint(d.reads(), names)) {
        if (d.assumes()) {
          return out.pendingFrom(k);
        }
        out = out.withCell(c.id(), x -> x.withOutput(Output.Pending.INSTANCE));
      }
    }
    return out;
  }

  /**
   * A state with the sliders a cell defines on its canvas: each created at its minimum, or given
   * its new range with its value snapped into it. A slider whose name a point took meanwhile is
   * left out.
   */
  private static DocumentState withSliders(DocumentState state, List<SliderSpec> specs) {
    DocumentState out = state;
    for (SliderSpec spec : specs) {
      CanvasBindings canvas = CanvasBindings.of(out);
      try {
        if (canvas.sliders().containsKey(spec.variable())) {
          out = new Command.ReplaceObject(canvas.sliderFor(spec)).apply(out);
        } else {
          Slider made = canvas.sliderFor(spec);
          out =
              new Command.AddObject(
                      id ->
                          new Slider(
                              id,
                              made.style(),
                              made.variable(),
                              made.min(),
                              made.max(),
                              made.step(),
                              made.value()))
                  .apply(out);
        }
      } catch (IllegalArgumentException taken) {
        // a point has the name now; the cell's statement stays a plain binding
      }
    }
    return out;
  }

  private void deliver(long gen, long id, Output output) {
    if (gen != generation) {
      return;
    }
    submitted.remove(id);
    DocumentState before = stack.current();
    stack.amend(s -> s.withCell(id, c -> c.withOutput(output)));
    if (output instanceof Output.Done d && !d.sliders().isEmpty()) {
      DocumentState withCell = stack.current();
      stack.amend(s -> withSliders(s, d.sliders()));
      // the cells before this one that read a slider it created or changed run again; the ones
      // after it are in this run, with the slider's value
      Set<String> changed =
          CanvasBindings.of(stack.current()).changedFrom(CanvasBindings.of(withCell));
      int at = stack.current().indexOf(id).orElse(0);
      stack.amend(s -> readersPending(s, changed, at));
      noteCanvas(withCell);
    }
    if (!before.equals(stack.current())) {
      publish(before);
    }
  }

  private void finished(long gen, Token t) {
    if (gen == generation) {
      token = null;
      updateBusy();
      // cells a slider defined in this run made stale run now, unless the run was stopped
      if (!t.requested && firstToRun(stack.current()) >= 0) {
        restart();
      }
      canvasStarted = true;
      scheduleCanvas();
    }
  }

  /** Busy while an evaluation or a canvas job runs. */
  private void updateBusy() {
    busy.set(token != null || canvasToken != null);
  }

  private static Set<ObjectId> ids(CanvasState canvas) {
    Set<ObjectId> out = new HashSet<>();
    for (CanvasObject o : canvas.objects()) {
      out.add(o.id());
    }
    return out;
  }

  /**
   * Gathers what a change makes the canvas recompute: the objects whose definitions changed (a
   * change of style only rebuilds the content), the canvas variables whose values changed, a new
   * window, new names.
   */
  private void noteCanvas(DocumentState before) {
    DocumentState after = stack.current();
    if (before.canvas() != after.canvas()) {
      for (ObjectId id : after.canvas().changedSince(before.canvas())) {
        CanvasObject now = after.canvas().object(id).orElseThrow();
        Optional<CanvasObject> was = before.canvas().object(id);
        if (was.isPresent() && now.withStyle(was.get().style()).equals(was.get())) {
          contentStale = true;
        } else {
          canvasDirty.add(id);
          canvasHeld.remove(id);
        }
      }
      canvasHeld.removeIf(id -> after.canvas().object(id).isEmpty());
      if (after.canvas().objects().size() != before.canvas().objects().size()
          || !after.canvas().geometry().equals(before.canvas().geometry())) {
        contentStale = true;
      }
    }
    canvasChanged.addAll(CanvasBindings.of(after).changedFrom(CanvasBindings.of(before)));
    if (!before.names().equals(after.names())
        || !Coordinates.format(before.settings()).equals(Coordinates.format(after.settings()))) {
      contentStale = true; // the rows name the points and write their coordinates
    }
    if (!before.viewport().equals(after.viewport())) {
      canvasResample = true;
    }
  }

  private static Window window(Viewport v) {
    return new Window(v.xMin(), v.xMax(), v.yMin(), v.yMax());
  }

  /**
   * Starts a canvas job when one is wanted and none is running. A running job is never cancelled by
   * a change: what changes meanwhile is gathered for the next job, which starts when this one ends,
   * so a drag keeps the canvas moving instead of restarting it at every step.
   */
  private void scheduleCanvas() {
    if (!canvasStarted || canvasToken != null) {
      return;
    }
    DocumentState state = stack.current();
    Environment env = environmentBefore(state, state.cells().size());
    boolean envChanged = !env.equals(canvasEnvironment);
    if (canvasDirty.isEmpty()
        && canvasChanged.isEmpty()
        && !canvasResample
        && !contentStale
        && !envChanged) {
      return;
    }
    if (state.canvas().objects().isEmpty()) {
      // nothing to recompute or draw: no job, so a worksheet without a canvas never looks busy
      canvasFrame.set(new CanvasFrame(state.canvas(), PlotContent.EMPTY, Map.of(), Map.of()));
      canvasDirty.clear();
      canvasChanged.clear();
      canvasResample = false;
      contentStale = false;
      canvasEnvironment = env;
      return;
    }
    CanvasJob.Input in =
        new CanvasJob.Input(
            state.canvas(),
            state.names(),
            Set.copyOf(canvasDirty),
            Set.copyOf(canvasChanged),
            Set.copyOf(canvasHeld),
            canvasResample,
            env,
            canvasEnvironment,
            window(state.viewport()),
            state.viewport().width(),
            state.viewport().height(),
            state.settings().engine(),
            canvasTheme,
            canvasFrame.get().slopes(),
            Coordinates.format(state.settings()));
    canvasDirty.clear();
    canvasChanged.clear();
    canvasResample = false;
    contentStale = false;
    canvasEnvironment = env;
    Token t = new Token();
    canvasToken = t;
    canvasJobIds = ids(state.canvas());
    updateBusy();
    worker.execute(
        () -> {
          CanvasJob.Output out;
          try {
            out = CanvasJob.run(in, sampling, content, Recomputes.standard(), rows, t);
          } catch (CancelledException e) {
            out = null;
          } catch (RuntimeException e) {
            // a defect, never a state the canvas is in: the job's objects go undefined and the
            // next change runs a job again, rather than the canvas never recomputing
            failed(e);
            ui.execute(() -> canvasFailed(t));
            return;
          }
          CanvasJob.Output done = out;
          ui.execute(() -> canvasDone(t, in.names(), done));
        });
  }

  /** Whether a canvas job's failure has been logged, so a repeating defect logs once. */
  private static final java.util.concurrent.atomic.AtomicBoolean LOGGED =
      new java.util.concurrent.atomic.AtomicBoolean();

  /** Logs a canvas job's failure, the first time. */
  private static void failed(RuntimeException e) {
    if (LOGGED.compareAndSet(false, true)) {
      System.getLogger(Document.class.getName())
          .log(System.Logger.Level.ERROR, "a canvas recompute failed", e);
    }
  }

  /**
   * A canvas job failed: its token is released, every object is undefined (its tracking state kept)
   * until the next job, which the next change starts and which recomputes them all.
   */
  private void canvasFailed(Token t) {
    canvasToken = null;
    updateBusy();
    if (t.requested) {
      return;
    }
    CanvasState current = stack.current().canvas();
    Map<ObjectId, Geometry> geometry = new HashMap<>(current.geometry());
    for (CanvasObject o : current.objects()) {
      geometry.put(o.id(), Geometry.UNDEFINED);
    }
    CanvasState undefined = new CanvasState(current.objects(), geometry, current.nextId());
    stack.amend(s -> s.withCanvas(undefined, s.names()));
    CanvasFrame shown = canvasFrame.get();
    canvasFrame.set(new CanvasFrame(undefined, PlotContent.EMPTY, Map.of(), shown.rows()));
    // recomputed by the next job, which the next change starts
    canvasDirty.addAll(canvasJobIds);
    contentStale = true;
  }

  /**
   * A canvas job's result arrives: each object whose record is what the job started from takes the
   * job's record (its tracking state) and geometry; an object changed meanwhile keeps its own, and
   * is already gathered for the next job. A cancelled job leaves the geometry as it was.
   */
  private void canvasDone(Token t, Map<ObjectId, String> names, CanvasJob.Output out) {
    canvasToken = null;
    updateBusy();
    if (out == null || t.requested) {
      // stopped: the next job waits for the next change
      return;
    }
    DocumentState before = stack.current();
    CanvasState current = before.canvas();
    List<CanvasObject> objects = new ArrayList<>(current.objects());
    Map<ObjectId, Geometry> geometry = new HashMap<>(current.geometry());
    boolean whole = objects.size() == out.result().objects().size();
    for (int k = 0; k < objects.size(); k++) {
      CanvasObject o = objects.get(k);
      if (out.from().object(o.id()).filter(o::equals).isPresent()) {
        objects.set(k, out.result().object(o.id()).orElseThrow());
        Geometry g = out.result().geometry().get(o.id());
        if (g != null) {
          geometry.put(o.id(), g);
          // recomputed because what it reads changed: no longer held
          canvasHeld.remove(o.id());
        }
      } else {
        whole = false;
      }
    }
    CanvasState merged = new CanvasState(objects, geometry, current.nextId());
    stack.amend(s -> s.withCanvas(merged, s.names()));
    boolean labelled = stack.current().names().equals(names);
    // a slope applies only to a graph whose record the job's result is
    Map<FunctionGraph, DoubleUnaryOperator> slopes = new HashMap<>(out.slopes());
    slopes.keySet().removeIf(g -> !merged.object(g.id()).filter(g::equals).isPresent());
    if (whole && labelled) {
      canvasFrame.set(
          new CanvasFrame(merged, out.built().content(), out.built().items(), out.rows(), slopes));
    } else {
      canvasFrame.set(
          new CanvasFrame(
              merged, canvasFrame.get().content(), canvasFrame.get().items(), out.rows(), slopes));
      contentStale = true;
    }
    // points the recompute moved are variables: their readers among the cells and the objects
    boolean moved = readersOfChanged(before);
    canvasChanged.addAll(CanvasBindings.of(stack.current()).changedFrom(CanvasBindings.of(before)));
    publish(before);
    if (moved && !stack.current().pendingIds().isEmpty()) {
      restart();
    }
    scheduleCanvas();
  }
}
