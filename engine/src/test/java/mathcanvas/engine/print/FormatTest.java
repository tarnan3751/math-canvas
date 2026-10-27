package mathcanvas.engine.print;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.layout.Cls;
import mathcanvas.engine.print.layout.Fraction;
import mathcanvas.engine.print.layout.Layout;
import mathcanvas.engine.print.layout.Row;
import mathcanvas.engine.print.layout.Space;
import mathcanvas.engine.print.layout.Style;
import mathcanvas.engine.print.layout.Text;
import mathcanvas.engine.rule.Path;
import org.junit.jupiter.api.Test;

/** The number formats in every printer: the brief's cases, displayed and held. */
class FormatTest {

  private static final Format DECIMAL = Format.decimal(4);
  private static final Format SCIENTIFIC = Format.scientific(4);
  private static final Format FRACTION = Format.FRACTION;

  private static Expr value(String text) {
    return Canonical.of(Parser.parseExpression(text, Set.of()::contains));
  }

  /**
   * The Unicode, ASCII and LaTeX displays of a value in a format; the layout flattens to the first.
   */
  private static void shows(String text, Format f, String unicode, String ascii, String latex) {
    Expr c = value(text);
    assertThat(UnicodePrinter.INSTANCE.withFormat(f).display(c))
        .as("unicode %s", text)
        .isEqualTo(unicode);
    assertThat(AsciiPrinter.INSTANCE.withFormat(f).display(c))
        .as("ascii %s", text)
        .isEqualTo(ascii);
    assertThat(LatexPrinter.INSTANCE.withFormat(f).display(c))
        .as("latex %s", text)
        .isEqualTo(latex);
    assertThat(LayoutPrinter.flatten(LayoutPrinter.INSTANCE.withFormat(f).display(c)))
        .as("layout %s", text)
        .isEqualTo(unicode);
  }

  @Test
  void exactIsTheDefaultAndChangesNothing() {
    assertThat(UnicodePrinter.INSTANCE.format()).isEqualTo(Format.EXACT);
    assertThat(LatexPrinter.INSTANCE.format()).isEqualTo(Format.EXACT);
    assertThat(LayoutPrinter.INSTANCE.format()).isEqualTo(Format.EXACT);
    for (String text :
        List.of("1/3", "7/2", "3/4*sqrt(2)", "1.23456789", "x^(3/2)", "1/2 + 3i/4")) {
      Expr c = value(text);
      assertThat(UnicodePrinter.INSTANCE.withFormat(Format.EXACT).display(c))
          .isEqualTo(UnicodePrinter.INSTANCE.display(c));
      assertThat(LatexPrinter.INSTANCE.withFormat(Format.EXACT).display(c))
          .isEqualTo(LatexPrinter.INSTANCE.display(c));
    }
  }

  @Test
  void decimalWritesRationalsAndDecimalsToTheDigits() {
    shows("1/3", DECIMAL, "0.3333", "0.3333", "0.3333");
    shows("7/2", DECIMAL, "3.5", "3.5", "3.5");
    shows("-7/2", DECIMAL, "-3.5", "-3.5", "-3.5");
    shows("1.23456789", DECIMAL, "1.235", "1.235", "1.235");
    shows("7x/2", DECIMAL, "3.5x", "3.5*x", "3.5x");
    shows("2x^2 + 1/3", DECIMAL, "2x² + 0.3333", "2*x^2 + 0.3333", "2x^{2}+0.3333");
    shows(
        "[[1/2, 1/3], [2, 7/2]]",
        DECIMAL,
        "[[0.5, 0.3333], [2, 3.5]]",
        "[[0.5, 0.3333], [2, 3.5]]",
        "\\begin{pmatrix}0.5 & 0.3333\\\\ 2 & 3.5\\end{pmatrix}");
    shows("1/2 + 3i/4", DECIMAL, "0.5 + 0.75i", "0.5 + 0.75*i", "0.5+0.75i");
    // A subexpression of numbers only is one decimal; constants and calls stay.
    shows("2^(1/2)*3/4", DECIMAL, "1.061", "1.061", "1.061");
    shows("2pi", DECIMAL, "2π", "2*pi", "2\\pi");
    shows("sin(1)", DECIMAL, "sin(1)", "sin(1)", "\\sin(1)");
    // Integers, integer exponents and root notation stay; a rational exponent is a decimal.
    shows(
        "10^21",
        DECIMAL,
        "1000000000000000000000",
        "1000000000000000000000",
        "1000000000000000000000");
    shows("1/x", DECIMAL, "1/x", "1/x", "\\frac{1}{x}");
    shows("sqrt(x)/3", DECIMAL, "0.3333√x", "0.3333*sqrt(x)", "0.3333\\sqrt{x}");
    shows("x^(3/2)", DECIMAL, "x^(1.5)", "x^(1.5)", "x^{1.5}");
  }

  @Test
  void scientificWritesDecimalsAndLongIntegersWithAPowerOfTen() {
    shows("1/3", SCIENTIFIC, "3.333e-1", "3.333e-1", "3.333\\times 10^{-1}");
    shows("7/2", SCIENTIFIC, "3.5e+0", "3.5e+0", "3.5\\times 10^{0}");
    shows("1.23456789", SCIENTIFIC, "1.235e+0", "1.235e+0", "1.235\\times 10^{0}");
    shows("10^21", SCIENTIFIC, "1e+21", "1e+21", "1\\times 10^{21}");
    shows("2", SCIENTIFIC, "2", "2", "2");
    shows("7x/2", SCIENTIFIC, "3.5e+0 x", "3.5e+0*x", "(3.5\\times 10^{0})x");
    shows(
        "1/2 + 3i/4",
        SCIENTIFIC,
        "5e-1 + 7.5e-1 i",
        "5e-1 + 7.5e-1*i",
        "5\\times 10^{-1}+(7.5\\times 10^{-1})i");
    // An exponent is written as a decimal, never in scientific notation.
    shows("x^(3/2)", SCIENTIFIC, "x^(1.5)", "x^(1.5)", "x^{1.5}");
  }

  @Test
  void fractionWritesImproperRationalsAsMixedNumbers() {
    shows("7/2", FRACTION, "3 1/2", "3 1/2", "3\\frac{1}{2}");
    shows("-7/2", FRACTION, "-3 1/2", "-3 1/2", "-3\\frac{1}{2}");
    shows("1/3", FRACTION, "1/3", "1/3", "\\frac{1}{3}");
    shows("x + 7/2", FRACTION, "x + 3 1/2", "x + 3 1/2", "x+3\\frac{1}{2}");
    shows("x - 7/2", FRACTION, "x - 3 1/2", "x - 3 1/2", "x-3\\frac{1}{2}");
    shows(
        "{-7/2, 5/3}",
        FRACTION,
        "{-3 1/2, 1 2/3}",
        "{-3 1/2, 1 2/3}",
        "\\{-3\\frac{1}{2}, 1\\frac{2}{3}\\}");
    shows(
        "[[1/2, 7/2]]",
        FRACTION,
        "[[1/2, 3 1/2]]",
        "[[1/2, 3 1/2]]",
        "\\begin{pmatrix}\\frac{1}{2} & 3\\frac{1}{2}\\end{pmatrix}");
    shows(
        "7/2 + 5i/2",
        FRACTION,
        "3 1/2 + (2 1/2) i",
        "3 1/2 + (2 1/2)*i",
        "3\\frac{1}{2}+(2\\frac{1}{2})i");
    // A coefficient over the line keeps its quotient; an exponent stays.
    shows("7x/2", FRACTION, "7x/2", "7*x/2", "\\frac{7x}{2}");
    shows("x^(3/2)", FRACTION, "x^(3/2)", "x^(3/2)", "x^{\\frac{3}{2}}");
  }

  @Test
  void aMixedNumberIsLaidOutAsItsWholePartAndAFraction() {
    Layout layout =
        LayoutPrinter.INSTANCE
            .withFormat(FRACTION)
            .print(Num.of(mathcanvas.engine.number.Rational.of(7, 2)));
    assertThat(layout)
        .isEqualTo(
            new Row(
                List.of(
                    new Text("3", Style.UPRIGHT, Cls.ORD, null),
                    new Space(Space.Kind.LINEAR),
                    new Fraction(
                        new Text("1", Style.UPRIGHT, Cls.ORD, null),
                        new Text("2", Style.UPRIGHT, Cls.ORD, null),
                        false,
                        false,
                        false,
                        null)),
                Path.ROOT));
    assertThat(LayoutPrinter.flatten(layout)).isEqualTo("3 1/2");
  }

  @Test
  void aHeldTreeHasItsLeavesFormatted() {
    Expr typed = Parser.parseExpression("1.23456789 + 1/3", Set.of()::contains);
    // The typed 1/3 is a quotient of integers, which stay; the decimal is rounded.
    assertThat(UnicodePrinter.INSTANCE.withFormat(DECIMAL).print(typed)).isEqualTo("1.235 + 1/3");
    assertThat(LayoutPrinter.flatten(LayoutPrinter.INSTANCE.withFormat(DECIMAL).print(typed)))
        .isEqualTo("1.235 + 1/3");
  }

  @Test
  void aBaseWithAnExponentMarkerIsParenthesised() {
    // Exact: a large decimal base, which read 1.0e+21^x as 1.0e+(21^x) before.
    shows("1.0e21^x", Format.EXACT, "(1.0e+21)^x", "(1.0e+21)^x", "(1.0\\times 10^{21})^{x}");
    // A held power keeps its square: the base is parenthesised before the superscript.
    Expr square = Parser.parseExpression("1.0e21^2", Set.of()::contains);
    assertThat(UnicodePrinter.INSTANCE.print(square)).isEqualTo("(1.0e+21)²");
    assertThat(AsciiPrinter.INSTANCE.print(square)).isEqualTo("(1.0e+21)^2");
    assertThat(LayoutPrinter.flatten(LayoutPrinter.INSTANCE.print(square))).isEqualTo("(1.0e+21)²");
    assertThat(Parser.parseExpression(UnicodePrinter.INSTANCE.print(square), Set.of()::contains))
        .isEqualTo(square);
    shows("(7/2)^x", SCIENTIFIC, "(3.5e+0)^x", "(3.5e+0)^x", "(3.5\\times 10^{0})^{x}");
    // The layout's base is the parenthesised literal.
    Layout layout = LayoutPrinter.INSTANCE.display(value("1.0e21^x"));
    assertThat(((mathcanvas.engine.print.layout.Scripts) layout).base())
        .isInstanceOf(mathcanvas.engine.print.layout.Delimited.class);
    // The exact text reads back as the value.
    for (Printer p : List.<Printer>of(UnicodePrinter.INSTANCE, AsciiPrinter.INSTANCE)) {
      for (String text : List.of("1.0e21^x", "1.5e-30^(x + 1)")) {
        Expr c = value(text);
        assertThat(Canonical.of(Parser.parseExpression(p.display(c), Set.of()::contains)))
            .as("%s through %s", text, p.getClass().getSimpleName())
            .isEqualTo(c);
      }
    }
  }

  @Test
  void theDigitsAreChecked() {
    assertThatThrownBy(() -> Format.decimal(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Format.scientific(Format.MAX_DIGITS + 1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Format(Format.Kind.FRACTION, 3))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(Format.decimal(Format.MAX_DIGITS).digits()).isEqualTo(Format.MAX_DIGITS);
  }
}
