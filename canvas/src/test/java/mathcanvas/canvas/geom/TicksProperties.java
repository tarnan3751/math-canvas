package mathcanvas.canvas.geom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.HashSet;
import java.util.Set;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;

/**
 * Ticks over random axes from 1e-8 to 1e9 units across 100 to 4000 pixels, centred anywhere within
 * 1e4 spans of the origin: the step is a 1-2-5 value 60 to 150 pixels apart, the minor step divides
 * it into two or five, and the labels of the visible ticks are all different.
 */
class TicksProperties {

  @Property
  void stepsAreTheSmallestOneTwoFiveWhoseLabelsFit(
      @ForAll @DoubleRange(min = -8, max = 9) double spanExponent,
      @ForAll @DoubleRange(min = -1e4, max = 1e4) double offset,
      @ForAll @DoubleRange(min = 100, max = 4000) double pixels) {
    double span = Math.pow(10, spanExponent);
    double min = offset * span;
    Ticks t = Ticks.forAxis(min, min + span, pixels);
    assertThat(t.mantissa()).isIn(1, 2, 5);
    var width = Ticks.estimate(Ticks.DEFAULT_LABEL_SIZE);
    double spacing = t.step() / span * pixels;
    assertThat(spacing).isGreaterThanOrEqualTo(Ticks.MIN_SPACING);
    assertThat(
            spacing >= pixels || t.widestLabel(min, min + span, width) + Ticks.LABEL_GAP <= spacing)
        .as("the labels of %s fit %s px", t, spacing)
        .isTrue();
    Ticks smaller = t.previous();
    double smallerSpacing = smaller.step() / span * pixels;
    assertThat(
            smallerSpacing < Ticks.MIN_SPACING
                || smaller.widestLabel(min, min + span, width) + Ticks.LABEL_GAP > smallerSpacing)
        .as("%s is the smallest step that fits", t)
        .isTrue();
    assertThat(t.step() / t.minorStep()).isCloseTo(t.mantissa() == 2 ? 2 : 5, within(1e-9));
  }

  @Property
  void visibleLabelsAreDistinct(
      @ForAll @DoubleRange(min = -8, max = 9) double spanExponent,
      @ForAll @DoubleRange(min = -1e4, max = 1e4) double offset,
      @ForAll @DoubleRange(min = 100, max = 4000) double pixels) {
    double span = Math.pow(10, spanExponent);
    double min = offset * span;
    Ticks t = Ticks.forAxis(min, min + span, pixels);
    Set<String> labels = new HashSet<>();
    for (long i = t.first(min); i <= t.last(min + span); i++) {
      assertThat(labels.add(t.label(i))).as("label of tick %d", i).isTrue();
    }
  }
}
