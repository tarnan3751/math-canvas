package mathcanvas.canvas.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** The orbit camera's limits and basis. */
class CameraTest {

  @Test
  void orbitingWrapsTheYawAndHoldsThePitch() {
    Camera c = Camera.DEFAULT.orbit(10 * Math.PI + 0.5, 0);
    assertThat(c.yaw()).isCloseTo(Camera.DEFAULT.yaw() + 0.5, within(1e-9));
    Camera turned = new Camera(Math.PI, 0, 6, 0, 0, 0).orbit(0.25, 0);
    assertThat(turned.yaw()).isCloseTo(-Math.PI + 0.25, within(1e-12));
    assertThat(Camera.DEFAULT.orbit(0, 100).pitch()).isEqualTo(Camera.MAX_PITCH);
    assertThat(Camera.DEFAULT.orbit(0, -100).pitch()).isEqualTo(-Camera.MAX_PITCH);
    assertThat(Camera.DEFAULT.orbit(Double.NaN, 1)).isEqualTo(Camera.DEFAULT);
  }

  @Test
  void zoomingHoldsTheDistanceOutsideTheCube() {
    assertThat(Camera.DEFAULT.zoom(1e9).distance()).isEqualTo(Camera.MIN_DISTANCE);
    assertThat(Camera.DEFAULT.zoom(1e-9).distance()).isEqualTo(Camera.MAX_DISTANCE);
    assertThat(Camera.DEFAULT.zoom(2).distance()).isEqualTo(4);
    assertThat(Camera.DEFAULT.zoom(0)).isEqualTo(Camera.DEFAULT);
    assertThat(Camera.MIN_DISTANCE).isGreaterThan(Math.sqrt(3));
  }

  @Test
  void panningMovesTheTargetAcrossTheLineOfSightWithinTheCube() {
    Camera c = new Camera(0, 0, 6, 0, 0, 0).pan(0.5, 0.25);
    // from +x looking at the origin, right is +y and up is +z; the target moves the other way
    assertThat(c.targetX()).isCloseTo(0, within(1e-12));
    assertThat(c.targetY()).isCloseTo(-0.5, within(1e-12));
    assertThat(c.targetZ()).isCloseTo(-0.25, within(1e-12));
    Camera far = c.pan(100, -100);
    assertThat(far.targetY()).isEqualTo(-1);
    assertThat(far.targetZ()).isEqualTo(1);
  }

  @Test
  void theBasisIsOrthonormalWithZUp() {
    for (double yaw = -3; yaw <= 3; yaw += 0.7) {
      for (double pitch = -1.5; pitch <= 1.5; pitch += 0.5) {
        Camera c = new Camera(yaw, pitch, 5, 0.1, -0.2, 0.3);
        double[] f = c.forward();
        double[] r = c.right();
        double[] u = c.up();
        assertThat(dot(f, f)).isCloseTo(1, within(1e-12));
        assertThat(dot(r, r)).isCloseTo(1, within(1e-12));
        assertThat(dot(u, u)).isCloseTo(1, within(1e-12));
        assertThat(dot(f, r)).isCloseTo(0, within(1e-12));
        assertThat(dot(f, u)).isCloseTo(0, within(1e-12));
        assertThat(dot(r, u)).isCloseTo(0, within(1e-12));
        assertThat(u[2]).isGreaterThan(0);
        double[] e = c.eye();
        assertThat(e[0] + 5 * f[0]).isCloseTo(0.1, within(1e-12));
        assertThat(e[2] + 5 * f[2]).isCloseTo(0.3, within(1e-12));
      }
    }
  }

  @Test
  void theEyeNeverEntersTheCube() {
    // a target at a corner, looked at from the corner's own direction, would put the eye inside
    assertThatThrownBy(() -> new Camera(Math.toRadians(-135), Math.toRadians(-35.26), 2.5, 1, 1, 1))
        .hasMessageContaining("beyond its target");
    Camera corner = new Camera(Math.toRadians(-135), -0.6, 8, 1, 1, 1).zoom(1e9);
    assertThat(corner.distance()).isCloseTo(2.5 + Math.sqrt(3), within(1e-12));
    Camera panned = new Camera(0.3, 0.2, 2.5, 0, 0, 0).pan(5, 5);
    for (Camera c : new Camera[] {corner, panned, panned.orbit(2, -1), corner.orbit(-3, 1.2)}) {
      double[] e = c.eye();
      assertThat(Math.sqrt(e[0] * e[0] + e[1] * e[1] + e[2] * e[2]))
          .isGreaterThanOrEqualTo(Camera.MIN_DISTANCE - 1e-12);
    }
  }

  @Test
  void aCameraOutsideItsLimitsIsRefused() {
    assertThatThrownBy(() -> new Camera(4, 0, 6, 0, 0, 0)).hasMessageContaining("yaw");
    assertThatThrownBy(() -> new Camera(0, 1.56, 6, 0, 0, 0)).hasMessageContaining("pitch");
    assertThatThrownBy(() -> new Camera(0, 0, 2, 0, 0, 0)).hasMessageContaining("distance");
    assertThatThrownBy(() -> new Camera(0, 0, 6, 0, 1.5, 0)).hasMessageContaining("target");
    assertThatThrownBy(() -> new Camera(Double.NaN, 0, 6, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static double dot(double[] a, double[] b) {
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
  }
}
