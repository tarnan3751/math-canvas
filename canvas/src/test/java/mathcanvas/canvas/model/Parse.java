package mathcanvas.canvas.model;

import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;

/** Expressions for the tests, parsed as typed. */
final class Parse {

  private Parse() {}

  static Expr expr(String text) {
    return Parser.parseExpression(text, Names.NONE);
  }

  static Relation relation(String text) {
    return (Relation) expr(text);
  }

  static Interval interval(String lower, String upper) {
    return Interval.of(expr(lower), expr(upper), true, true);
  }
}
