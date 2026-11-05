package mathcanvas.engine.number;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.math.MathContext;
import org.junit.jupiter.api.Test;

class IntTest {

  private static final MathContext MC = MathContext.DECIMAL128;

  @Test
  void constantsAndFactories() {
    assertThat(Int.ZERO).isEqualTo(Int.of(0));
    assertThat(Int.ONE).isEqualTo(Int.of(BigInteger.ONE));
    assertThat(Int.TWO).isEqualTo(Int.of(2));
    assertThat(Int.MINUS_ONE).isEqualTo(Int.of(-1));
    assertThat(Int.of(-42)).hasToString("-42");
    assertThatThrownBy(() -> new Int(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void arithmeticStaysExact() {
    assertThat(Int.of(2).add(Int.of(3), MC)).isEqualTo(Int.of(5));
    assertThat(Int.of(2).subtract(Int.of(3), MC)).isEqualTo(Int.of(-1));
    assertThat(Int.of(6).multiply(Int.of(-7), MC)).isEqualTo(Int.of(-42));
    assertThat(Int.of(4).divide(Int.of(2), MC)).isEqualTo(Int.of(2));
    assertThat(Int.of(2).divide(Int.of(4), MC)).isEqualTo(Rational.of(1, 2));
    assertThatThrownBy(() -> Int.ONE.divide(Int.ZERO, MC)).isInstanceOf(ArithmeticException.class);
  }

  @Test
  void factorialAndBinomial() {
    assertThat(Int.of(0).factorial()).isEqualTo(Int.ONE);
    assertThat(Int.of(1).factorial()).isEqualTo(Int.ONE);
    assertThat(Int.of(20).factorial()).isEqualTo(Int.of(2432902008176640000L));
    assertThat(Int.of(100).factorial().value().toString())
        .startsWith("93326215443944152681")
        .hasSize(158);
    assertThatThrownBy(() -> Int.of(-1).factorial()).isInstanceOf(ArithmeticException.class);
    assertThat(Int.of(50).binomial(Int.of(25))).isEqualTo(Int.of(126410606437752L));
    assertThat(Int.of(5).binomial(Int.of(0))).isEqualTo(Int.ONE);
    assertThat(Int.of(5).binomial(Int.of(5))).isEqualTo(Int.ONE);
    assertThat(Int.of(5).binomial(Int.of(6))).isEqualTo(Int.ZERO);
    assertThat(Int.of(5).binomial(Int.of(-1))).isEqualTo(Int.ZERO);
    // The falling-factorial extension: C(-3, 2) = (-3)(-4)/2.
    assertThat(Int.of(-3).binomial(Int.of(2))).isEqualTo(Int.of(6));
    assertThat(Int.of(-1).binomial(Int.of(3))).isEqualTo(Int.of(-1));
    assertThat(Int.of(-1).binomial(Int.of(300_000))).isEqualTo(Int.ONE);
    assertThat(Int.of(-2).binomial(Int.of(5))).isEqualTo(Int.of(-6));
    // Binary splitting: a 300,000-digit coefficient in well under a second.
    assertThat(Int.of(1_000_000).binomial(Int.of(500_000)).value().bitLength())
        .isBetween(999_980, 1_000_000);
  }

  @Test
  void gcdLcmAndMod() {
    assertThat(Int.ZERO.gcd(Int.ZERO)).isEqualTo(Int.ZERO);
    assertThat(Int.of(12).gcd(Int.of(-18))).isEqualTo(Int.of(6));
    assertThat(Int.of(-4).lcm(Int.of(6))).isEqualTo(Int.of(12));
    assertThat(Int.ZERO.lcm(Int.of(6))).isEqualTo(Int.ZERO);
    assertThat(Int.of(-7).mod(Int.of(3))).isEqualTo(Int.of(2));
    assertThat(Int.of(7).mod(Int.of(-3))).isEqualTo(Int.of(-2));
    assertThat(Int.of(-7).mod(Int.of(-3))).isEqualTo(Int.of(-1));
    assertThat(Int.of(6).mod(Int.of(-3))).isEqualTo(Int.ZERO);
    assertThatThrownBy(() -> Int.ONE.mod(Int.ZERO)).isInstanceOf(ArithmeticException.class);
  }

  @Test
  void rootsSquaresAndPrimes() {
    assertThat(Int.of(49).isPerfectSquare()).isTrue();
    assertThat(Int.of(0).isPerfectSquare()).isTrue();
    assertThat(Int.of(50).isPerfectSquare()).isFalse();
    assertThat(Int.of(-4).isPerfectSquare()).isFalse();
  }

  @Test
  void predicatesAndRounding() {
    assertThat(Int.ZERO.isZero()).isTrue();
    assertThat(Int.ONE.isOne()).isTrue();
    assertThat(Int.of(3).isInteger()).isTrue();
    assertThat(Int.of(3).isExact()).isTrue();
    assertThat(Int.of(-3).signum()).isEqualTo(-1);
    assertThat(Int.of(-3).abs()).isEqualTo(Int.of(3));
    assertThat(Int.of(3).floor()).isEqualTo(Int.of(3));
    assertThat(Int.of(3).round()).isEqualTo(Int.of(3));
    assertThat(Int.of(3).numerator()).isEqualTo(Int.of(3));
    assertThat(Int.of(3).denominator()).isEqualTo(Int.ONE);
    assertThat(Int.of(3).toDouble()).isEqualTo(3.0);
    assertThat(Int.of(3).toReal(MC)).isEqualTo(Real.of("3"));
    assertThat(Int.of(3).re()).isEqualTo(Int.of(3));
    assertThat(Int.of(3).im()).isEqualTo(Int.ZERO);
    assertThat(Int.of(3).conj()).isEqualTo(Int.of(3));
  }
}
