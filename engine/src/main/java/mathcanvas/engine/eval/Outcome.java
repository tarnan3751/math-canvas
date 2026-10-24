package mathcanvas.engine.eval;

import java.util.Objects;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.Statement;
import mathcanvas.engine.rule.Trace;

/**
 * What running one statement gave: a value, a definition's echo, or an evaluation error. The CLI
 * and the worksheet print it; the engine prints nothing.
 */
public sealed interface Outcome {

  /**
   * The value of an expression statement.
   *
   * @param value the canonical value
   * @param trace its trace, {@link Trace#NONE} unless the statement was {@code steps(…)}
   * @param quiet whether the statement was ended by {@code ;}, so the value is not printed
   */
  record Value(Expr value, Trace trace, boolean quiet) implements Outcome {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if the value or the trace is null
     */
    public Value {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(trace, "trace");
    }
  }

  /**
   * A definition, which echoes itself ({@code docs/language.md} §14.3).
   *
   * @param echo the definition statement
   */
  record Defined(Statement.Definition echo) implements Outcome {

    /**
     * Checks the field.
     *
     * @throws NullPointerException if the echo is null
     */
    public Defined {
      Objects.requireNonNull(echo, "echo");
    }
  }

  /**
   * An evaluation error; the environment is left as it was.
   *
   * @param error the error
   */
  record Failed(EvaluationException error) implements Outcome {

    /**
     * Checks the field.
     *
     * @throws NullPointerException if the error is null
     */
    public Failed {
      Objects.requireNonNull(error, "error");
    }
  }
}
