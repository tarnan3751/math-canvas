package mathcanvas.app.document;

import java.util.List;
import java.util.Objects;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.Position;
import mathcanvas.engine.parse.Statement;
import mathcanvas.engine.print.Format;
import mathcanvas.engine.print.layout.Layout;
import mathcanvas.engine.rule.Trace;
import mathcanvas.render.typeset.Box;

/**
 * What one statement of a cell gave, kept in the cell's output with what it shows, which the worker
 * prints and typesets so that neither runs on the FX thread: the text, the layout tree and its box
 * tree, or none when the tree is over the typesetting cap.
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
   * @param box the layout typeset on the worker, which the cell shows, or null with the layout
   * @param steps the curated narration steps of a traced value, none otherwise (a traced value may
   *     have none, when it rewrote nothing)
   * @param format the format the text, the layout and the steps were printed in; in a format other
   *     than {@link Format#EXACT}, {@code shown} is the value itself, since the printed tree is the
   *     value prepared for the format
   * @param numeric whether a definite integral in the statement was evaluated by quadrature, which
   *     the cell marks beside the value
   * @param note the note of a general solution's integer parameters, {@code k ∈ ℤ}, shown after the
   *     value ({@code docs/language.md} §14.7); empty when there are none
   * @param noteBoxes each relation of the note typeset on the worker, as the value is; none when
   *     the note is empty or a relation is too large to typeset
   * @param spoken the value as the ASCII printer prints it in the format, which assistive
   *     technology reads; empty for a quiet statement
   */
  record Value(
      Expr value,
      Trace trace,
      boolean quiet,
      String text,
      Expr shown,
      Layout layout,
      Box box,
      List<Step> steps,
      Format format,
      boolean numeric,
      String note,
      List<Box> noteBoxes,
      String spoken)
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
      Objects.requireNonNull(format, "format");
      Objects.requireNonNull(note, "note");
      noteBoxes = List.copyOf(noteBoxes);
      Objects.requireNonNull(spoken, "spoken");
    }

    /**
     * Whether the value was traced, so it has steps to show (perhaps none).
     *
     * @return true when its trace is not {@link Trace#NONE}, or its program narrated its run
     */
    public boolean traced() {
      return trace != Trace.NONE || !steps.isEmpty();
    }
  }

  /**
   * A value a statement's program printed ({@code docs/language.md} §19.2), shown above the
   * statement's own result.
   *
   * @param shown the display tree the text and the layout print, whose paths a selection names, in
   *     the exact format; the value itself in another
   * @param text the value's display
   * @param layout the display's layout tree, or null when it is too large to typeset
   * @param box the layout typeset on the worker, or null with the layout
   * @param format the format it was printed in
   */
  record Printed(Expr shown, String text, Layout layout, Box box, Format format) implements Result {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if an operand is null
     */
    public Printed {
      Objects.requireNonNull(shown, "shown");
      Objects.requireNonNull(text, "text");
      Objects.requireNonNull(format, "format");
    }
  }

  /**
   * A statement that ran and has no value ({@code docs/language.md} §19): a loop, an if whose
   * branch has none, a call of a function that ended with none. It shows only the narration of its
   * run, when it was traced.
   *
   * @param steps the narration steps, none when it was not traced
   */
  record Ran(List<Step> steps) implements Result {

    /** Copies the steps. */
    public Ran {
      steps = List.copyOf(steps);
    }
  }

  /**
   * A function defined with {@code function}, shown as the echo of its head, {@code function
   * fact(n)} ({@code docs/language.md} §19.4).
   *
   * @param echo the definition statement
   * @param text the echo's text
   * @param layout the echo's layout tree, or null when it is too large to typeset
   * @param box the layout typeset on the worker, or null with the layout
   */
  record Function(Statement.FunctionDefinition echo, String text, Layout layout, Box box)
      implements Result {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if an operand is null
     */
    public Function {
      Objects.requireNonNull(echo, "echo");
      Objects.requireNonNull(text, "text");
    }
  }

  /**
   * One narration step as a cell shows it, printed on the worker.
   *
   * @param text the step's text
   * @param after the whole expression after the step, as text
   * @param layout its layout tree, or null when it is too large to typeset
   * @param box the layout typeset on the worker, or null with the layout
   */
  record Step(String text, String after, Layout layout, Box box) {

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
   * @param box the layout typeset on the worker, or null with the layout
   */
  record Defined(Statement.Definition echo, String text, Layout layout, Box box) implements Result {

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
