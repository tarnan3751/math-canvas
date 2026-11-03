package mathcanvas.engine.calculus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.eval.Narration;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Numerics;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;
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
            "integrate(1/(x^4 + 1), x)")) {
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
  void aBoundMayHoldTheVariableOfTheOuterScope() {
    assertThat(value("integrate(x^2, x, 0, x + 1)")).isEqualTo(simplified("(x + 1)^3/3"));
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
  void definiteIntegralsThroughAnAntiderivative() {
    assertThat(value("integrate(log(x), x, 1, e)")).isEqualTo(simplified("1"));
    assertThat(value("integrate(x e^x, x, 0, 1)")).isEqualTo(simplified("1"));
    assertThat(value("integrate(1/(1 + x^2), x, 0, 1)")).isEqualTo(simplified("pi/4"));
    assertThat(value("integrate(1/x, x, 1, 2)")).isEqualTo(simplified("log(2)"));
    assertThat(value("integrate(1/x, x, -2, -1)")).isEqualTo(simplified("-log(2)"));
    // a definite integral drops the facts of its variable, which is bound
    assertThat(integrate("integrate(1/x, x, -2, -1)", facts("x > 0")).expr())
        .isEqualTo(simplified("-log(2)"));
    assertThat(value("integrate(sqrt(x), x, 0, 1)")).isEqualTo(simplified("2/3"));
    assertThat(value("integrate(x, x, 0, t)")).isEqualTo(simplified("t^2/2"));
    assertThat(value("integrate(x^2, x, 1, 0)")).isEqualTo(simplified("-1/3"));
  }

  @Test
  void aDefiniteIntegralAcrossASingularityStays() {
    for (String stays :
        List.of(
            "integrate(1/x, x, 1, t)",
            "integrate(log(x), x, -1, 1)",
            "integrate(tan(x), x, 0, 2)")) {
      assertThat(value(stays)).as(stays).isInstanceOf(Integral.class);
    }
    assertThat(integrate("integrate(1/x, x, 1, t)", facts("t > 1")).expr())
        .isEqualTo(IntegrateCases.simplified("log(t)", facts("t > 1")));
  }

  @Test
  void anImproperIntegralIsTheLimitsOfItsAntiderivative() {
    for (String[] c :
        List.of(
            new String[] {"integrate(1/x^2, x, 1, inf)", "1"},
            new String[] {"integrate(e^(-x), x, 0, inf)", "1"},
            new String[] {"integrate(x^2 e^(-x), x, 0, inf)", "2"},
            new String[] {"integrate(1/sqrt(x), x, 0, 1)", "2"},
            new String[] {"integrate(log(x), x, 0, 1)", "-1"},
            new String[] {"integrate(1/(x^2 + 4x + 5), x, -inf, inf)", "pi"},
            new String[] {"integrate(e^x, x, -inf, 0)", "1"},
            // the limit at ∞ of log|x| − log|x + 1|, of asec(x)
            new String[] {"integrate(1/(x (x + 1)), x, 1, inf)", "log(2)"},
            new String[] {"integrate(1/(x sqrt(x^2 - 1)), x, 2, inf)", "pi/6"})) {
      Rewriter.Result r = integrate(c[0], TRACED);
      assertThat(r.expr()).as(c[0]).isEqualTo(IntegrateCases.simplified(c[1], Context.DEFAULT));
      assertThat(r.trace().rewrites()).extracting(Rewrite::rule).as(c[0]).contains("limits");
      TraceChecks.assertChain(new Rewriter.Result(r.expr(), r.trace()));
    }
    assertThat(integrate("integrate(e^(-a x), x, 0, inf)", facts("a > 0")).expr())
        .isEqualTo(IntegrateCases.simplified("1/a", facts("a > 0")));
  }

  @Test
  void theSecantSubstitutionNeedsTheSideOfTheRoot() {
    assertThat(value("integrate(sqrt(x^2 - 1), x)")).isInstanceOf(Integral.class);
    assertThat(integrate("integrate(sqrt(x^2 - 1), x)", facts("x > 1")).expr())
        .isEqualTo(
            IntegrateCases.simplified(
                "x sqrt(x^2 - 1)/2 - log(abs(x + sqrt(x^2 - 1)))/2", facts("x > 1")));
    assertThat(integrate("integrate(1/(x sqrt(x^2 - 4)), x)", facts("x < -2")).expr())
        .isEqualTo(IntegrateCases.simplified("-asec(x/2)/2", facts("x < -2")));
    // a definite integral takes the side from its bounds
    assertThat(value("integrate(sqrt(x^2 - 1), x, 1, 2)"))
        .isEqualTo(IntegrateCases.simplified("sqrt(3) - log(2 + sqrt(3))/2", Context.DEFAULT));
    assertThat(value("integrate(1/(x sqrt(x^2 - 1)), x, -2, -1)"))
        .isEqualTo(IntegrateCases.simplified("-pi/3", Context.DEFAULT));
  }

  @Test
  void aDivergentIntegralIsUndefined() {
    // each end converges on its own, or the integral diverges: no principal value
    for (String diverges :
        List.of(
            "integrate(1/x, x, 1, inf)",
            "integrate(1/x, x, 0, 1)",
            "integrate(x, x, 0, inf)",
            "integrate(x, x, -inf, inf)",
            "integrate(sin(x), x, 0, inf)",
            // a pole of a rational integrand in the interval is never integrable
            "integrate(1/x^2, x, -1, 1)",
            "integrate(1/x, x, -1, 1)",
            "integrate(1/(x^2 - 4), x, 0, 3)",
            "integrate(x/(x - 1)^2, x, 0, 3)")) {
      Rewriter.Result r = integrate(diverges, TRACED);
      assertThat(r.expr()).as(diverges).isEqualTo(Constant.UNDEFINED);
      assertThat(r.trace().rewrites())
          .extracting(Rewrite::rule)
          .as(diverges)
          .containsAnyOf("diverges", "pole");
      TraceChecks.assertChain(new Rewriter.Result(r.expr(), r.trace()));
    }
  }

  @Test
  void aRefusedDefiniteIntegralSaysWhy() {
    for (String[] c :
        List.of(
            new String[] {"integrate(tan(x), x, 0, 2)", "singular"},
            new String[] {"integrate(1/x, x, 1, t)", "singular"},
            new String[] {"integrate(1, x, 0, sqrt(y))", "bound"},
            new String[] {"integrate(cos(x) e^(sin(x)), x, 0, inf)", "improper"},
            new String[] {"integrate(cos(log(x))/x, x, 1, inf)", "improper"},
            new String[] {"integrate(e^(x^2), x, 0, 1)", "none"})) {
      Rewriter.Result r = integrate(c[0], TRACED);
      assertThat(r.expr()).as(c[0]).isInstanceOf(Integral.class);
      assertThat(r.trace().rewrites()).extracting(Rewrite::rule).as(c[0]).containsExactly(c[1]);
      TraceChecks.assertChain(new Rewriter.Result(r.expr(), r.trace()));
    }
  }

  /** Values of none, in degrees. */
  private static final Numerics DEGREES =
      new Numerics() {
        @Override
        public Optional<Numeric> value(Expr e, java.math.MathContext mc) {
          return Optional.empty();
        }

        @Override
        public int halfTurn() {
          return 180;
        }
      };

  @Test
  void inDegreesADefiniteIntegralWithACircularFunctionStays() {
    Context degrees = TRACED.withNumerics(DEGREES);
    for (String stays : List.of("integrate(sin(x), x, 0, pi)", "integrate(1/(1 + x^2), x, 0, 1)")) {
      Rewriter.Result r = integrate(stays, degrees);
      assertThat(r.expr()).as(stays).isInstanceOf(Integral.class);
      assertThat(r.trace().rewrites()).extracting(Rewrite::rule).containsExactly("angle-mode");
      TraceChecks.assertChain(new Rewriter.Result(r.expr(), r.trace()));
    }
    assertThat(integrate("integrate(x^2, x, 0, 1)", degrees).expr()).isEqualTo(simplified("1/3"));
    assertThat(integrate("integrate(sin(x), x)", degrees).expr()).isEqualTo(simplified("-cos(x)"));
  }

  @Test
  void substitutionsNestOnlyToTheDepth() {
    Symbol x = Symbol.of("x");
    Expr f = Canonical.of(IntegrateCases.parse("2x cos(x^2)"));
    assertThat(IntegrateRules.attempt(f, x, 0, Context.DEFAULT)).isEmpty();
    assertThat(IntegrateRules.attempt(f, x, 1, Context.DEFAULT)).isPresent();
  }

  @Test
  void theStepsNameEachTechnique() {
    assertThat(texts("integrate(x^2 + 3 sin(x), x)"))
        .containsExactly(
            "Integrate term by term",
            "The integral of sin is -cos: ∫ sin(x) dx = -cos(x)",
            "Apply the power rule: ∫ x² dx = x³/3");
    assertThat(texts("integrate(x e^x, x)"))
        .containsExactly(
            "Integrate by parts with u = x and dv = e^x dx, then integrate ∫ e^x dx: ∫ x e^x dx ="
                + " x e^x - e^x");
    assertThat(texts("integrate(x cos(x^2), x)"))
        .containsExactly(
            "Substitute u = x²: the integral becomes ∫ cos(u)/2 du = sin(u)/2, so ∫ x cos(x²)"
                + " dx = sin(x²)/2");
    assertThat(texts("integrate(x^2, x, 0, 1)"))
        .containsExactly(
            "An antiderivative is x³/3; evaluate it at the bounds: ∫[0, 1] x² dx = 1/3");
    assertThat(texts("integrate(e^(x^2), x)"))
        .containsExactly(
            "No antiderivative found by the table, linearity, the trigonometric identities,"
                + " completing the square, substitution or integration by parts: ∫ e^(x²) dx"
                + " stays");
  }

  private static List<String> texts(String input) {
    Rewriter.Result r = integrate(input, TRACED);
    return Narration.steps(r.trace(), TRACED, StepText.standard()).stream()
        .map(Step::text)
        .toList();
  }

  @Test
  void theTraceReplaysToTheResult() {
    for (String input :
        List.of(
            "integrate(x^2 + 3 sin(x), x)",
            "integrate(x^3 e^x, x)",
            "integrate(sin(x)^4, x)",
            "integrate(1/(x^2 + 2x + 5), x)",
            "integrate(x, x, 0, 2)")) {
      Rewriter.Result r = integrate(input, TRACED);
      TraceChecks.assertChain(r);
      assertThat(r.expr()).isEqualTo(Simplifier.simplify(r.expr(), Context.DEFAULT).expr());
    }
  }
}
