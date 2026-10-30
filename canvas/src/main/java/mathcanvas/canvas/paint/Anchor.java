package mathcanvas.canvas.paint;

/**
 * Which point of a text's box sits at its anchor.
 *
 * @param horizontal the horizontal position
 * @param vertical the vertical position
 */
public record Anchor(Horizontal horizontal, Vertical vertical) {

  /** Where the anchor is across the text. */
  public enum Horizontal {
    /** The text starts at the anchor. */
    START,
    /** The text is centred on the anchor. */
    MIDDLE,
    /** The text ends at the anchor. */
    END
  }

  /** Where the anchor is up the text. */
  public enum Vertical {
    /** The text hangs below the anchor. */
    TOP,
    /** The text is centred on the anchor. */
    MIDDLE,
    /** The text sits on the anchor's baseline. */
    BASELINE,
    /** The text stands above the anchor. */
    BOTTOM
  }
}
