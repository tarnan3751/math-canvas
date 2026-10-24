package mathcanvas.engine.expr;

import java.util.Objects;

/**
 * A variable or a function head. Its assumptions and bindings live outside the node.
 *
 * @param name the name, as the lexer normalised it ({@code x₁} is {@code x_1})
 */
public record Symbol(String name) implements Expr {

  /**
   * Checks the name.
   *
   * @throws NullPointerException if {@code name} is null
   * @throws IllegalArgumentException if {@code name} is empty
   */
  public Symbol {
    Objects.requireNonNull(name, "name");
    if (name.isEmpty()) {
      throw new IllegalArgumentException("a symbol needs a name");
    }
  }

  /**
   * Returns the symbol {@code name}.
   *
   * @param name the name
   * @return the symbol
   */
  public static Symbol of(String name) {
    return new Symbol(name);
  }

  @Override
  public String toString() {
    return "Symbol " + name;
  }
}
