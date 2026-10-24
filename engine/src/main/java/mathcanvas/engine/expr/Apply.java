package mathcanvas.engine.expr;

import java.util.List;
import java.util.Objects;

/**
 * A function or command application, builtin or user-defined; functions and commands share one
 * namespace, so the head is a symbol. The canonical form never evaluates it.
 *
 * @param head the function's name
 * @param args the arguments, possibly none
 */
public record Apply(Symbol head, List<Expr> args) implements Expr {

  /**
   * Checks and copies the operands.
   *
   * @throws NullPointerException if the head or an argument is null
   */
  public Apply {
    Objects.requireNonNull(head, "head");
    args = List.copyOf(args);
  }

  /**
   * Returns the raw application {@code head(args)}.
   *
   * @param head the function's name
   * @param args the arguments
   * @return the application
   */
  public static Apply of(Symbol head, Expr... args) {
    return new Apply(head, List.of(args));
  }

  /**
   * Returns the raw application {@code head(args)}.
   *
   * @param head the function's name
   * @param args the arguments
   * @return the application
   */
  public static Apply of(Symbol head, List<? extends Expr> args) {
    return new Apply(head, List.copyOf(args));
  }

  @Override
  public String toString() {
    return Debug.of("Apply", head.name(), args);
  }
}
