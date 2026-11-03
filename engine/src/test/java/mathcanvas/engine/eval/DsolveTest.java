package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

/**
 * {@code dsolve} and the prime notation in the language: each class, branches, implicit solutions,
 * initial conditions, systems, the calls that stay, the constants' note and freshness, and the
 * steps, chained from the call to the result.
 */
class DsolveTest {

  @Test
  void linearEquationsGoThroughAnIntegratingFactor() {
    assertThat(shown("dsolve(y'(x) = y(x), y(x))")).isEqualTo("y(x) = C₁ e^x");
    assertThat(shown("dsolve(y'(x) + y(x)/x = x^2, y(x))")).isEqualTo("y(x) = (x⁴ + 4C₁)/(4x)");
    assertThat(shown("dsolve(y'(x) - 2y(x) = e^(2x), y(x))")).isEqualTo("y(x) = (C₁ + x) e^(2x)");
    assertThat(shown("dsolve(y'(x) = cos(x), y(x), y(0) = 1)")).isEqualTo("y(x) = sin(x) + 1");
    assertThat(shown("dsolve(diff(y(x), x) = x y(x), y(x), y(0) = 2)"))
        .isEqualTo("y(x) = 2 e^(x²/2)");
    assertThat(steps("dsolve(y'(x) + y(x)/x = x^2, y(x))"))
        .containsExactly("linear", "integrating-factor", "integrate", "solution");
  }

  @Test
  void separableEquationsGiveTheirBranches() {
    assertThat(shown("dsolve(y'(x) = x/y(x), y(x))"))
        .isEqualTo("{y(x) = √(x² + 2C₁), y(x) = -√(x² + 2C₁)}");
    assertThat(shown("dsolve(y'(x) = x/y(x), y(x), y(0) = 2)")).isEqualTo("y(x) = √(x² + 4)");
    assertThat(shown("dsolve(y'(x) = y(x)^2, y(x))")).isEqualTo("y(x) = -1/(C₁ + x)");
    assertThat(shown("dsolve(y'(x) = y(x)(1 - y(x)), y(x))"))
        .isEqualTo("y(x) = -1/(C₁ e^(-x) - 1)");
    // atan is not inverted by solve: the solution stays implicit
    assertThat(shown("dsolve(y'(x) = 1 + y(x)^2, y(x))")).isEqualTo("-x + atan(y(x)) = C₁");
    assertThat(steps("dsolve(y'(x) = y(x)^2, y(x))"))
        .containsExactly("separate", "integrate", "solution");
  }

  @Test
  void exactAndBernoulliEquations() {
    assertThat(shown("dsolve(2x y(x) + (x^2 + 3y(x)^2) y'(x) = 0, y(x))"))
        .isEqualTo("x² y(x) + y(x)³ = C₁");
    assertThat(shown("dsolve(2x y(x) + (x^2 + 3y(x)^2) y'(x) = 0, y(x), y(1) = 1)"))
        .isEqualTo("x² y(x) + y(x)³ = 2");
    assertThat(steps("dsolve(2x y(x) + (x^2 + 3y(x)^2) y'(x) = 0, y(x))"))
        .containsExactly("exact-test", "potential");
    assertThat(steps("dsolve(y'(x) + y(x) = x y(x)^2, y(x))"))
        .containsExactly("bernoulli", "integrating-factor", "integrate", "solution");
  }

  @Test
  void constantCoefficientsOfAnyOrder() {
    assertThat(shown("dsolve(y''(x) + 2y'(x) + 5y(x) = 0, y(x))"))
        .isEqualTo("y(x) = C₁ e^(-x) cos(2x) + C₂ e^(-x) sin(2x)");
    assertThat(shown("dsolve(y''(x) - 2y'(x) + y(x) = x e^x, y(x))"))
        .isEqualTo("y(x) = x³ e^x/6 + C₂ x e^x + C₁ e^x");
    assertThat(shown("dsolve(y'''(x) - y(x) = 0, y(x))"))
        .isEqualTo("y(x) = C₁ e^x + C₂ e^(-x/2) cos(x √3/2) + C₃ e^(-x/2) sin(x √3/2)");
    // resonance: the trial is multiplied by x
    assertThat(shown("dsolve(y''(x) + 4y(x) = sin(2x), y(x))"))
        .isEqualTo("y(x) = C₁ cos(2x) + C₂ sin(2x) - x cos(2x)/4");
    assertThat(shown("dsolve(y''(x) + y(x) = 0, y(x), y(0) = 1, y'(0) = 0)"))
        .isEqualTo("y(x) = cos(x)");
    // conditions no solution meets
    assertThat(shown("dsolve(y''(x) + y(x) = 0, y(x), y(0) = 0, y(pi) = 1)")).isEqualTo("{}");
    assertThat(steps("dsolve(y''(x) + 4y(x) = sin(2x), y(x))"))
        .containsExactly("characteristic", "basis", "particular");
  }

  @Test
  void linearSystemsThroughTheEigenvalues() {
    assertThat(shown("dsolve([x'(t) = y(t), y'(t) = -x(t)], [x(t), y(t)], x(0) = 1, y(0) = 0)"))
        .isEqualTo("[x(t) = cos(t), y(t) = -sin(t)]");
    // a defective matrix: the generalised eigenvector gives t eᵗ
    assertThat(shown("dsolve([x'(t) = x(t) + y(t), y'(t) = y(t)], [x(t), y(t)])"))
        .isEqualTo("[x(t) = C₂ t e^t + C₁ e^t, y(t) = C₂ e^t]");
    assertThat(
            shown(
                "dsolve([x'(t) = x(t) - 2y(t), y'(t) = 2x(t) + y(t)], [x(t), y(t)], x(0) = 1,"
                    + " y(0) = 0)"))
        .isEqualTo("[x(t) = e^t cos(2t), y(t) = e^t sin(2t)]");
    assertThat(steps("dsolve([x'(t) = y(t), y'(t) = -x(t)], [x(t), y(t)])"))
        .containsExactly("system", "eigen");
  }

  @Test
  void whatNoClassCoversStays() {
    assertThat(shown("dsolve(y'(x) = sin(x y(x)), y(x))"))
        .isEqualTo("dsolve(y′(x) = sin(x y(x)), y(x))");
    assertThat(shown("dsolve(x^2 y''(x) + y(x) = 0, y(x))"))
        .isEqualTo("dsolve(x² y′′(x) + y(x) = 0, y(x))");
    assertThat(steps("dsolve(y'(x) = sin(x y(x)), y(x))")).containsExactly("unsolved");
  }

  @Test
  void theConstantsAreFreshRealAndNoted() {
    Outcome.Value v = (Outcome.Value) Run.last("dsolve(y''(x) = 6x, y(x))");
    assertThat(UnicodePrinter.INSTANCE.display(v.value())).isEqualTo("y(x) = x³ + C₂ x + C₁");
    assertThat(UnicodePrinter.INSTANCE.display(v.parameters())).isEqualTo("{C₁ ∈ ℝ, C₂ ∈ ℝ}");
    // a name the call mentions, or one that is defined, is taken
    assertThat(shown("dsolve(y'(x) = C_1, y(x))")).isEqualTo("y(x) = C₁ x + C₂");
    assertThat(shown("C_1 := 3; dsolve(y'(x) = 1, y(x))")).isEqualTo("y(x) = C₂ + x");
    // after the statement the constant is real
    assertThat(shown("dsolve(y'(x) = 0, y(x)); assumptions()")).isEqualTo("{C₁ ∈ ℝ}");
  }

  @Test
  void anEquilibriumThatSeparationDividedAway() {
    assertThat(shown("dsolve(y'(x) = y(x)(1 - y(x)), y(x), y(0) = 0)")).isEqualTo("y(x) = 0");
    assertThat(shown("dsolve(y'(x) = y(x)^2, y(x), y(0) = 0)")).isEqualTo("y(x) = 0");
    assertThat(shown("dsolve(y'(x) = 2x (y(x) - 1)^2, y(x), y(0) = 1)")).isEqualTo("y(x) = 1");
    // Bernoulli with n > 0: y = 0
    assertThat(shown("dsolve(y'(x) + y(x) = x y(x)^3, y(x), y(0) = 0)")).isEqualTo("y(x) = 0");
  }

  @Test
  void everyConditionIsMet() {
    // an implicit solution through two points with different potentials: none
    assertThat(shown("dsolve(2x y(x) + (x^2 + 3y(x)^2) y'(x) = 0, y(x), y(0) = 1, y(1) = 5)"))
        .isEqualTo("{}");
    assertThat(shown("dsolve(2x y(x) + (x^2 + 3y(x)^2) y'(x) = 0, y(x), y(0) = 1, y(1) = 1)"))
        .isEqualTo("{}");
    assertThat(shown("dsolve(2x y(x) + (x^2 + 3y(x)^2) y'(x) = 0, y(x), y(1) = 1, y(-1) = 1)"))
        .isEqualTo("x² y(x) + y(x)³ = 2");
  }

  @Test
  void decimalConditionsAreMetToThePrecision() {
    assertThat(shown("dsolve(y'(x) = y(x), y(x), y(0.5) = 2)"))
        .isEqualTo("y(x) = 1.213061319425266847207599069982361 e^x");
    assertThat(shown("dsolve(y''(x) + y(x) = 0, y(x), y(0) = 1.5, y'(0) = 0)"))
        .isEqualTo("y(x) = 1.5 cos(x)");
    assertThat(shown("dsolve(y'(x) = 0.5 y(x), y(x), y(0) = 1)")).isEqualTo("y(x) = 1.0 e^(0.5x)");
    // a constant solved to a decimal 0 leaves no term
    assertThat(shown("dsolve(y''(x) + y(x) = 0, y(x), y(0) = 1.5, y'(0) = 0.0)"))
        .isEqualTo("y(x) = 1.5 cos(x)");
    assertThat(shown("dsolve(y''(x) + y(x) = 0, y(x), y(0) = 0.0)")).isEqualTo("y(x) = C₂ sin(x)");
  }

  @Test
  void potentialsTheSimplifierCannotEquateAreComparedNumerically() {
    // r is the real root of r³ + r = 1 by Cardano: the two conditions agree
    assertThat(
            shown(
                "r := cbrt(1/2 + sqrt(31/108)) + cbrt(1/2 - sqrt(31/108));"
                    + " dsolve(2x y(x) + (x^2 + 3y(x)^2) y'(x) = 0, y(x), y(0) = 1, y(1) = r)"))
        .isEqualTo("x² y(x) + y(x)³ = 1");
  }

  @Test
  void bernoulliWithOddPowersHasTwoBranches() {
    assertThat(shown("dsolve(y'(x) + y(x) = x y(x)^3, y(x))"))
        .isEqualTo(
            "{y(x) = e^(-x)/√(C₁ + x e^(-2x) + e^(-2x)/2),"
                + " y(x) = -e^(-x)/√(C₁ + x e^(-2x) + e^(-2x)/2)}");
    assertThat(shown("dsolve(y'(x) + y(x) = x y(x)^3, y(x), y(0) = 1)"))
        .isEqualTo("y(x) = e^(-x)/√(x e^(-2x) + e^(-2x)/2 + 1/2)");
    assertThat(shown("dsolve(y'(x) + y(x) = x y(x)^3, y(x), y(0) = -1)"))
        .isEqualTo("y(x) = -e^(-x)/√(x e^(-2x) + e^(-2x)/2 + 1/2)");
  }

  @Test
  void aConstantTakesTheValueItHasWhereTheCallStands() {
    assertThat(shown("subs(dsolve(y'(x) = y(x), y(x)), C_1 = 0)")).isEqualTo("y(x) = 0");
    assertThat(shown("f(C_1) := dsolve(y'(x) = y(x), y(x)); f(3)")).isEqualTo("y(x) = 3 e^x");
    Outcome.Value v = (Outcome.Value) Run.last("subs(dsolve(y''(x) = 0, y(x)), C_1 = 2)");
    assertThat(UnicodePrinter.INSTANCE.display(v.value())).isEqualTo("y(x) = C₂ x + 2");
    assertThat(UnicodePrinter.INSTANCE.display(v.parameters())).isEqualTo("{C₂ ∈ ℝ}");
  }

  @Test
  void aForcedSystemIsSolvedByVariationOfParameters() {
    assertThat(steps("dsolve([x'(t) = x(t) + 2y(t) + 1, y'(t) = 2x(t) + y(t)], [x(t), y(t)])"))
        .containsExactly("system", "eigen", "variation");
  }

  @Test
  void argumentErrors() {
    assertThat(Run.error("dsolve(y'(x) = 1, 2)"))
        .isEqualTo("dsolve needs an unknown function y(x), or a list of them in one variable");
    assertThat(Run.error("dsolve(y(x), y(x))"))
        .isEqualTo("dsolve needs an equation or a list of equations");
    assertThat(Run.error("dsolve(y'(x) = y(x), y(x), y(x) = 1)"))
        .isEqualTo(
            "an initial condition is y(a) = b or y'(a) = b, with a and b free of the unknowns");
    // inside dsolve the unknown is a function; outside, y(x) is a product again
    assertThat(shown("dsolve(y'(x) = y(x), y(x)); y(x)")).isEqualTo("x y");
  }

  private static List<String> steps(String call) {
    Session s = new Session(Environment.EMPTY);
    Outcome o = s.run(Parser.parseScript("steps(" + call + ")", s.names())).getLast();
    Outcome.Value v = (Outcome.Value) o;
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    ResourceBundle narration =
        ResourceBundle.getBundle("mathcanvas.engine.rule.narration", Locale.ROOT);
    for (Rewrite w : v.trace().rewrites()) {
      assertThat(narration.containsKey(w.ruleSet() + "." + w.rule())).as(w.rule()).isTrue();
    }
    return v.trace().rewrites().stream()
        .filter(r -> r.ruleSet().equals("dsolve"))
        .map(Rewrite::rule)
        .toList();
  }
}
