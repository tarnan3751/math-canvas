package mathcanvas.engine.calculus;

import static mathcanvas.engine.calculus.DiffCases.canonical;
import static mathcanvas.engine.calculus.DiffCases.facts;
import static mathcanvas.engine.calculus.DiffCases.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.DefaultNarrator;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.Trace;
import org.junit.jupiter.api.Test;

class CalculusTest {

  private static final Symbol X = Symbol.of("x");
  private static final Context TRACED = Context.DEFAULT.withTrace(true);

  private static Rewriter.Result d(String e, int n, Context ctx) {
    return Calculus.differentiate(parse(e), X, n, ctx);
  }

  private static String shown(String e, int n) {
    return UnicodePrinter.INSTANCE.display(d(e, n, Context.DEFAULT).expr());
  }

  private static String shown(String e) {
    return shown(e, 1);
  }

  @Test
  void thePlanExamples() {
    assertThat(shown("x^2 sin(x)")).isEqualTo("x² cos(x) + 2x sin(x)");
    assertThat(shown("x^x")).isEqualTo("x^x·(log(x) + 1)");
  }

  @Test
  void textbookDerivatives() {
    assertThat(shown("x^3 + 2x + 1")).isEqualTo("3x² + 2");
    assertThat(shown("sin(x)/x")).isEqualTo("(x cos(x) - sin(x))/x²");
    assertThat(shown("sin(x^2)")).isEqualTo("2x cos(x²)");
    assertThat(shown("exp(3x)")).isEqualTo("3 e^(3x)");
    assertThat(shown("ln(x^2 + 1)")).isEqualTo("2x/(x² + 1)");
    assertThat(shown("log10(x)")).isEqualTo("1/(x log(10))");
    assertThat(shown("2^x")).isEqualTo("2^x log(2)");
    assertThat(shown("e^x")).isEqualTo("e^x");
    assertThat(shown("sqrt(x)")).isEqualTo("1/(2√x)");
    assertThat(shown("atan(x)")).isEqualTo("1/(x² + 1)");
    assertThat(shown("a x^2 + y")).isEqualTo("2a x");
  }

  @Test
  void theSimplifierRunsOnceUnderTheAssumptions() {
    Context positive = Context.DEFAULT.withAssumptions(facts("x > 0"));
    assertThat(d("abs(x)", 1, positive).expr()).isEqualTo(canonical("1"));
    assertThat(d("abs(x)", 1, Context.DEFAULT).expr()).isEqualTo(canonical("sign(x)"));
  }

  @Test
  void higherOrders() {
    assertThat(shown("sin(x)", 2)).isEqualTo("-sin(x)");
    assertThat(shown("x^5", 3)).isEqualTo("60x²");
    assertThat(shown("x^2", 3)).isEqualTo("0");
    // A stuck derivative keeps its order, and the orders of a stuck inner derivative combine.
    assertThat(d("factorial(x)", 2, Context.DEFAULT).expr())
        .isEqualTo(Derivative.of(canonical("factorial(x)"), X, 2));
    assertThat(d("x factorial(x)", 2, Context.DEFAULT).expr())
        .isEqualTo(canonical("2 diff(factorial(x), x) + x diff(factorial(x), x, 2)"));
    assertThat(d("diff(factorial(x), x)", 2, Context.DEFAULT).expr())
        .isEqualTo(Derivative.of(canonical("factorial(x)"), X, 3));
  }

  @Test
  void unevaluatedFormsStay() {
    for (String stays :
        List.of(
            "integrate(x t, t)",
            "limit(x/t, t, 1)",
            "product(x k, k, 1, n)",
            "sum(k, k, 1, x)",
            "diff(factorial(x y), y)",
            "solve(t^2 = x, t)",
            "factorial(x)",
            "re(x)",
            "max(x, 1)")) {
      assertThat(d(stays, 1, Context.DEFAULT).expr())
          .as(stays)
          .isEqualTo(Derivative.of(canonical(stays), X, 1));
    }
  }

  @Test
  void otherVariablesAreConstants() {
    assertThat(shown("diff(x^2 y^3, y)")).isEqualTo("6x y²");
    assertThat(shown("sum(x^k, k, 1, n)")).isEqualTo("∑[k = 1, n] k x^(k - 1)");
  }

  @Test
  void theTraceIsTheDerivativeThenTheSimplifier() {
    Rewriter.Result r = d("e^x log(x)", 1, TRACED);
    Trace t = r.trace();
    assertThat(t.initial()).isEqualTo(Derivative.of(canonical("e^x log(x)"), X, 1));
    assertThat(t.result()).isEqualTo(r.expr());
    List<String> sets = t.rewrites().stream().map(Rewrite::ruleSet).distinct().toList();
    assertThat(sets.getFirst()).isEqualTo("diff");
    List<Expr> roots = t.roots();
    for (int i = 0; i < roots.size(); i++) {
      Expr at = roots.get(i);
      for (int k : t.rewrites().get(i).path().indices()) {
        at = Operands.of(at).get(k);
      }
      assertThat(at).isEqualTo(t.rewrites().get(i).after());
    }
    assertThat(Canonical.of(roots.getLast())).isEqualTo(r.expr());
    assertThat(d("x^2", 1, Context.DEFAULT).trace()).isEqualTo(Trace.NONE);
  }

  @Test
  void theTraceIsNarratedFromTheBundle() {
    List<Step> steps = new DefaultNarrator().narrate(d("sin(x^2)", 1, TRACED).trace(), TRACED);
    assertThat(steps)
        .extracting(Step::text)
        .containsExactly(
            "Apply the chain rule to sin(x²): d/dx sin(x²) = cos(x²) d/dx x²",
            "Apply the power rule: d/dx x² = 2x d/dx x",
            "The derivative of x with respect to x is 1");
  }
}
