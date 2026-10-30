package mathcanvas.app.document;

import java.util.List;
import java.util.Objects;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.Position;
import mathcanvas.engine.parse.Statement;
import mathcanvas.engine.print.layout.Layout;
import mathcanvas.engine.rule.Trace;

/**
 * What one statement of a cell gave, kept in the cell's output with what it shows, which the worker
 * prints so that no printing runs on the FX thread: the text, and the layout tree the cell
 * typesets, or none when the tree is over the typesetting cap.
 */
public sealed interface Result {

  /**
   * The value of an expression statement.
   *
   * @param value the canonical value
   * @param trace its trace, {@link Trace#NONE} unless the statement asked for steps
   * @param quiet whether the statement was ended by {@code ;}, so the value is not shown
   * @param text the value's display, empty for a quiet statement
   * @param shown the display tree the text and the layout print, whose paths a selection names; the
   *     value itself for a quiet statement
   * @param layout the display's layout tree, or null when the value is quiet or too large to
   *     typeset
   * @param steps the curated narration steps of a traced value, none otherwise (a traced value may
   *     have none, when it rewrote nothing)
   */
  record Value(
      Expr value,
      Trace trace,
      boolean quiet,
      String text,
      Expr shown,
      Layout layout,
      List<Step> steps)
      implements Result {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if an operand is null
     */
    public Value {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(trace, "trace");
      Objects.requireNonNull(text, "text");
      Objects.requireNonNull(shown, "shown");
      steps = List.copyOf(steps);
    }

    /**
     * Whether the value was traced, so it has steps to show (perhaps none).
     *
     * @return true when its trace is not {@link Trace#NONE}
     */
    public boolean traced() {
      return trace != Trace.NONE;
    }
  }

  /**
   * One narration step as a cell shows it, printed on the worker.
   *
   * @param text the step's text
   * @param after the whole expression after the step, as text
   * @param layout its layout tree, or null when it is too large to typeset
   */
  record Step(String text, String after, Layout layout) {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if the text or the after text is null
     */
    public Step {
      Objects.requireNonNull(text, "text");
      Objects.requireNonNull(after, "after");
    }
  }

  /**
   * A definition, shown as its echo.
   *
   * @param echo the definition statement
   * @param text the echo's text
   * @param layout the echo's layout tree, or null when it is too large to typeset
   */
  record Defined(Statement.Definition echo, String text, Layout layout) implements Result {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if an operand is null
     */
    public Defined {
      Objects.requireNonNull(echo, "echo");
      Objects.requireNonNull(text, "text");
    }
  }

  /**
   * A parse or an evaluation error.
   *
   * @param message the engine's message
   * @param at where it is in the cell's text
   * @param line the text of that line when the cell was evaluated, for the caret under it
   */
  record Failed(String message, Position at, String line) implements Result {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if an operand is null
     */
    public Failed {
      Objects.requireNonNull(message, "message");
      Objects.requireNonNull(at, "at");
      Objects.requireNonNull(line, "line");
    }
  }
}
