package mathcanvas.engine.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.simplify.Simplifier;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.statistics.Statistics;

/**
 * The recheck at the jump of a step function: {@code sign(log(u^n))} with {@code u} identically 1
 * by an identity is 0, which the simplifier finds, while the computed {@code u} cancels to within
 * rounding of 1 on either side, more digits the larger {@code x}; the soundness oracle never calls
 * such a point a disagreement.
 */
class RecheckProperties {

  private static final Symbol X = Symbol.of("x");

  @Provide
  Arbitrary<String> identities() {
    return Arbitraries.of(
        "cosh(x)^2 - sinh(x)^2",
        "sin(x)^2 + cos(x)^2",
        "sec(x)^2 - tan(x)^2",
        "csc(x)^2 - cot(x)^2",
        "e^x e^(-x)");
  }

  @Property(tries = 1000)
  void theSignOfTheLogOfAnIdentityNeverDisagrees(
      @ForAll("identities") String u,
      @ForAll @IntRange(min = -3, max = 3) int n,
      @ForAll @IntRange(min = -200, max = 200) int quarters) {
    Assume.that(n != 0);
    Expr before =
        Canonical.of(Parser.parseExpression("sign(log((" + u + ")^(" + n + ")))", Names.NONE));
    Expr after = Simplifier.simplify(before, Simplifier.standard(), Context.DEFAULT).expr();
    Map<Symbol, Numeric> point = Map.of(X, Rational.of(quarters, 4));
    Recheck.Outcome outcome;
    try {
      Soundness.check(before, after, List.of(point));
      outcome = Recheck.Outcome.AGREE;
    } catch (ArithmeticException overflow) {
      outcome = Recheck.Outcome.OVERFLOW;
    } catch (AssertionError disagreement) {
      outcome = Recheck.of(before, after, point);
    }
    Statistics.label("outcome").collect(outcome);
    Statistics.label("simplified to 0")
        .collect(after.equals(Canonical.of(Parser.parseExpression("0", Names.NONE))));
    assertThat(outcome)
        .as("%s against %s at %s", before, after, point)
        .isNotEqualTo(Recheck.Outcome.DISAGREE);
  }
}
