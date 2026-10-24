package mathcanvas.engine.algebra;

import static mathcanvas.engine.algebra.PolynomialTest.e;
import static mathcanvas.engine.algebra.PolynomialTest.p;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.DefaultNarrator;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

class FactorTest {

  private static Expr factor(String s) {
    return Algebra.factor(e(s), Context.DEFAULT).orElseThrow().expr();
  }

  private static Factorization f(String s) {
    return Factoring.factor(p(s), Steps.NONE);
  }

  private static List<Factorization.Factor> factors(String... spec) {
    List<Factorization.Factor> out = new java.util.ArrayList<>();
    for (int i = 0; i < spec.length; i += 2) {
      out.add(new Factorization.Factor(p(spec[i]), Integer.parseInt(spec[i + 1])));
    }
    return out;
  }

  @Test
  void textbookCases() {
    assertThat(factor("x^4 - 1")).isEqualTo(e("(x - 1)(x + 1)(x^2 + 1)"));
    assertThat(factor("x^2 - 5x + 6")).isEqualTo(e("(x - 2)(x - 3)"));
    assertThat(factor("2x^2 + 4x + 2")).isEqualTo(e("2(x + 1)^2"));
    assertThat(factor("6x^2 - x - 2")).isEqualTo(e("(2x + 1)(3x - 2)"));
    assertThat(factor("x^3 - x")).isEqualTo(e("x (x - 1)(x + 1)"));
    assertThat(factor("x^2/2 - 1/2")).isEqualTo(e("(x - 1)(x + 1)/2"));
    assertThat(factor("1 - x")).isEqualTo(e("-(x - 1)"));
    assertThat(factor("x^2 + 1")).isEqualTo(e("x^2 + 1"));
    assertThat(factor("12")).isEqualTo(e("12"));
    assertThat(factor("x^6 - 1")).isEqualTo(e("(x - 1)(x + 1)(x^2 + x + 1)(x^2 - x + 1)"));
  }

  @Test
  void kroneckersMethodFindsFactorsWithoutRoots() {
    assertThat(f("x^4 + 4").factors()).isEqualTo(factors("x^2 - 2x + 2", "1", "x^2 + 2x + 2", "1"));
    assertThat(f("x^4 + 4").complete()).isTrue();
    Factorization irreducible = f("x^4 - 10x^2 + 1");
    assertThat(irreducible.factors()).isEqualTo(factors("x^4 - 10x^2 + 1", "1"));
    assertThat(irreducible.complete()).isTrue();
    assertThat(f("x^8 + x^4 + 1").expand()).isEqualTo(p("x^8 + x^4 + 1"));
    assertThat(f("x^8 + x^4 + 1").factors())
        .isEqualTo(factors("x^2 + x + 1", "1", "x^2 - x + 1", "1", "x^4 - x^2 + 1", "1"));
    assertThat(f("(x^4 + x + 1)(x^4 + 2x^2 + 3)").factors())
        .isEqualTo(factors("x^4 + x + 1", "1", "x^4 + 2x^2 + 3", "1"));
  }

  @Test
  void stopsBeyondTheBounds() {
    Factorization big = f("x^100 - 1");
    assertThat(big.complete()).isFalse();
    assertThat(big.factors()).hasSize(3);
    assertThat(big.expand()).isEqualTo(p("x^100 - 1"));
    Factorization three = f("(x + y + z)(x^2 + y^2 + z^2 + 1) + x y z");
    assertThat(three.expand()).isEqualTo(p("(x + y + z)(x^2 + y^2 + z^2 + 1) + x y z"));
  }

  @Test
  void factorsInSeveralVariables() {
    assertThat(factor("x^2 - y^2")).isEqualTo(e("(x - y)(x + y)"));
    assertThat(factor("x^3 + y^3")).isEqualTo(e("(x + y)(x^2 - x y + y^2)"));
    assertThat(factor("x y + x + y + 1")).isEqualTo(e("(x + 1)(y + 1)"));
    assertThat(factor("x^2 y - x y^2")).isEqualTo(e("x y (x - y)"));
    assertThat(factor("x^4 - y^4")).isEqualTo(e("(x - y)(x + y)(x^2 + y^2)"));
    assertThat(factor("(x + y + z)^2 (x - z)")).isEqualTo(e("(x - z)(x + y + z)^2"));
    assertThat(factor("a x + a y + b x + b y")).isEqualTo(e("(a + b)(x + y)"));
    assertThat(f("x^2 + x y + y^2").complete()).isTrue();
  }

  @Test
  void factorsInKernels() {
    assertThat(factor("sin(x)^2 - 1")).isEqualTo(e("(sin(x) - 1)(sin(x) + 1)"));
    assertThat(factor("(x^2 - 1) = 0")).isEqualTo(e("(x - 1)(x + 1) = 0"));
    assertThat(factor("[x^2 - 1, 2x + 2]")).isEqualTo(e("[(x - 1)(x + 1), 2(x + 1)]"));
  }

  @Test
  void theUnitCarriesTheContentAndSign() {
    Factorization g = f("-6x^2/5 + 6/5");
    assertThat(g.unit()).isEqualTo(Rational.of(-6, 5));
    assertThat(f("0").unit()).isEqualTo(Int.ZERO);
    assertThat(f("0").factors()).isEmpty();
  }

  @Test
  void recordsAChainOfRealSteps() {
    Rewriter.Result r =
        Algebra.factor(e("2x^4 - 2"), Context.DEFAULT.withTrace(true)).orElseThrow();
    TraceChecks.assertChain(r);
    List<String> rules = r.trace().rewrites().stream().map(Rewrite::rule).toList();
    assertThat(rules).containsExactly("pull-content", "rational-root", "rational-root");
    assertThat(r.trace().rewrites().getFirst().before()).isEqualTo(e("2x^4 - 2"));
    List<String> texts =
        new DefaultNarrator()
            .narrate(r.trace(), Context.DEFAULT).stream().map(s -> s.text()).toList();
    assertThat(texts)
        .containsExactly(
            "Take the common factor 2 out of 2x⁴ - 2",
            "1 is a root, so x - 1 is a factor of 2(x⁴ - 1)",
            "-1 is a root, so x + 1 is a factor of 2(x - 1)(x³ + x² + x + 1)");
    // the linear parts and the last cofactor are not searched again
    Rewriter.Result two =
        Algebra.factor(e("x^2 - 1"), Context.DEFAULT.withTrace(true)).orElseThrow();
    assertThat(two.trace().rewrites()).extracting(Rewrite::rule).containsExactly("rational-root");
    // nothing is recorded for a search stopped by a bound: nothing changed
    Rewriter.Result big =
        Algebra.factor(e("x^100 - 1"), Context.DEFAULT.withTrace(true)).orElseThrow();
    TraceChecks.assertChain(big);
    assertThat(big.trace().rewrites())
        .extracting(Rewrite::rule)
        .containsExactly("rational-root", "rational-root");
    Rewriter.Result irreducible =
        Algebra.factor(e("x^2 + 1"), Context.DEFAULT.withTrace(true)).orElseThrow();
    assertThat(irreducible.trace().rewrites()).isEmpty();
  }

  @Test
  void theSignFollowsTheFirstSymbol() {
    assertThat(factor("x^2 - π^2")).isEqualTo(e("(x - π)(x + π)"));
    assertThat(factor("sin(x)^2 - x^2")).isEqualTo(e("-(x - sin(x))(x + sin(x))"));
    assertThat(factor("π^2 - 1")).isEqualTo(e("(π - 1)(π + 1)"));
  }

  @Test
  void refusesWhatIsNotAScalar() {
    assertThat(Algebra.factor(e("x + ∞"), Context.DEFAULT)).isEmpty();
    assertThat(Algebra.factor(e("[x^2 - 1, true]"), Context.DEFAULT)).isEmpty();
    assertThat(Algebra.factor(e("x^2 - undefined"), Context.DEFAULT)).isEmpty();
  }

  @Test
  void refusesDegreesPastTheBound() {
    assertThatThrownBy(() -> Algebra.factor(e("x^10001 - 1"), Context.DEFAULT))
        .isInstanceOf(AlgebraLimitException.class)
        .hasMessage("the polynomial's degree exceeds 10,000");
    assertThatThrownBy(() -> Algebra.factor(e("x^1000000000 - 1"), Context.DEFAULT))
        .isInstanceOf(AlgebraLimitException.class);
    assertThat(factor("3^100000000 x^2 - 3^100000000")).isEqualTo(e("3^100000000 (x - 1)(x + 1)"));
  }

  @Test
  void factorsByStructure() {
    Context ctx = Context.DEFAULT.withTrace(true);
    for (String done : List.of("(x + 1)(x + 2)", "2x/(x + 1)^2", "(x - y)^3 (x + y)", "x sin(x)")) {
      Rewriter.Result r = Algebra.factor(e(done), ctx).orElseThrow();
      assertThat(r.expr()).as(done).isEqualTo(e(done));
      assertThat(r.trace().rewrites()).as(done).isEmpty();
    }
    Rewriter.Result q = Algebra.factor(e("(x^2 - 1)/(x - 1)"), ctx).orElseThrow();
    TraceChecks.assertChain(q);
    assertThat(q.expr()).isEqualTo(e("x + 1"));
    assertThat(q.trace().rewrites())
        .extracting(Rewrite::rule)
        .containsExactly("rational-root", "cancel");
    // the canonical product is (x - 1)^-1·(x² - 1): the numerator's step is at its operand 1
    assertThat(q.trace().rewrites().getFirst().path().indices()).containsExactly(1);
    Rewriter.Result power = Algebra.factor(e("(x^2 - 1)^3"), ctx).orElseThrow();
    TraceChecks.assertChain(power);
    assertThat(power.expr()).isEqualTo(e("(x - 1)^3 (x + 1)^3"));
    Rewriter.Result root = Algebra.factor(e("(x^2 - 1)^(1/2)"), ctx).orElseThrow();
    TraceChecks.assertChain(root);
    assertThat(root.expr()).isEqualTo(e("((x - 1)(x + 1))^(1/2)"));
    assertThat(factor("3 (2x + 2)")).isEqualTo(e("6 (x + 1)"));
  }

  /** Each factor is factored on its own: the product of two degree-8 parts is not expanded. */
  @Test
  void aFactoredInputStaysWithinKroneckersBound() {
    Rewriter.Result r =
        Algebra.factor(e("(x^8 + 30x + 1440)(x^8 + 30x + 1441)"), Context.DEFAULT.withTrace(true))
            .orElseThrow();
    assertThat(r.expr()).isEqualTo(e("(x^8 + 30x + 1440)(x^8 + 30x + 1441)"));
    assertThat(r.trace().rewrites()).isEmpty();
  }

  /** A complete base hidden in an unsplit base of the other side cancels, either way round. */
  @Test
  void cancelsAFactorHiddenInAnUnsplitBase() {
    Expr unsplit = Algebra.expand(e("(x^4 + 1)(x^9 + 2)(x^9 + 3)"), Context.DEFAULT).expr();
    Expr cofactor = Algebra.expand(e("(x^9 + 2)(x^9 + 3)"), Context.DEFAULT).expr();
    Context ctx = Context.DEFAULT.withTrace(true);
    Rewriter.Result below = Algebra.factor(quotient(e("x^4 + 1"), unsplit), ctx).orElseThrow();
    TraceChecks.assertChain(below);
    assertThat(below.expr()).isEqualTo(quotient(e("1"), cofactor));
    assertThat(below.trace().rewrites()).extracting(Rewrite::rule).containsExactly("cancel");
    Rewriter.Result above = Algebra.factor(quotient(unsplit, e("x^4 + 1")), ctx).orElseThrow();
    TraceChecks.assertChain(above);
    assertThat(above.expr()).isEqualTo(cofactor);
    assertThat(Algebra.cancel(quotient(e("x^4 + 1"), unsplit), Context.DEFAULT))
        .isEqualTo(below.expr());
  }

  private static Expr quotient(Expr num, Expr den) {
    return mathcanvas.engine.expr.Canonical.of(
        mathcanvas.engine.expr.Product.of(
            num, mathcanvas.engine.expr.Power.of(den, mathcanvas.engine.expr.Num.MINUS_ONE)));
  }

  /** Exponents up to Integer.MAX_VALUE are factored; merged past it, they are the limit. */
  @Test
  void exponentsReachTheIntRange() {
    assertThat(factor("(x + 2)^2147483647")).isEqualTo(e("(x + 2)^2147483647"));
    assertThat(factor("(-x - 2)^2147483647")).isEqualTo(e("-(x + 2)^2147483647"));
    assertThat(factor("(-x - 2)^-2147483647")).isEqualTo(e("-1/(x + 2)^2147483647"));
    assertThatThrownBy(() -> factor("(x + 2)^1073741825 (-x - 2)^1073741823"))
        .isInstanceOf(AlgebraLimitException.class)
        .hasMessage("the exponents are too large");
    assertThat(factor("(x + 2)^1073741824 (-x - 2)^1073741823"))
        .isEqualTo(e("-(x + 2)^2147483647"));
  }

  /** A power whose unit's power is past the folding bound stays a power, its unit unfolded. */
  @Test
  void aHugePowerStaysAPower() {
    long t = System.nanoTime();
    assertThat(factor("(3x + 6)^1000000000")).isEqualTo(e("3^1000000000 (x + 2)^1000000000"));
    assertThat(factor("(3x + 6)^1000000000/(x + 2)"))
        .isEqualTo(e("3^1000000000 (x + 2)^999999999"));
    assertThat(factor("(x + 2)^2147483647 (x + 2)")).isEqualTo(e("(x + 2)^2147483648"));
    assertThat(System.nanoTime() - t).isLessThan(5_000_000_000L);
  }

  /** Merged multiplicities past an int are a limit, not a refusal. */
  @Test
  void mergedMultiplicitiesPastAnIntAreALimit() {
    assertThatThrownBy(
            () ->
                Algebra.factor(
                    e("(x + 2)^1073741823 (-x - 2)^1073741823 (x^2 + 4x + 4)"), Context.DEFAULT))
        .isInstanceOf(AlgebraLimitException.class)
        .hasMessage("the exponents are too large");
  }

  /** Bases left unsplit on both sides are cancelled through their gcd. */
  @Test
  void cancelsTheGcdOfUnsplitBases() {
    Expr num = Algebra.expand(e("(x^10 + x + 1)(x^9 + 2)"), Context.DEFAULT).expr();
    Expr den = Algebra.expand(e("(x^10 + x + 1)(x^9 + 3)"), Context.DEFAULT).expr();
    Expr quotient =
        mathcanvas.engine.expr.Canonical.of(
            mathcanvas.engine.expr.Product.of(
                num, mathcanvas.engine.expr.Power.of(den, mathcanvas.engine.expr.Num.MINUS_ONE)));
    Rewriter.Result r = Algebra.factor(quotient, Context.DEFAULT.withTrace(true)).orElseThrow();
    TraceChecks.assertChain(r);
    assertThat(r.expr()).isEqualTo(e("(x^9 + 2)/(x^9 + 3)"));
    assertThat(r.trace().rewrites()).extracting(Rewrite::rule).containsExactly("cancel");
  }
}
