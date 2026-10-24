package mathcanvas.engine.algebra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import org.junit.jupiter.api.Test;

class PolynomialTest {

  static Expr e(String s) {
    return Canonical.of(Parser.parseExpression(s, Names.NONE));
  }

  static Polynomial p(String s) {
    return Polynomial.of(e(s));
  }

  private static final Symbol X = Symbol.of("x");
  private static final Symbol Y = Symbol.of("y");

  @Test
  void convertsAndConvertsBack() {
    Polynomial cube = p("(x + 1)^3");
    assertThat(cube.variables()).containsExactly(X);
    assertThat(cube.degree()).isEqualTo(3);
    assertThat(cube.coefficient(2)).isEqualTo(Int.of(3));
    assertThat(cube.toExpr()).isEqualTo(e("x^3 + 3x^2 + 3x + 1"));
    assertThat(p("0").isZero()).isTrue();
    assertThat(p("1/2").constantTerm()).isEqualTo(Rational.of(1, 2));
  }

  @Test
  void termsAreInDescendingGradedLexicographicOrder() {
    Polynomial q = p("1 + y + x + y^2 + x*y + x^2");
    assertThat(
            q.terms().stream()
                .map(t -> List.of(t.monomial().exponent(0), t.monomial().exponent(1))))
        .containsExactly(
            List.of(2, 0),
            List.of(1, 1),
            List.of(0, 2),
            List.of(1, 0),
            List.of(0, 1),
            List.of(0, 0));
    assertThat(q.coefficients().firstKey()).isEqualTo(new Monomial(new int[] {2, 0}));
  }

  @Test
  void collectsKernels() {
    Polynomial q = p("(sin(x) + 1)^2");
    assertThat(q.variables()).containsExactly(e("sin(x)"));
    assertThat(q.toExpr()).isEqualTo(e("sin(x)^2 + 2 sin(x) + 1"));
    assertThat(p("π x + e").variables()).containsExactly(e("π"), e("e"), X);
    assertThat(p("2^x + 1").variables()).containsExactly(e("2^x"));
    assertThat(p("x^y").variables()).containsExactly(e("x^y"));
  }

  @Test
  void negativeAndFractionalPowersShareTheirKernel() {
    Polynomial q = p("x^-2 + x^-1");
    assertThat(q.variables()).containsExactly(e("x^-1"));
    assertThat(q.degree()).isEqualTo(2);
    Polynomial r = p("x^(3/2) + x^(1/2)");
    assertThat(r.variables()).containsExactly(e("x^(1/2)"));
    assertThat(r.degree()).isEqualTo(3);
    assertThat(r.toExpr()).isEqualTo(e("x^(3/2) + x^(1/2)"));
    assertThat(p("x^(-1/2)").variables()).containsExactly(e("x^(-1/2)"));
    assertThat(p("1/(x + 1)").variables()).containsExactly(e("(x + 1)^-1"));
  }

  @Test
  void decimalsAndComplexNumbersAreKernels() {
    assertThat(p("0.5 x").variables()).containsExactly(e("0.5"), X);
    assertThat(p("(x + i)^2").toExpr()).isEqualTo(e("x^2 + 2 i x - 1"));
  }

  @Test
  void restrictsTheVariablesWhenAsked() {
    assertThat(Polynomial.of(e("x^2 + y"), List.of(X, Y))).isPresent();
    assertThat(Polynomial.of(e("x^2 + y"), List.of(X))).isEmpty();
    assertThat(Polynomial.of(e("sin(x)"), List.of(X))).isEmpty();
  }

  @Test
  void refusesFactorsThatDoNotCommute() {
    assertThatThrownBy(() -> p("[[1, 2], [3, 4]] x")).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void arithmetic() {
    assertThat(p("x + y").multiply(p("x - y"))).isEqualTo(p("x^2 - y^2"));
    assertThat(p("x + y").subtract(p("y"))).isEqualTo(p("x"));
    assertThat(p("x + y").subtract(p("y")).variables()).containsExactly(X);
    assertThat(p("x + 1").pow(0)).isEqualTo(Polynomial.ONE);
    assertThat(p("2x + 3").pow(5)).isEqualTo(p("(2x + 3)^5"));
    assertThat(p("x + y + 1").pow(4))
        .isEqualTo(p("x + y + 1").multiply(p("x + y + 1")).multiply(p("(x + y + 1)^2")));
    assertThat(p("3 x^2 y").pow(3)).isEqualTo(p("27 x^6 y^3"));
    assertThat(p("x^3 y + x y^2").derivative(X)).isEqualTo(p("3 x^2 y + y^2"));
    assertThat(p("(1/2) x").multiply(Int.of(4))).isEqualTo(p("2 x"));
  }

  @Test
  void views() {
    Polynomial q = p("3 x^2 y + x^2 + y^3 + 2");
    assertThat(q.degree(X)).isEqualTo(2);
    assertThat(q.degree(Y)).isEqualTo(3);
    assertThat(q.degree(Symbol.of("z"))).isZero();
    assertThat(q.coefficient(X, 2)).isEqualTo(p("3y + 1"));
    assertThat(q.coefficient(X, 0)).isEqualTo(p("y^3 + 2"));
    assertThat(q.leadingCoefficient(Y)).isEqualTo(Polynomial.ONE);
    assertThat(q.totalDegree()).isEqualTo(3);
    assertThat(Polynomial.ZERO.totalDegree()).isEqualTo(-1);
    assertThatThrownBy(q::degree).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void refusesPolynomialsPastTheTotalSize() {
    for (String s : List.of("(x + 1)^99999", "(x + y)^99999")) {
      long t = System.nanoTime();
      assertThatThrownBy(() -> p(s))
          .as(s)
          .isInstanceOf(AlgebraLimitException.class)
          .hasMessage("the polynomial is too large");
      assertThat(System.nanoTime() - t).as(s).isLessThan(50_000_000L);
    }
    assertThat(p("(x + 1)^3000").degree()).isEqualTo(3000);
  }

  @Test
  void refusesPowersPastTheCap() {
    assertThatThrownBy(() -> p("(x + y)^100000")).isInstanceOf(AlgebraLimitException.class);
    assertThatThrownBy(() -> p("(x + y + z + 1)^100")).isInstanceOf(AlgebraLimitException.class);
    assertThat(p("x^100000").degree()).isEqualTo(100000);
    assertThat(p("(x^5 + x^4 + x^3 + x^2 + x + 1)^30").degree()).isEqualTo(150);
  }

  @Test
  void refusesWhatIsNotAScalar() {
    for (String s :
        List.of(
            "x + ∞", "undefined", "x + true", "(x = 1) + 1", "{x}", "[x, 1] + 1", "(x -> x) + 1")) {
      assertThatThrownBy(() -> p(s)).as(s).isInstanceOf(IllegalArgumentException.class);
    }
    assertThat(p("sin([x, 1]) + 1").variables()).containsExactly(e("sin([x, 1])"));
    assertThat(p("2^(1/2) x").variables()).containsExactly(e("2^(1/2)"), X);
  }

  @Test
  void anUnfoldedNumericPowerIsAKernel() {
    Polynomial q = p("3^100000000 x");
    assertThat(q.variables()).containsExactly(e("3^100000000"), X);
    assertThat(q.maxDegree()).isEqualTo(1);
    assertThat(p("3^-100000000").variables()).containsExactly(e("3^-100000000"));
  }

  @Test
  void checksTheInvariants() {
    Polynomial.Term one = new Polynomial.Term(Monomial.one(1), Int.ONE);
    assertThatThrownBy(() -> new Polynomial(List.of(X), List.of(one)))
        .hasMessageContaining("does not occur");
    assertThatThrownBy(() -> new Polynomial(List.of(Y, X), List.of()))
        .hasMessageContaining("sorted");
    assertThatThrownBy(
            () ->
                new Polynomial(List.of(), List.of(new Polynomial.Term(Monomial.one(0), Int.ZERO))))
        .hasMessageContaining("zero");
    assertThatThrownBy(() -> new Monomial(new int[] {-1}))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
