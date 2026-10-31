package mathcanvas.engine.calculus;

import static mathcanvas.engine.calculus.LimitCases.run;
import static mathcanvas.engine.calculus.LimitCases.shown;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.Limit.Direction;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Rule;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/** Each rule of {@code limit} deciding a limit at the top, with its value and narration. */
class LimitRulesTest {

  private static final Set<String> COVERED = new TreeSet<>();

  private static Rewriter.Result decided(
      String rule, String f, String point, Direction d, String value) {
    Rewriter.Result r = run(f, point, d);
    assertThat(shown(r)).as("lim %s at %s", f, point).isEqualTo(value);
    assertThat(LimitCases.rule(r)).as("rule of lim %s at %s", f, point).isEqualTo(rule);
    COVERED.add(rule);
    return r;
  }

  private static String text(Rewriter.Result r) {
    List<String> own = LimitCases.limitSteps(r);
    assertThat(own).hasSize(1);
    return own.getFirst();
  }

  @Test
  void substitution() {
    Rewriter.Result r = decided("substitution", "x^2 + 1", "2", Direction.BOTH, "5");
    assertThat(text(r)).isEqualTo("By substitution, x² + 1 → 5");
    assertThat(shown("sqrt(x)", "0")).isEqualTo("0");
    Rewriter.Result g = decided("substitution", "1/x", "a", Direction.BOTH, "1/a");
    assertThat(text(g)).isEqualTo("By substitution, 1/x → 1/a, for a ≠ 0");
  }

  @Test
  void pole() {
    Rewriter.Result r = decided("pole", "1/(x - 1)^2", "1", Direction.BOTH, "∞");
    assertThat(text(r))
        .isEqualTo(
            "The denominator of 1/(x - 1)² vanishes to order 2 at the point and its numerator"
                + " does not, so 1/(x - 1)² → ∞");
    decided("pole", "1/x", "0", Direction.RIGHT, "∞");
    decided("pole", "1/x", "0", Direction.LEFT, "-∞");
    Rewriter.Result both = decided("pole", "1/x", "0", Direction.BOTH, "undefined");
    assertThat(text(both))
        .endsWith("so 1/x → -∞ from the left and ∞ from the right; the limit is undefined");
    decided("pole", "(2x + 3)/(x - 1)", "1", Direction.RIGHT, "∞");
  }

  @Test
  void limitLaws() {
    Rewriter.Result r = decided("limit-laws", "sin(x)/x + cos(x)", "0", Direction.BOTH, "2");
    assertThat(text(r))
        .isEqualTo(
            "By the standard limit sin(u)/u → 1 as u → 0, sin(x)/x → 1; by the limit laws,"
                + " cos(x) + sin(x)/x → 2");
    decided("limit-laws", "log(x)", "0", Direction.RIGHT, "-∞");
    decided("limit-laws", "e^x", "-inf", Direction.BOTH, "0");
    decided("limit-laws", "atan(x)", "inf", Direction.BOTH, "π/2");
  }

  @Test
  void theStandardLimits() {
    Rewriter.Result r = decided("sine-over-argument", "sin(x)/x", "0", Direction.BOTH, "1");
    assertThat(text(r)).isEqualTo("By the standard limit sin(u)/u → 1 as u → 0, sin(x)/x → 1");
    decided("sine-over-argument", "3 sin(x)/x", "0", Direction.BOTH, "3");
    decided("one-minus-cosine", "(1 - cos(x))/x", "0", Direction.BOTH, "0");
    decided("exponential-minus-one", "(e^x - 1)/x", "0", Direction.BOTH, "1");
    decided("compound-interest", "(1 + 1/x)^x", "inf", Direction.BOTH, "e");
    decided("root-of-one-plus", "(1 + x)^(1/x)", "0", Direction.BOTH, "e");
    // a shape that differs is not the standard limit
    assertThat(LimitCases.rule(run("sin(2x)/x", "0"))).isNotEqualTo("sine-over-argument");
  }

  @Test
  void exponentialForm() {
    Rewriter.Result q = decided("exponential-form", "x^(1/x)", "inf", Direction.BOTH, "1");
    assertThat(text(q))
        .isEqualTo(
            "Write x^(1/x) as e^(log(x)/x); as x → ∞, log(x)/x behaves like its leading term"
                + " log(x)/x, so log(x)/x → 0; so x^(1/x) → 1");
  }

  @Test
  void oneSided() {
    Rewriter.Result r = decided("one-sided", "abs(x)/x", "0", Direction.RIGHT, "1");
    assertThat(text(r)).isEqualTo("From the right, |x|/x is 1");
    decided("one-sided", "abs(x)/x", "0", Direction.LEFT, "-1");
  }

  @Test
  void cancel() {
    // the body is simplified before the methods, so its rational common factors are gone: the
    // rule cancels the quotients the methods make, and is tested on the rule set itself
    Rewriter.Result r = raw("cancel", "(x^2 - 1)/(x - 1)", "1", "2");
    assertThat(LimitCases.limitSteps(r))
        .containsExactly(
            "Cancel the common factor x - 1, leaving x + 1; by substitution, x + 1 → 2");
    raw("cancel", "(x^3 - 8)/(x - 2)", "2", "12");
    Rewriter.Result c = raw("cancel", "1/x - 1/(x^2 + x)", "0", "1");
    assertThat(LimitCases.limitSteps(c).getFirst())
        .startsWith("Combine -1/(x² + x) + 1/x into 1/(x + 1)");
    // through the operation the simplifier cancels first, then substitution decides
    assertThat(LimitCases.steps(run("(x^2 - 1)/(x - 1)", "1")))
        .containsExactly(
            "Cancel the common factor x - 1: (x² - 1)/(x - 1) = x + 1",
            "By substitution, x + 1 → 2");
  }

  /** The rule set applied to the node itself, without the simplification before it. */
  private static Rewriter.Result raw(String rule, String f, String point, String value) {
    Expr node =
        Limit.of(LimitCases.parse(f), LimitCases.X, LimitCases.parse(point), Direction.BOTH);
    Rewriter.Result r =
        mathcanvas.engine.rule.Rewriter.rewrite(node, RuleSets.LIMIT, LimitCases.CTX);
    assertThat(shown(r)).isEqualTo(value);
    assertThat(LimitCases.rule(r)).isEqualTo(rule);
    COVERED.add(rule);
    return r;
  }

  @Test
  void conjugate() {
    Rewriter.Result r = decided("conjugate", "(sqrt(x + 4) - 2)/x", "0", Direction.BOTH, "1/4");
    assertThat(text(r)).startsWith("Multiply above and below by the conjugate");
    decided("conjugate", "sqrt(x^2 + 1) - x", "inf", Direction.BOTH, "0");
    decided("conjugate", "x - sqrt(x^2 - 3x)", "inf", Direction.BOTH, "3/2");
  }

  @Test
  void leadingTerm() {
    Rewriter.Result r =
        decided("leading-term", "(3x^2 + 1)/(2x^2 - x)", "inf", Direction.BOTH, "3/2");
    assertThat(text(r))
        .isEqualTo(
            "As x → ∞, (3x² + 1)/(2x² - x) behaves like its leading term 3/2, so (3x² + 1)/(2x²"
                + " - x) → 3/2");
    decided("leading-term", "x^3/e^x", "inf", Direction.BOTH, "0");
    decided("leading-term", "log(x)/sqrt(x)", "inf", Direction.BOTH, "0");
    decided("leading-term", "-x^3 + x", "-inf", Direction.BOTH, "∞");
    decided("leading-term", "e^x/x^100", "inf", Direction.BOTH, "∞");
  }

  @Test
  void reciprocalPoint() {
    Rewriter.Result s = decided("reciprocal-point", "x sin(1/x)", "inf", Direction.BOTH, "1");
    assertThat(text(s))
        .isEqualTo(
            "Put x = 1/t, so that t → 0 from above; the limit is that of sin(t)/t; by the standard"
                + " limit sin(u)/u → 1 as u → 0, sin(t)/t → 1");
  }

  @Test
  void squeeze() {
    Rewriter.Result r = decided("squeeze", "x sin(1/x)", "0", Direction.BOTH, "0");
    assertThat(text(r)).isEqualTo("sin(1/x) is bounded and x → 0, so x sin(1/x) → 0");
  }

  @Test
  void oscillation() {
    Rewriter.Result r = decided("oscillation", "sin(x)", "inf", Direction.BOTH, "undefined");
    assertThat(text(r)).isEqualTo("x grows without bound, so sin(x) oscillates and has no limit");
    decided("oscillation", "sin(1/x)", "0", Direction.RIGHT, "undefined");
  }

  @Test
  void twoSided() {
    Rewriter.Result r = decided("two-sided", "abs(x)/x", "0", Direction.BOTH, "undefined");
    assertThat(text(r))
        .isEqualTo(
            "From the left, |x|/x is -1; from the right, |x|/x is 1; from the left the limit is"
                + " -1 and from the right 1; they differ, so the limit is undefined");
    decided("two-sided", "sign(x)", "0", Direction.BOTH, "undefined");
    decided("two-sided", "e^(1/x)", "0", Direction.BOTH, "undefined");
  }

  @Test
  void theFactsAboutTheVariableDoNotHoldInTheExpression() {
    Context positive = LimitCases.CTX.withAssumptions(DiffCases.facts("x > 0"));
    Rewriter.Result r = LimitCases.run("sqrt(x^2)/x", "0", Direction.LEFT, positive);
    assertThat(shown(r)).isEqualTo("-1");
    LimitCases.rule(r);
    assertThat(shown(LimitCases.run("sqrt(x^2)/x", "0", Direction.RIGHT, positive))).isEqualTo("1");
  }

  @Test
  void gapsClosed() {
    decided("conjugate", "cbrt(x^3 + x^2) - x", "inf", Direction.BOTH, "1/3");
    decided("leading-term", "abs(x)/x", "-inf", Direction.BOTH, "-1");
    Rewriter.Result f = decided("squeeze", "floor(x)/x", "inf", Direction.BOTH, "1");
    assertThat(text(f))
        .endsWith("lies between (x - 1)/x and 1, which both tend to 1, so floor(x)/x → 1");
    Context a = LimitCases.CTX.withAssumptions(DiffCases.facts("a > 0", "s > 1"));
    assertThat(shown(LimitCases.run("x^a", "inf", Direction.BOTH, a))).isEqualTo("∞");
    assertThat(shown(LimitCases.run("s^x", "inf", Direction.BOTH, a))).isEqualTo("∞");
    // a product's lower end of 0 is taken when a factor takes 0: r·s may be 0, so x^(r s) stays
    Context zero = LimitCases.CTX.withAssumptions(DiffCases.facts("r >= 0", "s > 0"));
    assertThat(LimitCases.run("x^(r s)", "inf", Direction.BOTH, zero).expr())
        .isInstanceOf(Limit.class);
    assertThat(LimitCases.run("e^(-r s x)", "inf", Direction.BOTH, zero).expr())
        .isInstanceOf(Limit.class);
    // |x| at -∞ is -x
    assertThat(shown("(sqrt(x^2) + x)/x", "-inf")).isEqualTo("0");
    decided("leading-term", "x + abs(x)", "-inf", Direction.BOTH, "0");
    // a step-valued argument does not show an oscillation: cos(2π·|floor(x)|) is 1
    Rewriter.Result c = run("cos(2 pi abs(floor(x)))", "inf");
    assertThat(c.expr()).isInstanceOf(Limit.class);
  }

  @Test
  void whatNoMethodDecidesStays() {
    Rewriter.Result r = run("x!", "1/2");
    assertThat(r.expr()).isInstanceOf(Limit.class);
    assertThat(LimitCases.rule(r)).isEqualTo("none");
    assertThat(text(r)).isEqualTo("No method finds lim[x → 1/2] x!; it stays unevaluated");
  }

  @AfterAll
  static void everyRuleOfTheSetIsCovered() {
    Set<String> rules = new TreeSet<>();
    for (Rule rule : RuleSets.LIMIT.rules()) {
      rules.add(rule.name());
    }
    assertThat(COVERED).containsAll(rules);
  }
}
