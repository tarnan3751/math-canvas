package mathcanvas.engine.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;
import java.util.Random;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.RealNumeric;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/** The assumptions' inference agrees with the values at points within the assumptions. */
class InferenceProperties {

  @Provide
  Arbitrary<Expr> exprs() {
    return ExprArbitraries.exactExprs().map(Canonical::of);
  }

  @Property(tries = 3000)
  void everyKnownAnswerHoldsAtThePoints(@ForAll("exprs") Expr e, @ForAll Random random) {
    Assumptions a = FixtureProperties.assumptions(random);
    for (Map<Symbol, Numeric> point : Soundness.points(ExprArbitraries.SYMBOLS, a, 4, random)) {
      Optional<Numeric> value = NumericEvaluator.eval(e, point, Soundness.PRECISION);
      if (value.isEmpty()) {
        continue;
      }
      Numeric v = value.get();
      boolean real = v.im().isZero();
      String at = e + " at " + point + " = " + v + " under " + a.relations();
      check(a.isReal(e), real, at + ": real");
      if (real) {
        int sign = v.re().signum();
        check(a.isPositive(e), sign > 0, at + ": positive");
        check(a.isNegative(e), sign < 0, at + ": negative");
        check(a.isNonNegative(e), sign >= 0, at + ": non-negative");
        check(a.isNonPositive(e), sign <= 0, at + ": non-positive");
      }
      check(a.isZero(e), v.isZero(), at + ": zero");
      check(a.isNonZero(e), !v.isZero(), at + ": non-zero");
      if (v.isExact()) {
        check(a.isInteger(e), v.isInteger(), at + ": integer");
        check(a.isRational(e), v instanceof RealNumeric, at + ": rational");
        boolean even = v instanceof Int i && !i.value().testBit(0);
        boolean odd = v instanceof Int i && i.value().testBit(0);
        check(a.isEven(e), even, at + ": even");
        check(a.isOdd(e), odd, at + ": odd");
      }
      Statistics.label("decided sign").collect(a.isPositive(e) != Tri.UNKNOWN);
    }
  }

  private static void check(Tri answer, boolean actual, String what) {
    if (answer != Tri.UNKNOWN) {
      assertThat(answer).as(what).isEqualTo(Tri.of(actual));
    }
  }
}
