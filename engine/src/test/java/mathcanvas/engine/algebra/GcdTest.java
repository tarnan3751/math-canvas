package mathcanvas.engine.algebra;

import static mathcanvas.engine.algebra.PolynomialTest.p;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import org.junit.jupiter.api.Test;

class GcdTest {

  private static final Symbol X = Symbol.of("x");
  private static final Symbol Y = Symbol.of("y");

  @Test
  void dividesWithRemainder() {
    Division.Result r = Division.divide(p("x^3 - 2x^2 - 4"), p("x - 3"));
    assertThat(r.quotient()).isEqualTo(p("x^2 + x + 3"));
    assertThat(r.remainder()).isEqualTo(p("5"));
    Division.Result h = Division.divide(p("x^2 + 1"), p("2x"));
    assertThat(h.quotient()).isEqualTo(p("x/2"));
    assertThat(h.remainder()).isEqualTo(p("1"));
    assertThat(Division.divide(p("x"), p("x^2")).quotient()).isEqualTo(Polynomial.ZERO);
    assertThat(Division.divide(p("6"), p("4")).quotient()).isEqualTo(p("3/2"));
    assertThatThrownBy(() -> Division.divide(p("x"), p("y")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Division.divide(p("x"), Polynomial.ZERO))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void pseudoDivides() {
    Division.Result r = Division.pseudoDivide(p("x^2 y + x + 1"), p("y x + 1"), X);
    Polynomial lead = p("y").pow(2);
    assertThat(r.quotient().multiply(p("y x + 1")).add(r.remainder()))
        .isEqualTo(lead.multiply(p("x^2 y + x + 1")));
    assertThat(r.remainder().degree(X)).isZero();
  }

  @Test
  void dividesExactly() {
    assertThat(Division.exactQuotient(p("x^2 - y^2"), p("x - y"))).contains(p("x + y"));
    assertThat(Division.exactQuotient(p("x^2 + y^2"), p("x - y"))).isEmpty();
    assertThat(Division.exactQuotient(p("x"), p("y"))).isEmpty();
    assertThat(Division.exactQuotient(p("x/2"), p("3"))).contains(p("x/6"));
  }

  @Test
  void univariateGcds() {
    assertThat(Gcd.gcd(p("x^2 - 1"), p("x^2 + 2x + 1"))).isEqualTo(p("x + 1"));
    assertThat(Gcd.gcd(p("2x + 2"), p("4x + 4"))).isEqualTo(p("2x + 2"));
    assertThat(Gcd.gcd(p("x/2"), p("x/3"))).isEqualTo(p("x/6"));
    assertThat(Gcd.gcd(p("x^2 + 1"), p("x + 1"))).isEqualTo(Polynomial.ONE);
    assertThat(Gcd.gcd(p("6"), p("4"))).isEqualTo(p("2"));
    assertThat(Gcd.gcd(Polynomial.ZERO, p("-x - 1"))).isEqualTo(p("x + 1"));
    assertThat(Gcd.gcd(Polynomial.ZERO, Polynomial.ZERO)).isEqualTo(Polynomial.ZERO);
    // Knuth's example, §4.6.1
    assertThat(
            Gcd.gcd(
                p("x^8 + x^6 - 3x^4 - 3x^3 + 8x^2 + 2x - 5"), p("3x^6 + 5x^4 - 4x^2 - 9x + 21")))
        .isEqualTo(Polynomial.ONE);
  }

  @Test
  void multivariateGcds() {
    assertThat(Gcd.gcd(p("x^2 - y^2"), p("x^2 + 2x y + y^2"))).isEqualTo(p("x + y"));
    assertThat(Gcd.gcd(p("x y + x"), p("y^2 - 1"))).isEqualTo(p("y + 1"));
    assertThat(Gcd.gcd(p("(x + y + z)^2 (x - z)"), p("(x + y + z)(x + z)")))
        .isEqualTo(p("x + y + z"));
    assertThat(Gcd.gcd(p("x y"), p("x z"))).isEqualTo(p("x"));
    assertThat(Gcd.gcd(p("x + y"), p("x - y"))).isEqualTo(Polynomial.ONE);
    assertThat(Gcd.gcd(p("sin(x)^2 - 1"), p("sin(x) - 1"))).isEqualTo(p("sin(x) - 1"));
  }

  @Test
  void theSignRanksSymbolsFirst() {
    assertThat(Gcd.sign(p("x - π"))).isPositive();
    assertThat(Gcd.sign(p("π - x"))).isNegative();
    assertThat(Gcd.sign(p("x^2 - sin(x)^2"))).isPositive();
    assertThat(Gcd.sign(p("sin(x)^3 - x"))).isNegative();
    assertThat(Gcd.sign(p("π - 1"))).isPositive();
    assertThat(Gcd.gcd(p("x y - π^2 x"), p("x y - π^2 x"))).isEqualTo(p("x y - π^2 x"));
    assertThat(Gcd.primitivePart(p("π^2 x - x y"))).isEqualTo(p("x y - π^2 x"));
  }

  @Test
  void lcmsAndContents() {
    assertThat(Gcd.lcm(p("x^2 - 1"), p("x + 1"))).isEqualTo(p("x^2 - 1"));
    assertThat(Gcd.lcm(p("4"), p("6"))).isEqualTo(p("12"));
    assertThat(Gcd.lcm(p("-x"), p("y"))).isEqualTo(p("x y"));
    assertThat(Gcd.content(p("x y^2 + x^2 y"), X)).isEqualTo(p("y"));
    assertThat(Gcd.content(p("2x y + 4y"), X)).isEqualTo(p("2y"));
    assertThat(Gcd.rationalContent(p("6x/5 + 4/15"))).isEqualTo(Rational.of(2, 15));
    assertThat(Gcd.primitivePart(p("-6x/5 - 4/15"))).isEqualTo(p("9x + 2"));
    assertThat(Gcd.rationalContent(Polynomial.ZERO)).isEqualTo(Int.ZERO);
  }

  @Test
  void squareFreeDecompositions() {
    SquareFree.Decomposition d = SquareFree.of(p("x^5 - x^4 - 2x^3 + 2x^2 + x - 1"));
    // (x - 1)^3 (x + 1)^2
    assertThat(d.parts()).containsExactly(Polynomial.ONE, p("x + 1"), p("x - 1"));
    assertThat(d.unit()).isEqualTo(Int.ONE);
    SquareFree.Decomposition e = SquareFree.of(p("-2 x^2 y^3 + 2 y^3"));
    assertThat(e.unit()).isEqualTo(Int.of(-2));
    assertThat(e.parts()).containsExactly(p("x^2 - 1"), Polynomial.ONE, p("y"));
    assertThat(SquareFree.of(p("3")).parts()).isEmpty();
    assertThat(SquareFree.of(p("x^2 y^2 + 2 x y + 1")).parts())
        .isEqualTo(List.of(Polynomial.ONE, p("x y + 1")));
    assertThat(SquareFree.of(p("(x + y)^2 (x - y)")).parts())
        .containsExactly(p("x - y"), p("x + y"));
  }
}
