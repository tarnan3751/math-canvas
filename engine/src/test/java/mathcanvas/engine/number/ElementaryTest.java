package mathcanvas.engine.number;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Fixed values against published digits, at 50 and 100 digits, within one ulp. */
class ElementaryTest {

  private static final MathContext MC50 = new MathContext(50);
  private static final MathContext MC100 = new MathContext(100);

  private static final String PI =
      "3.141592653589793238462643383279502884197169399375105820974944592307816406286208998628034825342117068";
  private static final String E =
      "2.718281828459045235360287471352662497757247093699959574966967627724076630353547594571382178525166427";
  private static final String LN2 =
      "0.6931471805599453094172321214581765680755001343602552541206800094933936219696947156058633269964186875";
  private static final String LN10 =
      "2.302585092994045684017991454684364207601101488628772976033327900967572609677352480235997205089598298";
  private static final String SIN1 =
      "0.8414709848078965066525023216302989996225630607983710656727517099919104043912396689486397435430526959";
  private static final String TAN1 =
      "1.557407724654902230506974807458360173087250772381520038383946605698861397151727289555099965202242984";
  private static final String SINH1 =
      "1.175201193643801456882381850595600815155717981334095870229565413013307567304323895607117452089623392";
  private static final String COSH1 =
      "1.543080634815243778477905620757061682601529112365863704737402214710769063049223698964264726435543036";
  private static final String TANH1 =
      "0.7615941559557648881194582826047935904127685972579365515968105001219532445766384834589475216736767144";

  /** {@code acosh 2 = ln(2 + √3)}. */
  private static final String ACOSH2 =
      "1.316957896924816708625046347307968444026981971467516479768472256920460185416443976074219013450101784";

  /** {@code atanh(1/2)}. */
  private static final String ATANH_HALF =
      "0.5493061443340548456976226184612628523237452789113747258673471668187471466093044834368078774068660444";

  private static final String SQRT3 =
      "1.732050807568877293527446341505872366942805253810380628055806979451933016908800037081146186757248576";

  @Test
  void constants() {
    assertClose(Elementary.pi(MC100), PI, MC100);
    assertClose(Elementary.e(MC100), E, MC100);
    assertClose(Elementary.pi(new MathContext(3)), "3.14", new MathContext(3));
  }

  @Test
  void realFunctions() {
    assertClose(Elementary.log(Int.TWO, MC100), LN2, MC100);
    assertClose(Elementary.log(Int.of(10), MC100), LN10, MC100);
    assertClose(Elementary.exp(Int.ONE, MC100), E, MC100);
    assertClose(Elementary.sin(Int.ONE, MC100), SIN1, MC100);
    assertClose(Elementary.tan(Int.ONE, MC100), TAN1, MC100);
    assertClose(Elementary.sinh(Int.ONE, MC100), SINH1, MC100);
    assertClose(Elementary.cosh(Int.ONE, MC100), COSH1, MC100);
    assertClose(Elementary.tanh(Int.ONE, MC100), TANH1, MC100);
    assertClose(Elementary.atan(Int.ONE, MC50), quarter(PI), MC50);
    assertClose(Elementary.asin(Rational.of(1, 2), MC50), divide(PI, 6), MC50);
    assertClose(Elementary.acos(Rational.of(1, 2), MC50), divide(PI, 3), MC50);
    assertClose(Elementary.acos(Int.MINUS_ONE, MC50), PI, MC50);
    assertClose(Elementary.acos(Int.ONE, MC50), "0", MC50);
    assertClose(Elementary.asin(Int.MINUS_ONE, MC50), "-" + divide(PI, 2), MC50);
    assertClose(Elementary.log(Real.of("1E-300"), MC50), "-" + times(LN10, 300), MC50);
  }

  @Test
  void largeArgumentsAreReducedAccurately() {
    // K. C. Ng's example: sin(10^22) needs π to about 40 more digits than the result.
    assertClose(
        Elementary.sin(Real.of("1E22"), MC50),
        "-0.85220084976718880177270589375302936826176215041004",
        MC50);
    // Results of huge magnitude keep a small unscaled value: no expansion to the exponent.
    assertClose(
        Elementary.exp(Real.of("1E9"), new MathContext(10)),
        "8.002981771E+434294481",
        new MathContext(10));
    assertThatThrownBy(() -> Elementary.exp(Real.of("1E10"), MC50))
        .isInstanceOf(ArithmeticException.class);
    assertClose(
        Elementary.exp(Int.of(-1000), new MathContext(20)),
        "5.0759588975494567653E-435",
        new MathContext(20));
  }

  /** Arguments whose results or reductions are out of reach fail at once, not after minutes. */
  @Test
  @Timeout(10)
  void hopelessArgumentsThrowAtOnce() {
    assertThatThrownBy(() -> Elementary.exp(Real.of("1E100000"), MC50))
        .isInstanceOf(ArithmeticException.class)
        .hasMessageContaining("overflow");
    assertThatThrownBy(() -> Elementary.exp(Real.of("-1E100000"), MC50))
        .isInstanceOf(ArithmeticException.class)
        .hasMessageContaining("underflow");
    assertThatThrownBy(() -> Int.of(-2).pow(Real.of("1E100000000"), MC50))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> Int.TWO.pow(Real.of("1E100000000"), MC50))
        .isInstanceOf(ArithmeticException.class);
    for (Runnable tooLarge :
        List.<Runnable>of(
            () -> Elementary.sin(Real.of("1E10000"), MC50),
            () -> Elementary.cos(Real.of("1E100000"), MC50),
            () -> Elementary.tan(Real.of("1E10000"), MC50),
            () -> Elementary.sin(Complex.of(Real.of("1E10000"), Int.ONE), MC50),
            () -> Elementary.exp(Complex.of(Int.ONE, Real.of("1E10000")), MC50),
            () -> Elementary.tanh(Complex.of(Int.ONE, Real.of("1E10000")), MC50),
            () -> Int.TWO.pow(Complex.of(Int.ZERO, Real.of("1E10000")), MC50))) {
      assertThatThrownBy(tooLarge::run)
          .isInstanceOf(ArithmeticException.class)
          .hasMessageContaining("too large to reduce");
    }
    // An angle with a decimal exponent within the cap (4·50 + 400 = 600) is reduced.
    assertThat(Elementary.sin(Real.of("1E500"), MC50)).isInstanceOf(Real.class);
  }

  @Test
  @Timeout(10)
  void rootsOfHighDegreeAreFastAndFaithful() {
    MathContext p34 = MathContext.DECIMAL128;
    for (int n : new int[] {3, 1000, 1_000_000, Integer.MAX_VALUE}) {
      RealNumeric root = Int.of(3).root(n, p34);
      BigDecimal reference =
          ((Real)
                  Elementary.exp(
                      Elementary.log(Int.of(3), new MathContext(80))
                          .divide(Int.of(n), new MathContext(80)),
                      new MathContext(80)))
              .value();
      assertThat(((Real) root).value().subtract(reference).abs())
          .isLessThanOrEqualTo(reference.round(p34).ulp());
    }
    assertThat(Int.of(3).pow(Rational.of(BigInteger.ONE, BigInteger.ONE.shiftLeft(30)), p34))
        .isInstanceOf(Real.class);
    assertThat(Int.ONE.pow(Rational.of(BigInteger.ONE, BigInteger.ONE.shiftLeft(30)), p34))
        .isEqualTo(Int.ONE);
  }

  /** The exact-root test of a high degree forms one candidate, not a search. */
  @Test
  @Timeout(5)
  void exactRootTestsOfHighDegreeAreFast() {
    MathContext p34 = MathContext.DECIMAL128;
    Int million = Int.of(BigInteger.TEN.pow(1_000_000).add(BigInteger.ONE));
    Int hundredThousand = Int.of(BigInteger.TEN.pow(100_000).add(BigInteger.ONE));
    assertThat(million.pow(Rational.of(1, 65), p34)).isInstanceOf(Real.class);
    assertThat(million.root(65, p34)).isInstanceOf(Real.class);
    assertThat(hundredThousand.pow(Rational.of(1, 200), p34)).isInstanceOf(Real.class);
    // A perfect power of high degree is still found exactly.
    Int sevenTo65000 = Int.of(BigInteger.valueOf(7).pow(65_000));
    assertThat(sevenTo65000.root(65, p34)).isEqualTo(Int.of(BigInteger.valueOf(7).pow(1000)));
  }

  @Test
  void theWorkingPrecisionIsCapped() {
    // sin(π to 1000 digits) is about 1e-1000: its 34 digits need more than 4·34 + 400.
    Real pi1000 = Elementary.pi(new MathContext(1000));
    assertThatThrownBy(() -> Elementary.sin(pi1000, MathContext.DECIMAL128))
        .isInstanceOf(ArithmeticException.class)
        .hasMessageContaining("536");
    // π to 400 digits is within the cap: sin(π₄₀₀) = π - π₄₀₀.
    Real pi400 = Elementary.pi(new MathContext(400));
    BigDecimal difference = Elementary.pi(new MathContext(500)).value().subtract(pi400.value());
    assertClose(
        Elementary.sin(pi400, MathContext.DECIMAL128),
        difference.toString(),
        MathContext.DECIMAL128);
  }

  @Test
  void resultsAreInexactAndExactArgumentsAreAccepted() {
    assertThat(Elementary.exp(Int.ZERO, MC50)).isEqualTo(Real.of("1")).isInstanceOf(Real.class);
    assertThat(Elementary.sin(Int.ZERO, MC50)).isEqualTo(Real.of("0"));
    assertThat(Elementary.log(Int.ONE, MC50)).isEqualTo(Real.of("0"));
    assertThat(Elementary.tanh(Int.of(1000), MC50)).isEqualTo(Real.of("1"));
  }

  @Test
  void outsideTheRealDomainGivesThePrincipalComplexValue() {
    Numeric logMinusOne = Elementary.log(Int.MINUS_ONE, MC50);
    assertClose(logMinusOne.re(), "0", MC50);
    assertClose(logMinusOne.im(), PI, MC50);
    // The cut convention: counterclockwise continuity, as Mathematica (C99 gives +1.3169…i).
    Numeric asin2 = Elementary.asin(Int.TWO, MC50);
    assertClose(asin2.re(), divide(PI, 2), MC50);
    assertClose(asin2.im(), "-" + ACOSH2, MC50);
    Numeric asinMinus2 = Elementary.asin(Int.of(-2), MC50);
    assertClose(asinMinus2.re(), "-" + divide(PI, 2), MC50);
    assertClose(asinMinus2.im(), ACOSH2, MC50);
    Numeric acos2 = Elementary.acos(Int.TWO, MC50);
    assertClose(acos2.re(), "0", MC50);
    assertClose(acos2.im(), ACOSH2, MC50);
    Numeric acosMinus2 = Elementary.acos(Int.of(-2), MC50);
    assertClose(acosMinus2.re(), PI, MC50);
    assertClose(acosMinus2.im(), "-" + ACOSH2, MC50);
  }

  @Test
  void complexFunctionsAndTheirCuts() {
    Numeric atan2i = Elementary.atan(Complex.of(Int.ZERO, Int.TWO), MC50);
    assertClose(atan2i.re(), divide(PI, 2), MC50);
    assertClose(atan2i.im(), ATANH_HALF, MC50);
    Numeric atanMinus2i = Elementary.atan(Complex.of(Int.ZERO, Int.of(-2)), MC50);
    assertClose(atanMinus2i.re(), "-" + divide(PI, 2), MC50);
    assertClose(atanMinus2i.im(), "-" + ATANH_HALF, MC50);
    // The cut of the complex formulas agrees with the real-axis formulas on either side.
    Numeric asinAbove = Elementary.asin(Complex.of(Int.TWO, Real.of("1E-60")), MC50);
    assertClose(asinAbove.im(), ACOSH2, MC50);
    Numeric asinBelow = Elementary.asin(Complex.of(Int.TWO, Real.of("-1E-60")), MC50);
    assertClose(asinBelow.im(), "-" + ACOSH2, MC50);
    Numeric expIPi = Elementary.exp(Complex.of(Int.ZERO, Elementary.pi(MC100)), MC50);
    assertClose(expIPi.re(), "-1", MC50);
    Numeric logI = Elementary.log(Complex.I, MC50);
    assertClose(logI.re(), "0", MC50);
    assertClose(logI.im(), divide(PI, 2), MC50);
    Numeric sinI = Elementary.sin(Complex.I, MC50);
    assertClose(sinI.re(), "0", MC50);
    assertClose(sinI.im(), SINH1, MC50);
    Numeric coshI = Elementary.cosh(Complex.I, MC50);
    assertThat(
            coshI
                .re()
                .toReal(MC50)
                .value()
                .compareTo(
                    new BigDecimal(
                            "0.5403023058681397174009366074429766037323104206179222276700972553811003947744717645179518560871830893")
                        .round(MC50)))
        .isZero();
    assertThatThrownBy(() -> Elementary.atan(Complex.I, MC50))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> Elementary.log(Int.ZERO, MC50))
        .isInstanceOf(ArithmeticException.class);
    for (Runnable inexact :
        List.<Runnable>of(
            () -> Elementary.exp(Int.ONE, MathContext.UNLIMITED),
            () -> Int.TWO.pow(Rational.of(1, 3), MathContext.UNLIMITED),
            () -> Int.of(-1).argument(MathContext.UNLIMITED),
            () -> Complex.I.argument(MathContext.UNLIMITED))) {
      assertThatThrownBy(inexact::run)
          .isInstanceOf(ArithmeticException.class)
          .hasMessage("an inexact result needs a finite precision");
    }
  }

  @Test
  void generalPowers() {
    // The principal value: (-8)^(1/3) = 2e^(iπ/3) = 1 + √3 i.
    Numeric cubeRoot = Int.of(-8).pow(Rational.of(1, 3), MC50);
    assertThat(cubeRoot).isInstanceOf(Complex.class);
    assertClose(cubeRoot.re(), "1", MC50);
    assertClose(cubeRoot.im(), SQRT3, MC50);
    assertThat(Int.of(4).pow(Rational.of(1, 2), MC50)).isEqualTo(Int.TWO);
    assertThat(Int.of(8).pow(Rational.of(1, 3), MC50)).isEqualTo(Int.TWO);
    assertThat(Rational.of(8, 27).pow(Rational.of(2, 3), MC50)).isEqualTo(Rational.of(4, 9));
    assertThat(Int.of(8).pow(Rational.of(-1, 3), MC50)).isEqualTo(Rational.of(1, 2));
    assertThat(Int.of(-4).pow(Rational.of(1, 2), MC50)).isEqualTo(Complex.of(Int.ZERO, Int.TWO));
    assertThat(Int.of(-4).pow(Rational.of(3, 2), MC50)).isEqualTo(Complex.of(Int.ZERO, Int.of(-8)));
    assertThat(Int.of(-4).pow(Rational.of(-1, 2), MC50))
        .isEqualTo(Complex.of(Int.ZERO, Rational.of(-1, 2)));
    assertThat(Int.of(-8).pow(Real.of("2.0"), MC50)).isEqualTo(Real.of("64"));
    assertThat(Int.of(4).pow(Real.of("0.5"), MC50))
        .isEqualTo(Real.of("2"))
        .isInstanceOf(Real.class);
    assertThat(Int.of(-4).pow(Real.of("0.5"), MC50)).isEqualTo(Complex.of(Int.ZERO, Real.of("2")));
    assertThat(Complex.of(Int.of(3), Int.of(4)).pow(Rational.of(1, 2), MC50))
        .isEqualTo(Complex.of(Int.TWO, Int.ONE));
    assertClose(Int.TWO.pow(Rational.of(1, 2), MC50), SQRT2, MC50);
    assertClose(Int.TWO.pow(Real.of("0.5"), MC50), SQRT2, MC50);
    // i^i = e^(-π/2).
    Numeric iToTheI = Complex.I.pow(Complex.I, MC50);
    assertClose(
        iToTheI,
        "0.20787957635076190854695561983497877003387784163176960807513588305541987728",
        MC50);
    assertThat(Int.of(2).pow(Int.of(10), MC50)).isEqualTo(Int.of(1024));
    assertThat(Int.MINUS_ONE.pow(Int.of(1L << 40).add(Int.ONE, MC50), MC50))
        .isEqualTo(Int.MINUS_ONE);
    assertThatThrownBy(() -> Int.TWO.pow(Int.of(1L << 40), MC50))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void zeroBase() {
    assertThat(Int.ZERO.pow(Rational.of(1, 2), MC50)).isEqualTo(Int.ZERO);
    assertThat(Int.ZERO.pow(Real.of("0.5"), MC50)).isEqualTo(Real.of("0"));
    assertThat(Int.ZERO.pow(Real.of("0"), MC50)).isEqualTo(Real.of("1"));
    assertThat(Int.ZERO.pow(Complex.of(Int.ONE, Int.ONE), MC50)).isEqualTo(Int.ZERO);
    assertThatThrownBy(() -> Int.ZERO.pow(Rational.of(-1, 2), MC50))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> Int.ZERO.pow(Complex.I, MC50)).isInstanceOf(ArithmeticException.class);
  }

  @Test
  void argument() {
    assertThat(Int.of(3).argument(MC50)).isEqualTo(Int.ZERO);
    assertThat(Int.ZERO.argument(MC50)).isEqualTo(Int.ZERO);
    assertThat(Real.of("2.5").argument(MC50)).isEqualTo(Real.of("0"));
    assertClose(Int.of(-3).argument(MC50), PI, MC50);
    assertClose(Complex.of(Int.ONE, Int.ONE).argument(MC50), quarter(PI), MC50);
    assertClose(
        Complex.of(Int.of(-1), Int.of(-1)).argument(MC50), "-" + times(quarter(PI), 3), MC50);
  }

  private static final String SQRT2 =
      "1.414213562373095048801688724209698078569671875376948073176679737990732478462107038850387534327641573";

  private static String quarter(String x) {
    return divide(x, 4);
  }

  private static String divide(String x, int n) {
    return new BigDecimal(x).divide(BigDecimal.valueOf(n), new MathContext(110)).toString();
  }

  private static String times(String x, int n) {
    return new BigDecimal(x).multiply(BigDecimal.valueOf(n)).toString();
  }

  /**
   * The number is real (or its imaginary part negligible) and within one ulp of {@code expected}.
   */
  static void assertClose(Numeric actual, String expected, MathContext mc) {
    BigDecimal value = actual.re().toReal(mc).value();
    BigDecimal reference = new BigDecimal(expected);
    BigDecimal ulp =
        reference.signum() == 0
            ? BigDecimal.ONE.movePointLeft(mc.getPrecision())
            : reference.round(mc).ulp();
    assertThat(value.subtract(reference).abs())
        .as("%s against %s", actual, expected)
        .isLessThanOrEqualTo(ulp);
  }
}
