package mathcanvas.app.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.OptionalInt;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.parse.ParseException;
import org.junit.jupiter.api.Test;

/** The unit circle view's engine work: angles read in each mode, and the values. */
class TrigValuesTest {

  private static Environment in(AngleMode mode) {
    return Environment.EMPTY.withSettings(Settings.DEFAULT.withAngleMode(mode));
  }

  private static double read(String text, AngleMode mode) {
    return TrigValues.read(text, in(mode), Cancellation.NEVER).value();
  }

  private static List<String> texts(List<TrigValues.Value> values) {
    return values.stream().map(TrigValues.Value::text).toList();
  }

  @Test
  void aPlainNumberIsInTheModesUnitAndAnythingElseInRadians() {
    assertThat(read("30", AngleMode.DEGREES)).isCloseTo(Math.PI / 6, within());
    assertThat(read("50", AngleMode.GRADIANS)).isCloseTo(Math.PI / 4, within());
    assertThat(read("0.5", AngleMode.RADIANS)).isCloseTo(0.5, within());
    assertThat(read("15 + 15", AngleMode.DEGREES)).isCloseTo(Math.PI / 6, within());
    // π, as sin(π/6) reads it in every mode, and °, which is π/180
    assertThat(read("pi/6", AngleMode.DEGREES)).isCloseTo(Math.PI / 6, within());
    assertThat(read("π/4", AngleMode.GRADIANS)).isCloseTo(Math.PI / 4, within());
    assertThat(read("30°", AngleMode.RADIANS)).isCloseTo(Math.PI / 6, within());
    // reduced into one turn
    assertThat(read("-pi/2", AngleMode.RADIANS)).isCloseTo(3 * Math.PI / 2, within());
  }

  @Test
  void theSpecialAngleIsDecidedExactly() {
    assertThat(TrigValues.read("30", in(AngleMode.DEGREES), NEVER).special())
        .isEqualTo(OptionalInt.of(2));
    assertThat(TrigValues.read("pi/6 + 2pi", in(AngleMode.RADIANS), NEVER).special())
        .isEqualTo(OptionalInt.of(2));
    assertThat(TrigValues.read("-pi/2", in(AngleMode.GRADIANS), NEVER).special())
        .isEqualTo(OptionalInt.of(18));
    // a decimal near π/6 is not π/6: its values are the decimal's
    TrigValues.Angle near = TrigValues.read("0.52359877559", in(AngleMode.RADIANS), NEVER);
    assertThat(near.special()).isEmpty();
    assertThat(texts(TrigValues.values(near, near.special(), in(AngleMode.RADIANS), 15, NEVER)))
        .first()
        .isEqualTo("0.499999999992813");
    assertThat(TrigValues.read("30.0", in(AngleMode.DEGREES), NEVER).special()).isEmpty();
  }

  @Test
  void aHugeAngleIsReducedWhereItsValuesAre() {
    TrigValues.Angle huge = TrigValues.read("1e20", in(AngleMode.RADIANS), NEVER);
    assertThat(huge.value()).isBetween(0.0, 2 * Math.PI);
    List<String> values =
        texts(TrigValues.values(huge, OptionalInt.empty(), in(AngleMode.RADIANS), 15, NEVER));
    assertThat(Math.sin(huge.value())).isCloseTo(Double.parseDouble(values.get(0)), within(1e-9));
    assertThat(Math.cos(huge.value())).isCloseTo(Double.parseDouble(values.get(1)), within(1e-9));
  }

  @Test
  void aTypedAngleKeepsItsExactRadians() {
    TrigValues.Angle a = TrigValues.read("20", in(AngleMode.DEGREES), Cancellation.NEVER);
    assertThat(texts(TrigValues.values(a, OptionalInt.empty(), in(AngleMode.DEGREES), 10, NEVER)))
        .containsExactly("0.3420201433", "0.9396926208", "0.3639702343");
  }

  @Test
  void whatIsNotARealNumberIsRefused() {
    assertThat(TrigValues.read("x", in(AngleMode.DEGREES), Cancellation.NEVER)).isNull();
    assertThat(TrigValues.read("2 + i", in(AngleMode.RADIANS), Cancellation.NEVER)).isNull();
    assertThatThrownBy(() -> TrigValues.read("30 +", in(AngleMode.DEGREES), Cancellation.NEVER))
        .isInstanceOf(ParseException.class);
  }

  @Test
  void aSpecialAngleHasExactValuesInEveryMode() {
    for (AngleMode mode : AngleMode.values()) {
      assertThat(
              texts(
                  TrigValues.values(
                      TrigValues.Angle.of(Math.PI / 6), OptionalInt.of(2), in(mode), 10, NEVER)))
          .containsExactly("1/2", "√3/2", "√3/3");
    }
    assertThat(
            texts(
                TrigValues.values(
                    TrigValues.Angle.of(5 * Math.PI / 12),
                    OptionalInt.of(5),
                    in(AngleMode.DEGREES),
                    10,
                    NEVER)))
        .containsExactly("(√2 + √6)/4", "(√6 - √2)/4", "√3 + 2");
    List<TrigValues.Value> right =
        TrigValues.values(
            TrigValues.Angle.of(3 * Math.PI / 2),
            OptionalInt.of(18),
            in(AngleMode.RADIANS),
            10,
            NEVER);
    assertThat(texts(right)).containsExactly("-1", "0", null);
    assertThat(right.getFirst().box()).isNotNull();
    assertThat(right.getLast().box()).isNull();
  }

  @Test
  void anotherAngleHasDecimalsAtTheDigits() {
    TrigValues.Angle dragged = TrigValues.Angle.of(1);
    assertThat(
            texts(TrigValues.values(dragged, OptionalInt.empty(), in(AngleMode.DEGREES), 6, NEVER)))
        .containsExactly("0.841471", "0.540302", "1.55741");
  }

  private static final Cancellation NEVER = Cancellation.NEVER;

  private static org.assertj.core.data.Offset<Double> within() {
    return within(1e-12);
  }

  private static org.assertj.core.data.Offset<Double> within(double d) {
    return org.assertj.core.data.Offset.offset(d);
  }
}
