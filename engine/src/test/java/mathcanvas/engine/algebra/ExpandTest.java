package mathcanvas.engine.algebra;

import static mathcanvas.engine.testing.Inputs.canonical;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.DefaultNarrator;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Step;
import org.junit.jupiter.api.Test;

class ExpandTest {

  private static Expr expand(String s) {
    return Algebra.expand(canonical(s), Context.DEFAULT).expr();
  }

  @Test
  void textbookCases() {
    assertThat(expand("(x + 1)^3")).isEqualTo(canonical("x^3 + 3x^2 + 3x + 1"));
    assertThat(expand("(x + 1)(x - 1)")).isEqualTo(canonical("x^2 - 1"));
    assertThat(expand("(a + b)^2 (a - b)")).isEqualTo(canonical("a^3 + a^2 b - a b^2 - b^3"));
    assertThat(expand("2(x + 3)")).isEqualTo(canonical("2x + 6"));
    assertThat(expand("(x + y + z)^2"))
        .isEqualTo(canonical("x^2 + y^2 + z^2 + 2x y + 2x z + 2y z"));
    assertThat(expand("(x/2 + 1/3)^2")).isEqualTo(canonical("x^2/4 + x/3 + 1/9"));
    assertThat(expand("x + 1")).isEqualTo(canonical("x + 1"));
  }

  @Test
  void treatsOtherSubexpressionsAsKernels() {
    assertThat(expand("(sin(x) + 1)^2")).isEqualTo(canonical("sin(x)^2 + 2 sin(x) + 1"));
    assertThat(expand("(√x + 1)^2")).isEqualTo(canonical("x + 2√x + 1"));
    assertThat(expand("(x + 1)/x")).isEqualTo(canonical("1 + 1/x"));
    assertThat(expand("(x + 1)^2/(x - 1)"))
        .isEqualTo(canonical("x^2/(x - 1) + 2x/(x - 1) + 1/(x - 1)"));
    assertThat(expand("(e^x + 1)^2")).isEqualTo(canonical("e^(2x) + 2e^x + 1"));
  }

  @Test
  void expandsInsideFunctionArguments() {
    assertThat(expand("sin((x + 1)^2)")).isEqualTo(canonical("sin(x^2 + 2x + 1)"));
    assertThat(expand("(x + 1)^2 = 0")).isEqualTo(canonical("x^2 + 2x + 1 = 0"));
  }

  @Test
  void leavesWhatItCannotExpand() {
    assertThat(expand("(x + y)^100000")).isEqualTo(canonical("(x + y)^100000"));
    assertThat(expand("(x + 1)^(1/2)")).isEqualTo(canonical("(x + 1)^(1/2)"));
    assertThat(expand("(x + 1)^-2")).isEqualTo(canonical("(x + 1)^-2"));
    Expr m = canonical("[[1, 2], [3, 4]] (x + 1)");
    assertThat(expand("[[1, 2], [3, 4]] (x + 1)")).isEqualTo(m);
  }

  @Test
  void expandsLargePowers() {
    Expr r = expand("(x + y + z)^20");
    assertThat(((Sum) r).terms()).hasSize(231);
    assertThat(Polynomial.of(r)).isEqualTo(Polynomial.of(canonical("x + y + z")).pow(20));
  }

  @Test
  void recordsANamedStepPerNode() {
    Rewriter.Result r =
        Algebra.expand(canonical("(x + 1)^2 (x - 1)"), Context.DEFAULT.withTrace(true));
    List<String> rules = r.trace().rewrites().stream().map(Rewrite::rule).toList();
    assertThat(rules).containsExactly("binomial-theorem", "distribute");
    assertThat(r.trace().rewrites()).allMatch(w -> w.ruleSet().equals("expand"));
    TraceChecks.assertChain(r);
    List<Step> steps = new DefaultNarrator().narrate(r.trace(), Context.DEFAULT);
    assertThat(steps)
        .extracting(Step::text)
        .containsExactly(
            "Expand (x + 1)² by the binomial theorem",
            "Distribute (x - 1)(x² + 2x + 1) over its sums");
  }

  @Test
  void leavesWhatIsNotAScalar() {
    assertThat(expand("∞ (x + 1)")).isEqualTo(canonical("∞ (x + 1)"));
    assertThat(expand("(x + ∞)^2")).isEqualTo(canonical("(x + ∞)^2"));
    assertThat(expand("undefined (x + 1)")).isEqualTo(canonical("undefined (x + 1)"));
    assertThat(expand("{x + 1} (x + 1)")).isEqualTo(canonical("{x + 1} (x + 1)"));
    assertThat(expand("[x + 1] = [(x + 1)^2]")).isEqualTo(canonical("[x + 1] = [x^2 + 2x + 1]"));
  }

  @Test
  void leavesAnExpansionPastTheTotalSize() {
    assertThat(expand("(x + 1)^99999")).isEqualTo(canonical("(x + 1)^99999"));
    assertThat(expand("(x + y)^99999")).isEqualTo(canonical("(x + y)^99999"));
  }

  @Test
  void anUnfoldedNumericPowerIsAKernel() {
    assertThat(expand("3^100000000 (x + 1)")).isEqualTo(canonical("3^100000000 x + 3^100000000"));
  }

  @Test
  void collectsOnlyTheExponentsThatOccur() {
    assertThat(
            Algebra.collect(
                canonical("x^1073741823 + a x^1073741823 + 1"), canonical("x"), Context.DEFAULT))
        .isEqualTo(canonical("(a + 1) x^1073741823 + 1"));
  }

  @Test
  void collectsByPowers() {
    Context ctx = Context.DEFAULT;
    assertThat(Algebra.collect(canonical("a x + b x + x^2 + c"), canonical("x"), ctx))
        .isEqualTo(canonical("x^2 + (a + b) x + c"));
    assertThat(Algebra.collect(canonical("(x + y)^2"), canonical("x"), ctx))
        .isEqualTo(canonical("x^2 + 2y x + y^2"));
    assertThat(Algebra.collect(canonical("a sin(x)^2 + sin(x)^2 + 3"), canonical("sin(x)"), ctx))
        .isEqualTo(canonical("(a + 1) sin(x)^2 + 3"));
    assertThat(Algebra.collect(canonical("a + b"), canonical("x"), ctx))
        .isEqualTo(canonical("a + b"));
    assertThat(Algebra.collect(canonical("x + 1/x"), canonical("x"), ctx))
        .isEqualTo(canonical("x + 1/x"));
  }
}
