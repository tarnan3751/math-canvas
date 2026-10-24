package mathcanvas.engine.number;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.math.MathContext;
import org.junit.jupiter.api.Test;

class RationalTest {

  private static final MathContext MC = MathContext.DECIMAL128;

  @Test
  void normalisesToLowestTermsAndDemotes() {
    assertThat(Rational.of(2, 4)).isEqualTo(new Rational(BigInteger.ONE, BigInteger.TWO));
    assertThat(Rational.of(2, -4)).isEqualTo(Rational.of(-1, 2));
    assertThat(Rational.of(4, 2)).isEqualTo(Int.of(2)).isInstanceOf(Int.class);
    assertThat(Rational.of(0, 5)).isEqualTo(Int.ZERO);
    assertThat(Rational.of(-6, -3)).isEqualTo(Int.TWO);
    assertThat(new Rational(BigInteger.valueOf(6), BigInteger.valueOf(-4)))
        .isEqualTo(Rational.of(-3, 2));
  }

  @Test
  void rejectsInvalidState() {
    assertThatThrownBy(() -> Rational.of(1, 0)).isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> new Rational(BigInteger.ONE, BigInteger.ZERO))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> new Rational(BigInteger.valueOf(4), BigInteger.TWO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Rational(BigInteger.ZERO, BigInteger.TWO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void arithmetic() {
    assertThat(Rational.of(1, 3).add(Rational.of(1, 6), MC)).isEqualTo(Rational.of(1, 2));
    assertThat(Rational.of(1, 2).add(Rational.of(1, 2), MC)).isEqualTo(Int.ONE);
    assertThat(Rational.of(1, 2).subtract(Int.ONE, MC)).isEqualTo(Rational.of(-1, 2));
    assertThat(Rational.of(2, 3).multiply(Rational.of(3, 2), MC)).isEqualTo(Int.ONE);
    assertThat(Rational.of(2, 3).divide(Rational.of(4, 9), MC)).isEqualTo(Rational.of(3, 2));
    assertThatThrownBy(() -> Rational.of(1, 2).divide(Int.ZERO, MC))
        .isInstanceOf(ArithmeticException.class);
    assertThat(Rational.of(1, 2).negate()).isEqualTo(Rational.of(-1, 2));
  }

  @Test
  void roundingFunctions() {
    RealNumeric sevenHalves = Rational.of(7, 2);
    RealNumeric minusSevenHalves = Rational.of(-7, 2);
    assertThat(sevenHalves.floor()).isEqualTo(Int.of(3));
    assertThat(sevenHalves.ceil()).isEqualTo(Int.of(4));
    assertThat(sevenHalves.round()).isEqualTo(Int.of(4));
    assertThat(sevenHalves.truncate()).isEqualTo(Int.of(3));
    assertThat(minusSevenHalves.floor()).isEqualTo(Int.of(-4));
    assertThat(minusSevenHalves.ceil()).isEqualTo(Int.of(-3));
    assertThat(minusSevenHalves.round()).isEqualTo(Int.of(-4));
    assertThat(minusSevenHalves.truncate()).isEqualTo(Int.of(-3));
    assertThat(Rational.of(5, 2).round()).isEqualTo(Int.of(3));
    assertThat(Rational.of(-5, 2).round()).isEqualTo(Int.of(-3));
    assertThat(Rational.of(-1, 3).round()).isEqualTo(Int.ZERO);
    assertThat(Rational.of(-2, 3).round()).isEqualTo(Int.MINUS_ONE);
  }

  @Test
  void accessorsAndConversions() {
    Rational r = (Rational) Rational.of(-3, 4);
    assertThat(r.numerator()).isEqualTo(Int.of(-3));
    assertThat(r.denominator()).isEqualTo(Int.of(4));
    assertThat(r).hasToString("-3/4");
    assertThat(r.toDouble()).isEqualTo(-0.75);
    assertThat(Rational.of(1, 3).toDouble()).isEqualTo(1.0 / 3.0);
    assertThat(r.toReal(MC)).isEqualTo(Real.of("-0.75"));
    assertThat(Rational.of(1, 3).toReal(new MathContext(5))).isEqualTo(Real.of("0.33333"));
    assertThat(Rational.of(4, 9).isPerfectSquare()).isTrue();
    assertThat(Rational.of(2, 9).isPerfectSquare()).isFalse();
    assertThat(r.isZero() || r.isOne() || r.isInteger()).isFalse();
    assertThat(r.isExact()).isTrue();
    assertThatThrownBy(() -> Rational.of(1, 3).toReal(MathContext.UNLIMITED))
        .isInstanceOf(ArithmeticException.class);
  }
}
