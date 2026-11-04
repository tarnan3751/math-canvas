package mathcanvas.engine.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Definition;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.EvaluationException;
import mathcanvas.engine.expr.Expr;

/**
 * The run of one top-level statement ({@code docs/language.md} §19): the environment as the
 * statement changes it (a top-level block's assignments, a function's {@code global} ones), the
 * request to stop, the steps taken against the budget, the calls open, and whether a value was
 * computed numerically. The evaluator and the interpreter share it, so a function called from
 * inside an expression counts against the same budget and depth as the statement around it.
 * Mutable, and confined to the thread that runs the statement.
 */
public final class Execution {

  /**
   * The most steps one top-level statement may take: statements run, loop iterations and calls of
   * user functions.
   */
  public static final int MAX_STEPS = 1_000_000;

  private Environment environment;
  private final Cancellation cancellation;
  private long steps;
  private int calls;
  private boolean numeric;
  private boolean empty;
  private final List<Expr> printed = new ArrayList<>();

  /** The most events a traced run records; its narration shows fewer steps still. */
  static final int MAX_EVENTS = 5_000;

  private boolean traced;
  private int scopes;
  private int events;
  private boolean stopped;
  private final List<RunEvent> recorded = new ArrayList<>();

  /** The errors already placed at a call, by identity, so an outer call keeps their message. */
  private final Set<EvaluationException> placed =
      Collections.newSetFromMap(new IdentityHashMap<>());

  /**
   * A run in an environment.
   *
   * @param environment the environment the statement starts in
   * @param cancellation the request to stop
   */
  public Execution(Environment environment, Cancellation cancellation) {
    this.environment = Objects.requireNonNull(environment, "environment");
    this.cancellation = Objects.requireNonNull(cancellation, "cancellation");
  }

  /**
   * The environment now, with what the statement has defined so far.
   *
   * @return the environment
   */
  public Environment environment() {
    return environment;
  }

  /**
   * Binds a name of the document to a value computed by the run, as it is.
   *
   * @param name the name
   * @param value the canonical value
   */
  void assign(String name, Expr value) {
    environment = environment.with(name, new Definition.Computed(value));
  }

  /**
   * The request to stop.
   *
   * @return the cancellation
   */
  public Cancellation cancellation() {
    return cancellation;
  }

  /**
   * Takes one step: checks the request to stop and the budget. A statement run, a loop's turn and a
   * call of a user function each take one.
   *
   * @param node the node a budget error carries
   * @throws EvaluationException when the budget is spent
   */
  public void step(Expr node) {
    cancellation.check();
    if (++steps > MAX_STEPS) {
      throw new EvaluationException("the program ran more than " + MAX_STEPS + " steps", node);
    }
  }

  /**
   * The calls of user functions open now.
   *
   * @return the count
   */
  public int calls() {
    return calls;
  }

  /** Opens a call. */
  public void enterCall() {
    calls++;
  }

  /** Closes a call. */
  public void leaveCall() {
    calls--;
  }

  /**
   * Whether a value of the run was computed numerically.
   *
   * @return true if one was
   */
  public boolean numeric() {
    return numeric;
  }

  /**
   * Records that a value of the run was computed numerically.
   *
   * @param on whether this one was
   */
  public void numeric(boolean on) {
    numeric |= on;
  }

  /**
   * Whether the last expression evaluated was a call of a function that ended with no value: the
   * evaluator sets it for a statement that is such a call, and clears it for any other.
   *
   * @return true if it had no value
   */
  public boolean empty() {
    return empty;
  }

  /**
   * Sets whether the last expression evaluated had no value.
   *
   * @param on whether it had none
   */
  public void empty(boolean on) {
    empty = on;
  }

  /**
   * Appends a value to the run's output: a {@code print} statement.
   *
   * @param value the canonical value
   */
  void print(Expr value) {
    printed.add(value);
  }

  /**
   * What the run printed and, when traced, recorded so far.
   *
   * @return the log
   */
  public RunLog log() {
    return printed.isEmpty() && recorded.isEmpty() ? RunLog.NONE : new RunLog(printed, recorded);
  }

  /**
   * Sets whether the run records what its outermost block does, for the narration ({@code steps},
   * {@code docs/language.md} §19.6).
   *
   * @param on whether to record
   */
  public void trace(boolean on) {
    traced = on;
  }

  /**
   * Opens a block's scope: the top-level block or a function's body.
   *
   * @return the list its events go to, or null when it records none: only the outermost scope of a
   *     traced run records
   */
  List<RunEvent> enterScope() {
    return ++scopes == 1 && traced ? recorded : null;
  }

  /** Closes a block's scope. */
  void leaveScope() {
    scopes--;
  }

  /**
   * Keeps an event of the outermost block for the narration: the event and every event kept inside
   * it (a loop's kept turns, never the turns its fold drops) count toward {@link #MAX_EVENTS}; the
   * event that would pass it is not kept, and once, a {@link RunEvent.Stopped} says where the
   * narration stops.
   *
   * @param sink the outermost block's events
   * @param event the event
   */
  void keep(List<RunEvent> sink, RunEvent event) {
    if (stopped) {
      return;
    }
    int size = size(event);
    if (events + size > MAX_EVENTS) {
      // the event's beginning is kept, as far as the cap allows: a loop too long to keep whole
      // is narrated up to there rather than lost
      int[] budget = {MAX_EVENTS - events};
      RunEvent begun = truncated(event, budget);
      if (begun != null) {
        sink.add(begun);
      }
      events = MAX_EVENTS - budget[0];
      stopped = true;
      sink.add(new RunEvent.Stopped(events));
      return;
    }
    events += size;
    sink.add(event);
  }

  /**
   * The beginning of an event that keeps at most {@code budget[0]} events, in the order they are
   * narrated, the budget counted down; null when not even the event itself fits.
   */
  private static RunEvent truncated(RunEvent event, int[] budget) {
    if (budget[0] < 1) {
      return null;
    }
    return switch (event) {
      case RunEvent.Stopped s -> s;
      case RunEvent.Done d -> {
        budget[0]--;
        yield d;
      }
      case RunEvent.Loop l -> {
        budget[0]--;
        List<RunEvent.Turn> turns = new ArrayList<>();
        for (RunEvent.Turn t : l.turns()) {
          if (budget[0] < 1) {
            break;
          }
          budget[0]--;
          List<RunEvent> kept = new ArrayList<>();
          for (RunEvent e : t.events()) {
            RunEvent k = truncated(e, budget);
            if (k == null) {
              break;
            }
            kept.add(k);
          }
          turns.add(new RunEvent.Turn(t.index(), t.value(), kept));
        }
        yield new RunEvent.Loop(l.line(), l.variable(), l.count(), turns, l.folded());
      }
    };
  }

  /** The events an event keeps: itself, and a loop's kept turns with theirs. */
  private static int size(RunEvent event) {
    return switch (event) {
      case RunEvent.Done _ -> 1;
      case RunEvent.Stopped _ -> 0;
      case RunEvent.Loop l -> {
        int n = 1;
        for (RunEvent.Turn t : l.turns()) {
          n++;
          for (RunEvent e : t.events()) {
            n += size(e);
          }
        }
        yield n;
      }
    };
  }

  /** Whether an error was already placed at a call. */
  boolean placed(EvaluationException e) {
    return placed.contains(e);
  }

  /** Records that an error was placed at a call. */
  EvaluationException place(EvaluationException e) {
    placed.add(e);
    return e;
  }
}
