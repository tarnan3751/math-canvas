package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Sums and products through a session: term by term under the session, then the closed forms. */
class SumEvaluationTest {

  @Test
  void integerBoundsAreEvaluatedTermByTerm() {
    assertThat(Run.shown("sum(k^2, k, 1, 3)")).isEqualTo("14");
    assertThat(Run.shown("Σ[k=1,4] 1/k")).isEqualTo("25/12");
    assertThat(Run.shown("sum(factorial(k), k, 0, 4)")).isEqualTo("34");
    assertThat(Run.shown("sum(x^k, k, 0, 3)")).isEqualTo("x³ + x² + x + 1");
    assertThat(Run.shown("sum(k, k, 1, 0)")).isEqualTo("0");
    assertThat(Run.shown("product(k, k, 1, 5)")).isEqualTo("120");
    assertThat(Run.shown("product(k, k, 3, 2)")).isEqualTo("1");
  }

  @Test
  void eachTermIsEvaluatedUnderTheSession() {
    // the angle mode applies to every term
    assertThat(Run.shown("sum(sin(90.0 k), k, 1, 2)", Run.DEGREES)).isEqualTo("1.0");
    assertThat(Run.shown("sum(sin(90 k), k, 1, 3)", Run.DEGREES)).isEqualTo("0");
    // held commands evaluate inside the terms
    assertThat(Run.shown("sum(taylor(e^x, x, 0, k), k, 0, 2)")).isEqualTo("x²/2 + 2x + 3");
    assertThat(Run.shown("sum(limit(sin(k x)/x, x, 0), k, 1, 4)")).isEqualTo("10");
    assertThat(Run.shown("sum(sum(j, j, 1, k), k, 1, 3)")).isEqualTo("10");
    // a term that is not a number's arithmetic leaves the sum
    assertThat(Run.shown("k := 7; sum((x -> x + k), k, 1, 3)"))
        .isEqualTo("∑[k = 1, 3] (x → k + x)");
  }

  @Test
  void theStatementsBudgetIsAllOrNothing() {
    // 400 × 400 terms are more than the statement takes: nothing is added, the sum stays
    assertThat(Run.value("sum(sum(1/(j + k), j, 1, 400), k, 1, 400)"))
        .isInstanceOf(mathcanvas.engine.expr.BigSum.class);
    assertThat(
            NarrationTest.narrated("steps(sum(sum(1/(j + k), j, 1, 400), k, 1, 400))").getFirst())
        .startsWith("The terms of ∑[k = 1, 400]")
        .contains("are more than the statement takes one by one (100,000)");
    // a nested sum that evaluated to arithmetic is not taken again for each term
    assertThat(
            Run.shown(
                "sum(sum(1/(j k), j, 1, 300), k, 1, 400)"
                    + " - sum(1/j, j, 1, 300) sum(1/k, k, 1, 400)"))
        .isEqualTo("0");
    // a term that is not arithmetic says so
    assertThat(NarrationTest.narrated("k := 7\nsteps(sum((x -> x + k), k, 1, 3))").getFirst())
        .startsWith("A term of ∑[k = 1, 3] (x → k + x) is not a number's arithmetic");
  }

  @Test
  void theStepIsTheTermsAdded() {
    assertThat(NarrationTest.narrated("steps(sum(k^2, k, 1, 3))"))
        .containsExactly("Add the terms: ∑[k = 1, 3] k² = 14 | 14");
  }

  @Test
  void manyTermsStayCheap() {
    long start = System.nanoTime();
    assertThat(Run.value("sum(1/k, k, 1, 10000)").toString()).startsWith("Num ");
    long ms = (System.nanoTime() - start) / 1_000_000;
    // added in pairs: typically under a second locally, against the whole-run budget of 30 s
    // (docs/reference/engine.md, Timing budgets)
    assertThat(ms).as("Σ 1/k to 10,000 took %d ms", ms).isLessThan(30_000);
  }
}
