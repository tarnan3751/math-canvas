package mathcanvas.engine.parse;

import java.util.List;
import java.util.Objects;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;

/**
 * One statement of a script ({@code docs/language.md} §2): a definition or an expression. A quiet
 * statement was ended by {@code ;}, and its result is not printed.
 */
public sealed interface Statement {

  /**
   * Whether the statement was ended by {@code ;}.
   *
   * @return true if its result is not printed
   */
  boolean quiet();

  /**
   * A definition {@code name := body} or {@code name(params) := body} (§8.3).
   *
   * @param name the name defined
   * @param params the parameters, empty for {@code name := body}
   * @param body the right side, as parsed
   * @param quiet whether the statement was ended by {@code ;}
   */
  record Definition(Symbol name, List<Symbol> params, Expr body, boolean quiet)
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
    }
  }

  /**
   * An expression to evaluate.
   *
   * @param expr the expression, as parsed
   * @param quiet whether the statement was ended by {@code ;}
   */
  record Expression(Expr expr, boolean quiet) implements Statement {

    /**
     * Checks the operand.
     *
     * @throws NullPointerException if the expression is null
     */
    public Expression {
      Objects.requireNonNull(expr, "expr");
    }
  }
}
