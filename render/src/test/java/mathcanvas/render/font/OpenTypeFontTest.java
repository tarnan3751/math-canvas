package mathcanvas.render.font;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The reader against the bundled STIX Two Math: values read from the file with an independent
 * parser (and cross-checked against fontTools' table layout) on 2026-10-27.
 */
class OpenTypeFontTest {

  private static final OpenTypeFont FONT = OpenTypeFont.stix();

  @Test
  void theHeadersGiveTheMetrics() {
    assertThat(FONT.unitsPerEm()).isEqualTo(1000);
    assertThat(FONT.glyphCount()).isEqualTo(6760);
    assertThat(FONT.ascender()).isEqualTo(762);
    assertThat(FONT.descender()).isEqualTo(-238);
    assertThat(FONT.xHeight()).isEqualTo(473);
  }

  @Test
  void theCharacterMapCoversTheMathAlphabetsAndOperators() {
    assertThat(FONT.glyph('x')).isEqualTo(279);
    assertThat(FONT.glyph('(')).isEqualTo(1064);
    assertThat(FONT.glyph('∫')).isEqualTo(1698);
    assertThat(FONT.glyph('∑')).isEqualTo(1646);
    assertThat(FONT.glyph('√')).isEqualTo(1657);
    assertThat(FONT.glyph(0x2212)).isEqualTo(1197);
    assertThat(FONT.glyph(0x1D465)).isEqualTo(3354);
    assertThat(FONT.glyph(0x210E)).isEqualTo(1224);
    assertThat(FONT.glyph(0x1D453)).isEqualTo(3331);
    assertThat(FONT.glyph(0x4E00)).isZero();
    assertThat(FONT.advance(279)).isEqualTo(479);
  }

  @Test
  void theMathConstantsAreTheFilesValues() {
    MathConstants c = FONT.math().constants();
    assertThat(c.get(MathConstant.SCRIPT_PERCENT_SCALE_DOWN)).isEqualTo(70);
    assertThat(c.get(MathConstant.SCRIPT_SCRIPT_PERCENT_SCALE_DOWN)).isEqualTo(55);
    assertThat(c.get(MathConstant.DELIMITED_SUB_FORMULA_MIN_HEIGHT)).isEqualTo(1325);
    assertThat(c.get(MathConstant.DISPLAY_OPERATOR_MIN_HEIGHT)).isEqualTo(1800);
    assertThat(c.get(MathConstant.AXIS_HEIGHT)).isEqualTo(258);
    assertThat(c.get(MathConstant.SUBSCRIPT_SHIFT_DOWN)).isEqualTo(210);
    assertThat(c.get(MathConstant.SUPERSCRIPT_SHIFT_UP)).isEqualTo(360);
    assertThat(c.get(MathConstant.SUPERSCRIPT_SHIFT_UP_CRAMPED)).isEqualTo(252);
    assertThat(c.get(MathConstant.UPPER_LIMIT_GAP_MIN)).isEqualTo(135);
    assertThat(c.get(MathConstant.LOWER_LIMIT_BASELINE_DROP_MIN)).isEqualTo(670);
    assertThat(c.get(MathConstant.FRACTION_NUMERATOR_DISPLAY_STYLE_SHIFT_UP)).isEqualTo(640);
    assertThat(c.get(MathConstant.FRACTION_RULE_THICKNESS)).isEqualTo(68);
    assertThat(c.get(MathConstant.FRACTION_DENOM_DISPLAY_STYLE_GAP_MIN)).isEqualTo(150);
    assertThat(c.get(MathConstant.RADICAL_VERTICAL_GAP)).isEqualTo(85);
    assertThat(c.get(MathConstant.RADICAL_DISPLAY_STYLE_VERTICAL_GAP)).isEqualTo(170);
    assertThat(c.get(MathConstant.RADICAL_RULE_THICKNESS)).isEqualTo(68);
    assertThat(c.get(MathConstant.RADICAL_KERN_AFTER_DEGREE)).isEqualTo(-335);
    assertThat(c.get(MathConstant.RADICAL_DEGREE_BOTTOM_RAISE_PERCENT)).isEqualTo(55);
    assertThat(FONT.math().minConnectorOverlap()).isEqualTo(100);
  }

  @Test
  void delimitersHaveVariantsThenAnAssembly() {
    GlyphConstruction paren = FONT.math().vertical(FONT.glyph('('));
    assertThat(paren.variants()).hasSize(13);
    assertThat(paren.variants().subList(0, 3))
        .containsExactly(
            new GlyphConstruction.Variant(1064, 933),
            new GlyphConstruction.Variant(1301, 1187),
            new GlyphConstruction.Variant(1302, 1427));
    assertThat(paren.assembly().parts())
        .containsExactly(
            new GlyphConstruction.Part(4862, 0, 250, 1273, false),
            new GlyphConstruction.Part(4861, 1000, 1000, 1252, true),
            new GlyphConstruction.Part(4860, 250, 0, 1273, false));
    GlyphConstruction brace = FONT.math().vertical(FONT.glyph('{'));
    assertThat(brace.assembly().parts()).hasSize(5);
    GlyphConstruction integral = FONT.math().vertical(FONT.glyph('∫'));
    assertThat(integral.variants())
        .containsExactly(
            new GlyphConstruction.Variant(1698, 954), new GlyphConstruction.Variant(1699, 2347));
    assertThat(integral.assembly().italicsCorrection()).isEqualTo(80);
    GlyphConstruction sum = FONT.math().vertical(FONT.glyph('∑'));
    assertThat(sum.variants())
        .extracting(GlyphConstruction.Variant::advance)
        .containsExactly(1031, 1326);
    assertThat(sum.assembly()).isNull();
    assertThat(FONT.math().vertical(FONT.glyph('√')).variants()).hasSize(4);
    assertThat(FONT.math().vertical(FONT.glyph('x'))).isNull();
  }

  @Test
  void everyAssemblyPartsInkFitsItsFullAdvance() {
    // Parts are placed by their ink, so each part's ink must be no taller than its full advance;
    // some parts (the bar's) have their ink below their origin.
    int below = 0;
    int parts = 0;
    for (int g = 0; g < FONT.glyphCount(); g++) {
      for (GlyphConstruction c :
          new GlyphConstruction[] {FONT.math().vertical(g), FONT.math().horizontal(g)}) {
        if (c == null || c.assembly() == null) {
          continue;
        }
        boolean vertical = c == FONT.math().vertical(g);
        for (GlyphConstruction.Part part : c.assembly().parts()) {
          OpenTypeFont.Bounds b = FONT.bounds(part.glyph());
          int extent = vertical ? b.yMax() - b.yMin() : b.xMax() - b.xMin();
          assertThat(extent)
              .as("part %d of %d", part.glyph(), g)
              .isLessThanOrEqualTo(part.fullAdvance() + 1);
          parts++;
          if (vertical && b.yMin() < 0) {
            below++;
          }
        }
      }
    }
    assertThat(parts).isPositive();
    assertThat(below).isPositive();
  }

  @Test
  void italicsCorrectionsAndScriptAlternatesAreRead() {
    assertThat(FONT.math().italicsCorrection(3331)).isEqualTo(20);
    assertThat(FONT.math().italicsCorrection(FONT.glyph('('))).isZero();
    int x = FONT.glyph(0x1D465);
    int script = FONT.scriptGlyph(x, 1);
    int scriptScript = FONT.scriptGlyph(x, 2);
    assertThat(script).isNotEqualTo(x);
    assertThat(scriptScript).isNotIn(x, script);
    assertThat(FONT.scriptGlyph(x, 0)).isEqualTo(x);
    assertThat(FONT.scriptGlyph(0, 2)).isZero();
  }

  @Test
  void outlinesMatchTheirRecordedBoundsForEveryGlyph() {
    for (int g = 0; g < FONT.glyphCount(); g++) {
      Outline outline = FONT.outline(g);
      OpenTypeFont.Bounds bounds = FONT.bounds(g);
      if (outline.commands().isEmpty()) {
        assertThat(bounds).as("glyph %d", g).isEqualTo(new OpenTypeFont.Bounds(0, 0, 0, 0));
        continue;
      }
      double[] box = box(outline);
      // Exact for a simple glyph; a scaled component's points are rounded in the recorded box.
      double[] recorded = {bounds.xMin(), bounds.yMin(), bounds.xMax(), bounds.yMax()};
      for (int k = 0; k < 4; k++) {
        assertThat(box[k]).as("glyph %d", g).isCloseTo(recorded[k], within(1.0));
      }
    }
  }

  @Test
  void aCompositeGlyphIsItsComponentsPlaced() {
    // Glyph 29 is glyph 3 with glyph 729 placed over it.
    assertThat(FONT.outline(29).contours())
        .isEqualTo(FONT.outline(3).contours() + FONT.outline(729).contours());
    assertThat(FONT.bounds(29)).isEqualTo(new OpenTypeFont.Bounds(3, 0, 714, 826));
  }

  @Test
  void aGlyphsOutlineHasItsContours() {
    assertThat(FONT.outline(FONT.glyph('x')).contours()).isEqualTo(1);
    assertThat(FONT.outline(FONT.glyph('(')).contours()).isEqualTo(1);
    assertThat(FONT.outline(FONT.glyph('∑')).contours()).isEqualTo(1);
    assertThat(FONT.outline(FONT.glyph('=')).contours()).isEqualTo(2);
    assertThat(FONT.outline(FONT.glyph('8')).contours()).isEqualTo(3);
    assertThat(FONT.bounds(FONT.glyph('∫'))).isEqualTo(new OpenTypeFont.Bounds(30, -226, 654, 727));
    List<Outline.Command> commands = FONT.outline(FONT.glyph('x')).commands();
    assertThat(commands.getFirst()).isInstanceOf(Outline.MoveTo.class);
    assertThat(commands.getLast()).isInstanceOf(Outline.Close.class);
  }

  @Test
  void aFileWithoutTheTablesIsRefused() {
    assertThatThrownBy(() -> OpenTypeFont.read(new byte[64]))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static double[] box(Outline outline) {
    double[] box = {
      Double.POSITIVE_INFINITY,
      Double.POSITIVE_INFINITY,
      Double.NEGATIVE_INFINITY,
      Double.NEGATIVE_INFINITY
    };
    for (Outline.Command c : outline.commands()) {
      switch (c) {
        case Outline.MoveTo m -> grow(box, m.x(), m.y());
        case Outline.LineTo l -> grow(box, l.x(), l.y());
        case Outline.QuadTo q -> {
          grow(box, q.cx(), q.cy());
          grow(box, q.x(), q.y());
        }
        case Outline.Close close -> {}
      }
    }
    return box;
  }

  private static void grow(double[] box, double x, double y) {
    box[0] = Math.min(box[0], x);
    box[1] = Math.min(box[1], y);
    box[2] = Math.max(box[2], x);
    box[3] = Math.max(box[3], y);
  }
}
