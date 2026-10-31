package mathcanvas.engine.number;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.concurrent.atomic.AtomicInteger;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.CancelledException;
import mathcanvas.engine.number.Quadrature.Bound;
import mathcanvas.engine.number.Quadrature.Integrand;
import mathcanvas.engine.number.Quadrature.None;
import mathcanvas.engine.number.Quadrature.Reason;
import mathcanvas.engine.number.Quadrature.Result;
import mathcanvas.engine.number.Quadrature.Value;
import org.junit.jupiter.api.Test;

/**
 * The double-exponential rule against integrals known to many digits, its refusals and its
 * cancellation. The expected values are written out from independent computations (series in
 * Python's {@code decimal}, and the constants' known digits).
 */
class QuadratureTest {

  private static final Bound ZERO = Bound.finite(Real.of("0"));
  private static final Bound ONE = Bound.finite(Real.of("1"));
  private static final Bound INF = Bound.POSITIVE_INFINITY;
  private static final Bound NEG_INF = Bound.NEGATIVE_INFINITY;

  private static final String E_MINUS_ONE =
      "1.718281828459045235360287471352662497757247093699959574966967627724076630353547594571";
  private static final String PI =
      "3.141592653589793238462643383279502884197169399375105820974944592307816406286208998628";
  private static final String SQRT_PI =
      "1.772453850905516027298167483341145182797549456122387128213807789852911284591025248";
  private static final String GAUSS_0_1 =
      "0.746824132812427025399467436131853005354499686812606329027654498958605327561772831497";

  // the integrands, as the evaluator gives them: the value at x at the context given
  private static final Integrand CUBE = (x, mc) -> Real.of(x.value().pow(3, mc));
  private static final Integrand EXP = (x, mc) -> (Real) Elementary.exp(x, mc);
  private static final Integrand SIN = (x, mc) -> (Real) Elementary.sin(x, mc);
  private static final Integrand GAUSS =
      (x, mc) -> (Real) Elementary.exp(Real.of(x.value().multiply(x.value()).negate()), mc);
  private static final Integrand LORENTZ =
      (x, mc) -> Real.of(BigDecimal.ONE.divide(BigDecimal.ONE.add(x.value().pow(2)), mc));
  private static final Integrand INVERSE_ROOT =
      (x, mc) -> Real.of(BigDecimal.ONE.divide(x.value().sqrt(mc), mc));
  private static final Integrand INVERSE =
      (x, mc) -> x.isZero() ? null : Real.of(BigDecimal.ONE.divide(x.value(), mc));

  private static Real value(Result r) {
    assertThat(r).isInstanceOf(Value.class);
    return ((Value) r).value();
  }

  private static Result integrate(Integrand f, Bound a, Bound b, int digits) {
    return Quadrature.integrate(f, a, b, digits, Cancellation.NEVER);
  }

  /** The digits of {@code expected} rounded to {@code digits}. */
  private static Real rounded(String expected, int digits) {
    return Real.of(new BigDecimal(expected).round(new MathContext(digits)));
  }

  /** Within one unit in the last of {@code digits} places. */
  private static void close(Result r, String expected, int digits) {
    BigDecimal got = value(r).value();
    BigDecimal want = new BigDecimal(expected);
    BigDecimal ulp = want.round(new MathContext(digits)).ulp();
    assertThat(got.subtract(want).abs())
        .as("%s against %s", got, expected)
        .isLessThanOrEqualTo(ulp);
  }

  @Test
  void aPolynomial() {
    assertThat(value(integrate(CUBE, ZERO, ONE, 34))).isEqualTo(Real.of("0.25"));
  }

  @Test
  void theExponentialAtThirtyFourAndAHundredDigits() {
    assertThat(value(integrate(EXP, ZERO, ONE, 34))).isEqualTo(rounded(E_MINUS_ONE, 34));
    close(integrate(EXP, ZERO, ONE, 80), E_MINUS_ONE, 80);
  }

  @Test
  void theSineOverAHalfTurn() {
    Bound pi = Bound.finite(Real.of(PI));
    close(integrate(SIN, ZERO, pi, 34), "2", 34);
  }

  @Test
  void anEndpointSingularity() {
    close(integrate(INVERSE_ROOT, ZERO, ONE, 34), "2", 34);
  }

  @Test
  void anEndpointSingularityAwayFromZero() {
    // 1/√(1 − x²) on [−1, 1]: the nodes near ±1 carry their distance from the end
    Integrand f =
        (x, mc) ->
            Real.of(BigDecimal.ONE.divide(BigDecimal.ONE.subtract(x.value().pow(2)).sqrt(mc), mc));
    close(integrate(f, Bound.finite(Real.of("-1")), ONE, 34), PI, 34);
  }

  @Test
  void theGaussianOnTheWholeLine() {
    close(integrate(GAUSS, NEG_INF, INF, 34), SQRT_PI, 34);
    close(integrate(GAUSS, ZERO, ONE, 34), GAUSS_0_1, 34);
  }

  @Test
  void theLorentzianOnTheWholeLineAndAHalfLine() {
    close(integrate(LORENTZ, NEG_INF, INF, 34), PI, 34);
    BigDecimal halfPi = new BigDecimal(PI).divide(BigDecimal.TWO);
    close(integrate(LORENTZ, ZERO, INF, 34), halfPi.toPlainString(), 34);
    close(integrate(LORENTZ, NEG_INF, ZERO, 34), halfPi.toPlainString(), 34);
  }

  @Test
  void theExponentialDecayOnAHalfLine() {
    Integrand f = (x, mc) -> (Real) Elementary.exp(x.negate(), mc);
    close(integrate(f, ZERO, INF, 34), "1", 34);
  }

  @Test
  void reversedAndEqualBounds() {
    assertThat(value(integrate(EXP, ONE, ZERO, 34))).isEqualTo(rounded(E_MINUS_ONE, 34).negate());
    assertThat(value(integrate(EXP, ONE, ONE, 34)).isZero()).isTrue();
    assertThat(value(integrate(LORENTZ, INF, INF, 34)).isZero()).isTrue();
  }

  @Test
  void aDivergentIntegralIsNotANumber() {
    assertThat(integrate(INVERSE, ZERO, ONE, 34)).isEqualTo(new None(Reason.TAIL));
    assertThat(integrate(INVERSE, ONE, INF, 34)).isEqualTo(new None(Reason.TAIL));
    Integrand pole =
        (x, mc) -> Real.of(BigDecimal.ONE.divide(BigDecimal.ONE.subtract(x.value()), mc));
    assertThat(integrate(pole, ZERO, ONE, 34)).isEqualTo(new None(Reason.TAIL));
  }

  @Test
  void anUndefinedSampleInsideIsNotANumber() {
    assertThat(integrate(INVERSE, Bound.finite(Real.of("-1")), ONE, 34))
        .isEqualTo(new None(Reason.UNDEFINED));
  }

  @Test
  void anInteriorPoleThatNoNodeHitsDoesNotSettle() {
    Integrand pole =
        (x, mc) -> Real.of(BigDecimal.ONE.divide(x.value().subtract(new BigDecimal("0.3")), mc));
    assertThat(integrate(pole, ZERO, ONE, 10)).isInstanceOf(None.class);
  }

  @Test
  void theErrorEstimateIsTheLastDifference() {
    Value v = (Value) integrate(GAUSS, ZERO, ONE, 34);
    assertThat(v.error().value().compareTo(new BigDecimal("1e-36"))).isLessThanOrEqualTo(0);
    assertThat(v.level()).isBetween(Quadrature.MIN_LEVEL, Quadrature.maxLevel(34));
  }

  @Test
  void aCrossCheckThatRefusesEveryValueEndsInDisagreement() {
    assertThat(Quadrature.integrate(GAUSS, ZERO, ONE, 10, (v, l1) -> false, Cancellation.NEVER))
        .isEqualTo(new None(Reason.DISAGREE));
    // the check sees the value from the lower bound given to the upper one
    java.util.List<Real> seen = new java.util.ArrayList<>();
    Quadrature.integrate(
        EXP,
        ONE,
        ZERO,
        10,
        (v, l1) -> {
          seen.add(v);
          return true;
        },
        Cancellation.NEVER);
    assertThat(seen.getFirst().signum()).isNegative();
  }

  @Test
  void aNarrowSpikeIsSeenByTheLevelsFromTheMinimumOn() {
    // e^(−10⁴(x − 0.3)²) + 1, a spike of width 0.007 that the levels below the minimum step over:
    // the rule alone, no cross-check, gives the true value or none (the evaluator's cross-check
    // catches what the levels miss, NumericIntegralTest); 10 digits keeps the run under a second
    Integrand spike =
        (x, mc) -> {
          BigDecimal d = x.value().subtract(new BigDecimal("0.3"));
          return Real.of(
              ((Real) Elementary.exp(Real.of(d.multiply(d).multiply(new BigDecimal("-1e4"))), mc))
                  .value()
                  .add(BigDecimal.ONE));
        };
    Result r = integrate(spike, ZERO, ONE, 10);
    if (r instanceof Value v) {
      assertThat(v.value()).isEqualTo(Real.of("1.017724539"));
    }
  }

  @Test
  void theLevelCapGrowsWithTheDigits() {
    assertThat(Quadrature.maxLevel(10)).isEqualTo(10);
    assertThat(Quadrature.maxLevel(34)).isEqualTo(11);
    assertThat(Quadrature.maxLevel(100)).isEqualTo(12);
    assertThat(Quadrature.maxLevel(1000)).isEqualTo(15);
  }

  @Test
  void theResultIsDeterministic() {
    assertThat(integrate(GAUSS, NEG_INF, INF, 34)).isEqualTo(integrate(GAUSS, NEG_INF, INF, 34));
  }

  @Test
  void cancellationStopsALongQuadrature() {
    AtomicInteger checks = new AtomicInteger();
    Cancellation after = () -> checks.incrementAndGet() > 200;
    assertThatThrownBy(() -> Quadrature.integrate(GAUSS, ZERO, ONE, 34, after))
        .isInstanceOf(CancelledException.class);
    Cancellation now = () -> true;
    assertThatThrownBy(() -> Quadrature.integrate(GAUSS, ZERO, ONE, 34, now))
        .isInstanceOf(CancelledException.class);
  }
}
