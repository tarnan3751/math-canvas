package mathcanvas.engine.eval;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;

/**
 * What a name is bound to ({@code docs/language.md} §14.3). Both kinds hold the right side as
 * parsed: definitions are delayed, evaluated at each use.
 */
public sealed interface Definition {

  /**
   * The right side, as parsed.
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
}
