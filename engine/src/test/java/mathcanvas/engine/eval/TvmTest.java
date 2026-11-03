package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The solver {@code tvm} (§14.28): four parts by name, the fifth found. */
class TvmTest {

  @Test
  void itSolvesForEachPart() {
    assertThat(shown("tvm(n = 10, i = 5 %, pv = -1000, pmt = 0)"))
        .isEqualTo("[n = 10, i = 1/20, pv = -1000, pmt = 0, fv = 16679880978201/10240000000]");
    assertThat(shown("tvm(i = 1/10, pv = -100, pmt = 0, fv = 121)"))
        .isEqualTo("[n = 2, i = 1/10, pv = -100, pmt = 0, fv = 121]");
    assertThat(shown("tvm(n = 2, i = 1/10, pmt = 0, fv = 121)"))
        .isEqualTo("[n = 2, i = 1/10, pv = -100, pmt = 0, fv = 121]");
    assertThat(shown("tvm(n = 2, i = 1/100, pv = 100, fv = 0)"))
        .isEqualTo("[n = 2, i = 1/100, pv = 100, pmt = -10201/201, fv = 0]");
    assertThat(shown("tvm(n = 2, pv = -100, pmt = 0, fv = 121)"))
        .isEqualTo("[n = 2, i = 0.1, pv = -100, pmt = 0, fv = 121]");
    assertThat(((Outcome.Value) Run.last("tvm(n = 2, pv = -100, pmt = 0, fv = 121)")).numeric())
        .isTrue();
    assertThat(shown("tvm(n = 1, i = 1/10, pv = 0, pmt = -100, type = 1)"))
        .isEqualTo("[n = 1, i = 1/10, pv = 0, pmt = -100, fv = 110]");
  }

  @Test
  void solvingForTheRateRecordsItsNumericStep() {
    assertThat(NarrationTest.narrated("steps(tvm(n = 2, pv = -100, pmt = 0, fv = 121))"))
        .containsExactly(
            "Solve the time-value-of-money equation for the rate by Newton's method, 34 digits:"
                + " [n = 2, i = 0.1, pv = -100, pmt = 0, fv = 121]"
                + " | [n = 2, i = 0.1, pv = -100, pmt = 0, fv = 121]");
  }

  @Test
  void itsPartsAreNamesThere() {
    // pv, pmt and fv are builtins and i the imaginary unit elsewhere, n may be defined: inside tvm
    // they are its parts
    assertThat(shown("n := 3; tvm(n = 2, i = 1/10, pv = -100, pmt = 0)"))
        .isEqualTo("[n = 2, i = 1/10, pv = -100, pmt = 0, fv = 121]");
  }

  @Test
  void theErrors() {
    assertThat(error("tvm(n = 10, i = 5 %, pv = -1000, x = 0)"))
        .isEqualTo("tvm's parts are n, i, pv, pmt, fv and type");
    assertThat(error("tvm(n = 10, i = 5 %, pv = -1000, n = 0)"))
        .isEqualTo("tvm's parts must be distinct");
    assertThat(error("tvm(n = 10, i = 5 %, pv = -1000, pmt = y)"))
        .isEqualTo("tvm needs numbers: pmt");
    assertThat(error("tvm(n = 10, i = 5 %, pv = -1000, pmt = 0, fv = 1)"))
        .isEqualTo("tvm needs four of n, i, pv, pmt, fv");
  }
}
