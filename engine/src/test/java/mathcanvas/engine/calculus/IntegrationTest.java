package mathcanvas.engine.calculus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

class IntegrationTest {

  private static final Context TRACED = Context.DEFAULT.withTrace(true);

  private static Rewriter.Result integrate(String input, Context ctx) {
    return IntegrateCases.integrate(input, ctx);
  }

  private static Expr value(String input) {
    return integrate(input, Context.DEFAULT).expr();
  }

  private static Expr simplified(String text) {
    return IntegrateCases.simplified(text, Context.DEFAULT);
  }

  private static Context facts(String... relations) {
    Assumptions a = Assumptions.EMPTY;
    for (String r : relations) {
      a = a.with((Relation) IntegrateCases.parse(r));
    }
    return Context.DEFAULT.withAssumptions(a);
  }

  @Test
  void theIntegrandIsSimplifiedFirst() {
    Rewriter.Result r = integrate("integrate(2 sin(x) cos(x), x)", TRACED);
    assertThat(r.expr()).isEqualTo(simplified("-cos(2x)/2"));
    List<String> sets = r.trace().rewrites().stream().map(Rewrite::ruleSet).distinct().toList();
    assertThat(sets.getFirst()).isEqualTo("trig");
    assertThat(sets).contains(Calculus.INTEGRATE);
    TraceChecks.assertChain(r);
  }

  @Test
  void noAntiderivativeLeavesTheIntegralWithTheStepNone() {
    for (String stays :
        List.of(
            "integrate(e^(x^2), x)",
            "integrate(sin(x)/x, x)",
            "integrate(factorial(x), x)",
            "integrate(x^x, x)",
            "integrate(x atan(x), x)")) {
      Rewriter.Result r = integrate(stays, TRACED);
      assertThat(r.expr()).as(stays).isInstanceOf(Integral.class);
      assertThat(r.trace().rewrites()).extracting(Rewrite::rule).as(stays).containsExactly("none");
      TraceChecks.assertChain(new Rewriter.Result(r.expr(), r.trace()));
    }
  }

  @Test
  void anIndefiniteIntegralKeepsTheFactsOfItsVariable() {
    assertThat(integrate("integrate(1/x, x)", facts("x ∈ ℂ")).expr())
        .isEqualTo(IntegrateCases.simplified("log(x)", facts("x ∈ ℂ")));
    assertThat(integrate("integrate(1/x, x)", facts("x > 0")).expr())
        .isEqualTo(IntegrateCases.simplified("log(x)", facts("x > 0")));
  }

  @Test
  void aNonScalarIntegrandStays() {
    for (String stays :
        List.of(
            "integrate(ℝ, x)", "integrate(true, x)", "integrate([x, 1], x)", "integrate(inf, x)")) {
      Rewriter.Result r = integrate(stays, TRACED);
      assertThat(r.expr()).as(stays).isInstanceOf(Integral.class);
      assertThat(r.trace().rewrites()).extracting(Rewrite::rule).as(stays).containsExactly("none");
      TraceChecks.assertChain(new Rewriter.Result(r.expr(), r.trace()));
    }
  }

  @Test
  void anIntegralFoundInPartOnlyStaysWhole() {
    Expr node = Canonical.of(IntegrateCases.parse("integrate(x + factorial(x), x)"));
    assertThat(value("integrate(x + factorial(x), x)")).isEqualTo(node);
  }

  @Test
  void aLogarithmIsOfAnAbsoluteValueForARealArgument() {
    assertThat(value("integrate(1/(x + a), x)")).isEqualTo(simplified("log(abs(x + a))"));
    Context complex = facts("a ∈ ℂ");
    assertThat(integrate("integrate(1/(x + a), x)", complex).expr())
        .isEqualTo(IntegrateCases.simplified("log(x + a)", complex));
    assertThat(integrate("integrate(1/x, x)", facts("y > 0")).expr())
        .isEqualTo(simplified("log(abs(x))"));
  }

  @Test
  void aConstantMayNotBeDividedByUnlessItIsKnownNotZero() {
    assertThat(value("integrate(e^(a x), x)")).isInstanceOf(Integral.class);
    assertThat(value("integrate(x e^(a x^2), x)")).isInstanceOf(Integral.class);
    assertThat(integrate("integrate(e^(a x), x)", facts("a > 0")).expr())
        .isEqualTo(simplified("e^(a x)/a"));
    assertThat(value("integrate(x^n, x)")).isInstanceOf(Integral.class);
  }
}
