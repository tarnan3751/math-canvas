package mathcanvas.engine.number;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Random;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.statistics.Statistics;

/**
 * Quotients by far-apart divisors (parts more than 10,000 digits apart) whose exact value lies
 * within {@code 10⁻³⁰} of a power of ten, in every rounding mode: each part is the exact value
 * rounded once. The divisor is {@code w = r + si} with one part a 34-digit decimal and the other
 * {@code u·10^(±gap)}, {@code gap} from 5,100 to 5,200; the dividend is {@code c(1 + δ)} times
 * {@code w} or times its near part alone, {@code c} a power of ten and {@code δ} zero or {@code
 * v·10⁻ᵐ}, {@code m} from 31 to 45. The reference is the exact numerator over the exact norm,
 * formed as decimals and divided once by {@link BigDecimal#divide(BigDecimal, MathContext)}.
 */
class ComplexQuotientBoundaryProperties {

  /** A dividend and a far-apart divisor, as exact decimals. */
  record Case(BigDecimal zr, BigDecimal zi, BigDecimal r, BigDecimal s) {}

  @Provide
  Arbitrary<Case> cases() {
    return Arbitraries.randomValue(ComplexQuotientBoundaryProperties::draw);
  }

  @Provide
  Arbitrary<RoundingMode> modes() {
    return Arbitraries.of(RoundingMode.class).filter(m -> m != RoundingMode.UNNECESSARY);
  }

  private static Case draw(Random random) {
    BigDecimal near =
        new BigDecimal(
            new BigInteger(112, random).add(BigInteger.ONE).multiply(sign(random)),
            random.nextInt(60) - 20,
            MathContext.DECIMAL128);
    int gap = 5_100 + random.nextInt(101);
    BigDecimal far =
        BigDecimal.valueOf((random.nextInt(999) + 1) * (long) sign(random).intValue())
            .scaleByPowerOfTen(random.nextBoolean() ? -gap : gap);
    boolean farReal = random.nextBoolean();
    BigDecimal r = farReal ? far : near;
    BigDecimal s = farReal ? near : far;
    BigDecimal c = BigDecimal.ONE.scaleByPowerOfTen(random.nextInt(13) - 6);
    int v = random.nextInt(19) - 9;
    BigDecimal factor =
        c.add(c.multiply(BigDecimal.valueOf(v).scaleByPowerOfTen(-31 - random.nextInt(15))));
    if (random.nextBoolean()) {
      // c(1 + δ)·w: the quotient is c(1 + δ) itself, its imaginary part 0.
      return new Case(factor.multiply(r), factor.multiply(s), r, s);
    }
    // c(1 + δ) times the near part, in its own slot: the quotient is c(1 + δ)·n²/(n² + f²), just
    // below c(1 + δ) when the far part is the small one.
    return farReal
        ? new Case(BigDecimal.ZERO, factor.multiply(s), r, s)
        : new Case(factor.multiply(r), BigDecimal.ZERO, r, s);
  }

  private static BigInteger sign(Random random) {
    return random.nextBoolean() ? BigInteger.ONE : BigInteger.ONE.negate();
  }

  @Property(tries = 20_000)
  void eachPartIsTheExactValueRoundedOnce(
      @ForAll("cases") Case k,
      @ForAll @IntRange(min = 1, max = 40) int precision,
      @ForAll("modes") RoundingMode mode) {
    MathContext mc = new MathContext(precision, mode);
    Numeric z = Complex.of(new Real(k.zr()), new Real(k.zi()), mc);
    Numeric w = Complex.of(new Real(k.r()), new Real(k.s()), mc);
    Numeric actual = z.divide(w, mc);
    BigDecimal norm = k.r().multiply(k.r()).add(k.s().multiply(k.s()));
    BigDecimal re = k.zr().multiply(k.r()).add(k.zi().multiply(k.s()));
    BigDecimal im = k.zi().multiply(k.r()).subtract(k.zr().multiply(k.s()));
    check(actual.re(), re, norm, mc, k);
    check(actual.im(), im, norm, mc, k);
  }

  private static void check(
      RealNumeric part, BigDecimal numerator, BigDecimal norm, MathContext mc, Case k) {
    if (numerator.signum() == 0) {
      assertThat(part.isZero()).as("%s at %s", k, mc).isTrue();
      return;
    }
    BigDecimal expected = numerator.divide(norm, mc);
    BigDecimal value = part.toReal(mc).value();
    assertThat(value).as("%s at %s", k, mc).isEqualByComparingTo(expected);
    BigDecimal power =
        BigDecimal.ONE.scaleByPowerOfTen(expected.abs().precision() - expected.abs().scale() - 1);
    Statistics.label("at a power of ten").collect(expected.abs().compareTo(power) == 0);
  }
}
