package mathcanvas.app.ui;

import java.util.Locale;
import javafx.scene.Node;
import javafx.scene.control.Slider;

/**
 * The Material slider's track: the part up to the value in the primary colour, the rest in the
 * secondary container's ({@code docs/reference/design.md}, **The screen**). JavaFX's slider paints
 * its track in one colour, so the track's style is set from the value as it changes, in the theme's
 * looked-up colours. Touched only on the FX thread.
 */
final class SliderTrack {

  private SliderTrack() {}

  /**
   * Keeps a slider's track filled up to its value.
   *
   * @param slider the slider
   */
  static void follow(Slider slider) {
    Runnable fill = () -> fill(slider);
    slider.skinProperty().subscribe(fill);
    slider.valueProperty().subscribe(fill);
    slider.minProperty().subscribe(fill);
    slider.maxProperty().subscribe(fill);
  }

  private static void fill(Slider slider) {
    Node track = slider.lookup(".track");
    if (track == null) {
      return;
    }
    double range = slider.getMax() - slider.getMin();
    double part = range > 0 ? (slider.getValue() - slider.getMin()) / range : 0;
    String at = String.format(Locale.ROOT, "%.2f%%", 100 * Math.max(0, Math.min(1, part)));
    track.setStyle(
        "-fx-background-color: linear-gradient(to right, -mc-primary "
            + at
            + ", -mc-secondary-container "
            + at
            + ");");
  }
}
