package mathcanvas.canvas.geom;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TicksTest {

  @Test
  void picksTheSmallestOneTwoFiveStepAtLeastSixtyPixels() {
    // 20 px per unit: 2 is 40 px, 5 is 100 px.
    Ticks t = Ticks.forAxis(-10, 10, 400);
    assertThat(t.mantissa()).isEqualTo(5);
    assertThat(t.exponent()).isEqualTo(0);
    assertThat(t.minorStep()).isEqualTo(1);
    // 30 px per unit: 2 is exactly 60 px.
    Ticks two = Ticks.forAxis(0, 10, 300);
    assertThat(two.step()).isEqualTo(2);
    assertThat(two.minorStep()).isEqualTo(1);
    // 600 px per unit: 0.1 is 60 px, its minor 0.02.
    Ticks tenth = Ticks.forAxis(0, 1, 600);
    assertThat(tenth.step()).isEqualTo(0.1);
    assertThat(tenth.minorStep()).isEqualTo(0.02);
  }

  @Test
  void indexesTheTicksInARange() {
    Ticks t = Ticks.forAxis(-10, 10, 400);
    assertThat(t.first(-10)).isEqualTo(-2);
    assertThat(t.last(10)).isEqualTo(2);
    assertThat(t.first(-9.9)).isEqualTo(-1);
    assertThat(t.value(-2)).isEqualTo(-10);
  }

  @Test
  void labelsWithTheFewestDigits() {
    Ticks half = new Ticks(5, -1, 1, -1);
    assertThat(half.label(0)).isEqualTo("0");
    assertThat(half.label(1)).isEqualTo("0.5");
    assertThat(half.label(2)).isEqualTo("1");
    assertThat(half.label(-3)).isEqualTo("−1.5");
    Ticks tenth = new Ticks(1, -1, 2, -2);
    assertThat(tenth.label(3)).isEqualTo("0.3");
    Ticks thousands = new Ticks(2, 3, 1, 3);
    assertThat(thousands.label(7)).isEqualTo("14000");
  }

  @Test
  void labelsInScientificFormAtTheExtremes() {
    Ticks millions = new Ticks(2, 5, 1, 5);
    assertThat(millions.label(9)).isEqualTo("1.8×10⁶");
    assertThat(millions.label(5)).isEqualTo("1×10⁶");
    assertThat(millions.label(4)).isEqualTo("800000");
    assertThat(millions.label(-12)).isEqualTo("−2.4×10⁶");
    Ticks tiny = new Ticks(5, -6, 1, -6);
    assertThat(tiny.label(1)).isEqualTo("5×10⁻⁶");
    assertThat(tiny.label(20)).isEqualTo("0.0001");
    assertThat(tiny.label(19)).isEqualTo("9.5×10⁻⁵");
  }

  @Test
  void widensTheStepUntilTheLabelsFit() {
    // 1e7 ± 1e-3 across 800 px: the first 1-2-5 step at least 60 px apart labels its ticks with
    // twelve significant digits, wider than the spacing.
    java.util.function.ToDoubleFunction<String> width = Ticks.estimate(12);
    for (double[] axis : new double[][] {{1e7 - 1e-3, 1e7 + 1e-3}, {1e15, 1e15 + 1e4}}) {
      Ticks t = Ticks.forAxis(axis[0], axis[1], 800, width);
      double spacing = t.step() / (axis[1] - axis[0]) * 800;
      assertThat(t.widestLabel(axis[0], axis[1], width) + Ticks.LABEL_GAP)
          .as("labels of %s fit %s px", t, spacing)
          .isLessThanOrEqualTo(spacing);
      Ticks smaller = t.previous();
      double smallerSpacing = smaller.step() / (axis[1] - axis[0]) * 800;
      assertThat(
              smallerSpacing < Ticks.MIN_SPACING
                  || smaller.widestLabel(axis[0], axis[1], width) + Ticks.LABEL_GAP
                      > smallerSpacing)
          .as("the step below %s is too close or its labels overlap", t)
          .isTrue();
    }
    // A measurement that makes every label 200 px wide forces a step at least 212 px apart.
    Ticks wide = Ticks.forAxis(-10, 10, 400, label -> 200);
    assertThat(wide.step() * 20).isGreaterThanOrEqualTo(212);
  }

  @Test
  void stepsAroundTheSequence() {
    Ticks t = new Ticks(5, -1, 1, -1);
    assertThat(t.next()).isEqualTo(new Ticks(1, 0, 2, -1));
    assertThat(t.next().next()).isEqualTo(new Ticks(2, 0, 1, 0));
    assertThat(t.next().previous()).isEqualTo(t);
    assertThat(t.previous()).isEqualTo(new Ticks(2, -1, 1, -1));
  }

  @Test
  void handlesSpansNearTheDoubleRange() {
    // 60 · span overflows here; the step must still be a 1-2-5 value at least 60 px apart whose
    // labels (1×10³⁰⁶ and so on) fit, not a tick exponent of 2³¹.
    Ticks t = Ticks.forAxis(-5e306, 5e306, 800);
    double spacing = t.step() / 1e307 * 800;
    assertThat(t.exponent()).isEqualTo(306);
    assertThat(spacing).isGreaterThanOrEqualTo(Ticks.MIN_SPACING).isLessThan(800);
    assertThat(t.widestLabel(-5e306, 5e306, Ticks.estimate(12)) + Ticks.LABEL_GAP)
        .isLessThanOrEqualTo(spacing);
    assertThat(t.first(-5e306)).isLessThan(0);
  }

  @Test
  void rejectsAnUnusableAxis() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> Ticks.forAxis(-1e308, 1e308, 100))
        .isInstanceOf(IllegalArgumentException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> Ticks.forAxis(1, 1, 100))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
