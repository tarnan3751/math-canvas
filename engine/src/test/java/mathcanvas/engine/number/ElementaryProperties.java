package mathcanvas.engine.number;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.function.BiFunction;
import java.util.function.DoubleUnaryOperator;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/**
 * The accuracy claim of {@link Elementary}: every function at 34 digits against the same function
 * at 54 digits (the last-digit claim), against {@code java.lang.Math} at double precision, and the
 * classical identities.
 */
class ElementaryProperties {

  private static final MathContext P34 = MathContext.DECIMAL128;
  private static final MathContext P54 = new MathContext(54);

  enum Fn {
    EXP(Elementary::exp, Math::exp),
    LOG(Elementary::log, Math::log),
    SIN(Elementary::sin, Math::sin),
    COS(Elementary::cos, Math::cos),
    TAN(Elementary::tan, Math::tan),
    ATAN(Elementary::atan, Math::atan),
    ASIN(Elementary::asin, Math::asin),
    ACOS(Elementary::acos, Math::acos),
    SINH(Elementary::sinh, Math::sinh),
    COSH(Elementary::cosh, Math::cosh),
    TANH(Elementary::tanh, Math::tanh);

    final BiFunction<Numeric, MathContext, Numeric> exact;
    final DoubleUnaryOperator reference;

    Fn(BiFunction<Numeric, MathContext, Numeric> exact, DoubleUnaryOperator reference) {
      this.exact = exact;
      this.reference = reference;
    }
  }

  /** The documented bound, against a 54-digit evaluation of the same function. */
  @Property
  void realResultsAreWithinOneUlp(@ForAll Fn fn, @ForAll("arguments") BigDecimal x) {
    Numeric argument = Real.of(inDomain(fn, x));
    assertWithinOneUlp(fn.exact.apply(argument, P34), fn.exact.apply(argument, P54), 34);
  }

  @Property
  void complexResultsAreWithinOneUlpPerPart(
      @ForAll Fn fn, @ForAll("moderate") BigDecimal a, @ForAll("moderate") BigDecimal b) {
    Numeric z = Complex.of(Real.of(a), Real.of(b));
    Numeric actual;
    try {
      actual = fn.exact.apply(z, P34);
    } catch (ArithmeticException pole) {
      return;
    }
    assertWithinOneUlp(actual, fn.exact.apply(z, P54), 34);
  }

  /** Real arguments outside the real domain take the complex path for asin, acos and log. */
  @Property
  void outsideTheDomainResultsAreWithinOneUlpPerPart(
      @ForAll("outside") Fn fn, @ForAll("arguments") BigDecimal x) {
    Numeric argument = Real.of(fn == Fn.LOG ? x.abs().negate() : x.abs().add(BigDecimal.ONE));
    if (argument.isZero()) {
      return;
    }
    assertWithinOneUlp(fn.exact.apply(argument, P34), fn.exact.apply(argument, P54), 34);
    if (fn != Fn.LOG) {
      assertWithinOneUlp(
          fn.exact.apply(argument.negate(), P34), fn.exact.apply(argument.negate(), P54), 34);
    }
  }

  @Property
  void agreesWithJavaLangMath(@ForAll Fn fn, @ForAll("doubles") double d) {
    double x = mathDomain(fn, d);
    double expected = fn.reference.applyAsDouble(x);
    if (!Double.isFinite(expected)) {
      return;
    }
    // The exact binary value of x, so the comparison is of the functions alone.
    double actual = ((RealNumeric) fn.exact.apply(Real.of(new BigDecimal(x)), P34)).toDouble();
    // Math promises 1 ulp (2.5 for sinh, cosh, tanh); ours rounds once more to double.
    assertThat(Math.abs(actual - expected))
        .as("%s(%s)", fn, x)
        .isLessThanOrEqualTo(3 * Math.ulp(expected));
  }

  @Property
  void expInvertsLog(@ForAll("arguments") BigDecimal x) {
    Real positive = Real.of(x.abs().add(BigDecimal.ONE.movePointLeft(30)));
    Numeric roundTrip = Elementary.exp(Elementary.log(positive, P34), P34);
    // log x is off by under one ulp of itself, at most 10^(1-p)·|log x|, which exp turns into a
    // relative error of x; one ulp of x is at least 10^-p of it: so 10·|log x| ulps, plus one.
    BigDecimal log = ((Real) Elementary.log(positive, P34)).value().abs();
    int ulps = 10 * (log.intValue() + 1) + 1;
    assertWithinUlps(roundTrip, positive, 34, ulps);
  }

  @Property
  void sineSquaredPlusCosineSquaredIsOne(@ForAll("arguments") BigDecimal x) {
    Real argument = Real.of(x);
    Numeric sin = Elementary.sin(argument, P34);
    Numeric cos = Elementary.cos(argument, P34);
    Numeric sum = sin.multiply(sin, P54).add(cos.multiply(cos, P54), P54);
    assertWithinUlps(sum, Int.ONE, 34, 2);
  }

  @Property
  void squareRootSquaredIsTheRadicand(@ForAll("arguments") BigDecimal x) {
    Real radicand = Real.of(x.abs());
    Numeric root = radicand.sqrt(P34);
    // Half an ulp of the root doubles in relative terms when squared; across a decade boundary
    // that is up to √10 ulps of the radicand.
    assertWithinUlps(root.multiply(root, P54), radicand, 34, 4);
  }

  @Property
  void complexExpInvertsLog(@ForAll("moderate") BigDecimal a, @ForAll("moderate") BigDecimal b) {
    Numeric z = Complex.of(Real.of(a), Real.of(b));
    if (z.isZero()) {
      return;
    }
    Numeric roundTrip = Elementary.exp(Elementary.log(z, P54), P34);
    assertWithinUlpsNormwise(roundTrip, z, 34, 4);
  }

  @Property
  void sineInvertsArcsine(@ForAll("moderate") BigDecimal a, @ForAll("moderate") BigDecimal b) {
    Numeric z = Complex.of(Real.of(a), Real.of(b));
    Numeric roundTrip = Elementary.sin(Elementary.asin(z, P54), P34);
    assertWithinUlpsNormwise(roundTrip, z, 34, 4);
  }

  /** A general power against {@code exp(y log x)} composed at a higher precision. */
  @Property
  void powerIsExpOfLog(@ForAll("powerBases") Numeric x, @ForAll("powerExponents") Numeric y) {
    Numeric actual;
    try {
      actual = x.pow(y, P34);
    } catch (ArithmeticException zeroBase) {
      assertThat(x.isZero()).isTrue();
      return;
    }
    if (x.isZero()) {
      return;
    }
    MathContext high = new MathContext(80);
    Numeric reference = Elementary.exp(y.multiply(Elementary.log(x, high), high), high);
    assertWithinOneUlp(actual, reference, 34);
  }

  /**
   * The exact-root test (one seeded candidate, no search) finds every perfect power and only those:
   * {@code r^n} has the root {@code r}, and {@code r^n ± 1} has none.
   */
  @Property(tries = 300)
  void exactRootsOfLargePowersAreFound(
      @ForAll("rootBases") BigInteger r, @ForAll @IntRange(min = 3, max = 300) int n) {
    BigInteger power = r.pow(n);
    assertThat(Int.of(power).root(n, P34)).isEqualTo(Int.of(r));
    assertThat(Int.of(power.add(BigInteger.ONE)).root(n, P34)).isInstanceOf(Real.class);
    assertThat(Int.of(power.subtract(BigInteger.ONE)).root(n, P34)).isInstanceOf(Real.class);
  }

  @Provide
  Arbitrary<BigInteger> rootBases() {
    return Arbitraries.oneOf(
        Arbitraries.bigIntegers().between(BigInteger.TWO, BigInteger.valueOf(1000)),
        Arbitraries.bigIntegers().between(BigInteger.TWO, BigInteger.TEN.pow(40)));
  }

  @Provide
  Arbitrary<BigDecimal> arguments() {
    Arbitrary<BigInteger> unscaled =
        Arbitraries.oneOf(
            Arbitraries.bigIntegers().between(BigInteger.valueOf(-1000), BigInteger.valueOf(1000)),
            Arbitraries.bigIntegers()
                .between(BigInteger.TEN.pow(40).negate(), BigInteger.TEN.pow(40)));
    return Combinators.combine(unscaled, Arbitraries.integers().between(-60, 60))
        .as(BigDecimal::new)
        .map(
            x ->
                x.precision() - x.scale() > 30
                    ? x.movePointLeft(x.precision() - x.scale() - 30)
                    : x);
  }

  @Provide
  Arbitrary<BigDecimal> moderate() {
    return Combinators.combine(
            Arbitraries.bigIntegers()
                .between(BigInteger.valueOf(-50_000_000), BigInteger.valueOf(50_000_000)),
            Arbitraries.integers().between(5, 9))
        .as(BigDecimal::new);
  }

  @Provide
  Arbitrary<Fn> outside() {
    return Arbitraries.of(Fn.ASIN, Fn.ACOS, Fn.LOG);
  }

  @Provide
  Arbitrary<Double> doubles() {
    return Arbitraries.oneOf(
        Arbitraries.doubles().between(-1, 1).ofScale(17),
        Arbitraries.doubles().between(-700, 700).ofScale(12));
  }

  @Provide
  Arbitrary<Numeric> powerBases() {
    return Arbitraries.oneOf(
        NumberArbitraries.exacts()
            .filter(r -> r.abs().compareValue(Int.of(1_000_000)) < 0)
            .map(Numeric.class::cast),
        moderate().map(Real::of),
        Combinators.combine(moderate(), moderate())
            .as((a, b) -> Complex.of(Real.of(a), Real.of(b))));
  }

  @Provide
  Arbitrary<Numeric> powerExponents() {
    return Arbitraries.oneOf(
        Combinators.combine(
                Arbitraries.integers().between(-12, 12), Arbitraries.integers().between(2, 7))
            .as((p, q) -> (Numeric) Rational.of(p, q)),
        moderate().map(Real::of),
        Combinators.combine(moderate(), moderate())
            .as((a, b) -> Complex.of(Real.of(a), Real.of(b))));
  }

  /** Brings an argument into the function's real domain, and bounds exp and its kin. */
  private static BigDecimal inDomain(Fn fn, BigDecimal x) {
    return switch (fn) {
      case LOG -> x.signum() == 0 ? BigDecimal.ONE : x.abs();
      case ASIN, ACOS -> x.abs().compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE.divide(x, P54) : x;
      case EXP, SINH, COSH ->
          x.abs().compareTo(BigDecimal.valueOf(5000)) > 0
              ? x.movePointLeft(x.precision() - x.scale())
              : x;
      default -> x;
    };
  }

  private static double mathDomain(Fn fn, double x) {
    return switch (fn) {
      case LOG -> x == 0 ? 1 : Math.abs(x) * 1e3;
      case ASIN, ACOS -> x > 1 || x < -1 ? 1 / x : x;
      case TAN, SIN, COS, ATAN, TANH -> x;
      default -> x;
    };
  }

  /** Each part within one ulp at {@code p} of the reference, as {@link Elementary} documents. */
  static void assertWithinOneUlp(Numeric actual, Numeric reference, int p) {
    assertWithinUlps(actual, reference, p, 1);
  }

  static void assertWithinUlps(Numeric actual, Numeric reference, int p, int ulps) {
    MathContext reading = new MathContext(p + 40);
    BigDecimal re = reference.re().toReal(reading).value();
    BigDecimal im = reference.im().toReal(reading).value();
    BigDecimal floor = re.abs().max(im.abs()).scaleByPowerOfTen(-p);
    check(actual.re().toReal(reading).value(), re, floor, p, ulps, actual, reference);
    check(actual.im().toReal(reading).value(), im, floor, p, ulps, actual, reference);
  }

  static void assertWithinUlpsNormwise(Numeric actual, Numeric reference, int p, int ulps) {
    MathContext reading = new MathContext(p + 40);
    BigDecimal re = reference.re().toReal(reading).value();
    BigDecimal im = reference.im().toReal(reading).value();
    BigDecimal norm = re.abs().max(im.abs());
    check(actual.re().toReal(reading).value(), re, norm, p, ulps, actual, reference);
    check(actual.im().toReal(reading).value(), im, norm, p, ulps, actual, reference);
  }

  private static void check(
      BigDecimal actual,
      BigDecimal reference,
      BigDecimal floor,
      int p,
      int ulps,
      Numeric whole,
      Numeric expected) {
    BigDecimal scale = reference.abs().max(floor);
    if (scale.signum() == 0) {
      assertThat(actual.signum()).as("%s against %s", whole, expected).isZero();
      return;
    }
    int exponent = scale.precision() - scale.scale() - 1;
    BigDecimal ulp = BigDecimal.ONE.scaleByPowerOfTen(exponent - p + 1);
    assertThat(actual.subtract(reference).abs())
        .as("%s against %s", whole, expected)
        .isLessThanOrEqualTo(ulp.multiply(BigDecimal.valueOf(ulps)));
  }
}
