package mathcanvas.engine.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mathcanvas.engine.expr.Apply;
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
 * {@link #setPrecision} and {@link #setAngleMode}, which the language has no syntax for.
 *
 * <p>The session is the one mutable object of evaluation and is not thread-safe: a caller that
 * evaluates on a worker serialises its access. It does no I/O; the caller parses each statement
 * with {@link #names()} and prints the outcomes. Nothing but an outcome leaves {@link #run}: any
 * other runtime exception, a defect, becomes a failure whose message starts with "internal error".
 */
public final class Session {

  private Environment environment;

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
   * Runs one statement.
   *
   * @param statement the statement, as parsed
   * @return its outcome; on an evaluation error the environment is unchanged
   */
  public Outcome run(Statement statement) {
    return switch (statement) {
      case Statement.Definition d -> {
        Definition definition =
            d.params().isEmpty()
                ? new Definition.Value(d.body())
                : new Definition.Function(d.params(), d.body());
        environment = environment.with(d.name().name(), definition);
        yield new Outcome.Defined(d);
      }
      case Statement.Expression x -> expression(x);
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
    List<Outcome> out = new ArrayList<>(script.statements().size());
    for (Statement s : script.statements()) {
      out.add(run(s));
    }
    return out;
  }

  private Outcome expression(Statement.Expression x) {
    try {
      if (x.expr() instanceof Apply a && a.head().name().equals("assume")) {
        Evaluator.Assumed assumed = Evaluator.assume(a, environment);
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
      Evaluator.Result r = Evaluator.evaluate(x.expr(), environment, false);
      return new Outcome.Value(r.value(), r.trace(), x.quiet());
    } catch (EvaluationException e) {
      return new Outcome.Failed(e);
    } catch (RuntimeException e) {
      // A defect, not a property of the input: reported as a failure so that no caller crashes.
      return new Outcome.Failed(
          new EvaluationException(
              "internal error: " + e.getClass().getName() + ": " + e.getMessage(), x.expr()));
    }
  }
}
