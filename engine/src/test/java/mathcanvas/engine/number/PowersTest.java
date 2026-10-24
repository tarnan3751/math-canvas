package mathcanvas.engine.number;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import org.junit.jupiter.api.Test;

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
  void squareRoots() {
    assertThat(Int.of(4).sqrt(MC)).isEqualTo(Int.TWO);
    assertThat(Rational.of(4, 9).sqrt(MC)).isEqualTo(Rational.of(2, 3));
    assertThat(Int.of(-4).sqrt(MC)).isEqualTo(Complex.of(Int.ZERO, Int.TWO));
    assertThat(Int.ZERO.sqrt(MC)).isEqualTo(Int.ZERO);
    Numeric root8 = Int.of(8).sqrt(new MathContext(20));
    assertThat(root8).isEqualTo(Real.of("2.8284271247461900976"));
    assertThat(Real.of("4").sqrt(MC)).isEqualTo(Real.of("2")).isInstanceOf(Real.class);
    assertThat(Real.of("-2.25").sqrt(MC)).isEqualTo(Complex.of(Int.ZERO, Real.of("1.5")));
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
