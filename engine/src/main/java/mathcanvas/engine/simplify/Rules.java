package mathcanvas.engine.simplify;

import java.util.List;
import java.util.function.BiFunction;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;

/** What the rule sets share: templates written in the language and conditions on the facts. */
final class Rules {

  private Rules() {}

  /**
   * Parses a template in the empty environment.
   *
   * @param source the template in the language
   * @return the raw template
   */
  static Expr template(String source) {
    return Parser.parseExpression(source, Names.NONE);
  }

  /**
   * Returns {@code pattern} with the condition that {@code query} answers true of each variable.
   *
   * @param pattern the pattern
   * @param query an assumptions query, such as {@link Assumptions#isNonNegative}
   * @param names the variables
   * @return the pattern
   */
  static Pattern known(Pattern pattern, BiFunction<Assumptions, Expr, Tri> query, String... names) {
    Pattern p = pattern;
    for (String name : names) {
      p = p.where(List.of(name), (b, a) -> query.apply(a, b.get(name)) == Tri.TRUE);
    }
    return p;
  }

  /**
   * The exact rational value of a number node.
   *
   * @param e an expression
   * @return its value when it is an {@code Int} or a {@code Rational}, else null
   */
  static RealNumeric rational(Expr e) {
    return e instanceof Num n && (n.value() instanceof Int || n.value() instanceof Rational)
        ? (RealNumeric) n.value()
        : null;
  }

  /**
   * The value of a positive integer number node.
   *
   * @param e an expression
   * @return its value when it is an {@code Int} above 0, else null
   */
  static Int positiveInteger(Expr e) {
    return e instanceof Num n && n.value() instanceof Int i && i.signum() > 0 ? i : null;
  }

  /**
   * The value of a non-integer rational number node.
   *
   * @param e an expression
   * @return its value when it is a {@code Rational}, else null
   */
  static Rational fraction(Expr e) {
    return e instanceof Num n && n.value() instanceof Rational q ? q : null;
  }

  /**
   * Returns {@code pattern} with the condition that each variable's value commutes ({@link
   * Canonical#commutes}): no vector or matrix, whose arithmetic is the evaluator's, enters an
   * exponent a rule builds.
   *
   * @param pattern the pattern
   * @param names the variables
   * @return the pattern
   */
  static Pattern scalar(Pattern pattern, String... names) {
    Pattern p = pattern;
    for (String name : names) {
      p = p.where(name, Canonical::commutes);
    }
    return p;
  }
}
