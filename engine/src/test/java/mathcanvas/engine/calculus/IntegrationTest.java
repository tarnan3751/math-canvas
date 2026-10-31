package mathcanvas.engine.calculus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.simplify.Simplifier;
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
  void textbookIntegrals() {
    assertThat(value("integrate(x^3 + 2x + 1, x)")).isEqualTo(simplified("x^4/4 + x^2 + x"));
    assertThat(value("integrate(x^2 e^x, x)")).isEqualTo(simplified("x^2 e^x - 2(x e^x - e^x)"));
    assertThat(value("integrate(atan(x), x)")).isEqualTo(simplified("x atan(x) - log(x^2 + 1)/2"));
    assertThat(value("integrate(e^x/(1 + e^(2x)), x)")).isEqualTo(simplified("atan(e^x)"));
    assertThat(value("integrate(log(x)/x, x)")).isEqualTo(simplified("log(x)^2/2"));
    // log(x) is built of real parts, so the real form wins on the real domain
    assertThat(value("integrate(1/(x log(x)), x)")).isEqualTo(simplified("log(abs(log(x)))"));
    assertThat(value("integrate(1/(sqrt(x) (sqrt(x) - 1)), x)"))
        .isEqualTo(simplified("2 log(abs(sqrt(x) - 1))"));
    assertThat(value("integrate(sin(x)^2, x)")).isEqualTo(simplified("(2x - sin(2x))/4"));
    assertThat(value("integrate((3x + 2)^10, x)")).isEqualTo(simplified("(3x + 2)^11/33"));
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
  void aComplexInnerFunctionIsSubstitutedByAComplexVariable() {
    assertThat(value("integrate(2x/(x^2 + i), x)")).isEqualTo(simplified("log(x^2 + i)"));
    // up to a constant, which the final pass drops: the derivative is the integrand
    Expr linear = value("integrate(x/(x + i), x)");
    assertThat(linear.toString()).doesNotContain("abs");
    Expr back = Calculus.differentiate(linear, Symbol.of("x"), 1, Context.DEFAULT).expr();
    assertThat(
            Simplifier.simplify(
                    Canonical.of(
                        mathcanvas.engine.expr.Sum.of(
                            back,
                            mathcanvas.engine.expr.Product.of(
                                mathcanvas.engine.expr.Num.MINUS_ONE,
                                IntegrateCases.parseCanonical("x/(x + i)")))),
                    Context.DEFAULT)
                .expr())
        .isEqualTo(mathcanvas.engine.expr.Num.ZERO);
    assertThat(value("integrate(cos(x)/(sin(x) + i), x)")).isEqualTo(simplified("log(sin(x) + i)"));
    assertThat(value("integrate((2x + i)/(x^2 + i x + 1), x)"))
        .isEqualTo(simplified("log(x^2 + i x + 1)"));
    Context complex = facts("a ∈ ℂ");
    assertThat(integrate("integrate(2x/(x^2 + a), x)", complex).expr())
        .isEqualTo(IntegrateCases.simplified("log(x^2 + a)", complex));
    assertThat(value("integrate(2x/(x^2 + 1), x)")).isEqualTo(simplified("log(x^2 + 1)"));
    // a constant with no symbol in it that is not real goes the complex way too
    assertThat(value("integrate(2x/(x^2 + asin(2)), x)"))
        .isEqualTo(simplified("log(x^2 + asin(2))"));
    assertThat(value("integrate(2x/(x^2 + (-1)^(1/3)), x)"))
        .isEqualTo(simplified("log(x^2 + (-1)^(1/3))"));
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

  @Test
  void substitutionsNestOnlyToTheDepth() {
    Symbol x = Symbol.of("x");
    Expr f = Canonical.of(IntegrateCases.parse("2x cos(x^2)"));
    assertThat(IntegrateRules.attempt(f, x, 0, Context.DEFAULT)).isEmpty();
    assertThat(IntegrateRules.attempt(f, x, 1, Context.DEFAULT)).isPresent();
  }
}
