package mathcanvas.engine.number;

import java.math.BigDecimal;
import java.math.BigInteger;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Tuple;

/**
 * Generators shared by the number properties. Small values dominate so that zeros, ones and equal
 * pairs come up often; long and 60-to-100-digit values cover large magnitudes. {@link
 * #neighbours(Arbitrary)} pairs a value with the same value built another way, the same value in
 * another kind, or a value one tiny step away.
 */
final class NumberArbitraries {

  private NumberArbitraries() {}

  private static final BigInteger BIG = BigInteger.TEN.pow(100);

  static Arbitrary<BigInteger> integers() {
    return Arbitraries.frequencyOf(
        Tuple.of(5, Arbitraries.bigIntegers().between(BigInteger.valueOf(-10), BigInteger.TEN)),
        Tuple.of(3, Arbitraries.longs().map(BigInteger::valueOf)),
        Tuple.of(2, Arbitraries.bigIntegers().between(BIG.negate(), BIG)));
  }

  static Arbitrary<BigInteger> nonZeroIntegers() {
    return integers().filter(i -> i.signum() != 0);
  }

  static Arbitrary<Int> ints() {
    return integers().map(Int::of);
  }

  /** Quotients through {@link Rational#of}, so some are {@code Int}s. */
  static Arbitrary<RealNumeric> rationals() {
    return Combinators.combine(integers(), nonZeroIntegers()).as(Rational::of);
  }

  /** Exact numbers: {@code Int} and {@code Rational}. */
  static Arbitrary<RealNumeric> exacts() {
    return Arbitraries.oneOf(ints(), rationals());
  }

  static Arbitrary<Real> reals() {
    return Combinators.combine(integers(), Arbitraries.integers().between(-40, 40))
        .as((unscaled, scale) -> Real.of(new BigDecimal(unscaled, scale)));
  }

  /** Every non-complex kind. */
  static Arbitrary<RealNumeric> realNumerics() {
    return Arbitraries.oneOf(ints(), rationals(), reals());
  }

  /** Complex numbers through {@link Complex#of}, so some are real. */
  static Arbitrary<Numeric> complexes() {
    return Combinators.combine(realNumerics(), realNumerics()).as(Complex::of);
  }

  /** Exact complex numbers (Gaussian rationals), some of them real. */
  static Arbitrary<Numeric> exactComplexes() {
    return Combinators.combine(exacts(), exacts()).as(Complex::of);
  }

  static Arbitrary<Numeric> numerics() {
    return Arbitraries.oneOf(realNumerics(), complexes());
  }

  /** Each base value, or a value equal to it, equal in value in another kind, or adjacent. */
  static Arbitrary<RealNumeric> neighbours(Arbitrary<RealNumeric> base) {
    return base.flatMap(
        x ->
            Arbitraries.oneOf(
                Arbitraries.just(x),
                Arbitraries.just(rebuilt(x)),
                Arbitraries.just(otherKind(x)),
                Arbitraries.just(step(x, 1)),
                Arbitraries.just(step(x, -1))));
  }

  /** The same value through a different construction. */
  static RealNumeric rebuilt(RealNumeric x) {
    BigInteger k = BigInteger.valueOf(6);
    return switch (x) {
      case Int i -> Rational.of(i.value().multiply(k), k);
      case Rational r -> Rational.of(r.num().multiply(k), r.den().multiply(k));
      case Real r -> Real.of(r.value().setScale(r.value().scale() + 3));
    };
  }

  /** An equal value of another kind where one exists (an exact value as a {@code Real}). */
  static RealNumeric otherKind(RealNumeric x) {
    return switch (x) {
      case Int i -> Real.of(new BigDecimal(i.value()));
      case Rational r -> {
        try {
          yield Real.of(new BigDecimal(r.num()).divide(new BigDecimal(r.den())));
        } catch (ArithmeticException nonTerminating) {
          yield r;
        }
      }
      case Real r ->
          r.value().scale() <= 0
              ? Int.of(r.value().toBigIntegerExact())
              : Rational.of(r.value().unscaledValue(), BigInteger.TEN.pow(r.value().scale()));
    };
  }

  /** A value one tiny step away: one ulp of a {@code Real}, {@code 10^-40} of an exact value. */
  static RealNumeric step(RealNumeric x, int direction) {
    BigInteger d = BigInteger.valueOf(direction);
    return switch (x) {
      case Int i ->
          Rational.of(i.value().multiply(BigInteger.TEN.pow(40)).add(d), BigInteger.TEN.pow(40));
      case Rational r ->
          Rational.of(
              r.num().multiply(BigInteger.TEN.pow(40)).add(d.multiply(r.den())),
              r.den().multiply(BigInteger.TEN.pow(40)));
      case Real r -> Real.of(r.value().add(r.value().ulp().multiply(new BigDecimal(d))));
    };
  }

  /** Whether a number is in normal form; the constructors enforce it, the properties check it. */
  static boolean normal(Numeric x) {
    return switch (x) {
      case Int i -> i.value() != null;
      case Rational r ->
          r.den().compareTo(BigInteger.ONE) > 0 && r.num().gcd(r.den()).equals(BigInteger.ONE);
      case Real r -> r.value().equals(r.value().stripTrailingZeros());
      case Complex z ->
          !(z.im() instanceof Int i && i.isZero()) && normal(z.re()) && normal(z.im());
    };
  }
}
