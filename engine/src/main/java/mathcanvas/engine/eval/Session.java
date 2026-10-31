package mathcanvas.engine.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.CancelledException;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Script;
import mathcanvas.engine.parse.Statement;
import mathcanvas.engine.rule.Trace;

/**
 * Runs statements in order against an environment that each statement may change: a definition
 * binds its name, a top-level {@code assume(…)} records facts, a top-level {@code clear(name)}
 * unbinds a name, and an expression is evaluated. {@code assume} and {@code clear} work only as
 * whole statements; inside an expression they are an evaluation error. The settings change through
 * {@link #setPrecision} and {@link #setAngleMode}, which the language has no syntax for; {@link
 * #setTracing} traces every evaluation as {@code steps(…)} does one.
 *
 * <p>The session is the one mutable object of evaluation and is not thread-safe: a caller that
 * evaluates on a worker serialises its access. It does no I/O; the caller parses each statement
 * with {@link #names()} and prints the outcomes. Nothing but an outcome leaves {@link #run}: any
 * other runtime exception, a defect, becomes a failure whose message starts with "internal error",
 * and an {@link OutOfMemoryError}, a backstop for a path the size limits miss, the failure "result
 * too large".
 *
 * <p>A statement run with a {@link Cancellation} stops at the engine's next check once stopping is
 * requested or the thread is interrupted, and gives {@link Outcome.Cancelled}, with the environment
 * exactly as it was before the statement; the interrupt flag is left set.
 */
public final class Session {

  private Environment environment;
  private boolean tracing;

  /** A session on the empty environment. */
  public Session() {
    this(Environment.EMPTY);
  }

  /**
   * A session on a given environment.
   *
   * @param environment the environment
   */
  public Session(Environment environment) {
    this.environment = Objects.requireNonNull(environment, "environment");
  }

  /**
   * The current environment.
   *
   * @return the environment
   */
  public Environment environment() {
    return environment;
  }

  /**
   * The user-defined functions now, for parsing the next statement.
   *
   * @return the names
   */
  public Names names() {
    return environment.names();
  }

  /**
   * Sets the precision of inexact arithmetic.
   *
   * @param digits the precision, from {@link Settings#MIN_PRECISION} to {@link
   *     Settings#MAX_PRECISION}
   * @throws IllegalArgumentException if the precision is out of range
   */
  public void setPrecision(int digits) {
    environment = environment.withSettings(environment.settings().withPrecision(digits));
  }

  /**
   * Sets the angle mode.
   *
   * @param mode the angle mode
   */
  public void setAngleMode(AngleMode mode) {
    environment = environment.withSettings(environment.settings().withAngleMode(mode));
  }

  /**
   * Sets whether every expression statement is evaluated with its trace, as if written {@code
   * steps(…)}; off by default.
   *
   * @param on whether to trace
   */
  public void setTracing(boolean on) {
    tracing = on;
  }

  /**
   * Runs one statement.
   *
   * @param statement the statement, as parsed
   * @return its outcome; on an evaluation error the environment is unchanged
   */
  public Outcome run(Statement statement) {
    return run(statement, Cancellation.NEVER);
  }

  /**
   * Runs one statement, stopping when {@code cancellation} is requested or the thread is
   * interrupted.
   *
   * @param statement the statement, as parsed
   * @param cancellation the request to stop
   * @return its outcome, {@link Outcome.Cancelled} when it was stopped; on an evaluation error or a
   *     cancellation the environment is unchanged
   */
  public Outcome run(Statement statement, Cancellation cancellation) {
    Environment before = environment;
    try {
      cancellation.check();
      return outcome(statement, cancellation);
    } catch (CancelledException cancelled) {
      environment = before;
      return new Outcome.Cancelled();
    }
  }

  private Outcome outcome(Statement statement, Cancellation cancellation) {
    return switch (statement) {
      case Statement.Definition d -> {
        Definition definition =
            d.params().isEmpty()
                ? new Definition.Value(d.body())
                : new Definition.Function(d.params(), d.body());
        environment = environment.with(d.name().name(), definition);
        yield new Outcome.Defined(d);
      }
      case Statement.Expression x -> expression(x, cancellation);
    };
  }

  /**
   * Runs the statements of a script in order, each after the effects of those before it. A failure
   * does not stop the run; the caller decides what a failure means for the script.
   *
   * @param script the script, as parsed
   * @return the outcomes, one per statement
   */
  public List<Outcome> run(Script script) {
    return run(script, Cancellation.NEVER);
  }

  /**
   * Runs the statements of a script in order, as {@link #run(Script)} does, stopping when {@code
   * cancellation} is requested: the statement it stops and every one after it give {@link
   * Outcome.Cancelled}, and the statements before it keep their effects.
   *
   * @param script the script, as parsed
   * @param cancellation the request to stop
   * @return the outcomes, one per statement
   */
  public List<Outcome> run(Script script, Cancellation cancellation) {
    List<Outcome> out = new ArrayList<>(script.statements().size());
    boolean cancelled = false;
    for (Statement s : script.statements()) {
      Outcome o = cancelled ? new Outcome.Cancelled() : run(s, cancellation);
      cancelled = o instanceof Outcome.Cancelled;
      out.add(o);
    }
    return out;
  }

  private Outcome expression(Statement.Expression x, Cancellation cancellation) {
    return guarded(() -> evaluate(x, cancellation), x.expr());
  }

  private Outcome evaluate(Statement.Expression x, Cancellation cancellation) {
    if (x.expr() instanceof Apply a && a.head().name().equals("assume")) {
      Evaluator.Assumed assumed = Evaluator.assume(a, environment, cancellation);
      environment = environment.withAssumptions(assumed.assumptions());
      return new Outcome.Value(assumed.recorded(), Trace.NONE, x.quiet());
    }
    if (x.expr() instanceof Apply a && a.head().name().equals("clear")) {
      if (!(a.args().getFirst() instanceof Symbol name)) {
        throw new EvaluationException("clear needs a name", a.args().getFirst());
      }
      environment = environment.without(name.name());
      return new Outcome.Value(name, Trace.NONE, x.quiet());
    }
    Evaluator.Result r = Evaluator.evaluate(x.expr(), environment, tracing, cancellation);
    return new Outcome.Value(r.value(), r.trace(), x.quiet(), r.numeric());
  }

  /**
   * Runs a statement's evaluation and turns what it throws into an outcome: an evaluation error is
   * a failure; a cancellation passes on to {@link #run(Statement, Cancellation)}; any other runtime
   * exception, a defect, is an "internal error" failure; and an {@link OutOfMemoryError} is the
   * failure "result too large". The last is a backstop, not the mechanism: the evaluator's size
   * limits refuse a value before it is built, so the heap is never meant to run out; should a path
   * the limits miss exhaust it, the statement's state is unreachable once the error has unwound,
   * and the session goes on.
   *
   * @param body the evaluation
   * @param statement the statement's expression, which a failure carries
   * @return the outcome
   */
  static Outcome guarded(Supplier<Outcome> body, Expr statement) {
    try {
      return body.get();
    } catch (EvaluationException e) {
      return new Outcome.Failed(e);
    } catch (CancelledException e) {
      throw e;
    } catch (RuntimeException e) {
      // A defect, not a property of the input: reported as a failure so that no caller crashes.
      return new Outcome.Failed(
          new EvaluationException(
              "internal error: " + e.getClass().getName() + ": " + e.getMessage(), statement));
    } catch (OutOfMemoryError e) {
      return new Outcome.Failed(new EvaluationException("result too large", statement));
    }
  }
}
