package mathcanvas.engine.print;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.layout.BigOp;
import mathcanvas.engine.print.layout.Cls;
import mathcanvas.engine.print.layout.Delimited;
import mathcanvas.engine.print.layout.Fraction;
import mathcanvas.engine.print.layout.Glyph;
import mathcanvas.engine.print.layout.Layout;
import mathcanvas.engine.print.layout.Matrix;
import mathcanvas.engine.print.layout.Piecewise;
import mathcanvas.engine.print.layout.Radical;
import mathcanvas.engine.print.layout.Row;
import mathcanvas.engine.print.layout.Scripts;
import mathcanvas.engine.print.layout.Scripts.Form;
import mathcanvas.engine.print.layout.Space;
import mathcanvas.engine.print.layout.Style;
import mathcanvas.engine.print.layout.Text;
import mathcanvas.engine.rule.Path;
import org.junit.jupiter.api.Test;

/** The layout tree of each construct, its paths, and its linear text. */
class LayoutPrinterTest {

  private static final Space SP = new Space(Space.Kind.LINEAR);

  private static Expr parse(String text) {
    return Parser.parseExpression(text, Set.of("f")::contains);
  }

  private static Layout print(String text) {
    Expr e = parse(text);
    Layout layout = LayoutPrinter.INSTANCE.print(e);
    assertThat(LayoutPrinter.flatten(layout)).isEqualTo(UnicodePrinter.INSTANCE.print(e));
    return layout;
  }

  private static Layout display(String text) {
    Expr c = Canonical.of(parse(text));
    Layout layout = LayoutPrinter.INSTANCE.display(c);
    assertThat(LayoutPrinter.flatten(layout)).isEqualTo(UnicodePrinter.INSTANCE.display(c));
    return layout;
  }

  private static Path p(Integer... indices) {
    return new Path(Arrays.asList(indices));
  }

  private static Glyph italic(char c, Path path) {
    return new Glyph(c, Style.ITALIC, Cls.ORD, path);
  }

  private static Glyph op(char c, Cls cls) {
    return new Glyph(c, Style.UPRIGHT, cls, null);
  }

  private static Text number(String digits, Path path) {
    return new Text(digits, Style.UPRIGHT, Cls.ORD, path);
  }

  @Test
  void aSumIsARowOfItsTermsWithBinarySigns() {
    assertThat(print("x^2 + 1"))
        .isEqualTo(
            new Row(
                List.of(
                    new Scripts(
                        italic('x', p(0, 0)),
                        null,
                        number("2", p(0, 1)),
                        null,
                        Form.CHARACTERS,
                        p(0)),
                    SP,
                    op('+', Cls.BIN),
                    SP,
                    number("1", p(1))),
                p()));
    // A subtraction is a row of its sign and operand carrying the negated term's path.
    Row difference = (Row) print("a - b");
    assertThat(difference.items().get(2))
        .isEqualTo(new Row(List.of(op('-', Cls.BIN), SP, italic('b', p(1, 1))), p(1)));
  }

  @Test
  void aQuotientIsAFractionWithoutTheParenthesesOfItsLinearForm() {
    assertThat(display("x/(2*pi)"))
        .isEqualTo(
            new Fraction(
                italic('x', p(0)),
                new Row(List.of(number("2", p(1, 0, 0)), italic('π', p(1, 0, 1))), p(1, 0)),
                false,
                true,
                false,
                p()));
    Fraction sum = (Fraction) print("(x + 1)/2");
    assertThat(sum.numWrapped()).isTrue();
    assertThat(sum.num()).isInstanceOf(Row.class);
    // a/b/c reads ((a/b)/c), as the parser reads it.
    Fraction nested = (Fraction) print("a/b/c");
    assertThat(nested.num()).isInstanceOf(Fraction.class);
    assertThat(nested.den()).isEqualTo(italic('c', p(2, 0)));
    // The derivative-like quotient keeps its spaced slash in the linear form.
    assertThat(((Fraction) print("d / dx")).spaced()).isTrue();
  }

  @Test
  void aLeadingSignStandsBeforeTheBar() {
    assertThat(display("-x/2"))
        .isEqualTo(
            new Row(
                List.of(
                    op('-', Cls.BIN),
                    new Fraction(
                        italic('x', p(0, 1)), number("2", p(1, 0)), false, false, false, null)),
                p()));
    Row reciprocal = (Row) display("-1/x");
    assertThat(reciprocal.items().get(1)).isInstanceOf(Fraction.class);
    Row wrapped = (Row) print("-(a + b)/2");
    assertThat(((Fraction) wrapped.items().get(1)).numWrapped()).isTrue();
  }

  @Test
  void powersAndRadicalsTakeTheirScriptsAndRadicandsUnparenthesised() {
    assertThat(print("e^(x^2)"))
        .isEqualTo(
            new Scripts(
                italic('e', p(0)),
                null,
                new Scripts(
                    italic('x', p(1, 0)), null, number("2", p(1, 1)), null, Form.CHARACTERS, p(1)),
                null,
                Form.CARET_WRAPPED,
                p()));
    assertThat(print("sqrt(x + 1)"))
        .isEqualTo(
            new Radical(
                null,
                new Row(
                    List.of(italic('x', p(0, 0)), SP, op('+', Cls.BIN), SP, number("1", p(0, 1))),
                    p(0)),
                true,
                p()));
    assertThat(print("cbrt(x)"))
        .isEqualTo(new Radical(number("3", null), italic('x', p(0)), false, p()));
  }

  @Test
  void namesFollowTheItalicAndSubscriptRules() {
    assertThat(print("x_1"))
        .isEqualTo(
            new Scripts(
                italic('x', null),
                new Glyph('1', Style.UPRIGHT, Cls.ORD, null),
                null,
                Form.CHARACTERS,
                null,
                p()));
    assertThat(((Scripts) print("x_ab")).subForm()).isEqualTo(Form.UNDERSCORE);
    assertThat(print("xy")).isEqualTo(new Text("xy", Style.UPRIGHT, Cls.ORD, p()));
    assertThat(print("θ")).isEqualTo(italic('θ', p()));
    assertThat(print("Ω")).isEqualTo(new Glyph('Ω', Style.UPRIGHT, Cls.ORD, p()));
    assertThat(print("pi")).isEqualTo(italic('π', p()));
    assertThat(print("inf")).isEqualTo(new Glyph('∞', Style.UPRIGHT, Cls.ORD, p()));
    assertThat(print("sin(x)"))
        .isEqualTo(
            new Row(
                List.of(
                    new Text("sin", Style.UPRIGHT, Cls.OP, null),
                    new Delimited('(', new Row(List.of(italic('x', p(0))), null), ')', null)),
                p()));
    assertThat(((Row) print("f(x)")).items().getFirst()).isEqualTo(italic('f', null));
  }

  @Test
  void bigOperatorsCarryTheirBoundsAndBody() {
    assertThat(print("∫[0, 1] x^2 dx"))
        .isEqualTo(
            new BigOp(
                new Glyph('∫', Style.UPRIGHT, Cls.OP, null),
                number("0", p(2)),
                number("1", p(3)),
                new Row(
                    List.of(
                        new Scripts(
                            italic('x', p(0, 0)),
                            null,
                            number("2", p(0, 1)),
                            null,
                            Form.CHARACTERS,
                            p(0)),
                        new Space(Space.Kind.THIN),
                        italic('d', null),
                        italic('x', p(1))),
                    null),
                false,
                p()));
    assertThat(print("Σ[k=1,n] k"))
        .isEqualTo(
            new BigOp(
                new Glyph('∑', Style.UPRIGHT, Cls.OP, null),
                new Row(
                    List.of(italic('k', p(1)), SP, op('=', Cls.REL), SP, number("1", p(2))), null),
                italic('n', p(3)),
                italic('k', p(0)),
                true,
                p()));
    BigOp limit = (BigOp) print("lim[x → 0+] sin(x)");
    assertThat(limit.operator()).isEqualTo(new Text("lim", Style.UPRIGHT, Cls.OP, null));
    assertThat(limit.limits()).isTrue();
    assertThat(((Row) limit.lower()).items().getLast())
        .isEqualTo(
            new Scripts(number("0", p(2)), null, op('+', Cls.ORD), null, Form.APPENDED, null));
    BigOp derivative = (BigOp) print("d/dx x^2");
    assertThat(derivative.operator())
        .isEqualTo(
            new Fraction(
                italic('d', null),
                new Row(List.of(italic('d', null), italic('x', p(1))), null),
                false,
                false,
                false,
                null));
    assertThat(LayoutPrinter.flatten(print("d^2/dx^2 x^3"))).isEqualTo("d²/dx² x³");
    // a mixed partial: one head for the chain, the outer derivative's place, the inner's body
    BigOp partial = (BigOp) print("∂²/∂x∂y (x y)!");
    assertThat(LayoutPrinter.flatten(partial)).isEqualTo("∂²/∂x∂y (x y)!");
    assertThat(partial.path()).isEqualTo(p());
    Row denominator = (Row) ((Fraction) partial.operator()).den();
    assertThat(denominator.items().get(1)).isEqualTo(italic('x', p(1)));
    assertThat(denominator.items().get(3)).isEqualTo(italic('y', p(0, 1)));
    assertThat(partial.body().path()).isEqualTo(p(0, 0));
  }

  @Test
  void collectionsAreFencedAndMatricesAreTables() {
    assertThat(print("[[1, 2], [3, 4]]"))
        .isEqualTo(
            new Matrix(
                List.of(
                    List.<Layout>of(number("1", p(0)), number("2", p(1))),
                    List.<Layout>of(number("3", p(2)), number("4", p(3)))),
                '[',
                ']',
                p()));
    assertThat(print("⟨1, 2⟩"))
        .isEqualTo(
            new Delimited(
                '⟨',
                new Row(
                    List.of(number("1", p(0)), op(',', Cls.PUNCT), SP, number("2", p(1))), null),
                '⟩',
                p()));
    assertThat(print("{2, 3}")).isInstanceOf(Delimited.class);
    assertThat(print("abs(x)")).isEqualTo(new Delimited('|', italic('x', p(0)), '|', p()));
  }

  @Test
  void aPiecewiseExpressionHasItsRowsAndOtherwise() {
    Expr e =
        mathcanvas.engine.expr.Piecewise.of(
            List.of(new mathcanvas.engine.expr.Piecewise.Piece(Symbol.of("x"), parse("x > 0"))),
            parse("-x"));
    Layout layout = LayoutPrinter.INSTANCE.print(e);
    assertThat(LayoutPrinter.flatten(layout)).isEqualTo(UnicodePrinter.INSTANCE.print(e));
    Piecewise piecewise = (Piecewise) layout;
    assertThat(piecewise.pieces()).hasSize(1);
    assertThat(piecewise.pieces().getFirst().value()).isEqualTo(italic('x', p(0)));
    assertThat(piecewise.pieces().getFirst().condition().path()).isEqualTo(p(1));
    assertThat(piecewise.otherwise().path()).isEqualTo(p(2));
  }

  @Test
  void relationsLambdasAndNumbersHaveTheirClasses() {
    assertThat(((Row) print("x <= 1")).items().get(2)).isEqualTo(op('≤', Cls.REL));
    assertThat(((Row) print("x -> x^2")).items().get(2)).isEqualTo(op('→', Cls.REL));
    assertThat(((Row) print("u × v")).items().get(2)).isEqualTo(op('×', Cls.BIN));
    assertThat(((Row) print("n!")).items().get(1)).isEqualTo(op('!', Cls.CLOSE));
    assertThat(print("2000.0")).isEqualTo(number("2000.0", p()));
    // A number's synthetic display tree carries the number's own path throughout.
    Layout half = LayoutPrinter.INSTANCE.print(Num.of(Rational.of(1, 2)));
    assertThat(half).isInstanceOf(Fraction.class);
    assertThat(((Fraction) half).num().path()).isEqualTo(p());
  }

  /**
   * A negated fraction takes the parentheses of its numerator off only where the printer put them:
   * an interval keeps its own brackets, which differ in a half-open one ({@code -(a²·[[a, a]],
   * e]/12} flattened to {@code -(…, e)/12}; a random sample of {@code LayoutProperties} met it).
   */
  @Test
  void aNegatedNumeratorKeepsAnIntervalsBrackets() {
    Symbol a = Symbol.of("a");
    Expr matrix = mathcanvas.engine.expr.Matrix.of(List.of(List.of(a, a)));
    Expr twelfth = Num.of(Rational.of(-1, 12));
    for (Expr lower : List.of(a, matrix, Product.of(Power.of(a, Num.of(2)), matrix))) {
      for (boolean lowerClosed : new boolean[] {false, true}) {
        for (boolean upperClosed : new boolean[] {false, true}) {
          Expr e =
              Canonical.of(
                  Product.of(twelfth, Interval.of(lower, Constant.E, lowerClosed, upperClosed)));
          assertThat(LayoutPrinter.flatten(LayoutPrinter.INSTANCE.display(e)))
              .as("%s", e)
              .isEqualTo(UnicodePrinter.INSTANCE.display(e));
        }
      }
    }
    Expr halfOpen =
        Canonical.of(
            Product.of(
                twelfth,
                Interval.of(Product.of(Power.of(a, Num.of(2)), matrix), Constant.E, false, true)));
    assertThat(LayoutPrinter.flatten(LayoutPrinter.INSTANCE.display(halfOpen)))
        .isEqualTo("-(a²·[[a, a]], e]/12");
    // The printer's own parentheses still come off a negated numerator.
    Expr sum = Canonical.of(parse("-(a + b)/12"));
    assertThat(LayoutPrinter.flatten(LayoutPrinter.INSTANCE.display(sum)))
        .isEqualTo(UnicodePrinter.INSTANCE.display(sum));
  }
}
