package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.RealNumeric;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * The time value of money on random exact data (§14.28): {@code pv} and {@code fv} are inverse,
 * {@code pmt} satisfies the equation exactly, {@code nper} recovers the periods, and {@code rate}
 * and {@code irr} recover the rate that made the data to 1e-10.
 */
class FinanceProperties {

  /** Exact data: a rate, periods, a payment, a present and a future value, a type. */
  record Data(String r, int n, String pmt, String pv, String fv, int type) {}

  @Provide
  Arbitrary<Data> data() {
    return Arbitraries.randomValue(
        (Random g) ->
            new Data(
                g.nextInt(6) == 0 ? "0" : (1 + g.nextInt(30)) + "/" + (50 + g.nextInt(150)),
                1 + g.nextInt(40),
                String.valueOf(g.nextInt(400) - 200),
                String.valueOf(g.nextInt(4000) - 2000),
                String.valueOf(g.nextInt(4000) - 2000),
                g.nextInt(2)));
  }

  @Property(tries = 150)
  void pvAndFvAreInverse(@ForAll("data") Data d) {
    String fv =
        "fv(" + d.r() + ", " + d.n() + ", " + d.pmt() + ", " + d.pv() + ", " + d.type() + ")";
    String back = "pv(" + d.r() + ", " + d.n() + ", " + d.pmt() + ", " + fv + ", " + d.type() + ")";
    assertThat(Run.value(back)).as(back).isEqualTo(Run.value(d.pv()));
  }

  @Property(tries = 150)
  void pmtSatisfiesTheEquationExactly(@ForAll("data") Data d) {
    String pmt =
        "pmt(" + d.r() + ", " + d.n() + ", " + d.pv() + ", " + d.fv() + ", " + d.type() + ")";
    String fv = "fv(" + d.r() + ", " + d.n() + ", " + pmt + ", " + d.pv() + ", " + d.type() + ")";
    assertThat(Run.value(fv)).as(fv).isEqualTo(Run.value(d.fv()));
  }

  @Property(tries = 100)
  void nperRecoversThePeriods(@ForAll("data") Data d) {
    if (d.r().equals("0") && d.pmt().equals("0")) {
      return;
    }
    String fv = "fv(" + d.r() + ", " + d.n() + ", " + d.pmt() + ", " + d.pv() + ")";
    String nper = "numeric(nper(" + d.r() + ", " + d.pmt() + ", " + d.pv() + ", " + fv + "), 30)";
    if (!(Run.value(nper) instanceof Num n && n.value() instanceof RealNumeric v)) {
      return; // a degenerate case, such as a zero payment and present value
    }
    assertThat(Math.abs(v.toDouble() - d.n())).as(nper).isLessThan(1e-12);
  }

  @Property(tries = 60)
  void rateRecoversTheRate(@ForAll("data") Data d) {
    if (d.r().equals("0")) {
      return;
    }
    // a loan paid off: pv > 0, the payment that clears it
    String pv = String.valueOf(1000 + Math.abs(Integer.parseInt(d.pv())));
    String pmt = "pmt(" + d.r() + ", " + d.n() + ", " + pv + ")";
    String rate = "rate(" + d.n() + ", " + pmt + ", " + pv + ")";
    double expected =
        ((RealNumeric) ((Num) Run.value("numeric(" + d.r() + ")")).value()).toDouble();
    double found = ((RealNumeric) ((Num) Run.value(rate)).value()).toDouble();
    assertThat(Math.abs(found - expected)).as(rate).isLessThan(1e-10);
  }

  @Property(tries = 60)
  void irrRecoversTheRate(@ForAll("data") Data d) {
    if (d.r().equals("0")) {
      return;
    }
    // an investment of -1000 returning equal flows that discount to it at r
    String pmt = "-pmt(" + d.r() + ", " + d.n() + ", 1000)";
    StringBuilder flows = new StringBuilder("[-1000");
    for (int k = 0; k < d.n(); k++) {
      flows.append(", ").append(pmt);
    }
    String irr = "irr(" + flows + "])";
    double expected =
        ((RealNumeric) ((Num) Run.value("numeric(" + d.r() + ")")).value()).toDouble();
    double found = ((RealNumeric) ((Num) Run.value(irr)).value()).toDouble();
    assertThat(Math.abs(found - expected)).as(irr).isLessThan(1e-10);
  }
}
