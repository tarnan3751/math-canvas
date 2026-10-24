package mathcanvas.engine.print;

import mathcanvas.engine.expr.Expr;

/**
 * The ASCII printer (`docs/language.md` §15.1): pure ASCII in every operator, constant and number;
 * notation in command form and `*` for every multiplication. Its output parses back.
 */
public final class AsciiPrinter implements Printer {

  /** The printer. */
  public static final AsciiPrinter INSTANCE = new AsciiPrinter();

  private AsciiPrinter() {}

  @Override
  public String print(Expr e) {
    return InfixPrinter.ASCII.print(e);
  }
}
