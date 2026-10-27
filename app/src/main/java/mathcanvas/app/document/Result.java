package mathcanvas.app.document;

import java.util.Objects;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.Position;
import mathcanvas.engine.parse.Statement;
import mathcanvas.engine.rule.Trace;

/** What one statement of a cell gave, kept in the cell's output. */
public sealed interface Result {

  /**
   * The value of an expression statement.
   *
   * @param value the canonical value
   * @param trace its trace, {@link Trace#NONE} unless the statement asked for steps
   * @param quiet whether the statement was ended by {@code ;}, so the value is not shown
   */
  record Value(Expr value, Trace trace, boolean quiet) implements Result {

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
   * A definition, shown as its echo.
   *
   * @param echo the definition statement
   */
  record Defined(Statement.Definition echo) implements Result {

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
   * A parse or an evaluation error.
   *
   * @param message the engine's message
   * @param at where it is in the cell's text
   */
  record Failed(String message, Position at) implements Result {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if the message or the position is null
     */
    public Failed {
      Objects.requireNonNull(message, "message");
      Objects.requireNonNull(at, "at");
    }
  }
}
