package mathcanvas.engine.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.Map;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

/** The recheck's outcomes at a disagreeing point. */
class RecheckTest {

  private static Expr e(String text) {
    return Canonical.of(Parser.parseExpression(text, Names.NONE));
  }

  private static final Map<Symbol, Numeric> B_ZERO = Map.of(Symbol.of("b"), Int.ZERO);

  /**
   * A power on the discontinuity of {@code 0^0} is no evidence: the simplifier makes {@code
   * atan(tan π)} exactly 0 and the value 1, where the computed residue near {@code 10^-100} gives
   * {@code 0^(residue) = 0} at every precision (the trig set's soundness property met it).
   */
  @Test
  void aPowerOnZeroToZeroIsIllConditioned() {
    assertThat(Recheck.of(e("((1 + sqrt(2))^(1/2) b)^atan(tan(pi))"), e("1"), B_ZERO))
        .isEqualTo(Recheck.Outcome.ILL_CONDITIONED);
  }

  /** A {@code 0^0} power that does not account for the disagreement does not excuse it. */
  @Test
  void aZeroToZeroPowerBesideAnotherDisagreementDoesNotExcuseIt() {
    Map<Symbol, Numeric> origin = Map.of(Symbol.of("x"), Int.ZERO, Symbol.of("y"), Int.ZERO);
    assertThat(Recheck.of(e("(2y)^x + 5"), e("7"), origin)).isEqualTo(Recheck.Outcome.DISAGREE);
  }

  /**
   * A sign at its jump is no evidence: {@code cos(asin(cos²y + sin²y))} is exactly 0, which the
   * simplifier finds, while the computed residue near {@code 10^-50} has the sign 1 at every
   * precision (the trig set's soundness property met it on CI).
   */
  @Test
  void aSignAtItsJumpIsIllConditioned() {
    assertThat(
            Recheck.of(
                e("log(e^sign(cos(asin(cos(y)^2 + sin(y)^2))))"),
                e("log(e^sign(0))"),
                Map.of(Symbol.of("y"), Int.of(-16))))
        .isEqualTo(Recheck.Outcome.ILL_CONDITIONED);
  }

  /**
   * A sign whose argument lies within the tolerance of its jump on one side, against the other
   * side's value at the jump itself, is no evidence: {@code cosh²x - sinh²x} is exactly 1, so the
   * simplified side is 0, while the computed difference cancels to just below 1 and its {@code log}
   * has the sign -1 (the trig set's standard-list property met it).
   */
  @Test
  void aSignNearItsJumpAgainstTheValueAtTheJumpIsIllConditioned() {
    assertThat(
            Recheck.of(
                e("sign(log((cosh(x)^2 - sinh(x)^2)^-3))"),
                e("0"),
                Map.of(Symbol.of("x"), Rational.of(99, 4))))
        .isEqualTo(Recheck.Outcome.ILL_CONDITIONED);
    // none of -1, 0 and 1 reconciles a side that is off by 2
    assertThat(
            Recheck.of(
                e("sign(x) + 5"),
                e("7"),
                Map.of(Symbol.of("x"), Rational.of(BigInteger.ONE, BigInteger.TEN.pow(30)))))
        .isEqualTo(Recheck.Outcome.DISAGREE);
  }

  /** A step function within the tolerance of its jump, at an integer or a half-integer. */
  @Test
  void aStepFunctionAtItsJumpIsIllConditioned() {
    Map<Symbol, Numeric> belowOne =
        Map.of(
            Symbol.of("x"),
            Rational.of(BigInteger.TEN.pow(30).subtract(BigInteger.ONE), BigInteger.TEN.pow(30)));
    Map<Symbol, Numeric> aboveHalf =
        Map.of(
            Symbol.of("x"),
            Rational.of(
                BigInteger.TEN.pow(30).add(BigInteger.TWO),
                BigInteger.TEN.pow(30).multiply(BigInteger.TWO)));
    assertThat(Recheck.of(e("floor(x)"), e("1"), belowOne))
        .isEqualTo(Recheck.Outcome.ILL_CONDITIONED);
    assertThat(Recheck.of(e("trunc(x)"), e("1"), belowOne))
        .isEqualTo(Recheck.Outcome.ILL_CONDITIONED);
    assertThat(Recheck.of(e("round(x)"), e("0"), aboveHalf))
        .isEqualTo(Recheck.Outcome.ILL_CONDITIONED);
    assertThat(Recheck.of(e("floor(x) + 5"), e("7"), belowOne)).isEqualTo(Recheck.Outcome.DISAGREE);
    assertThat(Recheck.of(e("floor(x)"), e("1"), Map.of(Symbol.of("x"), Rational.of(1, 2))))
        .isEqualTo(Recheck.Outcome.DISAGREE);
  }

  private static final Map<Symbol, Numeric> ORDINARY =
      Map.of(
          Symbol.of("x"),
          Rational.of(7, 3),
          Symbol.of("y"),
          Rational.of(5, 4),
          Symbol.of("z"),
          Int.of(3));

  /**
   * A sum that cancels past the precision but whose noise cannot reach the value excuses nothing:
   * each inner identity is an inexact zero beside terms of size 1, and a wrong rewrite of the rest
   * still disagrees (review-9's probes, and its controls without a cancelling sum).
   */
  @Test
  void aCancellationThatCannotReachTheValueStillDisagrees() {
    assertThat(Recheck.of(e("sin(y) + z (sin(x)^2 + cos(x)^2 - 1)"), e("cos(y)"), ORDINARY))
        .isEqualTo(Recheck.Outcome.DISAGREE);
    assertThat(Recheck.of(e("sin(y) + (cosh(x)^2 - sinh(x)^2 - 1) z"), e("y"), ORDINARY))
        .isEqualTo(Recheck.Outcome.DISAGREE);
    assertThat(Recheck.of(e("sin(y) + exp(-(pi x - 4 atan(1) x)^2) - 1"), e("cos(y)"), ORDINARY))
        .isEqualTo(Recheck.Outcome.DISAGREE);
    assertThat(Recheck.of(e("sin(y) + log(sin(x)^2 + cos(x)^2)"), e("cos(y)"), ORDINARY))
        .isEqualTo(Recheck.Outcome.DISAGREE);
    assertThat(Recheck.of(e("y^2 + (e^x e^(-x) - 1) z"), e("y^3"), ORDINARY))
        .isEqualTo(Recheck.Outcome.DISAGREE);
  }

  /**
   * {@code (T + 1) − T} against {@code (T + 2) − T} disagrees whatever the size of {@code T}: past
   * 100 digits the sums cancel to noise, and evaluated again at the digits that resolve them they
   * are 1 and 2. A {@code T} whose sums would need more than 10,000 digits is no evidence.
   */
  @Test
  void aCancellingDifferenceIsResolved() {
    Map<Symbol, Numeric> one = Map.of(Symbol.of("x"), Real.of("1.0"));
    for (int exponent : new int[] {30, 80, 110}) {
      String t = "10^" + exponent + " x";
      assertThat(Recheck.of(e(t + " - (" + t + " - 1)"), e(t + " - (" + t + " - 2)"), one))
          .as("T = 10^%d", exponent)
          .isEqualTo(Recheck.Outcome.DISAGREE);
    }
    String t = "10^9900 x";
    assertThat(Recheck.of(e(t + " - (" + t + " - 1)"), e(t + " - (" + t + " - 2)"), one))
        .isEqualTo(Recheck.Outcome.ILL_CONDITIONED);
  }

  /** A zero base with an exponent of real size, and a plain disagreement, still disagree. */
  @Test
  void anOrdinaryDisagreementStillDisagrees() {
    assertThat(Recheck.of(e("b^(1/2)"), e("1"), B_ZERO)).isEqualTo(Recheck.Outcome.DISAGREE);
    assertThat(Recheck.of(e("b + 2"), e("b + 3"), B_ZERO)).isEqualTo(Recheck.Outcome.DISAGREE);
  }
}
