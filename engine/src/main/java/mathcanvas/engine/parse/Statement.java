package mathcanvas.engine.parse;

import java.util.List;
import java.util.Objects;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.script.Program;
import mathcanvas.engine.script.Stmt;

/**
 * One statement of a script ({@code docs/language.md} §2, §19): a definition, an expression, a
 * control statement ({@code if}, {@code for}, {@code while}) or a function defined with {@code
 * function}. A quiet statement was ended by {@code ;}, and its result is not printed. Its start is
 * the position of its first token.
 */
public sealed interface Statement {

  /**
   * Whether the statement was ended by {@code ;}.
   *
   * @return true if its result is not printed
   */
  boolean quiet();

  /**
   * Where the statement begins.
   *
   * @return the position of its first token
   */
  Position start();

  /**
   * A definition {@code name := body} or {@code name(params) := body} (§8.3).
   *
   * @param name the name defined
   * @param params the parameters, empty for {@code name := body}
   * @param body the right side, as parsed
   * @param quiet whether the statement was ended by {@code ;}
   * @param start the position of its first token
   */
  record Definition(Symbol name, List<Symbol> params, Expr body, boolean quiet, Position start)
      implements Statement {

    /**
     * Checks and copies the operands.
     *
     * @throws NullPointerException if an operand is null
     */
    public Definition {
      Objects.requireNonNull(name, "name");
      params = List.copyOf(params);
      Objects.requireNonNull(body, "body");
      Objects.requireNonNull(start, "start");
    }
  }

  /**
   * An expression to evaluate.
   *
   * @param expr the expression, as parsed
   * @param quiet whether the statement was ended by {@code ;}
   * @param start the position of its first token
   */
  record Expression(Expr expr, boolean quiet, Position start) implements Statement {

    /**
     * Checks the operands.
     *
     * @throws NullPointerException if the expression or the start is null
     */
    public Expression {
      Objects.requireNonNull(expr, "expr");
      Objects.requireNonNull(start, "start");
    }
  }

  /**
   * A control statement at the top level, {@code if}, {@code for} or {@code while} (§19): run in
   * the document's scope.
   *
   * @param statement the statement
   * @param quiet whether the statement was ended by {@code ;}
   * @param start the position of its first token
   */
  record Block(Stmt statement, boolean quiet, Position start) implements Statement {

    /**
     * Checks the operands.
     *
     * @throws NullPointerException if an operand is null
     */
    public Block {
      Objects.requireNonNull(statement, "statement");
      Objects.requireNonNull(start, "start");
    }
  }

  /**
   * A function defined with {@code function name(params) … end} (§19).
   *
   * @param name the name defined
   * @param params the parameters, distinct, possibly none
   * @param body the statements of its body
   * @param quiet whether the statement was ended by {@code ;}
   * @param start the position of its first token
   */
  record FunctionDefinition(
      Symbol name, List<Symbol> params, Program body, boolean quiet, Position start)
      implements Statement {

    /**
     * Checks and copies the operands.
     *
     * @throws NullPointerException if an operand is null
     */
    public FunctionDefinition {
      Objects.requireNonNull(name, "name");
      params = List.copyOf(params);
      Objects.requireNonNull(body, "body");
      Objects.requireNonNull(start, "start");
    }
  }
}
