package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.last;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.Trace;
import org.junit.jupiter.api.Test;

class NarrationTest {

  /** The steps of a script's last statement, each its text then its after, checked to replay. */
  static List<String> narrated(String script) {
    Outcome.Value v = (Outcome.Value) last(script);
    List<Step> steps = Narration.steps(new Evaluator.Result(v.value(), v.trace()), Context.DEFAULT);
    replays(v.trace(), steps);
    List<String> out = new ArrayList<>();
    for (Step s : steps) {
      out.add(s.text() + " | " + UnicodePrinter.INSTANCE.display(s.after()));
    }
    return out;
  }

  /**
   * The steps stand for every rewrite of the trace, in order, each run contiguous; each step starts
   * where the one before it ended, the first at the trace's start; and each step's after is the
   * trace replayed to the boundary after its last rewrite, the normalisations there applied, so the
   * last one's is the result.
   */
  static void replays(Trace trace, List<Step> steps) {
    List<Rewrite> all = new ArrayList<>();
    for (Step s : steps) {
      assertThat(s.rewrites()).isNotEmpty();
      all.addAll(s.rewrites());
    }
    assertThat(all).hasSize(trace.rewrites().size());
    for (int k = 0; k < all.size(); k++) {
      assertThat(all.get(k)).isSameAs(trace.rewrites().get(k));
    }
    List<Expr> boundaries = boundaries(trace);
    int end = 0;
    for (int k = 0; k < steps.size(); k++) {
      end += steps.get(k).rewrites().size();
      assertThat(steps.get(k).after()).isEqualTo(Canonical.of(boundaries.get(end)));
      Expr before = k == 0 ? Canonical.of(boundaries.get(0)) : steps.get(k - 1).after();
      assertThat(steps.get(k).before()).isEqualTo(before);
    }
    if (!steps.isEmpty()) {
      assertThat(steps.getLast().after()).isEqualTo(Canonical.of(trace.result()));
    }
  }

  /**
   * The whole expression at each boundary of a trace: element {@code k} after the first {@code k}
   * rewrites and the normalisations that follow them.
   */
  private static List<Expr> boundaries(Trace trace) {
    List<Expr> out = new ArrayList<>();
    Expr current = trace.initial();
    int next = 0;
    for (int k = 0; k <= trace.rewrites().size(); k++) {
      if (k > 0) {
        Rewrite r = trace.rewrites().get(k - 1);
        current = splice(current, r.path().indices(), r.after());
      }
      while (next < trace.normalisations().size()
          && trace.normalisations().get(next).afterRewrites() == k) {
        Trace.Normalisation n = trace.normalisations().get(next++);
        current = splice(current, n.path().indices(), n.node());
      }
      out.add(current);
    }
    return out;
  }

  private static Expr splice(Expr e, List<Integer> path, Expr node) {
    if (path.isEmpty()) {
      return node;
    }
    List<Expr> ops = new ArrayList<>(Operands.of(e));
    ops.set(path.getFirst(), splice(ops.get(path.getFirst()), path.subList(1, path.size()), node));
    return Operands.rebuild(e, ops);
  }

  @Test
  void simplifyFoldsAnIdeaIntoOneStep() {
    assertThat(narrated("steps(sqrt(8))"))
        .containsExactly("Write the radicand as a power of a smaller base: √8 = 2√2 | 2√2");
    assertThat(narrated("assume(x > 0); assume(y > 0); steps(log(x^2 y))"))
        .containsExactly(
            "x² > 0, so the logarithm of the product splits: log(x² y) = 2 log(x) + log(y)"
                + " | 2 log(x) + log(y)");
  }

  @Test
  void simplifyJoinsOneRuleOnSeparateSubexpressions() {
    assertThat(narrated("steps(sqrt(12) + sqrt(18))"))
        .containsExactly(
            "Take the perfect power out of the radicand: √12 = 2√3; likewise √18 = 3√2"
                + " | 3√2 + 2√3");
    assertThat(narrated("steps(sin(30°))"))
        .containsExactly("Reduce the angle and take the exact value: sin(π/6) = 1/2 | 1/2");
  }

  @Test
  void expandMultipliesOutAProductInOneStep() {
    assertThat(narrated("steps(expand((x + 1)^3))"))
        .containsExactly("Expand (x + 1)³ by the binomial theorem | x³ + 3x² + 3x + 1");
    assertThat(narrated("steps(expand((x + 1)(x + 2)(x + 3)))"))
        .containsExactly("Distribute (x + 1)(x + 2)(x + 3) over its sums | x³ + 6x² + 11x + 6");
    assertThat(narrated("steps(expand((x + 1)((x + 2)(x + 3) + 1)))"))
        .containsExactly("Multiply out (x + 1)((x + 2)(x + 3) + 1) | x³ + 6x² + 12x + 7");
    assertThat(narrated("steps(expand((x + 1)(x + 2) + (y + 1)(y + 2)))"))
        .containsExactly(
            "Distribute (x + 1)(x + 2) over its sums | x² + 3x + (y + 1)(y + 2) + 2",
            "Distribute (y + 1)(y + 2) over its sums | x² + y² + 3x + 3y + 4");
    assertThat(narrated("steps(expand((x + 1)^2 (x - 1)))"))
        .containsExactly(
            "Expand (x + 1)² by the binomial theorem | (x - 1)(x² + 2x + 1)",
            "Distribute (x - 1)(x² + 2x + 1) over its sums | x³ + x² - x - 1");
  }

  @Test
  void factorMergesTheRationalRoots() {
    assertThat(narrated("steps(factor(x^4 - 1))"))
        .containsExactly(
            "1, -1 are rational roots, so x⁴ - 1 = (x - 1)(x + 1)(x² + 1)"
                + " | (x - 1)(x + 1)(x² + 1)");
    assertThat(narrated("steps(factor(2x^2 - 2))"))
        .containsExactly(
            "Take the common factor 2 out of 2x² - 2 | 2(x² - 1)",
            "1, -1 are rational roots, so 2(x² - 1) = 2(x - 1)(x + 1) | 2(x - 1)(x + 1)");
    // the linear quotient the search leaves is a root too
    assertThat(narrated("steps(factor(x^3 - 6x^2 + 11x - 6))"))
        .containsExactly(
            "1, 2, 3 are rational roots, so x³ - 6x² + 11x - 6 = (x - 3)(x - 2)(x - 1)"
                + " | (x - 3)(x - 2)(x - 1)");
  }

  @Test
  void differentiationNamesEachRule() {
    assertThat(narrated("steps(diff(x^2 sin(x), x))"))
        .containsExactly(
            "Apply the product rule: d/dx x² sin(x) = x² d/dx sin(x) + sin(x) d/dx x²"
                + " | x² d/dx sin(x) + sin(x) d/dx x²",
            "The derivative of sin is cos: d/dx sin(x) = cos(x) | x² cos(x) + sin(x) d/dx x²",
            "Apply the power rule: d/dx x² = 2x | x² cos(x) + 2x sin(x)");
    assertThat(narrated("steps(diff(3x^2 + 2x + 1, x))"))
        .containsExactly(
            "Differentiate term by term; constants fall away | 3 d/dx x² + 2",
            "Apply the power rule: d/dx x² = 2x | 6x + 2");
    // no constant term: the linearity text does not mention constants
    assertThat(narrated("steps(diff(x^2 + 3x, x))"))
        .containsExactly(
            "Differentiate term by term | d/dx x² + 3",
            "Apply the power rule: d/dx x² = 2x | 2x + 3");
    assertThat(narrated("steps(diff(5, x))"))
        .containsExactly("5 does not depend on x, so its derivative is 0 | 0");
  }

  @Test
  void theChainRuleTakesItsInnerDerivative() {
    assertThat(narrated("steps(diff(sin(x^2), x))"))
        .containsExactly(
            "Apply the chain rule to sin(x²): the outer derivative is cos(x²), the inner is 2x."
                + " Apply the power rule: d/dx x² = 2x | 2x cos(x²)");
    assertThat(narrated("steps(diff(sin(cos(x^2)), x))"))
        .containsExactly(
            "Apply the chain rule to sin(cos(x²)): d/dx sin(cos(x²)) = cos(cos(x²)) d/dx cos(x²)"
                + " | cos(cos(x²)) d/dx cos(x²)",
            "Apply the chain rule to cos(x²): the outer derivative is -sin(x²), the inner is 2x."
                + " Apply the power rule: d/dx x² = 2x | -2x cos(cos(x²)) sin(x²)");
  }

  @Test
  void solvingNarratesEachStateOfTheEquation() {
    assertThat(narrated("steps(solve(x^2 - 5x + 6 = 0, x))"))
        .containsExactly(
            "Factor the left side | (x - 3)(x - 2) = 0", "The solutions are {2, 3} | {2, 3}");
    assertThat(narrated("steps(solve(3x - 6 = x + 2, x))"))
        .containsExactly(
            "Collect the terms in the unknown | 2x - 6 = 2",
            "Move the constant terms to the right side | 2x = 8",
            "Divide both sides by 2 | x = 4",
            "The solutions are {4} | {4}");
  }

  @Test
  void theQuadraticFormulaGivesTheSolutionsSimplified() {
    assertThat(narrated("steps(solve(x^2 - 2 = 0, x))"))
        .containsExactly(
            "The discriminant is 8; apply the quadratic formula | [x = -√8/2, x = √8/2]",
            "The solutions are {-√2, √2} | {-√2, √2}");
    assertThat(narrated("steps(solve(x^2 + 1 = 0, x))"))
        .containsExactly(
            "The discriminant is -4; apply the quadratic formula | [x = -i, x = i]",
            "The solutions are {-i, i} | {-i, i}");
  }

  @Test
  void eachStepStartsWhereTheOneBeforeItEnded() {
    // a raw tree canonicalised whole can take another canonical shape than the rewriter's
    // normalisation (2^(3/2)/20 and √2/10); the steps' boundaries are the rewriter's trees
    String script =
        "steps(log(((50^(3/2))^(-1))^(2/3) * ((50^(3/2))^(-1))^(2/3) * (50^(3/2))^1, 10))";
    List<String> steps = narrated(script);
    assertThat(steps).isNotEmpty();
    assertThat(steps).noneMatch(t -> t.endsWith("| log(2^(3/2)/20, 10)"));
  }

  @Test
  void anUntracedOrEmptyTraceHasNoSteps() {
    Outcome.Value v = (Outcome.Value) last("1 + 1");
    assertThat(Narration.steps(new Evaluator.Result(v.value(), v.trace()), Context.DEFAULT))
        .isEmpty();
    assertThat(narrated("steps(1 + 1)")).isEmpty();
  }
}
