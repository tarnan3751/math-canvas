package mathcanvas.engine.expr;

import java.util.List;
import java.util.Objects;

/**
 * An anonymous function {@code (params) -> body}. Equality is structural: {@code x -> x} and {@code
 * y -> y} differ.
 *
 * @param params the parameters, at least one, distinct
 * @param body the body
 */
public record Lambda(List<Symbol> params, Expr body) implements Expr {

  /**
   * Checks and copies the operands.
   *
   * @throws NullPointerException if a parameter or the body is null
   * @throws IllegalArgumentException if there is no parameter or two are equal
   */
  public Lambda {
    params = List.copyOf(params);
    Objects.requireNonNull(body, "body");
    if (params.isEmpty()) {
      throw new IllegalArgumentException("a lambda needs a parameter");
    }
    if (params.stream().distinct().count() != params.size()) {
      throw new IllegalArgumentException("lambda parameters must be distinct: " + params);
    }
  }

  /**
   * Returns the lambda {@code param -> body}.
   *
   * @param param the parameter
   * @param body the body
   * @return the lambda
   */
  public static Lambda of(Symbol param, Expr body) {
    return new Lambda(List.of(param), body);
  }

  /**
   * Returns the lambda {@code (params) -> body}.
   *
   * @param params the parameters, at least one, distinct
   * @param body the body
   * @return the lambda
   */
  public static Lambda of(List<Symbol> params, Expr body) {
    return new Lambda(params, body);
  }

  @Override
  public String toString() {
    return Debug.of("Lambda", params.stream().map(Symbol::name).toList(), body);
  }
}
