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
   * What the run printed so far.
   *
   * @return the log
   */
  public RunLog log() {
    return printed.isEmpty() ? RunLog.NONE : new RunLog(printed);
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
