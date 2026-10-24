package mathcanvas.engine.print;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Piecewise;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.parse.Brackets;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.ParseException;
import mathcanvas.engine.parse.Parser;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Example;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * The round trip of {@code docs/language.md} §15.4. A canonical value displayed by the ASCII or the
 * Unicode printer reads back, after canonicalisation, as itself; a raw tree printed faithfully
 * reads back as the tree the parser builds for its text ({@link TypedShape}). The generated
 * function heads {@code f} and {@code g} are user functions, so their applications read back as
 * calls.
 *
 * <p>The exceptions, which §15.4 lists and {@link #expressible} filters: intervals and piecewise
 * expressions (no syntax yet); a list that the bracket rule would read as a vector or a matrix, and
 * a vector or matrix with an element that is not scalar. Every canonical sample is first checked to
 * be a fixed point of the canonical form, since the round trip is stated for canonical values.
 */
class RoundTripProperties {

  private static final Names FUNCTIONS = Set.of("f", "g")::contains;

  @Provide
  Arbitrary<Expr> canonical() {
    return ExprArbitraries.exprs().map(Canonical::of).filter(RoundTripProperties::expressible);
  }

  @Provide
  Arbitrary<Expr> exactCanonical() {
    return ExprArbitraries.exactExprs().map(Canonical::of);
  }

  @Provide
  Arbitrary<Expr> raw() {
    return ExprArbitraries.exprs().filter(RoundTripProperties::expressible);
  }

  private static void displayed(Printer printer, Expr c) {
    assertThat(Canonical.of(c)).as("a fixed point of the canonical form").isEqualTo(c);
    String text = printer.display(c);
    assertThat(Canonical.of(parse(text))).as(text).isEqualTo(c);
  }

  private static void printed(Printer printer, Expr e) {
    String text = printer.print(e);
    assertThat(parse(text)).as(text).isEqualTo(TypedShape.of(e));
  }

  /** Parses printed text; a failure to parse names the text. */
  private static Expr parse(String text) {
    try {
      return Parser.parseExpression(text, FUNCTIONS);
    } catch (ParseException e) {
      throw new AssertionError(e + " in " + text, e);
    }
  }

  @Example
  void knownCases() {
    // An integer-valued decimal of 22 digits keeps its point, as an exponent, and reads back
    // inexact.
    Expr big = Canonical.of(Power.of(Num.of(Real.of("2.0")), Num.of(70)));
    displayed(AsciiPrinter.INSTANCE, big);
    displayed(UnicodePrinter.INSTANCE, big);
    // A symbol left in limit's fourth argument is not the side of a Limit.
    Expr limit =
        Apply.of(Symbol.of("limit"), Symbol.of("x"), Symbol.of("x"), Num.ZERO, Symbol.of("left"));
    printed(AsciiPrinter.INSTANCE, limit);
    printed(UnicodePrinter.INSTANCE, limit);
  }

  @Property(tries = 2000)
  void asciiDisplayReadsBack(@ForAll("canonical") Expr c) {
    displayed(AsciiPrinter.INSTANCE, c);
  }

  @Property(tries = 2000)
  void unicodeDisplayReadsBack(@ForAll("canonical") Expr c) {
    displayed(UnicodePrinter.INSTANCE, c);
  }

  @Property(tries = 2000)
  void exactValuesReadBack(@ForAll("exactCanonical") Expr c) {
    displayed(AsciiPrinter.INSTANCE, c);
    displayed(UnicodePrinter.INSTANCE, c);
  }

  @Property(tries = 2000)
  void asciiPrintReadsBackAsTyped(@ForAll("raw") Expr e) {
    printed(AsciiPrinter.INSTANCE, e);
  }

  @Property(tries = 2000)
  void unicodePrintReadsBackAsTyped(@ForAll("raw") Expr e) {
    printed(UnicodePrinter.INSTANCE, e);
  }

  @Property(tries = 1000)
  void latexPrintsEveryTree(@ForAll("raw") Expr e) {
    assertThat(LatexPrinter.INSTANCE.print(e)).isNotEmpty();
    assertThat(LatexPrinter.INSTANCE.display(Canonical.of(e))).isNotEmpty();
  }

  /** Whether the grammar can write the tree (§15.4); see the class comment. */
  static boolean expressible(Expr e) {
    return switch (e) {
      case Interval i -> false;
      case Piecewise p -> false;
      case Num n -> true;
      case Constant c -> true;
      case Bool b -> true;
      case Symbol s -> true;
      case Sum s -> all(s.terms());
      case Product p -> all(p.factors());
      case Power p -> expressible(p.base()) && expressible(p.exponent());
      case Apply a -> all(a.args());
      case Lambda l -> expressible(l.body());
      case Relation r -> expressible(r.left()) && expressible(r.right());
      case ListExpr l -> Brackets.of(l.items()) instanceof ListExpr && all(l.items());
      case Vector v -> Brackets.allScalar(v.items()) && all(v.items());
      case Matrix m -> m.rows().stream().allMatch(row -> Brackets.allScalar(row) && all(row));
      case SetExpr s -> all(s.items());
      case Derivative d -> expressible(d.expr());
      case Integral i ->
          expressible(i.integrand())
              && (!i.isDefinite() || (expressible(i.lower()) && expressible(i.upper())));
      case Limit l -> expressible(l.expr()) && expressible(l.point());
      case BigSum s -> expressible(s.body()) && expressible(s.from()) && expressible(s.to());
      case BigProduct s -> expressible(s.body()) && expressible(s.from()) && expressible(s.to());
    };
  }

  private static boolean all(List<Expr> items) {
    return items.stream().allMatch(RoundTripProperties::expressible);
  }
}
