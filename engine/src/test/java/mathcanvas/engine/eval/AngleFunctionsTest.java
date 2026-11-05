package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Degrees, minutes and seconds: {@code dms}, {@code degrees} and the literal {@code d°m′s″}. */
class AngleFunctionsTest {

  @Test
  void dmsWritesDegreesAsDegreesMinutesAndSeconds() {
    assertThat(shown("dms(61/2)")).isEqualTo("⟨30, 30, 0⟩");
    assertThat(shown("dms(30.2561)")).isEqualTo("⟨30, 15, 21.96⟩");
    assertThat(shown("dms(2723/90)")).isEqualTo("⟨30, 15, 20⟩");
    // a rational multiple of π is an angle in radians
    assertThat(shown("dms(π/7)")).isEqualTo("⟨25, 42, 360/7⟩");
    assertThat(shown("dms(30°)")).isEqualTo("⟨30, 0, 0⟩");
    // a plain number is degrees in every mode
    assertThat(shown("dms(61/2)", Run.DEGREES)).isEqualTo("⟨30, 30, 0⟩");
  }

  @Test
  void theSignGoesOnTheFirstComponentThatIsNotZero() {
    assertThat(shown("dms(-61/2)")).isEqualTo("⟨-30, 30, 0⟩");
    assertThat(shown("dms(-1/2)")).isEqualTo("⟨0, -30, 0⟩");
    assertThat(shown("dms(-1/7200)")).isEqualTo("⟨0, 0, -1/2⟩");
    assertThat(shown("degrees(-30, 30, 0)")).isEqualTo("-61/2");
    assertThat(shown("degrees(0, -30, 0)")).isEqualTo("-1/2");
    assertThat(error("degrees(30, -1, 0)"))
        .isEqualTo(
            "only the first part of `degrees` that is not zero can be negative, as in `degrees(-30, 15, 0)`");
  }

  @Test
  void degreesIsTheInverse() {
    assertThat(shown("degrees(30, 15, 20)")).isEqualTo("2723/90");
    assertThat(shown("degrees(30, 15.5, 0)")).startsWith("30.258333");
    assertThat(shown("dms(degrees(12, 34, 56))")).isEqualTo("⟨12, 34, 56⟩");
    assertThat(error("degrees(i, 0, 0)")).isEqualTo("`degrees` needs real numbers");
  }

  @Test
  void anythingElseLeavesTheCall() {
    assertThat(shown("dms(x)")).isEqualTo("dms(x)");
    assertThat(shown("dms(√2)")).isEqualTo("dms(√2)");
    assertThat(shown("degrees(d, 0, 0)")).isEqualTo("degrees(d, 0, 0)");
  }

  @Test
  void theLiteralIsAnAngle() {
    assertThat(shown("30°15′20″")).isEqualTo("2723π/16200");
    assertThat(shown("sin(30°0′)")).isEqualTo("1/2");
    assertThat(shown("dms(30°15′20″)")).isEqualTo("⟨30, 15, 20⟩");
    assertThat(shown("dms(30°15′)", Run.DEGREES)).isEqualTo("⟨30, 15, 0⟩");
  }
}
