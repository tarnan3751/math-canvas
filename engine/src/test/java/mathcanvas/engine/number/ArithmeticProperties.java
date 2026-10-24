package mathcanvas.engine.number;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.assertj.core.data.Offset;

class ArithmeticProperties {

  private static final MathContext MC = MathContext.DECIMAL128;
  private static final MathContext REFERENCE = new MathContext(200);

  @Property
  void additionIsAssociativeAndCommutative(
      @ForAll("exacts") Numeric a, @ForAll("exacts") Numeric b, @ForAll("exacts") Numeric c) {
    assertThat(a.add(b, MC)).isEqualTo(b.add(a, MC));
    assertThat(a.add(b, MC).add(c, MC)).isEqualTo(a.add(b.add(c, MC), MC));
  }

  @Property
  void multiplicationIsAssociativeAndCommutative(
      @ForAll("exacts") Numeric a, @ForAll("exacts") Numeric b, @ForAll("exacts") Numeric c) {
    assertThat(a.multiply(b, MC)).isEqualTo(b.multiply(a, MC));
    assertThat(a.multiply(b, MC).multiply(c, MC)).isEqualTo(a.multiply(b.multiply(c, MC), MC));
  }

  @Property
  void multiplicationDistributesOverAddition(
      @ForAll("exacts") Numeric a, @ForAll("exacts") Numeric b, @ForAll("exacts") Numeric c) {
    assertThat(a.multiply(b.add(c, MC), MC))
        .isEqualTo(a.multiply(b, MC).add(a.multiply(c, MC), MC));
  }

  @Property
  void identitiesAndInverses(@ForAll("exacts") Numeric a) {
    assertThat(a.add(Int.ZERO, MC)).isEqualTo(a);
    assertThat(a.multiply(Int.ONE, MC)).isEqualTo(a);
    assertThat(a.add(a.negate(), MC)).isEqualTo(Int.ZERO);
    assertThat(a.subtract(a, MC)).isEqualTo(Int.ZERO);
    if (!a.isZero()) {
      assertThat(a.multiply(Int.ONE.divide(a, MC), MC)).isEqualTo(Int.ONE);
    }
  }

  @Property
  void divisionUndoesMultiplication(@ForAll("exacts") Numeric a, @ForAll("exacts") Numeric b) {
    Assume.that(!b.isZero());
    assertThat(a.divide(b, MC).multiply(b, MC)).isEqualTo(a);
  }

  /** The ring axioms over the Gaussian rationals, through the complex dispatch. */
  @Property
  void gaussianRationalsFormAField(
      @ForAll("exactComplexes") Numeric a,
      @ForAll("exactComplexes") Numeric b,
      @ForAll("exactComplexes") Numeric c) {
    assertThat(a.multiply(b, MC)).isEqualTo(b.multiply(a, MC));
    assertThat(a.multiply(b, MC).multiply(c, MC)).isEqualTo(a.multiply(b.multiply(c, MC), MC));
    assertThat(a.multiply(b.add(c, MC), MC))
        .isEqualTo(a.multiply(b, MC).add(a.multiply(c, MC), MC));
    if (!b.isZero()) {
      assertThat(a.divide(b, MC).multiply(b, MC)).isEqualTo(a);
    }
  }

  @Property
  void powerIsRepeatedMultiplication(
      @ForAll("numerics") Numeric a, @ForAll @IntRange(min = 0, max = 6) int n) {
    Numeric repeated = Int.ONE;
    for (int i = 0; i < n; i++) {
      repeated = repeated.multiply(a, REFERENCE);
    }
    if (a.isExact()) {
      assertThat(a.pow(n, MC)).isEqualTo(repeated);
      if (!a.isZero()) {
        assertThat(a.pow(-n, MC)).isEqualTo(Int.ONE.divide(repeated, MC));
      }
    } else {
      // The repeated product at 200 digits, rounded once, against binary powering.
      Numeric expected = round(repeated, MC);
      assertThat(close(a.pow(n, MC), expected)).as("%s^%d", a, n).isTrue();
    }
  }

  /** {@code Real} arithmetic against {@code double} arithmetic on the same values. */
  @Property
  void realArithmeticAgreesWithDouble(
      @ForAll("moderateDoubles") double x, @ForAll("moderateDoubles") double y) {
    Real a = Real.of(new BigDecimal(x));
    Real b = Real.of(new BigDecimal(y));
    double scale = Math.abs(x) + Math.abs(y);
    assertThat(((RealNumeric) a.add(b, MC)).toDouble()).isCloseTo(x + y, within(1e-12 * scale));
    assertThat(((RealNumeric) a.subtract(b, MC)).toDouble())
        .isCloseTo(x - y, within(1e-12 * scale));
    assertThat(((RealNumeric) a.multiply(b, MC)).toDouble())
        .isCloseTo(x * y, within(1e-12 * Math.abs(x * y)));
    if (y != 0) {
      assertThat(((RealNumeric) a.divide(b, MC)).toDouble())
          .isCloseTo(x / y, within(1e-12 * Math.abs(x / y)));
    }
  }

  /** A {@code Real} result is the exact result rounded once: checked against UNLIMITED. */
  @Property
  void realResultsAreTheExactResultRoundedOnce(
      @ForAll("reals") RealNumeric a,
      @ForAll("realNumerics") RealNumeric b,
      @ForAll @IntRange(min = 1, max = 40) int precision) {
    MathContext mc = new MathContext(precision);
    BigDecimal x = a.toReal(MathContext.UNLIMITED).value();
    BigDecimal y = exactDecimal(b);
    Assume.that(y != null);
    assertThat(a.add(b, mc)).isEqualTo(Real.of(x.add(y, mc)));
    assertThat(a.multiply(b, mc)).isEqualTo(Real.of(x.multiply(y, mc)));
    if (y.signum() != 0) {
      assertThat(a.divide(b, mc)).isEqualTo(Real.of(x.divide(y, mc)));
    }
  }

  /**
   * Past a magnitude gap of 10,000 digits a mixed sum is rounded to odd before its one division; it
   * must still round as the exact sum does, in every rounding mode and at ties.
   */
  @Property
  void farApartMixedSumsRoundAsTheExactSum(
      @ForAll @IntRange(min = -999, max = 999) int unscaled,
      @ForAll @IntRange(min = 10_005, max = 10_050) int gap,
      @ForAll boolean tiny,
      @ForAll @IntRange(min = -2_000_000, max = 2_000_000) int num,
      @ForAll @IntRange(min = 1, max = 2_000) int den,
      @ForAll @IntRange(min = 1, max = 12) int precision,
      @ForAll RoundingMode mode) {
    Assume.that(mode != RoundingMode.UNNECESSARY && unscaled != 0);
    BigDecimal x = BigDecimal.valueOf(unscaled, tiny ? gap : -gap);
    RealNumeric r = Rational.of(num, den);
    Assume.that(!r.isZero());
    MathContext mc = new MathContext(precision, mode);
    BigInteger n = r instanceof Rational q ? q.num() : ((Int) r).value();
    BigInteger d = r instanceof Rational q ? q.den() : BigInteger.ONE;
    BigDecimal exact =
        x.multiply(new BigDecimal(d)).add(new BigDecimal(n)).divide(new BigDecimal(d), mc);
    assertThat(Real.of(x).add(r, mc)).isEqualTo(Real.of(exact));
  }

  @Provide
  Arbitrary<Numeric> exacts() {
    return NumberArbitraries.exacts().map(Numeric.class::cast);
  }

  @Provide
  Arbitrary<Numeric> exactComplexes() {
    return NumberArbitraries.exactComplexes();
  }

  @Provide
  Arbitrary<Numeric> numerics() {
    return NumberArbitraries.numerics();
  }

  @Provide
  Arbitrary<RealNumeric> reals() {
    return NumberArbitraries.reals().map(RealNumeric.class::cast);
  }

  @Provide
  Arbitrary<RealNumeric> realNumerics() {
    return NumberArbitraries.realNumerics();
  }

  @Provide
  Arbitrary<Double> moderateDoubles() {
    return Arbitraries.oneOf(
        Arbitraries.doubles().between(-1e6, 1e6),
        Arbitraries.doubles().between(-1, 1),
        Arbitraries.integers().between(-1000, 1000).map(Integer::doubleValue));
  }

  private static Offset<Double> within(double tolerance) {
    return Offset.offset(Math.max(tolerance, Double.MIN_VALUE));
  }

  /** The value as an exact decimal, or null when it has no terminating expansion. */
  private static BigDecimal exactDecimal(RealNumeric x) {
    try {
      return x.toReal(MathContext.UNLIMITED).value();
    } catch (ArithmeticException nonTerminating) {
      return null;
    }
  }

  private static Numeric round(Numeric x, MathContext mc) {
    return switch (x) {
      case RealNumeric r -> Arithmetic.round(r, mc);
      case Complex z -> Complex.of(Arithmetic.round(z.re(), mc), Arithmetic.round(z.im(), mc));
    };
  }

  /** Each part within one unit of the 34th digit of the larger part. */
  private static boolean close(Numeric actual, Numeric expected) {
    BigDecimal scale =
        actual.modulus(MC).toReal(MC).value().max(expected.modulus(MC).toReal(MC).value());
    if (scale.signum() == 0) {
      return actual.isZero() && expected.isZero();
    }
    BigDecimal ulp = scale.round(MC).ulp();
    return Math.max(
            distance(actual.re(), expected.re()).compareTo(ulp),
            distance(actual.im(), expected.im()).compareTo(ulp))
        <= 0;
  }

  private static BigDecimal distance(RealNumeric a, RealNumeric b) {
    return a.toReal(new MathContext(60))
        .value()
        .subtract(b.toReal(new MathContext(60)).value())
        .abs();
  }
}
