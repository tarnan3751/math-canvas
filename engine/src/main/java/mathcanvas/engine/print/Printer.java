package mathcanvas.engine.print;

import mathcanvas.engine.expr.Expr;

/**
 * A text printer ({@code docs/language.md} §15). {@link #print} is faithful: it prints a tree as it
 * holds its operands, so a raw parse is echoed as typed. {@link #display} prints a canonical value
 * in its conventional shape, through {@link Display#of}.
 */
public interface Printer {

  /**
   * Prints a tree as it holds its operands.
   *
   * @param e the expression
   * @return the text
   */
  String print(Expr e);

  /**
   * Prints a canonical value in its display shape: sums in reverse canonical order, quotients and
   * differences reconstructed, factors in display groups.
   *
   * @param canonical an expression in canonical form
   * @return the text
   */
  default String display(Expr canonical) {
    return print(Display.of(canonical));
  }
}
