package mathcanvas.engine.number;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RealTest {

  private static final MathContext MC = MathContext.DECIMAL128;

  @Test
  void trailingZerosDoNotMatter() {
    assertThat(Real.of("1.0")).isEqualTo(Real.of("1.00")).isEqualTo(Real.of("1"));
    assertThat(Real.of("1.0").hashCode()).isEqualTo(Real.of("1.00").hashCode());
    assertThat(Real.of("0.000")).isEqualTo(Real.of(BigDecimal.ZERO));
    assertThat(Real.of("500")).isEqualTo(Real.of("5E+2"));
  }

  @Test
  void aDecimalRoundsOnlyARational() {
    MathContext three = new MathContext(3);
    assertThat(Int.of(123456).decimal(three)).isEqualByComparingTo("123456");
    assertThat(Real.of("1.23456").decimal(three)).isEqualByComparingTo("1.23456");
    assertThat(Rational.of(1, 3).decimal(three)).isEqualByComparingTo("0.333");
    assertThat(Int.of(123456).toReal(three).value()).isEqualByComparingTo("1.23E+5");
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 2, 7, 16, 34, 100, 0})
  void decimalSumsAreExactAtEveryContext(int precision) {
    MathContext mc = new MathContext(precision);
    assertThat(Real.of("0.1").add(Real.of("0.2"), mc)).isEqualTo(Real.of("0.3"));
    assertThat(Real.of("0.1").add(Rational.of(1, 5), mc)).isEqualTo(Real.of("0.3"));
  }

  @Test
  void inexactnessIsContagious() {
    assertThat(Real.of("2.5").multiply(Int.TWO, MC))
        .isEqualTo(Real.of("5.0"))
        .isInstanceOf(Real.class);
    assertThat(Real.of("2.5").multiply(Int.TWO, MC)).hasToString("5.0");
    assertThat(Int.ONE.add(Real.of("0"), MC)).isInstanceOf(Real.class);
    assertThat(Real.of("0.5").add(Rational.of(1, 2), MC)).isEqualTo(Real.of("1"));
  }

  @Test
  void roundsOnceToTheContext() {
    MathContext five = new MathContext(5);
    assertThat(Real.of("1").divide(Int.of(3), five)).isEqualTo(Real.of("0.33333"));
    assertThat(Real.of("2").divide(Int.of(3), five)).isEqualTo(Real.of("0.66667"));
    // 1/3 is not rounded before it is added: 0.1 + 1/3 = 0.43333…, not 0.1 + 0.33333.
    assertThat(Real.of("0.1").add(Rational.of(1, 3), new MathContext(3)))
        .isEqualTo(Real.of("0.433"));
    assertThat(Real.of("1.23456").multiply(Real.of("1"), new MathContext(3)))
        .isEqualTo(Real.of("1.23"));
    assertThat(Real.of("1.23456").add(Int.ZERO, MathContext.UNLIMITED))
        .isEqualTo(Real.of("1.23456"));
    assertThat(Real.of("1.23456").negate()).isEqualTo(Real.of("-1.23456"));
  }

  @Test
  void operandsOfVeryDifferentMagnitudeAreNotExpanded() {
    Real huge = Real.of("1E+1000000000");
    assertThat(huge.add(Int.ONE, MC)).isEqualTo(huge);
    assertThat(huge.add(Real.of("1E-1000000000"), MC)).isEqualTo(huge);
    assertThat(huge.multiply(Real.of("2"), MC)).isEqualTo(Real.of("2E+1000000000"));
    assertThat(huge.add(Rational.of(1, 3), MC)).isEqualTo(huge);
    assertThat(Real.of("1E-1000000000").add(Rational.of(1, 3), new MathContext(5)))
        .isEqualTo(Real.of("0.33333"));
    // Just above a tie at five digits: the tiny term decides the rounding.
    assertThat(Real.of("1E-1000000000").add(Rational.of(123455, 1000000), new MathContext(5)))
        .isEqualTo(Real.of("0.12346"));
    assertThat(Real.of("-1E-1000000000").add(Rational.of(123465, 1000000), new MathContext(5)))
        .isEqualTo(Real.of("0.12346"));
  }

  @Test
  void divisionByZeroThrows() {
    assertThatThrownBy(() -> Real.of("1").divide(Real.of("0.0"), MC))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> Int.ONE.divide(Real.of("0"), MC))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> Real.of("1").divide(Int.ZERO, MC))
        .isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> Real.of("1").divide(Int.of(3), MathContext.UNLIMITED))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void roundingFunctionsStayReal() {
    assertThat(Real.of("2.5").round()).isEqualTo(Real.of("3"));
    assertThat(Real.of("-2.5").round()).isEqualTo(Real.of("-3"));
    assertThat(Real.of("2.7").floor()).isEqualTo(Real.of("2")).isInstanceOf(Real.class);
    assertThat(Real.of("-2.7").floor()).isEqualTo(Real.of("-3"));
    assertThat(Real.of("-2.7").ceil()).isEqualTo(Real.of("-2"));
    assertThat(Real.of("-2.7").truncate()).isEqualTo(Real.of("-2"));
    assertThat(Real.of("-2.7").abs()).isEqualTo(Real.of("2.7"));
  }

  @Test
  void roundingAnIntegerValueReturnsIt() {
    Real huge = Real.of("1E+100000000");
    assertThat(huge.floor()).isSameAs(huge);
    assertThat(huge.ceil()).isSameAs(huge);
    assertThat(huge.round()).isSameAs(huge);
    assertThat(huge.truncate()).isSameAs(huge);
  }

  @Test
  void integerPowersRoundOnceFromTheExactPower() {
    Real x = Real.of("1.00000000000000000000000000000000025");
    assertThat(x.pow(2, MC)).isEqualTo(x.multiply(x, MC));
    assertThat(x.pow(2, MC)).isEqualTo(Real.of("1.000000000000000000000000000000001"));
  }

  @Test
  void inexactResultsUnderAnUnlimitedContextThrowOneMessage() {
    MathContext unlimited = MathContext.UNLIMITED;
    for (Runnable inexact :
        List.<Runnable>of(
            () -> Real.of("1").divide(Int.of(3), unlimited),
            () -> Rational.of(1, 3).toReal(unlimited),
            () -> Int.TWO.sqrt(unlimited),
            () -> Int.TWO.root(3, unlimited),
            () -> Real.of("3").pow(-1, unlimited),
            () -> Complex.of(Real.of("1"), Real.of("1")).divide(Int.of(3), unlimited))) {
      assertThatThrownBy(inexact::run)
          .isInstanceOf(ArithmeticException.class)
          .hasMessage("an inexact result needs a finite precision");
    }
  }

  @Test
  void predicatesAreValueBased() {
    assertThat(Real.of("0.0").isZero()).isTrue();
    assertThat(Real.of("1.00").isOne()).isTrue();
    assertThat(Real.of("5.0").isInteger()).isTrue();
    assertThat(Real.of("500").isInteger()).isTrue();
    assertThat(Real.of("0.5").isInteger()).isFalse();
    assertThat(Real.of("1").isExact()).isFalse();
    assertThat(Real.of("4").isPerfectSquare()).isFalse();
  }

  @Test
  void conversions() {
    assertThat(Real.of(0.1)).isEqualTo(Real.of("0.1"));
    assertThatThrownBy(() -> Real.of(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Real.of(Double.POSITIVE_INFINITY))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Real.of("x")).isInstanceOf(NumberFormatException.class);
    assertThat(Real.of("2.5").toDouble()).isEqualTo(2.5);
    assertThat(Real.of("1.23456").toReal(new MathContext(3))).isEqualTo(Real.of("1.23"));
    assertThat(Real.of("2.5")).hasToString("2.5");
    assertThat(Real.of("-0.001")).hasToString("-0.001");
    assertThat(Real.of("1E+100")).hasToString("1.0e+100");
    assertThat(Real.of("1E-10")).hasToString("1.0e-10");
    assertThat(Real.of("-1.5E-30")).hasToString("-1.5e-30");
    assertThat(Real.of("1E+20")).hasToString("100000000000000000000.0");
    assertThat(Real.of("0.000001")).hasToString("0.000001");
    // An integer value of 22 digits has scale 0, and is still written with an exponent.
    assertThat(Real.of(new BigDecimal("1180591620717411303424")))
        .hasToString("1.180591620717411303424e+21");
  }
}
