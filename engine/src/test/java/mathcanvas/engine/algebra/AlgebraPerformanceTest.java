package mathcanvas.engine.algebra;

import static mathcanvas.engine.algebra.PolynomialTest.e;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import java.util.Random;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import org.junit.jupiter.api.Test;

/**
 * The algebra's budgets: each case finishes in well under a second on the development machine. The
 * bound asserted is generous so that slower CI runners pass.
 */
class AlgebraPerformanceTest {

  // Each whole call: typically 0.05 to 0.8 s locally (docs/reference/engine.md, Timing budgets).
  private static final Duration BOUND = Duration.ofSeconds(30);

  @Test
  void expandsTheTwentiethPowerOfThreeTerms() {
    Expr r =
        assertTimeoutPreemptively(
            BOUND, () -> Algebra.expand(e("(x + y + z)^20"), Context.DEFAULT).expr());
    assertThat(((Sum) r).terms()).hasSize(231);
  }

  /** A two-term power well inside the total-size bound: 3,001 coefficients of up to 3,000 bits. */
  @Test
  void expandsTheThreeThousandthPowerOfTwoTerms() {
    Expr r =
        assertTimeoutPreemptively(
            BOUND, () -> Algebra.expand(e("(x + 1)^3000"), Context.DEFAULT).expr());
    assertThat(((Sum) r).terms()).hasSize(3001);
  }

  /**
   * A sum of 5,456 terms converts in one pass: 30 collected powers of x and the 496 terms of the
   * coefficient of x⁰, which join the sum.
   */
  @Test
  void collectsTheThirtiethPowerOfFourTerms() {
    Expr r =
        assertTimeoutPreemptively(
            BOUND, () -> Algebra.collect(e("(x + y + z + w)^30"), e("x"), Context.DEFAULT));
    assertThat(((Sum) r).terms()).hasSize(526);
  }

  /** The 10,626 terms of the power are distributed over the product in one conversion. */
  @Test
  void expandsAProductWithALargePower() {
    Expr r =
        assertTimeoutPreemptively(
            BOUND,
            () -> Algebra.expand(e("(a + b + c + d + e)^20 (x + 1)"), Context.DEFAULT).expr());
    assertThat(((Sum) r).terms()).hasSize(21_252);
  }

  @Test
  void gcdAtTheDegreeBound() {
    Polynomial r =
        assertTimeoutPreemptively(
            BOUND, () -> Gcd.gcd(PolynomialTest.p("x^10000 - 1"), PolynomialTest.p("x^2 - 1")));
    assertThat(r).isEqualTo(PolynomialTest.p("x^2 - 1"));
    assertThat(Gcd.gcd(PolynomialTest.p("x^10000 + 3x^5000 + 1"), PolynomialTest.p("7x^2 + 1")))
        .isEqualTo(Polynomial.ONE);
  }

  /** The lcm divides before it multiplies, so no intermediate passes the degree bound. */
  @Test
  void lcmAtTheDegreeBound() {
    Polynomial l =
        assertTimeoutPreemptively(
            BOUND, () -> Gcd.lcm(PolynomialTest.p("x^10000 - 1"), PolynomialTest.p("x^2 - 1")));
    assertThat(l).isEqualTo(PolynomialTest.p("x^10000 - 1"));
  }

  @Test
  void collectsAHugeExponent() {
    Expr c =
        assertTimeoutPreemptively(
            BOUND, () -> Algebra.collect(e("x^1073741823 + 2"), e("x"), Context.DEFAULT));
    assertThat(c).isEqualTo(e("x^1073741823 + 2"));
  }

  @Test
  void gcdOfDegreeFiftyWithACommonFactorOfDegreeTwentyFive() {
    Random r = new Random(50);
    Polynomial g = dense(r, 25);
    Polynomial a = g.multiply(dense(r, 25));
    Polynomial b = g.multiply(dense(r, 25));
    Polynomial result = assertTimeoutPreemptively(BOUND, () -> Gcd.gcd(a, b));
    assertThat(result).isEqualTo(Gcd.primitivePart(g));
  }

  @Test
  void factorsXToTheHundredMinusOneInBoundedTime() {
    Factorization f =
        assertTimeoutPreemptively(
            BOUND, () -> Factoring.factor(PolynomialTest.p("x^100 - 1"), Steps.NONE));
    assertThat(f.complete()).isFalse();
    assertThat(f.factors()).hasSize(3);
  }

  /** About 180,000 interpolations, near the cap, prove this degree-8 polynomial irreducible. */
  @Test
  void kroneckersMethodNearItsCap() {
    Polynomial p = PolynomialTest.p("x^8 + 30x + 1440");
    Factorization f = assertTimeoutPreemptively(BOUND, () -> Factoring.factor(p, Steps.NONE));
    assertThat(f.complete()).isTrue();
    assertThat(f.factors()).hasSize(1);
  }

  @Test
  void factorsAtTheDegreeBound() {
    Factorization f =
        assertTimeoutPreemptively(
            BOUND, () -> Factoring.factor(PolynomialTest.p("x^10000 - 1"), Steps.NONE));
    assertThat(f.factors()).hasSize(3);
    Factorization g =
        assertTimeoutPreemptively(
            BOUND, () -> Factoring.factor(PolynomialTest.p("x^10000 + x + 1"), Steps.NONE));
    assertThat(g.complete()).isFalse();
  }

  @Test
  void cancelsAndDecomposesAtTheDegreeBound() {
    Expr c =
        assertTimeoutPreemptively(
            BOUND, () -> Algebra.cancel(e("(x^10000 - 1)/(x - 1)"), Context.DEFAULT));
    assertThat(((Sum) c).terms()).hasSize(10_000);
    Expr a =
        assertTimeoutPreemptively(
            BOUND,
            () -> Algebra.apart(e("1/(x^10000 - 1)"), e("x"), Context.DEFAULT).orElseThrow());
    assertThat(((Sum) a).terms()).hasSize(3);
  }

  /** A polynomial in x of degree n with coefficients in [-99, 99] and a leading one in [1, 99]. */
  private static Polynomial dense(Random r, int n) {
    Polynomial x = Polynomial.variable(PolynomialArbitraries.X);
    Polynomial p = Polynomial.constant(Int.of(1 + r.nextInt(99))).multiply(x.pow(n));
    for (int k = 0; k < n; k++) {
      p = p.add(Polynomial.constant(Int.of(r.nextInt(199) - 99)).multiply(x.pow(k)));
    }
    return p;
  }
}
