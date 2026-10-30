package mathcanvas.canvas.geom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class ViewportTest {

  private static final Viewport UNLOCKED = new Viewport(-10, 10, -5, 5, 400, 200, false);

  @Test
  void mapsCornersAndYUp() {
    assertThat(UNLOCKED.toScreenX(-10)).isEqualTo(0);
    assertThat(UNLOCKED.toScreenX(10)).isEqualTo(400);
    assertThat(UNLOCKED.toScreenY(5)).isEqualTo(0);
    assertThat(UNLOCKED.toScreenY(-5)).isEqualTo(200);
    assertThat(UNLOCKED.toScreenY(0)).isEqualTo(100);
    assertThat(UNLOCKED.toWorldX(200)).isEqualTo(0);
    assertThat(UNLOCKED.toWorldY(0)).isEqualTo(5);
    assertThat(UNLOCKED.pixelsPerUnitX()).isEqualTo(20);
    assertThat(UNLOCKED.pixelsPerUnitY()).isEqualTo(20);
  }

  @Test
  void panMovesTheContentWithTheDrag() {
    Viewport moved = UNLOCKED.pan(20, 40);
    // The world point that was under (100, 50) is now under (120, 90).
    double wx = UNLOCKED.toWorldX(100);
    double wy = UNLOCKED.toWorldY(50);
    assertThat(moved.toScreenX(wx)).isCloseTo(120, within(1e-9));
    assertThat(moved.toScreenY(wy)).isCloseTo(90, within(1e-9));
  }

  @Test
  void zoomKeepsThePointUnderTheCursor() {
    Viewport zoomed = UNLOCKED.zoom(2, 300, 50);
    assertThat(zoomed.toWorldX(300)).isCloseTo(UNLOCKED.toWorldX(300), within(1e-12));
    assertThat(zoomed.toWorldY(50)).isCloseTo(UNLOCKED.toWorldY(50), within(1e-12));
    assertThat(zoomed.pixelsPerUnitX()).isCloseTo(40, within(1e-9));
  }

  @Test
  void lockingWidensTheNarrowerAxisAboutItsCentre() {
    Viewport v = new Viewport(-1, 1, -10, 10, 200, 200, true);
    assertThat(v.yMin()).isEqualTo(-10);
    assertThat(v.yMax()).isEqualTo(10);
    assertThat(v.xMin()).isEqualTo(-10);
    assertThat(v.xMax()).isEqualTo(10);
    Viewport stretched = new Viewport(0, 4, 0, 1, 400, 400, false);
    Viewport locked = stretched.withAspectLocked(true);
    assertThat(locked.pixelsPerUnitX()).isEqualTo(locked.pixelsPerUnitY());
    assertThat(locked.yMin() + locked.yMax()).isCloseTo(1, within(1e-12));
    assertThat(locked.xMin()).isEqualTo(0);
  }

  @Test
  void resizeKeepsCentreAndScale() {
    Viewport bigger = UNLOCKED.resize(800, 100);
    assertThat(bigger.pixelsPerUnitX()).isEqualTo(20);
    assertThat(bigger.pixelsPerUnitY()).isEqualTo(20);
    assertThat(bigger.toWorldX(400)).isEqualTo(0);
    assertThat(bigger.toWorldY(50)).isEqualTo(0);
    assertThat(UNLOCKED.resize(400, 200)).isSameAs(UNLOCKED);
  }

  @Test
  void zoomStopsAtTheSupportedSpans() {
    Viewport far = new Viewport(-1e14, 1e14, -1e14, 1e14, 100, 100, true);
    assertThat(far.zoom(0.1, 50, 50)).isSameAs(far);
    Viewport near = new Viewport(1e6, 1e6 + 1e-5, 0, 1e-5, 100, 100, true);
    assertThat(near.zoom(100, 50, 50)).isSameAs(near);
    assertThat(near.zoom(2, 50, 50)).isNotSameAs(near);
  }

  @Test
  void rejectsBadWindows() {
    assertThatThrownBy(() -> new Viewport(1, 1, 0, 1, 10, 10, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Viewport(0, 1, 0, Double.NaN, 10, 10, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Viewport(0, 1, 0, 1, 0, 10, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> UNLOCKED.zoom(0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
    // Finite bounds whose span overflows: no pixels per unit to speak of.
    assertThatThrownBy(() -> new Viewport(-1e308, 1e308, 0, 1, 10, 10, false))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Viewport(0, 1, -1e308, 1e308, 10, 10, false))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
