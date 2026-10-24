package mathcanvas.engine.algebra;

import static mathcanvas.engine.algebra.PolynomialTest.e;
import static mathcanvas.engine.algebra.PolynomialTest.p;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

class RationalFunctionTest {

  private static Expr cancel(String s) {
    return Algebra.cancel(e(s), Context.DEFAULT);
  }

  private static Expr factor(String s) {
    return Algebra.factor(e(s), Context.DEFAULT).orElseThrow().expr();
  }

  private static Expr apart(String s) {
    return Algebra.apart(e(s), e("x"), Context.DEFAULT).orElseThrow();
  }

  @Test
  void normalForm() {
    RationalFunction f = RationalFunction.of(p("2x^2 - 2"), p("-4x - 4"));
    assertThat(f.numerator()).isEqualTo(p("-x/2 + 1/2"));
    assertThat(f.denominator()).isEqualTo(Polynomial.ONE);
    RationalFunction g = RationalFunction.of(p("x"), p("2x + 2"));
    assertThat(g.numerator()).isEqualTo(p("x/2"));
    assertThat(g.denominator()).isEqualTo(p("x + 1"));
    assertThat(RationalFunction.of(Polynomial.ZERO, p("x")).denominator())
        .isEqualTo(Polynomial.ONE);
    assertThatThrownBy(() -> new RationalFunction(p("1"), p("2x")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RationalFunction.of(p("1"), Polynomial.ZERO))
        .isInstanceOf(ArithmeticException.class);
  }

  @Test
  void bringsOverACommonDenominator() {
    RationalFunction f = RationalFunction.of(e("1/x + 1/y"));
    assertThat(f.numerator()).isEqualTo(p("x + y"));
    assertThat(f.denominator()).isEqualTo(p("x y"));
    RationalFunction g = RationalFunction.of(e("x^(-1/2) + 1"));
    assertThat(g.denominator()).isEqualTo(Polynomial.of(e("x^(1/2)")));
  }

  @Test
  void cancels() {
    assertThat(cancel("(x^2 - 1)/(x + 1)")).isEqualTo(e("x - 1"));
    assertThat(cancel("(x^2 - 1)/(x^2 + 2x + 1)")).isEqualTo(e("(x - 1)/(x + 1)"));
    assertThat(cancel("x/x")).isEqualTo(e("1"));
    assertThat(cancel("(x^2 - y^2)/(2x - 2y)")).isEqualTo(e("x/2 + y/2"));
    assertThat(cancel("1/x + 1/y")).isEqualTo(e("(x + y)/(x y)"));
    assertThat(cancel("(sin(x)^2 - 1)/(sin(x) + 1)")).isEqualTo(e("sin(x) - 1"));
    assertThat(cancel("[[1, 2], [3, 4]] x")).isEqualTo(e("[[1, 2], [3, 4]] x"));
  }

  @Test
  void factorsRationalExpressions() {
    assertThat(factor("(x^2 - 1)/(x^2 + 2x + 1)")).isEqualTo(e("(x - 1)/(x + 1)"));
    assertThat(factor("1/x + 1/y")).isEqualTo(e("(x + y)/(x y)"));
    assertThat(factor("x/(2x^2 - 8)")).isEqualTo(e("x/(2 (x - 2)(x + 2))"));
  }

  @Test
  void theTraceStartsFromTheInputForm() {
    Context ctx = Context.DEFAULT.withTrace(true);
    Rewriter.Result sum = Algebra.factor(e("1/x + 1/y"), ctx).orElseThrow();
    TraceChecks.assertChain(sum);
    assertThat(sum.trace().rewrites())
        .extracting(Rewrite::rule)
        .containsExactly("common-denominator");
    assertThat(sum.trace().rewrites().getFirst().before()).isEqualTo(e("1/x + 1/y"));
    Rewriter.Result q = Algebra.factor(e("(x^2 - 1)/(x^2 - 2x + 1)"), ctx).orElseThrow();
    TraceChecks.assertChain(q);
    assertThat(q.trace().rewrites())
        .extracting(Rewrite::rule)
        .containsExactly("rational-root", "square-free-split", "cancel");
    assertThat(q.expr()).isEqualTo(e("(x + 1)/(x - 1)"));
    Rewriter.Result both = Algebra.factor(e("(x^2 - 1)/(x^2 - 4)"), ctx).orElseThrow();
    TraceChecks.assertChain(both);
    assertThat(both.trace().rewrites())
        .extracting(Rewrite::rule)
        .containsExactly("rational-root", "rational-root");
  }

  @Test
  void refusesWhatIsNotAScalarAndBoundsTheDegree() {
    Context ctx = Context.DEFAULT;
    assertThat(cancel("(x + ∞)/x")).isEqualTo(e("(x + ∞)/x"));
    assertThat(Algebra.apart(e("1/(x + ∞)"), e("x"), ctx)).isEmpty();
    assertThat(Algebra.apart(e("{1, 2}"), e("x"), ctx)).isEmpty();
    assertThat(cancel("3^1000000000 x/x")).isEqualTo(e("3^1000000000"));
    assertThatThrownBy(() -> cancel("(x^10001 - 1)/(x - 1)"))
        .isInstanceOf(AlgebraLimitException.class);
    assertThatThrownBy(() -> Algebra.apart(e("1/(x^10001 - 1)"), e("x"), ctx))
        .isInstanceOf(AlgebraLimitException.class);
  }

  /** A huge negative power's unit is never raised: each refuses at once, all under 5 s. */
  @Test
  void hugeNegativePowersAreRefusedAtOnce() {
    Context ctx = Context.DEFAULT;
    long t = System.nanoTime();
    for (String s : java.util.List.of("(3x + 6)^-1000000000", "1/(3x + 6)^1000000000")) {
      assertThatThrownBy(() -> Algebra.cancel(e(s), ctx))
          .as(s)
          .isInstanceOf(AlgebraLimitException.class);
      assertThatThrownBy(() -> Algebra.apart(e(s), e("x"), ctx))
          .as(s)
          .isInstanceOf(AlgebraLimitException.class);
    }
    assertThat(System.nanoTime() - t).isLessThan(5_000_000_000L);
  }

  @Test
  void partialFractions() {
    assertThat(apart("1/(x^2 - 1)")).isEqualTo(e("1/(2(x - 1)) - 1/(2(x + 1))"));
    assertThat(apart("(x^3 + 1)/(x^2 - 3x + 2)")).isEqualTo(e("x + 3 - 2/(x - 1) + 9/(x - 2)"));
    assertThat(apart("1/(x^2 (x + 1))")).isEqualTo(e("1/x^2 - 1/x + 1/(x + 1)"));
    assertThat(apart("(2x + 3)/(x + 1)^3")).isEqualTo(e("2/(x + 1)^2 + 1/(x + 1)^3"));
    assertThat(apart("1/(x (x^2 + 1))")).isEqualTo(e("1/x - x/(x^2 + 1)"));
    assertThat(apart("x^2 + 1")).isEqualTo(e("x^2 + 1"));
    assertThat(apart("1/(x y)")).isEqualTo(e("1/(x y)"));
  }
}
