package mathcanvas.render.font;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The OpenType MATH table: its constants, the italics corrections, and the vertical and horizontal
 * glyph constructions. The top accent attachments, the extended-shape coverage and the kerning
 * information are in the table too and are read when accents need them.
 */
public final class MathTable {

  private final MathConstants constants;
  private final Map<Integer, Integer> italicsCorrections;
  private final int minConnectorOverlap;
  private final Map<Integer, GlyphConstruction> vertical;
  private final Map<Integer, GlyphConstruction> horizontal;

  private MathTable(
      MathConstants constants,
      Map<Integer, Integer> italicsCorrections,
      int minConnectorOverlap,
      Map<Integer, GlyphConstruction> vertical,
      Map<Integer, GlyphConstruction> horizontal) {
    this.constants = constants;
    this.italicsCorrections = italicsCorrections;
    this.minConnectorOverlap = minConnectorOverlap;
    this.vertical = vertical;
    this.horizontal = horizontal;
  }

  /**
   * Returns the constants.
   *
   * @return the constants
   */
  public MathConstants constants() {
    return constants;
  }

  /**
   * Returns a glyph's italics correction.
   *
   * @param glyph the glyph
   * @return the correction in font units, 0 when the table gives none
   */
  public int italicsCorrection(int glyph) {
    return italicsCorrections.getOrDefault(glyph, 0);
  }

  /**
   * Returns the least overlap between two parts of an assembly.
   *
   * @return the overlap in font units
   */
  public int minConnectorOverlap() {
    return minConnectorOverlap;
  }

  /**
   * Returns how a glyph grows vertically.
   *
   * @param glyph the glyph
   * @return the construction, or null when it does not grow
   */
  public GlyphConstruction vertical(int glyph) {
    return vertical.get(glyph);
  }

  /**
   * Returns how a glyph grows horizontally.
   *
   * @param glyph the glyph
   * @return the construction, or null when it does not grow
   */
  public GlyphConstruction horizontal(int glyph) {
    return horizontal.get(glyph);
  }

  static MathTable read(Bytes b, int at) {
    MathConstants constants = MathConstants.read(b, at + b.u16(at + 4));
    int glyphInfo = at + b.u16(at + 6);
    Map<Integer, Integer> italics = new HashMap<>();
    int italicsInfo = glyphInfo + b.u16(glyphInfo);
    Map<Integer, Integer> coverage = Coverage.read(b, italicsInfo + b.u16(italicsInfo));
    for (Map.Entry<Integer, Integer> e : coverage.entrySet()) {
      italics.put(e.getKey(), b.s16(italicsInfo + 4 + 4 * e.getValue()));
    }
    int variants = at + b.u16(at + 8);
    int verticalCount = b.u16(variants + 6);
    Map<Integer, GlyphConstruction> vertical =
        constructions(b, variants, variants + b.u16(variants + 2), verticalCount, 10);
    Map<Integer, GlyphConstruction> horizontal =
        constructions(
            b,
            variants,
            variants + b.u16(variants + 4),
            b.u16(variants + 8),
            10 + 2 * verticalCount);
    return new MathTable(constants, italics, b.u16(variants), vertical, horizontal);
  }

  private static Map<Integer, GlyphConstruction> constructions(
      Bytes b, int variants, int coverageAt, int count, int offsetsAt) {
    Map<Integer, GlyphConstruction> out = new HashMap<>();
    if (count == 0) {
      return out;
    }
    for (Map.Entry<Integer, Integer> e : Coverage.read(b, coverageAt).entrySet()) {
      int construction = variants + b.u16(variants + offsetsAt + 2 * e.getValue());
      List<GlyphConstruction.Variant> list = new ArrayList<>();
      int n = b.u16(construction + 2);
      for (int k = 0; k < n; k++) {
        int record = construction + 4 + 4 * k;
        list.add(new GlyphConstruction.Variant(b.u16(record), b.u16(record + 2)));
      }
      GlyphConstruction.Assembly assembly = null;
      if (b.u16(construction) != 0) {
        int a = construction + b.u16(construction);
        List<GlyphConstruction.Part> parts = new ArrayList<>();
        int partCount = b.u16(a + 4);
        for (int k = 0; k < partCount; k++) {
          int part = a + 6 + 10 * k;
          parts.add(
              new GlyphConstruction.Part(
                  b.u16(part),
                  b.u16(part + 2),
                  b.u16(part + 4),
                  b.u16(part + 6),
                  (b.u16(part + 8) & 1) != 0));
        }
        assembly = new GlyphConstruction.Assembly(b.s16(a), parts);
      }
      out.put(e.getKey(), new GlyphConstruction(list, assembly));
    }
    return out;
  }
}
