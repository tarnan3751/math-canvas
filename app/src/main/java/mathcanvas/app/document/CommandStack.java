package mathcanvas.app.document;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.function.UnaryOperator;

/**
 * The document's history: the current state, and the states undo and redo return to. A command
 * pushes the state before it; undo and redo move whole states between the two stacks, so they
 * restore exactly what was there. Consecutive commands with the same {@linkplain Command#mergeKey()
 * merge key}, each within {@link #MERGE_NANOS} of the one before, are one undo step: typing a word
 * in a cell is undone at once. Any other command, an undo, a redo or {@link #endMerge} ends the
 * step. At most {@link #MAX_UNDO} steps are kept, the oldest dropped first.
 *
 * <p>Not thread-safe: the document runs it on the FX thread. The clock is passed in, so tests
 * control it.
 */
public final class CommandStack {

  /** The longest pause between two merged commands: one second. */
  public static final long MERGE_NANOS = 1_000_000_000L;

  /** The most undo steps kept. */
  public static final int MAX_UNDO = 500;

  private final LongSupplier clock;
  private final Deque<DocumentState> undo = new ArrayDeque<>();
  private final Deque<DocumentState> redo = new ArrayDeque<>();
  private DocumentState current;
  private Object mergeKey;
  private long mergedAt;

  /**
   * A stack with no history.
   *
   * @param initial the current state
   * @param clock a monotonic clock in nanoseconds, such as {@code System::nanoTime}
   */
  public CommandStack(DocumentState initial, LongSupplier clock) {
    this.current = Objects.requireNonNull(initial, "initial");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  /**
   * The current state.
   *
   * @return the state
   */
  public DocumentState current() {
    return current;
  }

  /**
   * Runs a command. A command that changes nothing records nothing.
   *
   * @param command the command
   * @return whether the state changed
   */
  public boolean execute(Command command) {
    DocumentState next = command.apply(current);
    if (next.equals(current)) {
      return false;
    }
    long now = clock.getAsLong();
    Optional<Object> key = command.mergeKey();
    boolean merge =
        key.isPresent()
            && key.get().equals(mergeKey)
            && !undo.isEmpty()
            && now - mergedAt <= MERGE_NANOS;
    redo.clear();
    if (merge && next.equals(undo.peek())) {
      // the merged edits came back to where the step began: the step is gone
      undo.pop();
      current = next;
      endMerge();
      return true;
    }
    if (!merge) {
      undo.push(current);
      if (undo.size() > MAX_UNDO) {
        undo.removeLast();
      }
      mergeKey = key.orElse(null);
    }
    mergedAt = now;
    current = next;
    return true;
  }

  /**
   * Returns to the state before the last step.
   *
   * @return whether there was a step to undo
   */
  public boolean undo() {
    endMerge();
    if (undo.isEmpty()) {
      return false;
    }
    redo.push(current);
    current = undo.pop();
    return true;
  }

  /**
   * Returns to the state the last undo left.
   *
   * @return whether there was a step to redo
   */
  public boolean redo() {
    endMerge();
    if (redo.isEmpty()) {
      return false;
    }
    undo.push(current);
    current = redo.pop();
    return true;
  }

  /**
   * Whether there is a step to undo.
   *
   * @return true if {@link #undo} would change the state
   */
  public boolean canUndo() {
    return !undo.isEmpty();
  }

  /**
   * Whether there is a step to redo.
   *
   * @return true if {@link #redo} would change the state
   */
  public boolean canRedo() {
    return !redo.isEmpty();
  }

  /** Makes the next command a step of its own even if it would merge with the last. */
  public void endMerge() {
    mergeKey = null;
  }

  /**
   * Changes the current state without recording an undo step: how evaluation results enter the
   * document. The history is untouched, so an undo returns to the state before the last command,
   * with the outputs it had then.
   *
   * @param change the change
   */
  public void amend(UnaryOperator<DocumentState> change) {
    current = Objects.requireNonNull(change.apply(current));
  }
}
