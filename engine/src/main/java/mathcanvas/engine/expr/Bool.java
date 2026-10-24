package mathcanvas.engine.expr;

/**
 * A truth value, the result of a decided relation; {@code true} and {@code false} in the language.
 *
 * @param value the truth value
 */
public record Bool(boolean value) implements Expr {

  /** True. */
  public static final Bool TRUE = new Bool(true);

  /** False. */
  public static final Bool FALSE = new Bool(false);

  /**
   * Returns the truth value {@code value}.
   *
   * @param value the value
   * @return the node
   */
  public static Bool of(boolean value) {
    return new Bool(value);
  }

  @Override
  public String toString() {
    return "Bool " + value;
  }
}
