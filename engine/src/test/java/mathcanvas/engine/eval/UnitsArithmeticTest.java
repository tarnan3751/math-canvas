package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Quantities in arithmetic (§14.26): dimensions checked, units of one dimension combined. */
class UnitsArithmeticTest {

  @Test
  void quantitiesOfOneDimensionAddInTheFirstTermsUnit() {
    assertThat(shown("3 _m + 2 _cm")).isEqualTo("151/50 _m");
    assertThat(shown("2 _cm + 3 _m")).isEqualTo("151/50 _m");
    assertThat(shown("x _m + 2 _cm")).isEqualTo("(x + 1/50) _m");
    assertThat(shown("5 _km/_h + 1 _m/_s")).isEqualTo("43/18 _m/_s");
    assertThat(shown("1/_s + 1/_ms")).isEqualTo("1001/_s");
    assertThat(shown("3.0 _m + 2 _cm")).isEqualTo("3.02 _m");
    assertThat(shown("(2 _m + 3 _cm)^2")).isEqualTo("41209/10000 _m²");
  }

  @Test
  void dimensionlessSumsAndProductsAreNumbers() {
    assertThat(shown("1 + 30 _deg")).isEqualTo("π/6 + 1");
    assertThat(shown("_km/_m")).isEqualTo("1000");
    assertThat(shown("3 _h _Hz")).isEqualTo("10800");
    assertThat(shown("_J/(_N _m)")).isEqualTo("1");
    assertThat(shown("30 _deg")).isEqualTo("30 _deg");
  }

  @Test
  void unitsOfOneDimensionInAProductMergeButDerivedUnitsStay() {
    assertThat(shown("_km _m")).isEqualTo("1000 _m²");
    assertThat(shown("3 _N * 2 _m")).isEqualTo("6 _N _m");
    assertThat(shown("2 _kWh / _h")).isEqualTo("2 _kWh/_h");
  }

  @Test
  void temperaturesAddAsDifferencesAndCompareAbsolutely() {
    assertThat(shown("5 _degC + 3 _degC")).isEqualTo("8 _degC");
    assertThat(shown("20 _degC + 10 _degF")).isEqualTo("230/9 _degC");
    assertThat(shown("30 _degC > 300 _K")).isEqualTo("true");
    assertThat(shown("20 _degC = 68 _degF")).isEqualTo("true");
  }

  @Test
  void comparisonsAndExtremaAreInTheCoherentUnits() {
    assertThat(shown("1 _km > 900 _m")).isEqualTo("true");
    assertThat(shown("3 _m = 300 _cm")).isEqualTo("true");
    assertThat(shown("x _m = 3 _m")).isEqualTo("x _m = 3 _m");
    assertThat(shown("max(3 _m, 200 _cm)")).isEqualTo("3 _m");
    assertThat(shown("min(3 _m, 200 _cm)")).isEqualTo("200 _cm");
  }

  @Test
  void functionsTakeNumbersAndDimensionlessQuantities() {
    assertThat(shown("sin(30 _deg)")).isEqualTo("1/2");
    assertThat(shown("sin(30 _deg)", Run.DEGREES)).isEqualTo("1/2");
    assertThat(shown("sin(2 _rad)", Run.DEGREES)).isEqualTo("sin(2)");
    assertThat(shown("sin(_km/_m)")).isEqualTo("sin(1000)");
    assertThat(shown("abs(-3 _m)")).isEqualTo("3 _m");
    assertThat(shown("sqrt(4 _m^2)")).isEqualTo("2 _m");
  }

  @Test
  void commandsTreatUnitsAsConstants() {
    assertThat(shown("solve(x _m = 3 _cm, x)")).isEqualTo("{3/100}");
    assertThat(shown("integrate(3 _m, x)")).isEqualTo("3x _m");
    assertThat(shown("numeric(5 _km/_h)")).isEqualTo("5.0 _km/_h");
    assertThat(shown("exact(3.02 _m)")).isEqualTo("151/50 _m");
  }

  @Test
  void theErrors() {
    assertThat(error("3 _m + 2 _s")).isEqualTo("cannot add m and s");
    assertThat(error("3 _m + 2")).isEqualTo("cannot add a number and m");
    assertThat(error("3 _m < 2 _s")).isEqualTo("cannot compare m and s");
    assertThat(error("sin(3 _m)")).isEqualTo("`sin` needs a number, not a quantity in m");
    assertThat(error("log(5 _km/_h)")).isEqualTo("`log` needs a number, not a quantity in km/h");
    assertThat(error("2^_m")).isEqualTo("an exponent cannot have units");
    assertThat(error("_m^x")).isEqualTo("a quantity's exponent must be a rational number");
    assertThat(error("max(3 _m, 2 _s)")).isEqualTo("cannot compare m and s");
    assertThat(error("x _m + 2 _cm = 3 _s")).isEqualTo("cannot compare m and s");
    assertThat(error("3 _m in Reals"))
        .isEqualTo("a quantity with units does not belong to a number domain");
  }

  @Test
  void anExactZeroHasEveryDimension() {
    assertThat(shown("solve(x _m + 2 _cm = 0, x)")).isEqualTo("{-1/50}");
    assertThat(shown("0 _degC < 5 _degC")).isEqualTo("true");
    assertThat(shown("3 _m > 0")).isEqualTo("true");
    assertThat(shown("max(0, 3 _m)")).isEqualTo("3 _m");
    assertThat(shown("min(0, -3 _m, 2 _s - 2 _s)")).isEqualTo("-3 _m");
    assertThat(error("3 _m > 1")).isEqualTo("cannot compare m and a number");
  }

  @Test
  void theItemsOfACollectionAreCheckedEach() {
    assertThat(error("[3 _m] + [2 _s]")).isEqualTo("cannot add m and s");
    assertThat(error("[[1, 3 _m]] + [[2, 2 _s]]")).isEqualTo("cannot add m and s");
    assertThat(shown("[3 _m] + [2 _cm]")).isEqualTo("⟨151/50 _m⟩");
  }
}
