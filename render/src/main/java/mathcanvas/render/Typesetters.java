package mathcanvas.render;

import mathcanvas.render.font.OpenTypeFont;
import mathcanvas.render.typeset.Typesetter;

/**
 * The shared, thread-safe typesetter: the bundled font, and text the font lacks measured as {@link
 * MathView} draws it. A worker typesets with it and hands the box tree to {@link MathView#setBox};
 * loading this class and typesetting loads no JavaFX scene class until a text the font lacks is
 * measured for the first time ({@link MeasuredFallback}).
 */
public final class Typesetters {

  private Typesetters() {}

  /**
   * Returns the standard typesetter, the one every {@link MathView} uses.
   *
   * @return the typesetter
   */
  public static Typesetter standard() {
    return Holder.STANDARD;
  }

  private static final class Holder {
    static final Typesetter STANDARD =
        new Typesetter(OpenTypeFont.stix(), MeasuredFallback.INSTANCE);

    private Holder() {}
  }
}
