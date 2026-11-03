package mathcanvas.engine.algebra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.testing.Soundness;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.statistics.Statistics;

/**
 * Factoring multiplies back exactly into normal-form, square-free bases, recovers products of known
 * irreducibles, and its expression and trace agree with the input.
 */
class FactorProperties {

  /** Irreducible over ℚ, each of degree at most 4. */
  private static final List<String> IRREDUCIBLE =
      List.of(
          "x",
          "x - 5",
          "2x + 3",
          "x^2 + 1",
          "x^2 - 2",
          "3x^2 - 7",
          "x^2 + x + 1",
          "x^3 - 2",
          "x^3 + x + 1",
          "x^4 + 1",
          "x^4 - 10x^2 + 1",
          "x^4 + x + 1",
          "x^2 + 2x + 2");

  @Provide
  Arbitrary<Polynomial> products() {
    return Arbitraries.oneOf(
        PolynomialArbitraries.products(1, 4),
        PolynomialArbitraries.products(2, 2),
        PolynomialArbitraries.products(3, 2));
  }

  @Provide
  Arbitrary<List<Integer>> picks() {
    return Arbitraries.integers()
        .between(0, IRREDUCIBLE.size() - 1)
        .list()
        .ofMinSize(1)
        .ofMaxSize(3);
  }

  @Property(tries = 400)
  void multipliesBackIntoNormalFormBases(@ForAll("products") Polynomial p) {
    Factorization f = Factoring.factor(p, Steps.NONE);
    assertThat(f.expand()).isEqualTo(p);
    List<Polynomial> seen = new ArrayList<>();
    for (Factorization.Factor x : f.factors()) {
      Polynomial b = x.base();
      assertThat(b.isConstant()).isFalse();
      assertThat(Gcd.primitivePart(b)).isEqualTo(b);
      assertThat(SquareFree.of(b).parts()).containsExactly(b);
      assertThat(seen).doesNotContain(b);
      seen.add(b);
    }
    assertThat(f.normalised()).isEqualTo(f);
    Statistics.label("complete").collect(f.complete());
  }

  /**
   * The square-free part left after the rational roots must be well within Kronecker's degree bound
   * for the factorization to be complete (at degree 8 the candidate cap can stop it), so the picks
   * are limited to degree 6.
   */
  @Property(tries = 300)
  void recoversProductsOfIrreducibles(
      @ForAll("picks") List<Integer> picks, @ForAll boolean square) {
    long nonLinear =
        picks.stream()
            .distinct()
            .mapToLong(i -> PolynomialTest.p(IRREDUCIBLE.get(i)).totalDegree())
            .filter(d -> d > 1)
            .sum();
    Assume.that(nonLinear <= 6);
    Polynomial p = Polynomial.constant(Int.of(-3));
    List<Factorization.Factor> expected = new ArrayList<>();
    for (int i : picks) {
      Polynomial q = PolynomialTest.p(IRREDUCIBLE.get(i));
      int m = square ? 2 : 1;
      p = p.multiply(q.pow(m));
      expected.add(new Factorization.Factor(q, m));
    }
    Factorization f = Factoring.factor(p, Steps.NONE);
    assertThat(f.complete()).as("%s", p).isTrue();
    assertThat(f).isEqualTo(new Factorization(Int.of(-3), expected, true).normalised());
  }

  /** The time the timed call of each try took, summed over a property's tries. */
  private long timed;

  @BeforeProperty
  void startTiming() {
    timed = 0;
  }

  /** Timing budgets are whole runs, never one call (docs/reference/engine.md, algebra tests). */
  @AfterProperty
  void checkTiming() {
    // typically about 0.1 s locally (docs/reference/engine.md, Timing budgets)
    assertThat(timed).as("the timed calls of the whole run").isLessThan(120_000_000_000L);
  }

  /**
   * A linear base to any power factors at once: a unit too large to fold stays unfolded. The second
   * call of each try, after a warm-up call, is timed, and the 300 tries take under 5 s together.
   */
  @Property(tries = 300)
  void aPowerOfALinearBaseFactorsAtOnce(
      @ForAll @IntRange(min = -9, max = 9) int a,
      @ForAll @IntRange(min = -9, max = 9) int b,
      @ForAll @IntRange(min = 1) int n) {
    Assume.that(a != 0 && b != 0);
    Expr e =
        Canonical.of(
            Power.of(
                mathcanvas.engine.expr.Sum.of(
                    Product.of(Num.of(a), PolynomialArbitraries.X), Num.of(b)),
                Num.of(n)));
    Algebra.factor(e, Context.DEFAULT);
    long t = System.nanoTime();
    Expr r = Algebra.factor(e, Context.DEFAULT).orElseThrow().expr();
    timed += System.nanoTime() - t;
    // the base is factored and raised again: a sum only for n = 1 with coprime a and b
    assertThat(r instanceof mathcanvas.engine.expr.Sum)
        .isEqualTo(n == 1 && Math.abs(Int.of(a).gcd(Int.of(b)).value().intValue()) == 1 && a > 0);
  }

  /** Irreducible over ℚ, among them parts too large to split (degree 9 and 10, no roots). */
  private static final List<String> MIXED =
      List.of("x^4 + 1", "x^2 + 1", "x - 3", "2x + 1", "x^9 + 2", "x^9 + 3", "x^10 + x + 1");

  @Provide
  Arbitrary<List<Integer>> mixedPicks() {
    return Arbitraries.integers().between(0, MIXED.size() - 1).list().ofMinSize(1).ofMaxSize(3);
  }

  /**
   * A quotient of expanded products factors to what cancel gives, in value, and in lowest terms:
   * however the bases are left unsplit, the result's numerator and denominator have gcd 1.
   */
  @Property(tries = 200)
  void factorCancelsAsCancelDoes(
      @ForAll("mixedPicks") List<Integer> top,
      @ForAll("mixedPicks") List<Integer> bottom,
      @ForAll long seed) {
    Polynomial p = Polynomial.ONE;
    for (int i : top) {
      p = p.multiply(PolynomialTest.p(MIXED.get(i)));
    }
    Polynomial q = Polynomial.ONE;
    for (int i : bottom) {
      q = q.multiply(PolynomialTest.p(MIXED.get(i)));
    }
    Expr e = Canonical.of(Product.of(p.toExpr(), Power.of(q.toExpr(), Num.MINUS_ONE)));
    Expr factored = Algebra.factor(e, Context.DEFAULT).orElseThrow().expr();
    Expr cancelled = Algebra.cancel(e, Context.DEFAULT);
    List<Map<Symbol, Numeric>> points =
        Soundness.points(PolynomialArbitraries.VARIABLES, Assumptions.EMPTY, 3, new Random(seed));
    Soundness.check(cancelled, factored, points);
    // the factors with a negative exponent are the denominator
    Polynomial num = Polynomial.ONE;
    Polynomial den = Polynomial.ONE;
    List<Expr> factors = factored instanceof Product f ? f.factors() : List.of(factored);
    for (Expr f : factors) {
      if (f instanceof Power w
          && w.exponent() instanceof Num n
          && n.value().isExact()
          && ((mathcanvas.engine.number.RealNumeric) n.value()).signum() < 0) {
        den =
            den.multiply(
                Polynomial.of(Power.of(w.base(), Canonical.of(Product.of(Num.MINUS_ONE, n)))));
      } else {
        num = num.multiply(Polynomial.of(f));
      }
    }
    assertThat(Gcd.gcd(num, den).isConstant()).as("%s", factored).isTrue();
  }

  /**
   * The factored expression has the input's value, and its trace is a chain of real rewrites: also
   * over quotients, where the conversion to a common denominator is a step of its own.
   */
  @Property(tries = 300)
  void theExpressionHasTheInputsValueAndTheTraceIsAChain(
      @ForAll("products") Polynomial p,
      @ForAll("products") Polynomial q,
      @ForAll boolean quotient,
      @ForAll long seed) {
    Expr e =
        quotient && !q.isZero()
            ? Canonical.of(Product.of(p.toExpr(), Power.of(q.toExpr(), Num.MINUS_ONE)))
            : p.toExpr();
    Rewriter.Result r = Algebra.factor(e, Context.DEFAULT.withTrace(true)).orElseThrow();
    List<Map<Symbol, Numeric>> points =
        Soundness.points(PolynomialArbitraries.VARIABLES, Assumptions.EMPTY, 3, new Random(seed));
    Soundness.check(e, r.expr(), points);
    // the chain starts from the input: each before is the node at its path in the tree as it stood
    assertThat(r.trace().initial()).isEqualTo(e);
    TraceChecks.assertChain(r);
    assertThat(Algebra.factor(e, Context.DEFAULT).orElseThrow().expr()).isEqualTo(r.expr());
  }
}
