package mathcanvas.app.ui;

import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.engine.print.Format;
import org.junit.jupiter.api.Test;

class ReadoutTest {

  /** 40 pixels to a unit: a pixel is 0.025, so two decimal places. */
  private static final Viewport VIEW = Viewport.centred(0, 0, 1 / 40.0, 640, 480, true);

  private static Hit at(double x, double y) {
    return new Hit(0, Hit.Kind.POLYLINE, 3, 0.5, x, y, 1);
  }

  @Test
  void roundsToAPixelAndPrintsInTheFormat() {
    assertThat(Readout.text(at(1.23456, -0.5), VIEW, Format.EXACT)).isEqualTo("x = 1.23, y = -0.5");
    assertThat(Readout.text(at(2, 0.001), VIEW, Format.EXACT)).isEqualTo("x = 2.0, y = 0.0");
    assertThat(Readout.text(at(1234.5678, 1), VIEW, Format.scientific(3)))
        .isEqualTo("x = 1.23e+3, y = 1e+0");
  }

  @Test
  void zoomingInShowsMoreDigits() {
    Viewport near = Viewport.centred(0, 0, 1e-5, 640, 480, true);

    assertThat(Readout.text(at(0.123456789, 0), near, Format.EXACT))
        .isEqualTo("x = 0.12346, y = 0.0");
  }

  @Test
  void aCoordinateThatIsNotFiniteHasNoReadout() {
    assertThat(Readout.text(at(Double.NaN, 1), VIEW, Format.EXACT)).isNull();
    assertThat(Readout.text(at(1, Double.POSITIVE_INFINITY), VIEW, Format.EXACT)).isNull();
  }

  @Test
  void aSlopeIsRoundedToTheFinerAxis() {
    assertThat(Readout.text(at(1, 1), 2.0004, VIEW, Format.EXACT))
        .isEqualTo("x = 1.0, y = 1.0, slope = 2.0");
    assertThat(Readout.text(at(1, 1), -0.125, VIEW, Format.EXACT))
        .isEqualTo("x = 1.0, y = 1.0, slope = -0.12");
    assertThat(Readout.text(at(1, 1), Double.NaN, VIEW, Format.EXACT))
        .isEqualTo("x = 1.0, y = 1.0");
  }
}
