package mathcanvas.engine.calculus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
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

  private static Expr parse(String s) {
    return Canonical.of(Parser.parseExpression(s, Names.NONE));
  }

  private static Rewriter.Result run(String body, String from, String to, boolean sum) {
    Expr node =
        sum
            ? BigSum.of(parse(body), K, parse(from), parse(to))
            : BigProduct.of(parse(body), K, parse(from), parse(to));
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
    assertThat(UnicodePrinter.INSTANCE.display(r.expr())).isEqualTo(v);
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
  void whatNoRuleEvaluatesStaysWithItsReason() {
    Rewriter.Result none = stays(run("1/k", "1", "n", true));
    assertThat(none.expr()).isInstanceOf(BigSum.class);
    assertThat(text(none)).isEqualTo("No closed form is known for ∑[k = 1, n] 1/k; it stays");
    assertThat(text(stays(run("1/k", "1", "20000", true))))
        .isEqualTo(
            "∑[k = 1, 20000] 1/k has more than 10,000 terms to add one by one and no closed form"
                + " is known; it stays");
    assertThat(text(stays(run("k", "1", "1/2", true))))
        .isEqualTo(
            "The bounds of ∑[k = 1, 1/2] k are not integers with the upper at least the lower minus"
                + " one; it stays");
    assertThat(stays(run("k", "3", "1", true)).expr()).isInstanceOf(BigSum.class);
    assertThat(stays(run("1/k^3", "1", "inf", true)).expr()).isInstanceOf(BigSum.class);
    assertThat(stays(run("k", "1", "inf", false)).expr()).isInstanceOf(BigProduct.class);
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
