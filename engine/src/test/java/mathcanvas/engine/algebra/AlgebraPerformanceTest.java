package mathcanvas.engine.algebra;

import static mathcanvas.engine.algebra.PolynomialTest.e;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.rule.Context;
import org.junit.jupiter.api.Test;

/**
 * The algebra's budgets: each case finishes in well under a second on the development machine. The
 * bound asserted is generous so that slower CI runners pass.
 */
class AlgebraPerformanceTest {

  private static final Duration BOUND = Duration.ofSeconds(5);

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
  void collectsAHugeExponent() {
    Expr c =
        assertTimeoutPreemptively(
            BOUND, () -> Algebra.collect(e("x^1073741823 + 2"), e("x"), Context.DEFAULT));
    assertThat(c).isEqualTo(e("x^1073741823 + 2"));
  }
}
