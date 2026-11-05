package mathcanvas.engine.testing;

import static mathcanvas.engine.testing.Inputs.canonical;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigInteger;
import java.util.Map;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import org.junit.jupiter.api.Test;

/** The recheck's outcomes at a disagreeing point. */
class RecheckTest {

  private static final Map<Symbol, Numeric> B_ZERO = Map.of(Symbol.of("b"), Int.ZERO);

  /**
   * A power on the discontinuity of {@code 0^0} is no evidence: the simplifier makes {@code
   * atan(tan π)} exactly 0 and the value 1, where the computed residue near {@code 10^-100} gives
   * {@code 0^(residue) = 0} at every precision (the trig set's soundness property met it).
   */
  @Test
  void aPowerOnZeroToZeroIsIllConditioned() {
    assertThat(
            Recheck.of(canonical("((1 + sqrt(2))^(1/2) b)^atan(tan(pi))"), canonical("1"), B_ZERO))
        .isEqualTo(Recheck.Outcome.ILL_CONDITIONED);
  }

  /** A {@code 0^0} power that does not account for the disagreement does not excuse it. */
  @Test
  void aZeroToZeroPowerBesideAnotherDisagreementDoesNotExcuseIt() {
    Map<Symbol, Numeric> origin = Map.of(Symbol.of("x"), Int.ZERO, Symbol.of("y"), Int.ZERO);
    assertThat(Recheck.of(canonical("(2y)^x + 5"), canonical("7"), origin))
        .isEqualTo(Recheck.Outcome.DISAGREE);
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
                canonical("log(e^sign(cos(asin(cos(y)^2 + sin(y)^2))))"),
                canonical("log(e^sign(0))"),
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
                canonical("sign(log((cosh(x)^2 - sinh(x)^2)^-3))"),
                canonical("0"),
                Map.of(Symbol.of("x"), Rational.of(99, 4))))
        .isEqualTo(Recheck.Outcome.ILL_CONDITIONED);
    // none of -1, 0 and 1 reconciles a side that is off by 2
    assertThat(
            Recheck.of(
                canonical("sign(x) + 5"),
                canonical("7"),
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
    assertThat(Recheck.of(canonical("floor(x)"), canonical("1"), belowOne))
        .isEqualTo(Recheck.Outcome.ILL_CONDITIONED);
    assertThat(Recheck.of(canonical("trunc(x)"), canonical("1"), belowOne))
        .isEqualTo(Recheck.Outcome.ILL_CONDITIONED);
    assertThat(Recheck.of(canonical("round(x)"), canonical("0"), aboveHalf))
        .isEqualTo(Recheck.Outcome.ILL_CONDITIONED);
    assertThat(Recheck.of(canonical("floor(x) + 5"), canonical("7"), belowOne))
        .isEqualTo(Recheck.Outcome.DISAGREE);
    assertThat(
            Recheck.of(
                canonical("floor(x)"), canonical("1"), Map.of(Symbol.of("x"), Rational.of(1, 2))))
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
    assertThat(
            Recheck.of(
                canonical("sin(y) + z (sin(x)^2 + cos(x)^2 - 1)"), canonical("cos(y)"), ORDINARY))
        .isEqualTo(Recheck.Outcome.DISAGREE);
    assertThat(
            Recheck.of(
                canonical("sin(y) + (cosh(x)^2 - sinh(x)^2 - 1) z"), canonical("y"), ORDINARY))
        .isEqualTo(Recheck.Outcome.DISAGREE);
    assertThat(
            Recheck.of(
                canonical("sin(y) + exp(-(pi x - 4 atan(1) x)^2) - 1"),
                canonical("cos(y)"),
                ORDINARY))
        .isEqualTo(Recheck.Outcome.DISAGREE);
    assertThat(
            Recheck.of(
                canonical("sin(y) + log(sin(x)^2 + cos(x)^2)"), canonical("cos(y)"), ORDINARY))
        .isEqualTo(Recheck.Outcome.DISAGREE);
    assertThat(Recheck.of(canonical("y^2 + (e^x e^(-x) - 1) z"), canonical("y^3"), ORDINARY))
        .isEqualTo(Recheck.Outcome.DISAGREE);
  }

  /**
   * {@code (T + 1) − T} against {@code (T + 2) − T} disagrees whatever the size of {@code T}: past
   * 100 digits the sums cancel to noise, and evaluated again at the digits that resolve them they
   * are 1 and 2. A {@code T} whose sums would need more than 3,000 digits is no evidence.
   */
  @Test
  void aCancellingDifferenceIsResolved() {
    Map<Symbol, Numeric> one = Map.of(Symbol.of("x"), Real.of("1.0"));
    for (int exponent : new int[] {30, 80, 110}) {
      String t = "10^" + exponent + " x";
      assertThat(
              Recheck.of(
                  canonical(t + " - (" + t + " - 1)"), canonical(t + " - (" + t + " - 2)"), one))
          .as("T = 10^%d", exponent)
          .isEqualTo(Recheck.Outcome.DISAGREE);
    }
    String t = "10^2900 x";
    assertThat(
            Recheck.of(
                canonical(t + " - (" + t + " - 1)"), canonical(t + " - (" + t + " - 2)"), one))
        .isEqualTo(Recheck.Outcome.ILL_CONDITIONED);
  }

  /**
   * A logarithm of an argument within rounding of 1 is resolved: {@code e^(e^(−6y))} at {@code y =
   * 198/5} is {@code 1 + 10^(−103)}, which rounds to 1 at 34 and at 100 digits, so {@code
   * log(e^(e^(−6y)))} is 0 where its simplification {@code e^(−6y)} is not, and a large factor
   * shows it (the exp-log set's property met it). Evaluated again at the digits that resolve the
   * deviation, the sides agree.
   */
  @Test
  void aLogarithmNearOneIsResolved() {
    Map<Symbol, Numeric> point =
        Map.of(
            Symbol.of("a"), Complex.of(Int.of(17), Rational.of(-25, 8)),
            Symbol.of("b"), Int.of(-53),
            Symbol.of("x"), Int.of(-8),
            Symbol.of("y"), Rational.of(198, 5));
    assertThat(
            Recheck.of(
                canonical("(abs(b^(1 + 2a)) log(e^(e^(-6y))))^(1/3)"),
                canonical("(e^(-6y) abs(b^(1 + 2a)))^(1/3)"),
                point))
        .isEqualTo(Recheck.Outcome.AGREE);
  }

  /**
   * An exact argument near 1 loses its digits inside {@code acosh} too: {@code 10^30·acosh(1 +
   * y²/10^60)} at {@code y = 3} is {@code 3√2}, which 34 digits give as 4.243. Resolved, it agrees
   * with {@code √2·y} and still disagrees with a wrong {@code 2√2·y} (review-9's probe).
   */
  @Test
  void anAcoshOfAnExactArgumentNearOneIsResolved() {
    Map<Symbol, Numeric> three = Map.of(Symbol.of("y"), Int.of(3));
    assertThat(Recheck.of(canonical("10^30 acosh(1 + y^2/10^60)"), canonical("sqrt(2) y"), three))
        .isEqualTo(Recheck.Outcome.AGREE);
    assertThat(Recheck.of(canonical("10^30 acosh(1 + y^2/10^60)"), canonical("2 sqrt(2) y"), three))
        .isEqualTo(Recheck.Outcome.DISAGREE);
  }

  /** A zero base with an exponent of real size, and a plain disagreement, still disagree. */
  @Test
  void anOrdinaryDisagreementStillDisagrees() {
    assertThat(Recheck.of(canonical("b^(1/2)"), canonical("1"), B_ZERO))
        .isEqualTo(Recheck.Outcome.DISAGREE);
    assertThat(Recheck.of(canonical("b + 2"), canonical("b + 3"), B_ZERO))
        .isEqualTo(Recheck.Outcome.DISAGREE);
  }
}
