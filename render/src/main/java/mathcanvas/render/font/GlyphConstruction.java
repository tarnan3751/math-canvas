package mathcanvas.render.font;

import java.util.List;

/**
 * How a glyph grows in one direction: its size variants, smallest first (the glyph itself first),
 * and the assembly that builds it larger than the largest, if it has one.
 *
 * @param variants the variants
 * @param assembly the assembly, or null
 */
public record GlyphConstruction(List<Variant> variants, Assembly assembly) {

  /** Copies the variants. */
  public GlyphConstruction {
    variants = List.copyOf(variants);
  }

  /**
   * A size variant.
   *
   * @param glyph the glyph
   * @param advance its size in the direction of growth, in font units
   */
  public record Variant(int glyph, int advance) {}

  /**
   * A glyph assembly: parts from the bottom (or the left), the extenders repeated as needed.
   *
   * @param italicsCorrection the italics correction of the assembled glyph
   * @param parts the parts
   */
  public record Assembly(int italicsCorrection, List<Part> parts) {

    /** Copies the parts. */
    public Assembly {
      parts = List.copyOf(parts);
    }
  }

  /**
   * A part of an assembly.
   *
   * @param glyph the glyph
   * @param startConnector the length of the connector at its start
   * @param endConnector the length of the connector at its end
   * @param fullAdvance its full size in the direction of growth
   * @param extender whether it may be repeated or left out
   */
  public record Part(
      int glyph, int startConnector, int endConnector, int fullAdvance, boolean extender) {}
}
