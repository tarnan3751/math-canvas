package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The time value of money (§14.28): closed forms exact, the rates numeric, the solver. */
class FinanceTest {

  @Test
  void theClosedFormsAreExact() {
    assertThat(shown("fv(1/10, 2, 0, -100)")).isEqualTo("121");
    assertThat(shown("pv(1/10, 2, 0, 121)")).isEqualTo("-100");
    assertThat(shown("pmt(1/100, 2, 100)")).isEqualTo("-10201/201");
    assertThat(shown("nper(1/10, 0, -100, 121)")).isEqualTo("2");
    assertThat(shown("fv(0, 10, -5, -100)")).isEqualTo("150");
    assertThat(shown("pmt(0, 4, 100)")).isEqualTo("-25");
    assertThat(shown("fv(1/10, 1, -100, 0, 1)")).isEqualTo("110");
    assertThat(shown("effective(1/10, 2)")).isEqualTo("41/400");
    assertThat(shown("nominal(41/400, 2)")).isEqualTo("1/10");
    assertThat(shown("npv(1/10, [-100, 60, 60])")).isEqualTo("5000/1331");
  }

  @Test
  void decimalsGiveDecimals() {
    assertThat(shown("pmt(0.05/12, 360, 200000)")).startsWith("-1073.643246024");
    assertThat(shown("fv(0.1, 2, 0, -100)")).isEqualTo("121.0");
  }

  @Test
  void theRatesAreNumeric() {
    assertThat(shown("rate(2, 0, -100, 121)")).startsWith("0.1");
    assertThat(shown("irr([-100, 60, 60])")).startsWith("0.130662386291807");
    // a root at 0, found exactly
    assertThat(shown("rate(4, -25, 100)")).isEqualTo("0");
    assertThat(shown("irr([-100, 50, 50])")).isEqualTo("0");
    assertThat(shown("rate(4, -25.0, 100)")).isEqualTo("0");
    // an exact guess of any size starts Newton's method without a double
    assertThat(error("irr([-100, 60, 60], 10^400)")).startsWith("irr did not converge");
    assertThat(shown("irr([-100, 60, 60], 1/3)")).startsWith("0.130662386291807");
    Outcome o = Run.last("rate(2, 0, -100, 121)");
    assertThat(((Outcome.Value) o).numeric()).isTrue();
  }

  @Test
  void theRatesAreAtTheSessionsPrecision() {
    mathcanvas.engine.eval.Settings fifty =
        mathcanvas.engine.eval.Settings.DEFAULT.withPrecision(50);
    assertThat(shown("irr([-100, 60, 60])", fifty))
        .isEqualTo("0.13066238629180748525842627449074920102322142489557");
  }

  @Test
  void amortization() {
    assertThat(shown("amortization(1/10, 2, 100)"))
        .isEqualTo("[[1, -1210/21, -10, -1000/21, 1100/21], [2, -1210/21, -110/21, -1100/21, 0]]");
  }

  @Test
  void undefinedAndUnevaluated() {
    assertThat(shown("fv(-1, 2, 0, -100)")).isEqualTo("undefined");
    assertThat(shown("nper(1/10, 0, 100, 121)")).isEqualTo("undefined");
    assertThat(shown("fv(r, 2, 0, -100)")).isEqualTo("fv(r, 2, 0, -100)");
  }

  @Test
  void theErrors() {
    assertThat(error("fv(1/10, 2, 0, -100, 2)"))
        .isEqualTo("fv's type must be 0 (end) or 1 (beginning)");
    assertThat(error("irr([100, 60])")).isEqualTo("irr needs cash flows of both signs");
    assertThat(error("amortization(1/10, 0, 100)"))
        .isEqualTo("amortization's periods must be an integer from 1 to 1000");
    assertThat(error("effective(1/10, 1/2)")).isEqualTo("effective's m must be a positive integer");
  }

  @Test
  void theClosedFormsCompile() {
    java.util.List<mathcanvas.engine.expr.Symbol> r =
        java.util.List.of(mathcanvas.engine.expr.Symbol.of("r"));
    for (String f :
        new String[] {
          "fv(r, 10, -50, -1000)", "pv(r, 10, -50, 2000, 1)", "pmt(r, 360, 200000)",
          "nper(r, -400, 1000)", "effective(r, 12)", "nominal(r, 4)"
        }) {
      mathcanvas.engine.compile.Compiled c =
          mathcanvas.engine.compile.Compiled.compile(Run.value(f), r, Settings.DEFAULT);
      for (String at : new String[] {"1/20", "1/100", "3/10"}) {
        double exact =
            ((mathcanvas.engine.number.RealNumeric)
                    ((mathcanvas.engine.expr.Num)
                            Run.value("numeric(subs(" + f + ", r = " + at + "))"))
                        .value())
                .toDouble();
        double x =
            ((mathcanvas.engine.number.RealNumeric)
                    ((mathcanvas.engine.expr.Num) Run.value("numeric(" + at + ")")).value())
                .toDouble();
        assertThat(c.eval(x))
            .as(f + " at " + at)
            .isCloseTo(
                exact, org.assertj.core.data.Offset.offset(1e-9 * Math.max(1, Math.abs(exact))));
      }
    }
  }
}
