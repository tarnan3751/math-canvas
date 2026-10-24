package mathcanvas.engine.number;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.MathContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ComplexTest {

  private static final MathContext MC = MathContext.DECIMAL128;

  private static Numeric c(long re, long im) {
    return Complex.of(Int.of(re), Int.of(im));
  }

  @Test
  void anExactZeroImaginaryPartIsReal() {
    assertThat(Complex.of(Int.of(3), Int.ZERO)).isEqualTo(Int.of(3));
    assertThatThrownBy(() -> new Complex(Int.of(3), Int.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    Numeric inexactZero = Complex.of(Real.of("3"), Real.of("0.0"));
    assertThat(inexactZero).isInstanceOf(Complex.class);
    assertThat(inexactZero.isExact()).isFalse();
    assertThat(inexactZero.isInteger()).isTrue();
    assertThat(Complex.of(Int.of(3), Real.of("0.0"), MC))
        .isEqualTo(new Complex(Real.of("3"), Real.of("0")));
  }

  @Test
  void arithmetic() {
    assertThat(c(1, 2).multiply(c(3, -1), MC)).isEqualTo(c(5, 5));
    assertThat(c(1, 2).add(c(3, -2), MC)).isEqualTo(Int.of(4)).isInstanceOf(Int.class);
    assertThat(c(1, 2).subtract(c(1, 2), MC)).isEqualTo(Int.ZERO);
    assertThat(c(5, 5).divide(c(3, -1), MC)).isEqualTo(c(1, 2));
    assertThat(Int.ONE.divide(Complex.I, MC)).isEqualTo(c(0, -1));
    assertThat(c(2, 4).divide(Int.TWO, MC)).isEqualTo(c(1, 2));
    assertThat(c(1, 1).divide(Int.TWO, MC))
        .isEqualTo(Complex.of(Rational.of(1, 2), Rational.of(1, 2)));
    assertThatThrownBy(() -> c(1, 1).divide(Int.ZERO, MC)).isInstanceOf(ArithmeticException.class);
    assertThatThrownBy(() -> Int.ONE.divide(Complex.of(Real.of("0"), Real.of("0")), MC))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void iSquaredIsTheIntegerMinusOne() {
    Numeric square = Complex.I.multiply(Complex.I, MC);
    assertThat(square).isEqualTo(Int.MINUS_ONE).isInstanceOf(Int.class);
    assertThat(Complex.I.pow(2, MC)).isEqualTo(Int.MINUS_ONE);
    assertThat(Complex.I.pow(4, MC)).isEqualTo(Int.ONE);
    assertThat(Complex.I.pow(-1, MC)).isEqualTo(c(0, -1));
  }

  @Test
  void productsAndQuotientsRoundEachPartOnceFromTheExactValue() {
    Numeric z = Complex.of(Real.of("1.00000000000000000001"), Real.of("1"));
    Numeric square = z.multiply(z, MC);
    assertThat(square.re()).isEqualTo(Real.of("2.00000000000000000001E-20"));
    assertThat(square).isEqualTo(z.pow(2, MC));
    Numeric w = Complex.of(Real.of("1"), Real.of("1.00000000000000000001"));
    // z/w = (1.00000000000000000001 + 1.00000000000000000001 + (1 - 1.0000…0001²)i) / |w|².
    Numeric quotient = z.divide(w, MC);
    assertThat(quotient.im().toReal(MC).value())
        .isEqualByComparingTo(
            new BigDecimal("-2.00000000000000000001E-20")
                .divide(new BigDecimal("2.0000000000000000000200000000000000000001"), MC));
  }

  @Test
  @Timeout(10)
  void aDivisorWithFarApartPartsDoesNotExpandItsNorm() {
    Numeric z = Complex.of(Int.ONE, Int.ONE);
    Numeric w = Complex.of(Real.of("1E+1000000000"), Real.of("1"));
    Numeric quotient = z.divide(w, MC);
    assertThat(quotient).isEqualTo(Complex.of(Real.of("1E-1000000000"), Real.of("1E-1000000000")));
  }

  @Test
  void partsAreBothExactOrBothReal() {
    assertThatThrownBy(() -> new Complex(Int.ONE, Real.of("2.0")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Complex.of(Rational.of(1, 2), Real.of("2.0")))
        .isInstanceOf(IllegalArgumentException.class);
    // An Int 0 imaginary part still makes the number real, whatever the real part.
    assertThat(Complex.of(Real.of("2.5"), Int.ZERO)).isEqualTo(Real.of("2.5"));
    // The contextual factory promotes the exact part: an Int exactly, a Rational at the context.
    Numeric z = Complex.of(Int.ONE, Real.of("2.0"), MC);
    assertThat(z).isEqualTo(new Complex(Real.of("1"), Real.of("2")));
    assertThat(z.isExact()).isFalse();
    assertThat(Complex.of(Int.of(123_456_789), Real.of("1"), new MathContext(5)))
        .isEqualTo(new Complex(Real.of("123456789"), Real.of("1")));
    assertThat(Complex.of(Real.of("1"), Rational.of(1, 3), new MathContext(5)))
        .isEqualTo(new Complex(Real.of("1"), Real.of("0.33333")));
    assertThat(Complex.of(Real.of("1"), Int.ZERO, MC)).isEqualTo(Real.of("1"));
    assertThatThrownBy(() -> Complex.of(Real.of("1"), Rational.of(1, 3), MathContext.UNLIMITED))
        .isInstanceOf(ArithmeticException.class)
        .hasMessage("an inexact result needs a finite precision");
    // No operation yields a mixed pair.
    assertThat(z.multiply(Int.TWO, MC)).isEqualTo(new Complex(Real.of("2"), Real.of("4")));
    assertThat(z.add(Real.of("0.5"), MC)).isEqualTo(new Complex(Real.of("1.5"), Real.of("2")));
    assertThat(c(1, 2).add(Real.of("0.5"), MC))
        .isEqualTo(new Complex(Real.of("1.5"), Real.of("2")));
    assertThat(c(1, 2).multiply(Real.of("0.5"), MC))
        .isEqualTo(new Complex(Real.of("0.5"), Real.of("1")));
    assertThat(Real.of("-2.25").sqrt(MC)).isEqualTo(new Complex(Real.of("0"), Real.of("1.5")));
    assertThat(Int.of(-4).pow(Real.of("0.5"), MC))
        .isEqualTo(new Complex(Real.of("0"), Real.of("2")));
    Numeric root = Real.of("-200000.0").sqrt(MC);
    assertThat(root.re()).isEqualTo(Real.of("0"));
    assertThat(root.im()).isEqualTo(Real.of("447.2135954999579392818347337462552"));
    assertThat(Int.of(-8).pow(Real.of("0.5"), MC).isExact()).isFalse();
    assertThat(Int.of(-8).pow(Real.of("0.5"), MC).re()).isInstanceOf(Real.class);
  }

  @Test
  void partsConjugateAndModulus() {
    Numeric z = c(3, 4);
    assertThat(z.re()).isEqualTo(Int.of(3));
    assertThat(z.im()).isEqualTo(Int.of(4));
    assertThat(z.conj()).isEqualTo(c(3, -4));
    assertThat(z.negate()).isEqualTo(c(-3, -4));
    assertThat(z.modulus(MC)).isEqualTo(Int.of(5));
    assertThat(c(1, 1).modulus(new MathContext(5))).isEqualTo(Real.of("1.4142"));
    assertThat(z.toReal(MC)).isEqualTo(Complex.of(Real.of("3"), Real.of("4")));
    assertThat(z.isZero() || z.isOne() || z.isInteger()).isFalse();
  }

  @Test
  void debugStrings() {
    assertThat(c(3, 2)).hasToString("3+2i");
    assertThat(c(3, -2)).hasToString("3-2i");
    assertThat(c(0, -1)).hasToString("-i");
    assertThat(Complex.I).hasToString("i");
    assertThat(c(0, 2)).hasToString("2i");
    assertThat(Complex.of(Real.of("1.5"), Real.of("-1"))).hasToString("1.5-1.0i");
  }
}
