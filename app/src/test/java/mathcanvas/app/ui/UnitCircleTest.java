package mathcanvas.app.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.OptionalInt;
import mathcanvas.engine.eval.AngleMode;
import org.junit.jupiter.api.Test;

/** The unit circle view's arithmetic: snapping, the arrow keys' steps, the texts, the calls. */
class UnitCircleTest {

  private static double deg(double d) {
    return Math.toRadians(d);
  }

  @Test
  void aDraggedAngleSnapsWithinThreeDegrees() {
    assertThat(UnitCircle.snap(deg(32.9))).isEqualTo(OptionalInt.of(2));
    assertThat(UnitCircle.snap(deg(27.1))).isEqualTo(OptionalInt.of(2));
    assertThat(UnitCircle.snap(deg(33.1))).isEmpty();
    assertThat(UnitCircle.snap(deg(37.5))).isEmpty();
    // across 0: 358° and -1° snap to 0, never to 24
    assertThat(UnitCircle.snap(deg(358))).isEqualTo(OptionalInt.of(0));
    assertThat(UnitCircle.snap(deg(-1))).isEqualTo(OptionalInt.of(0));
    assertThat(UnitCircle.snap(deg(345 + 2))).isEqualTo(OptionalInt.of(23));
  }

  @Test
  void aSpecialAngleIsOneOnlyToRounding() {
    assertThat(UnitCircle.special(Math.PI / 6)).isEqualTo(OptionalInt.of(2));
    assertThat(UnitCircle.special(deg(30.001))).isEmpty();
  }

  @Test
  void thePointerGivesTheAngleWithYUp() {
    assertThat(UnitCircle.angleAt(1, 0)).isEqualTo(0);
    assertThat(UnitCircle.angleAt(0, 1)).isCloseTo(Math.PI / 2, within(1e-15));
    assertThat(UnitCircle.angleAt(0, -1)).isCloseTo(3 * Math.PI / 2, within(1e-15));
    assertThat(UnitCircle.normalize(-Math.PI / 6)).isCloseTo(11 * Math.PI / 6, within(1e-15));
    assertThat(UnitCircle.normalize(2 * Math.PI)).isEqualTo(0);
  }

  @Test
  void theArrowKeysStepToTheNextSpecialAngleOrOneDegree() {
    assertThat(Math.toDegrees(UnitCircle.step(deg(30), true, false))).isCloseTo(45, within(1e-9));
    assertThat(Math.toDegrees(UnitCircle.step(deg(31), true, false))).isCloseTo(45, within(1e-9));
    assertThat(Math.toDegrees(UnitCircle.step(deg(31), false, false))).isCloseTo(30, within(1e-9));
    assertThat(Math.toDegrees(UnitCircle.step(deg(30), false, false))).isCloseTo(15, within(1e-9));
    assertThat(Math.toDegrees(UnitCircle.step(0, false, false))).isCloseTo(345, within(1e-9));
    assertThat(UnitCircle.step(deg(345), true, false)).isEqualTo(0);
    assertThat(Math.toDegrees(UnitCircle.step(deg(30), true, true))).isCloseTo(31, within(1e-9));
    assertThat(Math.toDegrees(UnitCircle.step(0, false, true))).isCloseTo(359, within(1e-9));
  }

  @Test
  void aSpecialAngleIsWrittenInEachUnit() {
    assertThat(UnitCircle.radians(0)).isEqualTo("0");
    assertThat(UnitCircle.radians(2)).isEqualTo("π/6");
    assertThat(UnitCircle.radians(12)).isEqualTo("π");
    assertThat(UnitCircle.radians(18)).isEqualTo("3π/2");
    assertThat(UnitCircle.radians(11)).isEqualTo("11π/12");
    assertThat(UnitCircle.inUnit(2, AngleMode.DEGREES)).isEqualTo("30");
    assertThat(UnitCircle.inUnit(2, AngleMode.GRADIANS)).isEqualTo("100/3");
    assertThat(UnitCircle.inUnit(3, AngleMode.GRADIANS)).isEqualTo("50");
    assertThat(UnitCircle.decimal(1, AngleMode.DEGREES)).isEqualTo("57.3");
    assertThat(UnitCircle.decimal(1, AngleMode.RADIANS)).isEqualTo("1");
    assertThat(UnitCircle.decimal(0.82467, AngleMode.RADIANS)).isEqualTo("0.8247");
  }

  @Test
  void theCallIsInTheModesUnit() {
    assertThat(UnitCircle.call("sin", 2, AngleMode.RADIANS)).isEqualTo("sin(pi/6)");
    assertThat(UnitCircle.call("cos", 18, AngleMode.RADIANS)).isEqualTo("cos(3pi/2)");
    assertThat(UnitCircle.call("tan", 2, AngleMode.DEGREES)).isEqualTo("tan(30)");
    assertThat(UnitCircle.call("sin", 2, AngleMode.GRADIANS)).isEqualTo("sin(100/3)");
  }

  @Test
  void aLiveValueIsRoundedToTheDigits() {
    assertThat(UnitCircle.live(Math.sin(1), 10)).isEqualTo("0.8414709848");
    assertThat(UnitCircle.live(-0.5, 10)).isEqualTo("-0.5");
    assertThat(UnitCircle.live(1e-17, 10)).isEqualTo("0");
    assertThat(UnitCircle.live(Math.PI, 40)).isEqualTo("3.14159265358979");
    assertThat(Double.isNaN(UnitCircle.tan(Math.PI / 2))).isTrue();
  }
}
