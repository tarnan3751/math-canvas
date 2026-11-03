package mathcanvas.engine.eval;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.script.Program;

/**
 * What a name is bound to ({@code docs/language.md} §14.3, §19). A value and a function hold the
 * right side as parsed: definitions are delayed, evaluated at each use. A function defined with
 * {@code function} holds its body's statements, and a value a program assigned holds the value it
 * computed, which is never evaluated again.
 */
public sealed interface Definition {

  /**
   * The right side, as parsed; for a function with statements, every expression of its body as one
   * list, and for a computed value, the value: what an analysis of the names a definition reads
   * walks.
   *
   * @return the body
   */
  Expr body();

  /**
   * A value binding, {@code a := body}.
   *
   * @param body the right side, as parsed
   */
  record Value(Expr body) implements Definition {

    /**
     * Checks the body.
     *
     * @throws NullPointerException if the body is null
     */
    public Value {
      Objects.requireNonNull(body, "body");
    }
  }

  /**
   * A function, {@code f(params) := body} or {@code f := params -> body}.
   *
   * @param params the parameters, distinct, at least one
   * @param body the right side, as parsed
   */
  record Function(List<Symbol> params, Expr body) implements Definition {

    /**
     * Checks and copies the operands.
     *
     * @throws NullPointerException if an operand is null
     * @throws IllegalArgumentException if there is no parameter or two are equal
     */
    public Function {
      params = List.copyOf(params);
      Objects.requireNonNull(body, "body");
      if (params.isEmpty()) {
        throw new IllegalArgumentException("a function needs a parameter");
      }
      Set<String> names = new HashSet<>();
      for (Symbol p : params) {
        if (!names.add(p.name())) {
          throw new IllegalArgumentException("parameter " + p.name() + " appears twice");
        }
      }
    }
  }

  /**
   * A function defined with {@code function f(params) … end} (§19): its body is statements, run at
   * each call by the interpreter.
   *
   * @param params the parameters, distinct, possibly none
   * @param program the body
   */
  record Procedure(List<Symbol> params, Program program) implements Definition {

    /**
     * Checks and copies the operands.
     *
     * @throws NullPointerException if an operand is null
     * @throws IllegalArgumentException if two parameters are equal
     */
    public Procedure {
      params = List.copyOf(params);
      Objects.requireNonNull(program, "program");
      Set<String> names = new HashSet<>();
      for (Symbol p : params) {
        if (!names.add(p.name())) {
          throw new IllegalArgumentException("parameter " + p.name() + " appears twice");
        }
      }
    }

    @Override
    public Expr body() {
      return program.expressions();
    }
  }

  /**
   * A value a program assigned in the document (§19): computed when it was assigned, canonical, and
   * taken as it is at each use, as a function's parameter is, never evaluated again.
   *
   * @param value the value
   */
  record Computed(Expr value) implements Definition {

    /**
     * Checks the value.
     *
     * @throws NullPointerException if the value is null
     */
    public Computed {
      Objects.requireNonNull(value, "value");
    }

    @Override
    public Expr body() {
      return value;
    }
  }
}
