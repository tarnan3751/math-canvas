package mathcanvas.render.typeset;

import static mathcanvas.render.typeset.Boxes.TYPESETTER;
import static mathcanvas.render.typeset.Boxes.all;
import static mathcanvas.render.typeset.Boxes.display;
import static mathcanvas.render.typeset.Boxes.held;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import mathcanvas.engine.print.layout.Cls;
import mathcanvas.engine.print.layout.Glyph;
import mathcanvas.engine.print.layout.Row;
import mathcanvas.engine.print.layout.Style;
import mathcanvas.engine.rule.Path;
import mathcanvas.render.font.OpenTypeFont;
import org.junit.jupiter.api.Test;

/**
 * Positions against the MATH constants. At a size of 1000 px one pixel is one font unit, so the
 * constants read directly: axis 258, rule 68, display numerator shift 640 and gap 150, superscript
 * shift 360, subscript shift 210, radical gap 85 and 170, limit gaps 135, limit rise 300 and drop
 * 670, display operator height 1800.
 */
class TypesetterTest {

  private static final double EM = 1000;
  private static final OpenTypeFont FONT = OpenTypeFont.stix();

  private static Path p(Integer... indices) {
    return new Path(Arrays.asList(indices));
  }

  private static Box box(Box root, Path path) {
    return root.find(path).orElseThrow(() -> new AssertionError("no box at " + path));
  }

  @Test
  void aFractionBarSitsOnTheAxisWithTheGapsAboveAndBelow() {
    Box root = TYPESETTER.typeset(display("x/(2*pi)"), EM, true);
    Box.RuleBox bar = all(root, Box.RuleBox.class).getFirst();
    assertThat(bar.ascent() + bar.descent()).isCloseTo(68, within(1e-9));
    assertThat((bar.top() + bar.bottom()) / 2).isCloseTo(-258, within(1e-9));
    Box num = box(root, p(0));
    Box den = box(root, p(1, 0));
    assertThat(num.baseline()).isLessThanOrEqualTo(-640);
    assertThat(bar.top() - num.bottom()).isGreaterThanOrEqualTo(150 - 1e-9);
    assertThat(den.baseline()).isGreaterThanOrEqualTo(640);
    assertThat(den.top() - bar.bottom()).isGreaterThanOrEqualTo(150 - 1e-9);
    // Centred over each other, inside the padding.
    assertThat(num.x() + num.width() / 2).isCloseTo(bar.x() + bar.width() / 2, within(1e-9));
    assertThat(bar.x()).isCloseTo(Typesetter.FRACTION_PADDING * EM, within(1e-9));
    // In text style the numerator moves to script size and the text-style gaps apply.
    Box text = TYPESETTER.typeset(display("x/(2*pi)"), EM, false);
    Box.RuleBox textBar = all(text, Box.RuleBox.class).getFirst();
    assertThat(textBar.top() - box(text, p(0)).bottom()).isGreaterThanOrEqualTo(68 - 1e-9);
    assertThat(box(text, p(0)).baseline()).isLessThanOrEqualTo(-585);
  }

  @Test
  void scriptsShiftByTheScriptConstants() {
    Box sup = TYPESETTER.typeset(held("x^2"), EM, false);
    Box base = box(sup, p(0));
    Box two = box(sup, p(1));
    assertThat(two.baseline()).isCloseTo(-360, within(1e-9));
    int italicX = FONT.glyph(0x1D465);
    double advance = FONT.advance(italicX);
    double italic = FONT.math().italicsCorrection(italicX);
    assertThat(two.x()).isCloseTo(base.x() + advance + italic, within(1e-9));
    // The script is set at 70% with its ssty glyph.
    Box.GlyphBox digit = all(two, Box.GlyphBox.class).getFirst();
    assertThat(digit.scale()).isCloseTo(0.7, within(1e-12));
    assertThat(digit.glyph()).isEqualTo(FONT.scriptGlyph(FONT.glyph('2'), 1));
    Box sub = TYPESETTER.typeset(held("x_1"), EM, false);
    Box.GlyphBox one = all(sub, Box.GlyphBox.class).getLast();
    assertThat(one.baseline()).isCloseTo(210, within(1e-9));
    assertThat(one.x()).isCloseTo(advance, within(1e-9));
    // A subscripted name's power stacks its scripts, the name keeping a box of its own.
    Box stacked = TYPESETTER.typeset(held("x_1^2"), EM, false);
    Box name = box(stacked, p(0));
    Box square = box(stacked, p(1));
    assertThat(square.x()).isLessThan(name.x() + name.width());
    assertThat(square.baseline()).isLessThan(0);
    // In a cramped style (a denominator) the superscript rises less.
    Box cramped = TYPESETTER.typeset(held("1/x^2"), EM, true);
    Box power = box(cramped, p(1, 0, 0));
    Box exponent = box(cramped, p(1, 0, 1));
    assertThat(power.baseline() - exponent.baseline()).isCloseTo(252, within(1e-9));
  }

  @Test
  void fencesStretchToCoverTheirBody() {
    Box root = TYPESETTER.typeset(held("(x/y + 1)^2"), EM, true);
    List<Box.GlyphBox> glyphs = all(root, Box.GlyphBox.class);
    Box.GlyphBox open = glyphs.getFirst();
    assertThat(open.glyph()).isNotEqualTo(FONT.glyph('('));
    Box sum = box(root, p(0)).children().get(1);
    double axis = 258;
    double delta = Math.max(-sum.top() - axis, sum.bottom() + axis);
    double required = Math.max(2 * delta * 901 / 1000, 2 * delta - 500);
    double size =
        FONT.math().vertical(FONT.glyph('(')).variants().stream()
            .filter(v -> v.glyph() == open.glyph())
            .findFirst()
            .orElseThrow()
            .advance();
    assertThat(size).isGreaterThanOrEqualTo(required);
    assertThat((open.top() + open.bottom()) / 2).isCloseTo(-axis, within(1e-9));
    // A body below delimitedSubFormulaMinHeight keeps the base glyph.
    Box small = TYPESETTER.typeset(held("(x + 1)^2"), EM, true);
    assertThat(all(small, Box.GlyphBox.class).getFirst().glyph()).isEqualTo(FONT.glyph('('));
  }

  @Test
  void aTallFenceIsAssembledFromItsParts() {
    Box root = TYPESETTER.typeset(held("f([[1], [2], [3], [4], [5], [6]])"), EM, true);
    List<Box.GlyphBox> glyphs = all(root, Box.GlyphBox.class);
    assertThat(glyphs).extracting(Box.GlyphBox::glyph).contains(4862, 4861, 4860);
    // The call's parentheses: the fence, then the matrix, then the other fence.
    Box call = root.children().get(1);
    Box open = call.children().getFirst();
    Box matrix = call.children().get(1);
    assertThat(open.ascent() + open.descent())
        .isGreaterThanOrEqualTo(0.9 * (matrix.ascent() + matrix.descent()));
    // TeX lets a fence fall short of its body by the shortfall, half at each end.
    assertThat(open.top())
        .isLessThanOrEqualTo(matrix.top() + Typesetter.DELIMITER_SHORTFALL * EM / 2 + 1);
  }

  /** The top and bottom of the ink of the glyphs in a box, y down. */
  private static double[] ink(Box box) {
    double top = Double.POSITIVE_INFINITY;
    double bottom = Double.NEGATIVE_INFINITY;
    for (Box.GlyphBox g : all(box, Box.GlyphBox.class)) {
      OpenTypeFont.Bounds b = FONT.bounds(g.glyph());
      top = Math.min(top, g.baseline() - b.yMax() * g.scale());
      bottom = Math.max(bottom, g.baseline() - b.yMin() * g.scale());
    }
    return new double[] {top, bottom};
  }

  @Test
  void stretchedBarsAreCentredOnTheAxisAndAsTallAsParentheses() {
    Box bars = TYPESETTER.typeset(held("abs(1/(x + 1))"), EM, true);
    Box parens = TYPESETTER.typeset(held("(1/(x + 1))^2"), EM, true);
    Box bar = bars.children().getFirst();
    Box paren = box(parens, p(0)).children().getFirst();
    // The bar is assembled (its part's ink is 220 units below its origin), the parenthesis a
    // size variant; both are centred on the axis.
    assertThat(all(bar, Box.GlyphBox.class)).hasSizeGreaterThan(1);
    double[] barInk = ink(bar);
    double[] parenInk = ink(paren);
    assertThat((barInk[0] + barInk[1]) / 2).isCloseTo(-258.0, within(1.0));
    assertThat((parenInk[0] + parenInk[1]) / 2).isCloseTo(-258.0, within(1.0));
    // The variant is the first size that reaches the target, the assembly meets it exactly, so
    // the bar spans the target and the parenthesis at most one variant step more.
    double target = barInk[1] - barInk[0];
    assertThat(parenInk[1] - parenInk[0]).isBetween(target - 1, target + 250);
  }

  @Test
  void aLinearSpaceBetweenNamesOfSeveralLettersIsThin() {
    double thin = 3 * EM / 18;
    Box words = TYPESETTER.typeset(held("ab cd"), EM, false);
    assertThat(box(words, p(1)).x() - (box(words, p(0)).x() + box(words, p(0)).width()))
        .isCloseTo(thin, within(1e-9));
    Box mixed = TYPESETTER.typeset(held("x ab"), EM, false);
    double italic = FONT.math().italicsCorrection(FONT.glyph(0x1D465));
    assertThat(box(mixed, p(1)).x() - (box(mixed, p(0)).x() + box(mixed, p(0)).width() + italic))
        .isCloseTo(thin, within(1e-9));
    // Two one-letter names stay together, italic against italic without a correction.
    Box letters = TYPESETTER.typeset(held("x y"), EM, false);
    assertThat(box(letters, p(1)).x())
        .isCloseTo(box(letters, p(0)).x() + box(letters, p(0)).width(), within(1e-9));
    Box number = TYPESETTER.typeset(held("2x"), EM, false);
    assertThat(box(number, p(1)).x())
        .isCloseTo(box(number, p(0)).x() + box(number, p(0)).width(), within(1e-9));
  }

  @Test
  void aRadicalHasItsRuleAndGapAboveTheRadicand() {
    Box root = TYPESETTER.typeset(held("sqrt(x + 1)"), EM, false);
    Box.RuleBox rule = all(root, Box.RuleBox.class).getFirst();
    Box radicand = box(root, p(0));
    assertThat(rule.ascent() + rule.descent()).isCloseTo(68, within(1e-9));
    assertThat(radicand.top() - rule.bottom()).isGreaterThanOrEqualTo(85 - 1e-9);
    assertThat(rule.x()).isCloseTo(radicand.x(), within(1e-9));
    assertThat(rule.width()).isCloseTo(radicand.width(), within(1e-9));
    Box.GlyphBox sign = all(root, Box.GlyphBox.class).getFirst();
    assertThat(sign.top()).isCloseTo(rule.top(), within(1e-9));
    Box display = TYPESETTER.typeset(held("sqrt(x + 1)"), EM, true);
    Box.RuleBox displayRule = all(display, Box.RuleBox.class).getFirst();
    assertThat(box(display, p(0)).top() - displayRule.bottom()).isGreaterThanOrEqualTo(170 - 1e-9);
    // A cube root's index sits before the sign, in scriptscript size.
    Box cube = TYPESETTER.typeset(held("cbrt(x)"), EM, false);
    Box.GlyphBox three = all(cube, Box.GlyphBox.class).getFirst();
    assertThat(three.scale()).isCloseTo(0.55, within(1e-12));
  }

  @Test
  void limitsGoBelowAndAboveASumInDisplayStyleAndBesideItInText() {
    Box root = TYPESETTER.typeset(held("Σ[k=1,n] k"), EM, true);
    Box.GlyphBox sigma =
        all(root, Box.GlyphBox.class).stream()
            .filter(g -> g.glyph() == 1646 || g.glyph() == 1647)
            .findFirst()
            .orElseThrow();
    assertThat(sigma.glyph()).isEqualTo(1647);
    assertThat((sigma.top() + sigma.bottom()) / 2).isCloseTo(-258, within(1e-9));
    Box upper = box(root, p(3));
    Box index = box(root, p(1));
    assertThat(sigma.top() - upper.bottom()).isGreaterThanOrEqualTo(135 - 1e-9);
    assertThat(sigma.top() - upper.baseline()).isGreaterThanOrEqualTo(300 - 1e-9);
    assertThat(index.top() - sigma.bottom()).isGreaterThanOrEqualTo(135 - 1e-9);
    assertThat(index.baseline() - sigma.bottom()).isGreaterThanOrEqualTo(670 - 1e-9);
    assertThat(upper.x() + upper.width() / 2)
        .isCloseTo(sigma.x() + sigma.width() / 2, within(1e-9));
    Box text = TYPESETTER.typeset(held("Σ[k=1,n] k"), EM, false);
    Box.GlyphBox small =
        all(text, Box.GlyphBox.class).stream()
            .filter(g -> g.glyph() == 1646)
            .findFirst()
            .orElseThrow();
    assertThat(box(text, p(3)).x()).isGreaterThan(small.x() + small.width() / 2);
  }

  @Test
  void anIntegralTakesItsBoundsAsScriptsAndItsDisplaySize() {
    Box root = TYPESETTER.typeset(held("∫[0, 1] x^2 dx"), EM, true);
    Box.GlyphBox integral = all(root, Box.GlyphBox.class).getFirst();
    assertThat(integral.glyph()).isEqualTo(1699);
    // The advance covers the ink (1032 against 957), so the upper bound goes at the advance and
    // the lower one is tucked left by the italic correction (540).
    double advance = FONT.advance(1699);
    double correction = FONT.math().italicsCorrection(1699);
    assertThat(advance).isEqualTo(1032);
    assertThat(correction).isEqualTo(540);
    Box lower = box(root, p(2));
    Box upper = box(root, p(3));
    assertThat(upper.x()).isCloseTo(integral.x() + advance, within(1e-9));
    assertThat(lower.x()).isCloseTo(integral.x() + advance - correction, within(1e-9));
    assertThat(box(root, p(0)).x()).isGreaterThan(upper.x() + upper.width());
    Box text = TYPESETTER.typeset(held("∫[0, 1] x^2 dx"), EM, false);
    assertThat(all(text, Box.GlyphBox.class).getFirst().glyph()).isEqualTo(1698);
    // A sum in text style takes its bounds as scripts by the same rule.
    Box sum = TYPESETTER.typeset(held("Σ[k=1,n] k"), EM, false);
    Box.GlyphBox sigma = all(sum, Box.GlyphBox.class).getFirst();
    assertThat(sigma.glyph()).isEqualTo(1646);
    double sigmaCorrection = FONT.math().italicsCorrection(1646);
    assertThat(box(sum, p(3)).x()).isCloseTo(sigma.x() + FONT.advance(1646), within(1e-9));
    Box index = box(sum, p(1));
    assertThat(index.x()).isCloseTo(sigma.x() + FONT.advance(1646) - sigmaCorrection, within(1e-9));
  }

  @Test
  void aMatrixIsCentredColumnsBetweenStretchedBrackets() {
    Box root = TYPESETTER.typeset(held("[[1, 22], [333, 4]]"), EM, true);
    Box a = box(root, p(0));
    Box b = box(root, p(1));
    Box c = box(root, p(2));
    Box d = box(root, p(3));
    assertThat(a.baseline()).isEqualTo(b.baseline());
    assertThat(c.baseline()).isGreaterThan(a.baseline());
    assertThat(a.x() + a.width() / 2).isCloseTo(c.x() + c.width() / 2, within(1e-9));
    assertThat(b.x() - (c.x() + c.width())).isCloseTo(Typesetter.COLUMN_GAP * EM, within(1e-9));
    assertThat(d.x() + d.width() / 2).isCloseTo(b.x() + b.width() / 2, within(1e-9));
    Box.GlyphBox open = all(root, Box.GlyphBox.class).getFirst();
    assertThat(open.top()).isLessThanOrEqualTo(a.top());
    assertThat(open.bottom()).isGreaterThanOrEqualTo(c.bottom());
  }

  @Test
  void aPiecewiseExpressionHasABraceBeforeItsRows() {
    mathcanvas.engine.expr.Expr e =
        mathcanvas.engine.expr.Piecewise.of(
            List.of(
                new mathcanvas.engine.expr.Piecewise.Piece(
                    mathcanvas.engine.expr.Symbol.of("x"),
                    mathcanvas.engine.parse.Parser.parseExpression("x > 0", n -> false))),
            mathcanvas.engine.parse.Parser.parseExpression("-x", n -> false));
    Box root =
        TYPESETTER.typeset(mathcanvas.engine.print.LayoutPrinter.INSTANCE.print(e), EM, true);
    Box value = box(root, p(0));
    Box condition = box(root, p(1));
    Box otherwise = box(root, p(2));
    assertThat(value.x()).isEqualTo(otherwise.x());
    assertThat(condition.x()).isGreaterThan(value.x() + value.width());
    Box.GlyphBox brace = all(root, Box.GlyphBox.class).getFirst();
    assertThat(brace.x()).isLessThan(value.x());
    // The word "otherwise" follows the last value, left-aligned with the conditions.
    List<Box.GlyphBox> glyphs = all(root, Box.GlyphBox.class);
    assertThat(glyphs.getLast().baseline()).isEqualTo(otherwise.baseline());
  }

  @Test
  void classesSpaceTheAtomsOfARow() {
    Box sum = TYPESETTER.typeset(held("a + b"), EM, false);
    Box a = box(sum, p(0));
    Box plus = all(sum, Box.GlyphBox.class).get(1);
    double italic = FONT.math().italicsCorrection(FONT.glyph(0x1D44E));
    // Medium space (4 mu) on both sides of a binary sign.
    assertThat(plus.x() - (a.x() + a.width() + italic)).isCloseTo(4 * EM / 18, within(1e-9));
    // A leading minus is unary: no space after it.
    Box negation = TYPESETTER.typeset(held("-a"), EM, false);
    Box.GlyphBox minus = all(negation, Box.GlyphBox.class).getFirst();
    assertThat(minus.glyph()).isEqualTo(FONT.glyph(0x2212));
    assertThat(box(negation, p(1)).x()).isCloseTo(minus.x() + minus.width(), within(1e-9));
    // A relation takes thick spaces; none in script style.
    Box relation = TYPESETTER.typeset(held("a = b"), EM, false);
    Box equals = all(relation, Box.GlyphBox.class).get(1);
    Box left = box(relation, p(0));
    assertThat(equals.x() - (left.x() + left.width() + italic))
        .isCloseTo(5 * EM / 18, within(1e-9));
  }

  @Test
  void aCharacterTheFontLacksIsMeasuredByTheFallback() {
    Box root = TYPESETTER.typeset(new Glyph(0x4E00, Style.UPRIGHT, Cls.ORD, Path.ROOT), EM, false);
    assertThat(root).isInstanceOf(Box.TextBox.class);
    assertThat(root.width()).isCloseTo(600, within(1e-9));
    assertThat(root.path()).isEqualTo(Path.ROOT);
    Box row = TYPESETTER.typeset(held("一 + 1"), EM, false);
    assertThat(all(row, Box.TextBox.class)).hasSize(1);
    assertThat(TYPESETTER.typeset(new Row(List.of(), null), EM, true).width()).isZero();
  }

  @Test
  void aThousandNodeExpressionTypesetsWellWithinTheBudget() {
    // About 1,050 nodes; typically 15-40 ms on a laptop once the font is read.
    String text = "x_1^2/(x + 1) + sqrt(y + 2)·sin(z) - [[1, 2], [3, 4]] + ".repeat(45) + "1";
    var layout = held(text);
    long start = System.nanoTime();
    Box root = TYPESETTER.typeset(layout, 20, true);
    Duration took = Duration.ofNanos(System.nanoTime() - start);
    assertThat(root.width()).isPositive();
    assertThat(took).isLessThan(Duration.ofSeconds(30));
  }
}
