package mathcanvas.engine.calculus;

import static mathcanvas.engine.testing.Inputs.canonical;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/** Each rule of {@code sum} and {@code product}, with its value and narration, and the notes. */
class SumsTest {

  private static final Symbol K = Symbol.of("k");
  private static final Context CTX = TestNumerics.CONTEXT.withTrace(true);
  private static final Set<String> COVERED = new TreeSet<>();

  private static Rewriter.Result run(String body, String from, String to, boolean sum) {
    Expr node =
        sum
            ? BigSum.of(canonical(body), K, canonical(from), canonical(to))
            : BigProduct.of(canonical(body), K, canonical(from), canonical(to));
    return Calculus.sum(node, CTX);
  }

  private static Rewriter.Result sum(String rule, String body, String from, String to, String v) {
    return check("sum", rule, run(body, from, to, true), v);
  }

  private static Rewriter.Result product(
      String rule, String body, String from, String to, String v) {
    return check("product", rule, run(body, from, to, false), v);
  }

  private static Rewriter.Result check(String set, String rule, Rewriter.Result r, String v) {
    if (v != null) {
      assertThat(UnicodePrinter.INSTANCE.display(r.expr())).isEqualTo(v);
    }
    // after the simplifier's rewrites of the body, the family's, then the simplifier's of the value
    Rewrite first =
        r.trace().rewrites().stream()
            .filter(w -> w.ruleSet().equals(set))
            .findFirst()
            .orElseThrow();
    assertThat(first.ruleSet() + "." + first.rule()).isEqualTo(set + "." + rule);
    mathcanvas.engine.algebra.TraceChecks.assertChain(r);
    COVERED.add(set + "." + rule);
    return r;
  }

  private static String text(Rewriter.Result r) {
    List<Step> steps = mathcanvas.engine.eval.Narration.steps(r.trace(), CTX, StepText.standard());
    return steps.stream()
        .filter(s -> Set.of("sum", "product").contains(s.rewrites().getFirst().ruleSet()))
        .findFirst()
        .orElseThrow()
        .text();
  }

  /** A note's trace: a chain whose terminal record leaves the node, which stays. */
  private static Rewriter.Result stays(Rewriter.Result r) {
    Rewrite last = r.trace().rewrites().getLast();
    assertThat(last.after()).isEqualTo(r.expr());
    mathcanvas.engine.algebra.TraceChecks.assertChain(r);
    return r;
  }

  @Test
  void polynomials() {
    Rewriter.Result r = sum("polynomial", "k", "1", "n", "n·(n + 1)/2");
    assertThat(text(r))
        .isEqualTo(
            "Sum the powers of the index by Faulhaber's formula: ∑[k = 1, n] k = n·(n + 1)/2");
    sum("polynomial", "k^2", "1", "n", "n·(n + 1)(2n + 1)/6");
    sum("polynomial", "k^3", "1", "n", "n² (n + 1)²/4");
    sum("polynomial", "c", "1", "n", "c n");
    sum("polynomial", "k", "a", "b", "-(a + b)(a - b - 1)/2");
    sum("polynomial", "k", "1", "inf", "∞");
    sum("polynomial", "k", "1", "200000", "20000100000");
  }

  @Test
  void geometricSeries() {
    sum("geometric", "2^k", "0", "n", "2^(n + 1) - 1");
    Rewriter.Result r = sum("geometric", "r^k", "0", "n", "(r^(n + 1) - 1)/(r - 1)");
    assertThat(text(r))
        .isEqualTo(
            "Sum the geometric series of ratio r: ∑[k = 0, n] r^k = (r^(n + 1) - 1)/(r - 1),"
                + " for r ≠ 1");
    sum("geometric", "(1/2)^k", "0", "inf", "2");
    sum("geometric", "2^k", "0", "inf", "∞");
    sum("geometric", "(-1)^k", "0", "inf", "undefined");
  }

  @Test
  void arithmeticGeometricSeries() {
    sum("arithmetic-geometric", "k*(1/2)^k", "1", "inf", "2");
    sum("arithmetic-geometric", "k*2^k", "0", "n", "2(n·2^(n + 1) - (n + 1)·2^n + 1)");
  }

  @Test
  void telescopingSums() {
    Rewriter.Result r = sum("telescoping", "1/(k(k+1))", "1", "n", "-1/(n + 1) + 1");
    assertThat(text(r)).startsWith("The terms telescope:");
    sum("telescoping", "1/(k(k+1))", "1", "inf", "1");
    sum("telescoping", "1/(k^2 - 1)", "2", "inf", "3/4");
    sum("telescoping", "log((k+1)/k)", "1", "n", "log(n + 1)");
    sum("telescoping", "log((k+1)/k)", "1", "inf", "∞");
    sum("telescoping", "log(k+1) - log(k)", "1", "n", "log(n + 1)");
    // each term's partial fractions, their polynomial parts cancelling
    sum("telescoping", "k/(k+1) - (k+1)/(k+2)", "1", "inf", "-1/2");
  }

  @Test
  void aLongSummandTelescopesTermByTerm() {
    // Σ_j j/(k + j) − j/(k + j + 1): 101 partial fractions, none merged
    StringBuilder body = new StringBuilder("0");
    for (int j = 1; j <= 100; j++) {
      body.append(" + ").append(j).append("/(k + ").append(j).append(")");
      body.append(" - ").append(j).append("/(k + ").append(j + 1).append(")");
    }
    Rewriter.Result r = check("sum", "telescoping", run(body.toString(), "1", "inf", true), null);
    // Σ_j Σ_k (1/(k + j) − 1/(k + 101)) = Σ_{m=2}^{101} (m − 1)/m
    Expr expected = canonical("0");
    for (int m = 2; m <= 101; m++) {
      expected =
          Canonical.of(mathcanvas.engine.expr.Sum.of(expected, canonical((m - 1) + "/" + m)));
    }
    assertThat(r.expr()).isEqualTo(expected);
  }

  @Test
  void binomialSums() {
    sum("binomial", "binomial(n, k)", "0", "n", "2^n");
    sum("binomial", "binomial(n, k) x^k", "0", "n", "(x + 1)^n");
  }

  @Test
  void pSeries() {
    sum("p-series", "1/k^2", "1", "inf", "π²/6");
    sum("p-series", "1/k^4", "1", "inf", "π⁴/90");
    sum("p-series", "1/k^6", "2", "inf", "π⁶/945 - 1");
    sum("p-series", "1/k", "1", "inf", "∞");
    sum("p-series", "1/sqrt(k)", "1", "inf", "∞");
  }

  @Test
  void products() {
    product("constant", "2", "1", "n", "2^n");
    product("factorial", "k", "1", "n", "n!");
    product("factorial", "2k", "1", "n", "2^n n!");
    product("factorial", "k^2", "1", "n", "(n!)²");
    product("factorial", "k + 1", "1", "n", "(n + 1)!");
    product("geometric", "2^k", "1", "n", "2^(n·(n + 1)/2)");
  }

  @Test
  void whatNoRuleEvaluatesStaysWithItsReason() {
    Rewriter.Result none = stays(run("1/k", "1", "n", true));
    assertThat(none.expr()).isInstanceOf(BigSum.class);
    assertThat(text(none))
        .isEqualTo("No closed form is known for ∑[k = 1, n] 1/k, so it is left as it is");
    assertThat(text(stays(run("1/k", "1", "20000", true))))
        .isEqualTo(
            "∑[k = 1, 20000] 1/k has more than 10,000 terms to add one by one and no closed form"
                + " is known, so it is left as it is");
    assertThat(text(stays(run("k", "1", "1/2", true))))
        .isEqualTo(
            "The bounds of ∑[k = 1, 1/2] k are not integers with the upper at least the lower minus"
                + " one, so it is left as it is");
    assertThat(stays(run("k", "3", "1", true)).expr()).isInstanceOf(BigSum.class);
    assertThat(stays(run("1/k^3", "1", "inf", true)).expr()).isInstanceOf(BigSum.class);
    assertThat(stays(run("k", "1", "inf", false)).expr()).isInstanceOf(BigProduct.class);
  }

  @Test
  void theBodyIsSimplifiedFirst() {
    sum("polynomial", "log(e^k)", "1", "n", "n·(n + 1)/2");
  }

  @Test
  void largeSumsStayCheap() {
    long start = System.nanoTime();
    Rewriter.Result r = run("k^200", "1", "n", true);
    long ms = (System.nanoTime() - start) / 1_000_000;
    assertThat(r.expr()).isNotInstanceOf(BigSum.class);
    // the closed form is expanded above degree 20, not factored; typically about a second, against
    // the whole-run budget (docs/reference/engine.md, Timing budgets)
    assertThat(ms).as("Σ k^200 took %d ms", ms).isLessThan(120_000);
  }

  @Test
  void longSummandsAreNotMerged() {
    long start = System.nanoTime();
    // 65 powers: past the terms Faulhaber's formula merges into one polynomial
    StringBuilder powers = new StringBuilder("1");
    for (int i = 1; i <= 64; i++) {
      powers.append(" + k^").append(i);
    }
    assertThat(stays(run(powers.toString(), "1", "n", true)).expr()).isInstanceOf(BigSum.class);
    // 65 quadratic bases: past the terms merged into one rational function
    StringBuilder quadratic = new StringBuilder("0");
    for (int j = 1; j <= 65; j++) {
      quadratic.append(" + 1/(k^2 + ").append(j).append(")");
    }
    assertThat(stays(run(quadratic.toString(), "1", "n", true)).expr()).isInstanceOf(BigSum.class);
    // 1000 linear bases whose coefficients do not cancel: taken term by term, quickly
    StringBuilder linear = new StringBuilder("0");
    for (int j = 1; j <= 1000; j++) {
      linear.append(" + 1/(k + ").append(j).append(")");
    }
    assertThat(stays(run(linear.toString(), "1", "n", true)).expr()).isInstanceOf(BigSum.class);
    long ms = (System.nanoTime() - start) / 1_000_000;
    // typically well under a second locally, against the whole-run budget
    // (docs/reference/engine.md,
    // Timing budgets)
    assertThat(ms).as("the long summands took %d ms", ms).isLessThan(120_000);
  }

  @AfterAll
  static void everyRuleIsCovered() {
    Set<String> rules = new TreeSet<>();
    for (Rule r : RuleSets.SUM.rules()) {
      rules.add("sum." + r.name());
    }
    for (Rule r : RuleSets.PRODUCT.rules()) {
      rules.add("product." + r.name());
    }
    assertThat(COVERED).containsAll(rules);
  }
}
