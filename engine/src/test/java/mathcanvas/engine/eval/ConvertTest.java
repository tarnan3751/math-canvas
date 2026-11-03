package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** {@code convert}, {@code units} and {@code magnitude} (§14.26), with the conversion's step. */
class ConvertTest {

  @Test
  void convertWritesAQuantityInOtherUnitsOfItsDimension() {
    assertThat(shown("convert(5 _km/_h, _m/_s)")).isEqualTo("25/18 _m/_s");
    assertThat(shown("convert(6 _N _m, _J)")).isEqualTo("6 _J");
    assertThat(shown("convert(3 _N * 2 _m, _J)")).isEqualTo("6 _J");
    assertThat(shown("convert(1 _mi, _km)")).isEqualTo("25146/15625 _km");
    assertThat(shown("convert(1 _kWh, _MJ)")).isEqualTo("18/5 _MJ");
    assertThat(shown("convert(3 _m + 2 _cm, _cm)")).isEqualTo("302 _cm");
    assertThat(shown("convert(π, _deg)")).isEqualTo("180 _deg");
    assertThat(shown("convert(x _ft, _in)")).isEqualTo("12x _in");
    assertThat(shown("convert(2.5 _h, _min)")).isEqualTo("150.0 _min");
  }

  @Test
  void temperaturesConvertOnTheAbsoluteScale() {
    assertThat(shown("convert(20 _degC, _degF)")).isEqualTo("68 _degF");
    assertThat(shown("convert(10 _degC, _K)")).isEqualTo("5663/20 _K");
    assertThat(shown("convert(212 _degF, _degC)")).isEqualTo("100 _degC");
    assertThat(shown("convert(x _degC, _degF)")).isEqualTo("(9x/5 + 32) _degF");
    // a temperature in a compound unit is a difference
    assertThat(shown("convert(5 _degC/_s, _K/_s)")).isEqualTo("5 _K/_s");
  }

  @Test
  void unitsAndMagnitude() {
    assertThat(shown("units(5 _km/_h)")).isEqualTo("_km/_h");
    assertThat(shown("magnitude(5 _km/_h)")).isEqualTo("5");
    assertThat(shown("units(3)")).isEqualTo("1");
    assertThat(shown("magnitude(3 _m + 2 _cm)")).isEqualTo("151/50");
  }

  @Test
  void theConversionIsOneStep() {
    assertThat(NarrationTest.narrated("steps(convert(5 _km/_h, _m/_s))"))
        .containsExactly(
            "1 _km = 1000 _m. 1 _h = 3600 _s. So 5 _km/_h = 5000/3600 _m/_s = 25/18 _m/_s"
                + " | 25/18 _m/_s");
    assertThat(NarrationTest.narrated("steps(convert(36 _km/_h, _m/_s))"))
        .containsExactly(
            "1 _km = 1000 _m. 1 _h = 3600 _s. So 36 _km/_h = 36000/3600 _m/_s = 10 _m/_s"
                + " | 10 _m/_s");
    assertThat(NarrationTest.narrated("steps(convert(1 _kWh, _MJ))"))
        .containsExactly(
            "1 _kWh = 3600000 _kg _m²/_s². 1 _MJ = 1000000 _kg _m²/_s². So _kWh ="
                + " 3600000/1000000 _MJ = 18/5 _MJ | 18/5 _MJ");
    // in lowest terms already: no quotient
    assertThat(NarrationTest.narrated("steps(convert(3 _m, _cm))"))
        .containsExactly("1 _cm = 1/100 _m. So 3 _m = 300 _cm | 300 _cm");
    assertThat(NarrationTest.narrated("steps(convert(6 _N _m, _J))"))
        .containsExactly("1 _N = _kg _m/_s². 1 _J = _kg _m²/_s². So 6 _N _m = 6 _J | 6 _J");
  }

  @Test
  void theErrors() {
    assertThat(error("convert(3 _m, _s)")).isEqualTo("cannot convert m to s");
    assertThat(error("convert(3 _km/_h, _s)")).isEqualTo("cannot convert km/h to s");
    assertThat(error("convert(3 _m/_s^2, _s)")).isEqualTo("cannot convert m/s² to s");
    // 0 _degC is the number 0 in canonical form (§14.26)
    assertThat(error("convert(0 _degC, _degF)"))
        .isEqualTo(
            "a temperature of 0 is the number 0, which keeps no unit: write 0.0 _degC or 273.15 _K");
    assertThat(shown("convert(0.0 _degC, _degF)")).isEqualTo("32.0 _degF");
    assertThat(error("convert(3 _m, 2 _km)")).isEqualTo("convert needs a unit such as _m/_s");
    assertThat(error("convert(3 _m^2, _km _m)"))
        .isEqualTo("convert's target must not hold two units of one dimension");
  }
}
