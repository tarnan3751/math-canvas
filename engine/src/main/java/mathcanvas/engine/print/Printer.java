package mathcanvas.engine.print;

import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.parse.Statement;

/**
 * A text printer ({@code docs/language.md} §15). {@link #print} is faithful: it prints a tree as it
 * holds its operands, so a raw parse is echoed as typed. {@link #display} prints a canonical value
 * in its conventional shape, through {@link Display#of}. {@link #echo} prints a definition as the
 * worksheet and the CLI echo it. {@link #withFormat} gives a printer that writes numbers in another
 * {@link Format}.
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
    return display(canonical, Cancellation.NEVER);
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
    return print(Display.of(NumberFormat.prepare(canonical, format(), cancellation), cancellation));
  }

  /**
   * Returns how this printer writes numbers.
   *
   * @return the format; {@link Format#EXACT} unless the printer was made with another
   */
  default Format format() {
    return Format.EXACT;
  }

  /**
   * Returns a printer like this one that writes numbers in a format, in {@code print} and {@code
   * display} alike. The format changes how numbers are written, never the value printed.
   *
   * @param format the format
   * @return the printer
   */
  Printer withFormat(Format format);

  /**
   * Prints a definition as it echoes ({@code docs/language.md} §14.3): its head and its body as
   * parsed, joined by {@code :=}, as in {@code f(x) := x² + 1} or {@code g := t → t²}.
   *
   * @param definition the definition statement
   * @return the text
   */
  default String echo(Statement.Definition definition) {
    Expr head =
        definition.params().isEmpty()
            ? definition.name()
            : Apply.of(definition.name(), definition.params());
    return print(head) + " := " + print(definition.body());
  }
}
