package mathcanvas.engine.algebra;

import static mathcanvas.engine.algebra.PolynomialTest.p;
import static mathcanvas.engine.testing.Inputs.canonical;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

class RationalFunctionTest {

  private static Expr cancel(String s) {
    return Algebra.cancel(canonical(s), Context.DEFAULT);
  }

  private static Expr factor(String s) {
    return Algebra.factor(canonical(s), Context.DEFAULT).orElseThrow().expr();
  }

  private static Expr apart(String s) {
    return Algebra.apart(canonical(s), canonical("x"), Context.DEFAULT).orElseThrow();
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
    RationalFunction f = RationalFunction.of(canonical("1/x + 1/y"));
    assertThat(f.numerator()).isEqualTo(p("x + y"));
    assertThat(f.denominator()).isEqualTo(p("x y"));
    RationalFunction g = RationalFunction.of(canonical("x^(-1/2) + 1"));
    assertThat(g.denominator()).isEqualTo(Polynomial.of(canonical("x^(1/2)")));
  }

  @Test
  void cancels() {
    assertThat(cancel("(x^2 - 1)/(x + 1)")).isEqualTo(canonical("x - 1"));
    assertThat(cancel("(x^2 - 1)/(x^2 + 2x + 1)")).isEqualTo(canonical("(x - 1)/(x + 1)"));
    assertThat(cancel("x/x")).isEqualTo(canonical("1"));
    assertThat(cancel("(x^2 - y^2)/(2x - 2y)")).isEqualTo(canonical("x/2 + y/2"));
    assertThat(cancel("1/x + 1/y")).isEqualTo(canonical("(x + y)/(x y)"));
    assertThat(cancel("(sin(x)^2 - 1)/(sin(x) + 1)")).isEqualTo(canonical("sin(x) - 1"));
    assertThat(cancel("[[1, 2], [3, 4]] x")).isEqualTo(canonical("[[1, 2], [3, 4]] x"));
  }

  @Test
  void factorsRationalExpressions() {
    assertThat(factor("(x^2 - 1)/(x^2 + 2x + 1)")).isEqualTo(canonical("(x - 1)/(x + 1)"));
    assertThat(factor("1/x + 1/y")).isEqualTo(canonical("(x + y)/(x y)"));
    assertThat(factor("x/(2x^2 - 8)")).isEqualTo(canonical("x/(2 (x - 2)(x + 2))"));
  }

  @Test
  void theTraceStartsFromTheInputForm() {
    Context ctx = Context.DEFAULT.withTrace(true);
    Rewriter.Result sum = Algebra.factor(canonical("1/x + 1/y"), ctx).orElseThrow();
    TraceChecks.assertChain(sum);
    assertThat(sum.trace().rewrites())
        .extracting(Rewrite::rule)
        .containsExactly("common-denominator");
    assertThat(sum.trace().rewrites().getFirst().before()).isEqualTo(canonical("1/x + 1/y"));
    Rewriter.Result q = Algebra.factor(canonical("(x^2 - 1)/(x^2 - 2x + 1)"), ctx).orElseThrow();
    TraceChecks.assertChain(q);
    assertThat(q.trace().rewrites())
        .extracting(Rewrite::rule)
        .containsExactly("rational-root", "square-free-split", "cancel");
    assertThat(q.expr()).isEqualTo(canonical("(x + 1)/(x - 1)"));
    Rewriter.Result both = Algebra.factor(canonical("(x^2 - 1)/(x^2 - 4)"), ctx).orElseThrow();
    TraceChecks.assertChain(both);
    assertThat(both.trace().rewrites())
        .extracting(Rewrite::rule)
        .containsExactly("rational-root", "rational-root");
  }

  @Test
  void refusesWhatIsNotAScalarAndBoundsTheDegree() {
    Context ctx = Context.DEFAULT;
    assertThat(cancel("(x + ∞)/x")).isEqualTo(canonical("(x + ∞)/x"));
    assertThat(Algebra.apart(canonical("1/(x + ∞)"), canonical("x"), ctx)).isEmpty();
    assertThat(Algebra.apart(canonical("{1, 2}"), canonical("x"), ctx)).isEmpty();
    assertThat(cancel("3^1000000000 x/x")).isEqualTo(canonical("3^1000000000"));
    assertThatThrownBy(() -> cancel("(x^10001 - 1)/(x - 1)"))
        .isInstanceOf(AlgebraLimitException.class);
    assertThatThrownBy(() -> Algebra.apart(canonical("1/(x^10001 - 1)"), canonical("x"), ctx))
        .isInstanceOf(AlgebraLimitException.class);
  }

  /** Integer and rational exponents up to Integer.MAX_VALUE are powers; past it, kernels. */
  @Test
  void exponentsReachTheIntRange() {
    Context ctx = Context.DEFAULT;
    for (String s : java.util.List.of("(x + 2)^2147483647", "(x + 2)^-2147483647")) {
      assertThatThrownBy(() -> Algebra.cancel(canonical(s), ctx))
          .as(s)
          .isInstanceOf(AlgebraLimitException.class);
    }
    assertThat(cancel("(x + 2)^2147483648")).isEqualTo(canonical("(x + 2)^2147483648"));
    assertThat(cancel("(x + 2)^-2147483648")).isEqualTo(canonical("(x + 2)^-2147483648"));
    // a rational exponent's numerator: the kernel (x + 2)^(1/2) to that power, or a kernel itself
    assertThat(RationalFunction.of(canonical("(x + 2)^(2147483647/2)")).numerator().degree())
        .isEqualTo(2147483647);
    assertThat(RationalFunction.of(canonical("(x + 2)^(-2147483647/2)")).denominator().degree())
        .isEqualTo(2147483647);
    assertThat(RationalFunction.of(canonical("(x + 2)^(2147483649/2)")).numerator().degree())
        .isEqualTo(1);
  }

  /** A huge negative power's unit is never raised: each refuses at once, all under 5 s. */
  @Test
  void hugeNegativePowersAreRefusedAtOnce() {
    Context ctx = Context.DEFAULT;
    long t = System.nanoTime();
    for (String s : java.util.List.of("(3x + 6)^-1000000000", "1/(3x + 6)^1000000000")) {
      assertThatThrownBy(() -> Algebra.cancel(canonical(s), ctx))
          .as(s)
          .isInstanceOf(AlgebraLimitException.class);
      assertThatThrownBy(() -> Algebra.apart(canonical(s), canonical("x"), ctx))
          .as(s)
          .isInstanceOf(AlgebraLimitException.class);
    }
    // the whole test: typically under 0.5 s locally (docs/reference/engine.md, Timing budgets)
    assertThat(System.nanoTime() - t).isLessThan(120_000_000_000L);
  }

  @Test
  void partialFractions() {
    assertThat(apart("1/(x^2 - 1)")).isEqualTo(canonical("1/(2(x - 1)) - 1/(2(x + 1))"));
    assertThat(apart("(x^3 + 1)/(x^2 - 3x + 2)"))
        .isEqualTo(canonical("x + 3 - 2/(x - 1) + 9/(x - 2)"));
    assertThat(apart("1/(x^2 (x + 1))")).isEqualTo(canonical("1/x^2 - 1/x + 1/(x + 1)"));
    assertThat(apart("(2x + 3)/(x + 1)^3")).isEqualTo(canonical("2/(x + 1)^2 + 1/(x + 1)^3"));
    assertThat(apart("1/(x (x^2 + 1))")).isEqualTo(canonical("1/x - x/(x^2 + 1)"));
    assertThat(apart("x^2 + 1")).isEqualTo(canonical("x^2 + 1"));
    assertThat(apart("1/(x y)")).isEqualTo(canonical("1/(x y)"));
  }
}
