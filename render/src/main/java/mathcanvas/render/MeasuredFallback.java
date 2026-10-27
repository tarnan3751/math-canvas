package mathcanvas.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javafx.geometry.Bounds;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import mathcanvas.render.typeset.Fallback;

/**
 * The metrics of text the font lacks, as JavaFX draws it in the default font, measured once per
 * text and size and kept. A {@link MathView} typesetting on the FX thread measures, and so fills
 * the map, for the layouts it is given; a worker that meets a text not yet measured measures it
 * once off the FX thread, with a fresh {@code Text} attached to no scene, which JavaFX permits. The
 * JavaFX classes are loaded only then ({@link Measure}).
 */
final class MeasuredFallback implements Fallback {

  static final MeasuredFallback INSTANCE = new MeasuredFallback();

  private record Key(String text, double size) {}

  private final Map<Key, Metrics> metrics = new ConcurrentHashMap<>();

  private MeasuredFallback() {}

  @Override
  public Metrics measure(String text, double size) {
    return metrics.computeIfAbsent(new Key(text, size), k -> Measure.of(k.text(), k.size()));
  }

  /** The JavaFX measurement, in its own class so that it loads only when first needed. */
  private static final class Measure {

    private Measure() {}

    static Metrics of(String text, double size) {
      Text t = new Text(text);
      t.setFont(Font.font(size));
      Bounds b = t.getLayoutBounds();
      return new Metrics(b.getWidth(), -b.getMinY(), b.getMaxY());
    }
  }
}
