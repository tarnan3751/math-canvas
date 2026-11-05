package mathcanvas.engine.testing;

import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;

/** The expressions the tests write as text, read in the empty environment. */
public final class Inputs {

  private Inputs() {}

  /**
   * Parses an expression, raw: not canonicalised.
   *
   * @param text the source
   * @return the tree the parser builds
   */
  public static Expr parse(String text) {
    return Parser.parseExpression(text, Names.NONE);
  }

  /**
   * Parses an expression and puts it in canonical form.
   *
   * @param text the source
   * @return the canonical expression
   */
  public static Expr canonical(String text) {
    return Canonical.of(parse(text));
  }

  /**
   * A symbol, by name: a pattern variable when the name ends in {@code _}.
   *
   * @param name the name
   * @return the symbol
   */
  public static Symbol v(String name) {
    return Symbol.of(name);
  }
}
