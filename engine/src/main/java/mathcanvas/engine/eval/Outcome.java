package mathcanvas.engine.eval;

import java.util.Objects;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.parse.Statement;
import mathcanvas.engine.rule.Trace;

/**
 * What running one statement gave: a value, a definition's echo, a statement that ran with no
 * value, an evaluation error, or a cancellation. The CLI and the worksheet print it; the engine
 * prints nothing.
 */
public sealed interface Outcome {

  /**
   * The value of an expression statement.
   *
   * @param value the canonical value
   * @param trace its trace, {@link Trace#NONE} unless the statement was {@code steps(…)}
   * @param quiet whether the statement was ended by {@code ;}, so the value is not printed
   * @param numeric whether a definite integral in the statement was evaluated by quadrature, or
   *     eigenvalues were found only numerically, so the value holds a number no closed form gave,
   *     which the worksheet marks ({@code docs/language.md} §10.2, §14.16)
   * @param parameters the integer parameters of the general solutions in the value and its
   *     constants of integration, as a canonical set of relations {@code k ∈ ℤ} and {@code C ∈ ℝ},
   *     printed after it as its note; empty when there are none ({@code docs/language.md} §14.7)
   */
  record Value(Expr value, Trace trace, boolean quiet, boolean numeric, SetExpr parameters)
      implements Outcome {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public Value {
      Objects.requireNonNull(value, "value");
      Objects.requireNonNull(trace, "trace");
      Objects.requireNonNull(parameters, "parameters");
    }

    /**
     * A value with no general solution in it.
     *
     * @param value the canonical value
     * @param trace its trace
     * @param quiet whether the statement was ended by {@code ;}
     * @param numeric whether a value in it was computed numerically
     */
    public Value(Expr value, Trace trace, boolean quiet, boolean numeric) {
      this(value, trace, quiet, numeric, Evaluator.NO_PARAMETERS);
    }

    /**
     * A value no quadrature gave.
     *
     * @param value the canonical value
     * @param trace its trace
     * @param quiet whether the statement was ended by {@code ;}
     */
    public Value(Expr value, Trace trace, boolean quiet) {
      this(value, trace, quiet, false);
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
   * A function defined with {@code function}, which echoes its head ({@code docs/language.md} §19).
   *
   * @param echo the definition statement
   */
  record DefinedFunction(Statement.FunctionDefinition echo) implements Outcome {

    /**
     * Checks the field.
     *
     * @throws NullPointerException if the echo is null
     */
    public DefinedFunction {
      Objects.requireNonNull(echo, "echo");
    }
  }

  /**
   * A statement that ran and has no value: a loop, an if whose branch has none, a call of a
   * function that ended with none ({@code docs/language.md} §19).
   *
   * @param quiet whether the statement was ended by {@code ;}
   */
  record Executed(boolean quiet) implements Outcome {}

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

  /**
   * The statement was cancelled before it finished ({@link mathcanvas.engine.Cancellation}); the
   * environment is as it was before the statement.
   */
  record Cancelled() implements Outcome {}
}
