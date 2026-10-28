package mathcanvas.engine.number;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class PowersTest {

  private static final MathContext MC = MathContext.DECIMAL128;

  @Test
  void integerPowersAreExact() {
    assertThat(Int.ZERO.pow(0, MC)).isEqualTo(Int.ONE);
    assertThat(Int.of(2).pow(10, MC)).isEqualTo(Int.of(1024));
    assertThat(Int.of(2).pow(-2, MC)).isEqualTo(Rational.of(1, 4));
    assertThat(Rational.of(-2, 3).pow(3, MC)).isEqualTo(Rational.of(-8, 27));
    assertThat(Rational.of(-2, 3).pow(-2, MC)).isEqualTo(Rational.of(9, 4));
    assertThat(Rational.of(2, 3).pow(0, MC)).isEqualTo(Int.ONE);
    assertThatThrownBy(() -> Int.ZERO.pow(-1, MC)).isInstanceOf(ArithmeticException.class);
  }

  /**
   * An inexact complex base to an integer power of some thousands is formed on exact decimals,
   * without the gcds of reduced fractions: {@code z^-1160} took 13 s through Gaussian rationals and
   * stalled a soundness property, and its value is the one the Gaussian-rational power gave, bit
   * for bit.
   */
  @Test
  // typically under 0.3 s locally (docs/reference/engine.md, Timing budgets)
  @Timeout(30)
  void aLargeComplexIntegerPowerIsFormedOnExactDecimals() {
    Numeric z =
        Complex.of(
            Real.of("40.99371194442192979527491390646486"),
            Real.of("10.95695428885963705873734219251806"));
    assertThat(z.pow(-1160, MC))
        .isEqualTo(
            Complex.of(
                Real.of("1.437074205540150745590157036838045E-1889"),
                Real.of("-7.211705836793934288605502681850108E-1889")));
    assertThat(z.pow(Int.of(-1160), MC)).isEqualTo(z.pow(-1160, MC));
    // a zero imaginary part stays a Real zero, and a zero base has no negative power
    assertThat(Complex.of(Real.of("2.5"), Real.of("0")).pow(-2, MC))
        .isEqualTo(Complex.of(Real.of("0.16"), Real.of("0")));
    assertThatThrownBy(() -> Complex.of(Real.of("0"), Real.of("0")).pow(-3, MC))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void realPowersRoundOnce() {
    assertThat(Real.of("1.5").pow(2, MC)).isEqualTo(Real.of("2.25"));
    assertThat(Real.of("0").pow(0, MC)).isEqualTo(Real.of("1")).isInstanceOf(Real.class);
    assertThat(Real.of("2").pow(-1, MC)).isEqualTo(Real.of("0.5"));
    assertThat(Real.of("1.1").pow(100, new MathContext(10))).isEqualTo(Real.of("13780.61234"));
    assertThat(Real.of("1.1").pow(3, MathContext.UNLIMITED)).isEqualTo(Real.of("1.331"));
    assertThatThrownBy(() -> Real.of("0.0").pow(-2, MC)).isInstanceOf(ArithmeticException.class);
  }

  @Test
  // typically under 0.6 s locally (docs/reference/engine.md, Timing budgets)
  @Timeout(30)
  void anIntegerValuedRealExponentIsBounded() {
    Real billion = Real.of("1E+9");
    // References: exp(n·log x) at 80 digits (Python's decimal), rounded to 34.
    assertWithinOneUlp(
        Rational.of(1, 3).pow(billion, MC), "1.906942345153165669905421356930052E-477121255");
    assertWithinOneUlp(Int.TWO.pow(billion, MC), "4.612976001169069393116119221037316E+301029995");
    // Small powers of an exact base stay the exact power rounded once.
    assertThat(Int.of(-2).pow(Real.of("3.0"), MC)).isEqualTo(Real.of("-8"));
    assertThat(Rational.of(1, 3).pow(Real.of("2.0"), MC))
        .isEqualTo(Real.of("0.1111111111111111111111111111111111"));
    assertThat(Rational.of(2, 3).pow(Real.of("-2.0"), MC)).isEqualTo(Real.of("2.25"));
  }

  @Test
  // typically under 0.6 s locally (docs/reference/engine.md, Timing budgets)
  @Timeout(30)
  void complexPowersWithARealZeroPartAreFast() {
    // (1+i)^(10^9) is 2^(5·10^8): reference exp(n·log 2) at 80 digits (Python's decimal).
    String reference = "6.791889281465849538187147637201301E+150514997";
    for (Numeric power :
        List.of(
            Complex.of(Int.ONE, Int.ONE).pow(Real.of("1E+9"), MC),
            Complex.of(Real.of("1.0"), Real.of("1.0")).pow(1_000_000_000, MC))) {
      assertWithinOneUlp(power.re(), reference);
      assertThat(power.im()).isEqualTo(Real.of("0"));
    }
  }

  private static void assertWithinOneUlp(Numeric actual, String expected) {
    assertThat(actual).isInstanceOf(Real.class);
    BigDecimal reference = new BigDecimal(expected);
    BigDecimal error = ((Real) actual).value().subtract(reference).abs();
    assertThat(error).as("%s against %s", actual, expected).isLessThanOrEqualTo(reference.ulp());
  }

  @Test
  void squareRoots() {
    assertThat(Int.of(4).sqrt(MC)).isEqualTo(Int.TWO);
    assertThat(Rational.of(4, 9).sqrt(MC)).isEqualTo(Rational.of(2, 3));
    assertThat(Int.of(-4).sqrt(MC)).isEqualTo(Complex.of(Int.ZERO, Int.TWO));
    assertThat(Int.ZERO.sqrt(MC)).isEqualTo(Int.ZERO);
    Numeric root8 = Int.of(8).sqrt(new MathContext(20));
    assertThat(root8).isEqualTo(Real.of("2.8284271247461900976"));
    assertThat(Real.of("4").sqrt(MC)).isEqualTo(Real.of("2")).isInstanceOf(Real.class);
    assertThat(Real.of("-2.25").sqrt(MC)).isEqualTo(Complex.of(Real.of("0"), Real.of("1.5")));
    assertThat(Complex.of(Int.of(3), Int.of(4)).sqrt(MC)).isEqualTo(Complex.of(Int.TWO, Int.ONE));
    assertThat(Complex.of(Int.of(-3), Int.of(-4)).sqrt(MC))
        .isEqualTo(Complex.of(Int.ONE, Int.of(-2)));
    assertThat(Complex.of(Int.ZERO, Int.TWO).sqrt(MC)).isEqualTo(Complex.of(Int.ONE, Int.ONE));
    assertThat(Complex.of(Int.ZERO, Int.ONE).sqrt(new MathContext(10)))
        .isEqualTo(Complex.of(Real.of("0.7071067812"), Real.of("0.7071067812")));
    assertThatThrownBy(() -> Int.TWO.sqrt(MathContext.UNLIMITED))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void realRoots() {
    assertThat(Rational.of(8, 27).root(3, MC)).isEqualTo(Rational.of(2, 3));
    assertThat(Int.of(-8).root(3, MC)).isEqualTo(Int.of(-2)).isInstanceOf(Int.class);
    assertThat(Int.of(81).root(4, MC)).isEqualTo(Int.of(3));
    assertThat(Int.of(5).root(1, MC)).isEqualTo(Int.of(5));
    assertThat(Int.of(2).root(3, new MathContext(20))).isEqualTo(Real.of("1.2599210498948731648"));
    assertThat(Int.of(-2).root(3, new MathContext(20)))
        .isEqualTo(Real.of("-1.2599210498948731648"));
    assertThat(Real.of("27").root(3, MC)).isEqualTo(Real.of("3"));
    assertThat(Real.of("0.001").root(3, MC)).isEqualTo(Real.of("0.1"));
    // An exact decimal power has its exact root, in every rounding mode and under UNLIMITED.
    assertThat(Real.of("1E-49").root(7, new MathContext(34, RoundingMode.UP)))
        .isEqualTo(Real.of("1E-7"));
    assertThat(Real.of("1E-49").root(7, new MathContext(34, RoundingMode.CEILING)))
        .isEqualTo(Real.of("1E-7"));
    assertThat(Real.of("8.0").root(3, MathContext.UNLIMITED)).isEqualTo(Real.of("2.0"));
    assertThat(Real.of("64000").root(3, MC)).isEqualTo(Real.of("40"));
    assertThat(Real.of("-0.008").root(3, MC)).isEqualTo(Real.of("-0.2"));
    assertThat(Int.of(2).root(2, new MathContext(5))).isEqualTo(Real.of("1.4142"));
    assertThatThrownBy(() -> Int.of(-16).root(4, MC)).isInstanceOf(ArithmeticException.class);
    // Degrees near the int range neither overflow nor allocate 2^n-bit integers.
    assertThat(Powers.exactRoot(BigInteger.valueOf(3), Integer.MAX_VALUE)).isNull();
    assertThat(Powers.exactRoot(BigInteger.ONE, Integer.MAX_VALUE)).isEqualTo(BigInteger.ONE);
    assertThat(Int.ONE.root(Integer.MAX_VALUE, MC)).isEqualTo(Int.ONE);
    assertThat(Int.ZERO.root(1 << 30, MC)).isEqualTo(Int.ZERO);
    assertThat(Int.TWO.pow(200, MC).re().root(100, MC)).isEqualTo(Int.of(4));
    assertThat(Int.of(3).pow(300, MC).re().root(150, MC)).isEqualTo(Int.of(9));
    assertThatThrownBy(() -> Int.of(16).root(0, MC)).isInstanceOf(IllegalArgumentException.class);
  }
}
