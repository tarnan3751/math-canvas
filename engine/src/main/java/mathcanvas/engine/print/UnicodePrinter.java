package mathcanvas.engine.print;

import mathcanvas.engine.expr.Expr;

/**
 * The Unicode printer (`docs/language.md` §15.1–§15.3): pretty text that parses back, with
 * notation, superscript powers, subscripted names, `·` or juxtaposition between factors, and the
 * ASCII hyphen-minus for minus.
 */
public final class UnicodePrinter implements Printer {

  /** The printer. */
  public static final UnicodePrinter INSTANCE = new UnicodePrinter();

  private UnicodePrinter() {}

  @Override
  public String print(Expr e) {
    return InfixPrinter.UNICODE.print(e);
  }
}
