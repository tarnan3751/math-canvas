package mathcanvas.app.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import mathcanvas.app.document.Cell;
import mathcanvas.app.document.Command;
import mathcanvas.app.document.CommandStack;
import mathcanvas.app.document.DocumentState;
import mathcanvas.app.document.Inspection;
import mathcanvas.app.document.Output;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;

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
 * and the cells after it are evaluated without it.
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
  private final List<Consumer<Change>> listeners = new CopyOnWriteArrayList<>();
  private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(this, "busy", false);
  private final ReadOnlyBooleanWrapper canUndo = new ReadOnlyBooleanWrapper(this, "canUndo");
  private final ReadOnlyBooleanWrapper canRedo = new ReadOnlyBooleanWrapper(this, "canRedo");
  private long generation;
  private boolean tracing;
  private Token token;

  /**
   * A document. Its pending cells start evaluating at {@link #start}.
   *
   * @param initial the state
   * @param worker where jobs run: a single thread, never the FX thread
   * @param ui where results are delivered: the document's own thread
   * @param clock a monotonic clock in nanoseconds, for merging edits
   */
  public Document(DocumentState initial, Executor worker, Executor ui, LongSupplier clock) {
    this.stack = new CommandStack(initial, clock);
    this.worker = Objects.requireNonNull(worker, "worker");
    this.ui = Objects.requireNonNull(ui, "ui");
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
   * Whether a job is running.
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

  /** Starts evaluating the cells that are pending, as after loading. */
  public void start() {
    restart();
  }

  /**
   * Runs a command.
   *
   * @param command the command
   */
  public void execute(Command command) {
    DocumentState before = stack.current();
    boolean changed = stack.execute(command);
    if (changed) {
      publish(before);
    }
    // a submission always starts over, even of a cell already pending with older text
    if (command instanceof Command.Submit
        || changed && !before.pendingIds().equals(stack.current().pendingIds())) {
      restart();
    }
  }

  /** Undoes the last step. */
  public void undo() {
    DocumentState before = stack.current();
    if (stack.undo()) {
      changed(before);
    }
  }

  /** Redoes the last undone step. */
  public void redo() {
    DocumentState before = stack.current();
    if (stack.redo()) {
      changed(before);
    }
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

  /** Stops the running job, if any; its remaining cells become cancelled. */
  public void stop() {
    if (token != null) {
      token.requested = true;
    }
  }

  private void changed(DocumentState before) {
    boolean restart = !before.pendingIds().equals(stack.current().pendingIds());
    publish(before);
    if (restart) {
      restart();
    }
  }

  private void publish(DocumentState before) {
    canUndo.set(stack.canUndo());
    canRedo.set(stack.canRedo());
    Change change = new Change(before, stack.current());
    for (Consumer<Change> l : listeners) {
      l.accept(change);
    }
  }

  private void restart() {
    generation++;
    stop();
    token = null;
    DocumentState state = stack.current();
    int from = firstToRun(state);
    if (from < 0) {
      busy.set(false);
      return;
    }
    Environment before = environmentBefore(state, from);
    List<EvaluationJob.Input> inputs = new ArrayList<>();
    for (Cell c : state.cells().subList(from, state.cells().size())) {
      if (c.output() instanceof Output.Pending) {
        inputs.add(new EvaluationJob.Input(c.id(), c.input()));
      }
    }
    long gen = generation;
    boolean traced = tracing;
    Token t = new Token();
    token = t;
    busy.set(true);
    worker.execute(
        () -> {
          EvaluationJob.run(
              inputs, before, t, traced, (id, out) -> ui.execute(() -> deliver(gen, id, out)));
          ui.execute(() -> finished(gen));
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
   * it, or the empty environment with the document's settings.
   *
   * @param state the state
   * @param index the cell's index
   * @return the environment
   */
  public static Environment environmentBefore(DocumentState state, int index) {
    for (int k = index - 1; k >= 0; k--) {
      if (state.cells().get(k).output() instanceof Output.Done d) {
        return d.after();
      }
    }
    return Environment.EMPTY.withSettings(state.settings().engine());
  }

  private void deliver(long gen, long id, Output output) {
    if (gen != generation) {
      return;
    }
    DocumentState before = stack.current();
    stack.amend(s -> s.withCell(id, c -> c.withOutput(output)));
    if (!before.equals(stack.current())) {
      publish(before);
    }
  }

  private void finished(long gen) {
    if (gen == generation) {
      token = null;
      busy.set(false);
    }
  }
}
