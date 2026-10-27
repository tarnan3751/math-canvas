package mathcanvas.engine.print;

import mathcanvas.engine.Cancellation;
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

  /**
   * Prints a canonical value in its display shape, checking {@code cancellation} while the shape is
   * built ({@link Display#of(Expr, Cancellation)}).
   *
   * @param canonical an expression in canonical form
   * @param cancellation the request to stop
   * @return the text
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  default String display(Expr canonical, Cancellation cancellation) {
    return print(Display.of(canonical, cancellation));
  }
}
