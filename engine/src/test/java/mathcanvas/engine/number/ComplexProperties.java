package mathcanvas.engine.number;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;
import net.jqwik.api.constraints.IntRange;

/**
 * Complex products and quotients are each part's exact value rounded once, also when the parts
 * cancel; the integer power agrees with the product; inexact roots of rationals are faithful.
 */
class ComplexProperties {

  @Property
  void productsRoundEachPartOnce(
      @ForAll("productPairs") Tuple.Tuple2<Numeric, Numeric> pair,
      @ForAll @IntRange(min = 1, max = 40) int precision,
      @ForAll("modes") RoundingMode mode) {
    MathContext mc = new MathContext(precision, mode);
    Numeric z = pair.get1();
    Numeric w = pair.get2();
    assertRoundedOnce(z.multiply(w, mc), exact(z).multiply(exact(w), mc), z, w, mc);
  }

  @Property
  void quotientsRoundEachPartOnce(
      @ForAll("quotientPairs") Tuple.Tuple2<Numeric, Numeric> pair,
      @ForAll @IntRange(min = 1, max = 40) int precision,
      @ForAll("modes") RoundingMode mode) {
    MathContext mc = new MathContext(precision, mode);
    Numeric z = pair.get1();
    Numeric w = pair.get2();
    Assume.that(!w.isZero());
    assertRoundedOnce(z.divide(w, mc), exact(z).divide(exact(w), mc), z, w, mc);
  }

  /**
   * Past a 10,000-digit gap between the divisor's squared parts the norm is rounded to odd, and
   * each part of the quotient is faithful: within one ulp plus a margin of a hundredth of an ulp.
   * 200 tries, not jqwik's 1000: the exact reference divides 10,000-digit rationals, and at 1000
   * tries this one property cost about 12 s of every gate run.
   */
  @Property(tries = 200)
  void farApartQuotientsAreFaithful(
      @ForAll("farApartPairs") Tuple.Tuple2<Numeric, Numeric> pair,
      @ForAll @IntRange(min = 1, max = 40) int precision,
      @ForAll("modes") RoundingMode mode) {
    MathContext mc = new MathContext(precision, mode);
    Numeric z = pair.get1();
    Numeric w = pair.get2();
    Numeric actual = z.divide(w, mc);
    Numeric exact = exact(z).divide(exact(w), mc);
    MathContext reading = new MathContext(precision + 40);
    for (boolean imaginary : new boolean[] {false, true}) {
      RealNumeric part = imaginary ? actual.im() : actual.re();
      BigDecimal reference = (imaginary ? exact.im() : exact.re()).toReal(reading).value();
      if (reference.signum() == 0) {
        assertThat(part.isZero()).isTrue();
        continue;
      }
      BigDecimal ulp = reference.round(new MathContext(precision, RoundingMode.DOWN)).ulp();
      assertThat(part.toReal(reading).value().subtract(reference).abs())
          .as("%s with %s at %s", z, w, mc)
          .isLessThanOrEqualTo(ulp.multiply(new BigDecimal("1.01")));
    }
  }

  @Property
  void theSquareIsTheProduct(
      @ForAll("numbers") Numeric z, @ForAll @IntRange(min = 1, max = 40) int precision) {
    MathContext mc = new MathContext(precision);
    assertThat(z.pow(2, mc)).isEqualTo(z.multiply(z, mc));
  }

  /** The documented bound for inexact roots of rationals: within one ulp of a 60-digit value. */
  @Property
  void rootsOfRationalsAreFaithful(
      @ForAll("rationals") RealNumeric x, @ForAll @IntRange(min = 2, max = 9) int n) {
    RealNumeric radicand = x.abs();
    Assume.that(!radicand.isZero());
    MathContext mc = MathContext.DECIMAL128;
    RealNumeric root = radicand.root(n, mc);
    Assume.that(!root.isExact());
    BigDecimal reference = ((Real) radicand.root(n, new MathContext(60))).value();
    BigDecimal ulp = reference.round(mc).ulp();
    assertThat(((Real) root).value().subtract(reference).abs()).isLessThanOrEqualTo(ulp);
    if (n == 2) {
      Real sqrt = (Real) radicand.sqrt(mc);
      assertThat(sqrt.value().subtract(reference).abs()).isLessThanOrEqualTo(ulp);
    }
  }

  @Property
  void binomialsFollowPascalsRule(
      @ForAll @IntRange(min = -40, max = 60) int n, @ForAll @IntRange(min = -3, max = 45) int k) {
    Int nn = Int.of(n);
    Int kk = Int.of(k);
    if (k == 0) {
      assertThat(nn.binomial(kk)).isEqualTo(Int.ONE);
    } else {
      Int previous = Int.of(n - 1L);
      Numeric sum =
          previous.binomial(Int.of(k - 1L)).add(previous.binomial(kk), MathContext.UNLIMITED);
      assertThat(nn.binomial(kk)).isEqualTo(sum);
    }
  }

  @Provide
  Arbitrary<Numeric> numbers() {
    return Arbitraries.oneOf(parts().map(Numeric.class::cast), complexes());
  }

  @Provide
  Arbitrary<RealNumeric> rationals() {
    return NumberArbitraries.rationals();
  }

  @Provide
  Arbitrary<RoundingMode> modes() {
    return Arbitraries.of(RoundingMode.class).filter(m -> m != RoundingMode.UNNECESSARY);
  }

  /** Random pairs, and pairs built so that {@code pr - qs} cancels: {@code w = (q + ε) + pi}. */
  @Provide
  Arbitrary<Tuple.Tuple2<Numeric, Numeric>> productPairs() {
    Arbitrary<Tuple.Tuple2<Numeric, Numeric>> cancelling =
        Combinators.combine(parts(), parts(), Arbitraries.of(1, -1))
            .as((p, q, d) -> Tuple.of(complex(p, q), complex(NumberArbitraries.step(q, d), p)));
    return Arbitraries.oneOf(Combinators.combine(numbers(), numbers()).as(Tuple::of), cancelling);
  }

  /** Random pairs, and pairs built so that {@code qr - ps} cancels: {@code w = (p + ε) + qi}. */
  @Provide
  Arbitrary<Tuple.Tuple2<Numeric, Numeric>> quotientPairs() {
    Arbitrary<Tuple.Tuple2<Numeric, Numeric>> cancelling =
        Combinators.combine(parts(), parts(), Arbitraries.of(1, -1))
            .as((p, q, d) -> Tuple.of(complex(p, q), complex(NumberArbitraries.step(p, d), q)));
    return Arbitraries.oneOf(Combinators.combine(numbers(), numbers()).as(Tuple::of), cancelling);
  }

  /**
   * Divisors whose parts are so far apart that {@code r²} and {@code s²} differ by 10,000 digits.
   */
  @Provide
  Arbitrary<Tuple.Tuple2<Numeric, Numeric>> farApartPairs() {
    Arbitrary<RealNumeric> farPart =
        Combinators.combine(
                Arbitraries.integers().between(-999, 999).filter(u -> u != 0),
                Arbitraries.integers().between(5_100, 5_200),
                Arbitraries.of(true, false))
            .as((u, gap, large) -> Real.of(BigDecimal.valueOf(u, large ? -gap : gap)));
    return Combinators.combine(numbers(), farPart, parts(), Arbitraries.of(true, false))
        .as(
            (z, far, near, realFar) ->
                Tuple.of(z, realFar ? complex(far, near) : complex(near, far)));
  }

  private Arbitrary<Numeric> complexes() {
    return Combinators.combine(parts(), parts()).as(ComplexProperties::complex);
  }

  /** {@code re + im·i}, an exact part beside a {@code Real} one promoted at 34 digits. */
  private static Numeric complex(RealNumeric re, RealNumeric im) {
    return Complex.of(re, im, MathContext.DECIMAL128);
  }

  /** Parts of every kind, of moderate size so the exact reference stays cheap. */
  private Arbitrary<RealNumeric> parts() {
    Arbitrary<BigInteger> unscaled =
        Arbitraries.oneOf(
            Arbitraries.bigIntegers().between(BigInteger.valueOf(-20), BigInteger.valueOf(20)),
            Arbitraries.bigIntegers()
                .between(BigInteger.TEN.pow(30).negate(), BigInteger.TEN.pow(30)));
    Arbitrary<RealNumeric> reals =
        Combinators.combine(unscaled, Arbitraries.integers().between(-20, 40))
            .as((u, s) -> Real.of(new BigDecimal(u, s)));
    Arbitrary<RealNumeric> rationals =
        Combinators.combine(unscaled, unscaled.filter(d -> d.signum() != 0)).as(Rational::of);
    return Arbitraries.oneOf(reals, rationals);
  }

  /** The exact value of a number: each {@code Real} part as the rational it denotes. */
  private static Numeric exact(Numeric x) {
    return Complex.of(exactPart(x.re()), exactPart(x.im()));
  }

  private static RealNumeric exactPart(RealNumeric x) {
    return switch (x) {
      case Int i -> i;
      case Rational r -> r;
      case Real r ->
          r.value().scale() <= 0
              ? Int.of(r.value().toBigIntegerExact())
              : Rational.of(r.value().unscaledValue(), BigInteger.TEN.pow(r.value().scale()));
    };
  }

  /** Each part of {@code actual} is the exact part rounded once (a {@code Real} if inexact). */
  private static void assertRoundedOnce(
      Numeric actual, Numeric exact, Numeric z, Numeric w, MathContext mc) {
    if (z.isExact() && w.isExact()) {
      assertThat(actual).isEqualTo(exact);
      return;
    }
    assertThat(actual.isExact()).isFalse();
    assertPart(actual.re(), exact.re(), z, w, mc);
    if (actual instanceof Complex) {
      assertPart(actual.im(), exact.im(), z, w, mc);
    } else {
      assertThat(exact.im().isZero()).isTrue();
    }
  }

  private static void assertPart(
      RealNumeric actual, RealNumeric exact, Numeric z, Numeric w, MathContext mc) {
    if (actual.isExact()) {
      // A part whose own operands are all exact stays exact.
      assertThat(actual).as("%s with %s at %s", z, w, mc).isEqualTo(exact);
      return;
    }
    BigDecimal expected = exact.toReal(mc).value();
    assertThat(((Real) actual).value())
        .as("%s with %s at %s", z, w, mc)
        .isEqualByComparingTo(expected);
  }
}
