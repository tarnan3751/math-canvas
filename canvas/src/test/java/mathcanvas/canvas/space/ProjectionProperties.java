package mathcanvas.canvas.space;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;

/** The projection over random cameras and points of the cube. */
class ProjectionProperties {

  @Property
  void projectingAndUnprojectingRoundTrips(
      @ForAll @DoubleRange(min = -3.14, max = 3.14) double yaw,
      @ForAll @DoubleRange(min = -1.55, max = 1.55) double pitch,
      @ForAll @DoubleRange(min = 2.5, max = 40) double distance,
      @ForAll @DoubleRange(min = -1, max = 1) double x,
      @ForAll @DoubleRange(min = -1, max = 1) double y,
      @ForAll @DoubleRange(min = -1, max = 1) double z) {
    Projection p = new Projection(new Camera(yaw, pitch, distance, 0, 0, 0), 800, 600);
    double[] s = new double[3];
    p.project(x, y, z, s);
    assertThat(s[2]).isGreaterThan(Projection.NEAR);
    double[] back = p.unproject(s[0], s[1], s[2]);
    assertThat(back[0]).isCloseTo(x, within(1e-9));
    assertThat(back[1]).isCloseTo(y, within(1e-9));
    assertThat(back[2]).isCloseTo(z, within(1e-9));
  }

  @Property
  void theTargetIsAtTheCentreAndTheCubeInView(
      @ForAll @DoubleRange(min = -3.14, max = 3.14) double yaw,
      @ForAll @DoubleRange(min = -1.55, max = 1.55) double pitch,
      @ForAll @DoubleRange(min = 8, max = 40) double distance) {
    Projection p = new Projection(new Camera(yaw, pitch, distance, 0, 0, 0), 800, 600);
    double[] s = new double[3];
    p.project(0, 0, 0, s);
    assertThat(s[0]).isCloseTo(400, within(1e-9));
    assertThat(s[1]).isCloseTo(300, within(1e-9));
    assertThat(s[2]).isCloseTo(distance, within(1e-9));
    // from the default distance on, every corner of the cube is on the screen
    for (int k = 0; k < 8; k++) {
      p.project((k & 1) * 2 - 1, (k & 2) - 1, (k & 4) / 2.0 - 1, s);
      assertThat(s[0]).isBetween(0.0, 800.0);
      assertThat(s[1]).isBetween(0.0, 600.0);
    }
  }
}
