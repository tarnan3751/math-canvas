package mathcanvas.engine.calculus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.DefaultNarrator;
import mathcanvas.engine.rule.Replacement;
import mathcanvas.engine.rule.Rule;
import org.junit.jupiter.api.Test;

class IntegrateRulesTest {

  private static final ResourceBundle BUNDLE =
      ResourceBundle.getBundle(DefaultNarrator.BUNDLE, Locale.ROOT);

  @Test
  void everyRuleHasAPositiveAndANegativeCase() {
    new IntegrateCases(Context.DEFAULT)
        .fires("undefined", "integrate(undefined + x!, x)", "undefined")
        .skips("undefined", "integrate(x, x)")
        .fires("constant", "integrate(a^2 + pi, x)", "(a^2 + pi) x")
        .skips("constant", "integrate(a x, x)")
        .fires("sum-rule", "integrate(x^2 + x, x)", "x^3/3 + x^2/2")
        .skips("sum-rule", "integrate(x^2, x)")
        .fires("constant-multiple", "integrate(3 sin(x), x)", "-3 cos(x)")
        .skips("constant-multiple", "integrate(sin(x), x)")
        .fires("power", "integrate(x^3, x)", "x^4/4")
        .fires("power", "integrate((2x + 1)^(1/2), x)", "(2x + 1)^(3/2)/3")
        .skips("power", "integrate(x^(-1), x)")
        .skips("power", "integrate(x^n, x)")
        .fires("reciprocal", "integrate(1/x, x)", "log(abs(x))")
        .fires("reciprocal", "integrate(1/(3x - 2), x)", "log(abs(3x - 2))/3")
        .skips("reciprocal", "integrate(1/x^2, x)")
        .fires("exp", "integrate(e^(2x), x)", "e^(2x)/2")
        .skips("exp", "integrate(e^(x^2), x)")
        .fires("exponential", "integrate(2^x, x)", "2^x/log(2)")
        .skips("exponential", "integrate(a^x, x)")
        .fires("sin", "integrate(sin(2x + 1), x)", "-cos(2x + 1)/2")
        .skips("sin", "integrate(sin(x^2), x)")
        .fires("cos", "integrate(cos(x/2), x)", "2 sin(x/2)")
        .skips("cos", "integrate(cos(x^2), x)")
        .fires("tan", "integrate(tan(x), x)", "-log(abs(cos(x)))")
        .skips("tan", "integrate(tan(x^2), x)")
        .fires("cot", "integrate(cot(x), x)", "log(abs(sin(x)))")
        .skips("cot", "integrate(cot(x^2), x)")
        .fires("sec", "integrate(sec(x), x)", "log(abs(sec(x) + tan(x)))")
        .skips("sec", "integrate(sec(x^2), x)")
        .fires("csc", "integrate(csc(x), x)", "-log(abs(csc(x) + cot(x)))")
        .skips("csc", "integrate(csc(x^2), x)")
        .fires("sec-squared", "integrate(sec(3x)^2, x)", "tan(3x)/3")
        .skips("sec-squared", "integrate(sec(x^2)^2, x)")
        .fires("csc-squared", "integrate(csc(x)^2, x)", "-cot(x)")
        .skips("csc-squared", "integrate(csc(x^2)^2, x)")
        .fires("sec-tan", "integrate(sec(x) tan(x), x)", "sec(x)")
        .skips("sec-tan", "integrate(sec(x) tan(2x), x)")
        .fires("csc-cot", "integrate(csc(x) cot(x), x)", "-csc(x)")
        .skips("csc-cot", "integrate(csc(x) cot(2x), x)")
        .fires("sinh", "integrate(sinh(x), x)", "cosh(x)")
        .skips("sinh", "integrate(sinh(x^2), x)")
        .fires("cosh", "integrate(cosh(3x), x)", "sinh(3x)/3")
        .skips("cosh", "integrate(cosh(x^2), x)")
        .fires("tanh", "integrate(tanh(x), x)", "log(cosh(x))")
        .skips("tanh", "integrate(tanh(x^2), x)")
        .fires("coth", "integrate(coth(x), x)", "log(abs(sinh(x)))")
        .skips("coth", "integrate(coth(x^2), x)")
        .fires("sech", "integrate(sech(x), x)", "atan(sinh(x))")
        .skips("sech", "integrate(sech(x^2), x)")
        .fires("csch", "integrate(csch(x), x)", "log(abs(tanh(x/2)))")
        .skips("csch", "integrate(csch(x^2), x)")
        .fires("sech-squared", "integrate(sech(x)^2, x)", "tanh(x)")
        .skips("sech-squared", "integrate(sech(x^2)^2, x)")
        .fires("csch-squared", "integrate(csch(x)^2, x)", "-coth(x)")
        .skips("csch-squared", "integrate(csch(x^2)^2, x)")
        .fires("sech-tanh", "integrate(sech(x) tanh(x), x)", "-sech(x)")
        .skips("sech-tanh", "integrate(sech(x) tanh(2x), x)")
        .fires("csch-coth", "integrate(csch(x) coth(x), x)", "-csch(x)")
        .skips("csch-coth", "integrate(csch(x) coth(2x), x)")
        .fires("square-reciprocal", "integrate(1/(x^2 + 1), x)", "atan(x)")
        .fires("square-reciprocal", "integrate(1/(4 + 9x^2), x)", "atan(3x/2)/6")
        .fires(
            "square-reciprocal",
            "integrate(1/(x^2 - 4), x)",
            "(log(abs(x - 2)) - log(abs(x + 2)))/4")
        .skips("square-reciprocal", "integrate(1/(x^2 + a), x)")
        .fires("square-root-reciprocal", "integrate(1/sqrt(1 - x^2), x)", "asin(x)")
        .fires("square-root-reciprocal", "integrate(1/sqrt(x^2 + 1), x)", "log(x + sqrt(x^2 + 1))")
        .fires(
            "square-root-reciprocal",
            "integrate(1/sqrt(x^2 - 1), x)",
            "log(abs(x + sqrt(x^2 - 1)))")
        .skips("square-root-reciprocal", "integrate(1/sqrt(-x^2 - 1), x)")
        .fires("odd-power", "integrate(sin(x)^3, x)", "-(cos(x) - cos(x)^3/3)")
        .fires("odd-power", "integrate(sin(x)^2 cos(x)^3, x)", "sin(x)^3/3 - sin(x)^5/5")
        .skips("odd-power", "integrate(sin(x) cos(x)^2, x)")
        .fires("half-angle", "integrate(cos(x)^2, x)", "(2x + sin(2x))/4")
        .skips("half-angle", "integrate(sin(x)^3, x)")
        .fires("product-to-sum", "integrate(sin(3x) cos(2x), x)", "(-5 cos(x) - cos(5x))/10")
        .skips("product-to-sum", "integrate(sin(x^2) cos(x), x)")
        .fires("tan-power", "integrate(tan(x)^2, x)", "tan(x) - x")
        .skips("tan-power", "integrate(tan(x), x)")
        .fires("cot-power", "integrate(cot(x)^3, x)", "-cot(x)^2/2 - log(abs(sin(x)))")
        .skips("cot-power", "integrate(cot(x), x)")
        .fires(
            "sec-reduction",
            "integrate(sec(x)^3, x)",
            "log(abs(sec(x) + tan(x)))/2 + sec(x) tan(x)/2")
        .skips("sec-reduction", "integrate(sec(x)^2, x)")
        .fires(
            "csc-reduction",
            "integrate(csc(x)^3, x)",
            "-cot(x) csc(x)/2 - log(abs(cot(x) + csc(x)))/2")
        .skips("csc-reduction", "integrate(csc(x)^2, x)")
        .fires("complete-the-square", "integrate(1/(x^2 + 2x + 5), x)", "atan((x + 1)/2)/2")
        .fires("complete-the-square", "integrate(1/sqrt(3 + 2x - x^2), x)", "asin((x - 1)/2)")
        .skips("complete-the-square", "integrate(1/(x^2 + 1), x)")
        .fires("substitution", "integrate(2x cos(x^2), x)", "sin(x^2)")
        .fires(
            "substitution", "integrate(x sqrt(x + 1), x)", "2 (x + 1)^(5/2)/5 - 2 (x + 1)^(3/2)/3")
        .skips("substitution", "integrate(x e^x, x)")
        .fires("by-parts-twice", "integrate(e^x sin(x), x)", "(e^x sin(x) - e^x cos(x))/2")
        .skips("by-parts-twice", "integrate(x sin(x), x)")
        .fires("by-parts", "integrate(x e^x, x)", "x e^x - e^x")
        .fires("by-parts", "integrate(log(x), x)", "x log(x) - x")
        .fires("by-parts", "integrate(x atan(x), x)", "x^2 atan(x)/2 + atan(x)/2 - x/2")
        .skips("by-parts", "integrate(sin(x)/x, x)")
        .fires("expand", "integrate((x^2 + 1)^2, x)", "x^5/5 + 2x^3/3 + x")
        .skips("expand", "integrate((x + 1)^2, x)")
        .fires(
            "partial-fractions",
            "integrate(1/((x - 1)(x + 2)), x)",
            "log(abs(x - 1))/3 - log(abs(x + 2))/3")
        .fires(
            "partial-fractions",
            "integrate(1/((x - 1)^2 (x + 1)), x)",
            "-log(abs(x - 1))/4 + log(abs(x + 1))/4 - 1/(2(x - 1))")
        .skips("partial-fractions", "integrate(1/(x^2 + 1), x)")
        .skips("partial-fractions", "integrate(1/(x^4 + 1), x)")
        .fires("hermite", "integrate(1/(x^2 + 1)^2, x)", "x/(2(x^2 + 1)) + atan(x)/2")
        .fires(
            "hermite",
            "integrate(1/(x^2 + 2x + 5)^2, x)",
            "atan((x + 1)/2)/16 + (x + 1)/(8(x^2 + 2x + 5))")
        .skips("hermite", "integrate(1/(x^2 + 1), x)")
        .skips("hermite", "integrate(1/(x - 1)^2, x)")
        .fires(
            "log-part",
            "integrate((x + 3)/(x^2 + x + 1), x)",
            "5 sqrt(3) atan(2 (x + 1/2) sqrt(3)/3)/3 + log(x^2 + x + 1)/2")
        .skips("log-part", "integrate(2x/(x^2 + 1), x)")
        .skips("log-part", "integrate(1/(x^3 - x + 1), x)")
        .fires("definite", "integrate(x^2, x, 0, 1)", "1/3")
        .fires("definite", "integrate(sin(x), x, 0, pi)", "2")
        .skips("definite", "integrate(1/x, x, -1, 1)")
        .skips("definite", "integrate(x^2, x, 0, inf)")
        .fires("limits", "integrate(1/x^2, x, 1, inf)", "1")
        .fires("limits", "integrate(1/sqrt(x), x, 0, 1)", "2")
        .fires("limits", "integrate(1/(1 + x^2), x, -inf, inf)", "pi")
        .skips("limits", "integrate(1/x, x, 1, inf)")
        .skips("limits", "integrate(x^2, x, 0, 1)")
        .fires("diverges", "integrate(1/x, x, 1, inf)", "undefined")
        .fires("diverges", "integrate(1/x, x, 0, 1)", "undefined")
        .skips("diverges", "integrate(1/x^2, x, 1, inf)")
        .covered();
  }

  /** The names a rule's text may print: its values, and each step's integral and result. */
  private static Set<String> named(Rule r) {
    Set<String> names = new TreeSet<>(Set.of("integral", "result", "before", "after"));
    if (r.replacement() instanceof Replacement.Valued v) {
      names.addAll(v.names());
    }
    return names;
  }

  @Test
  void everyRuleIsNarratedWithItsOwnVariablesAndValues() {
    Pattern argument = Pattern.compile("\\{(\\d+)}");
    Pattern name = Pattern.compile("\\{([a-z]+)}");
    for (Rule r : RuleSets.INTEGRATE.rules()) {
      String key = RuleSets.INTEGRATE.name() + "." + r.name();
      assertThat(BUNDLE.containsKey(key)).as(key).isTrue();
      String text = BUNDLE.getString(key);
      Matcher m = argument.matcher(text);
      while (m.find()) {
        assertThat(Integer.parseInt(m.group(1))).as(key).isLessThan(r.pattern().variables().size());
      }
      Matcher n = name.matcher(text);
      while (n.find()) {
        assertThat(named(r)).as(key).contains(n.group(1));
      }
    }
  }

  @Test
  void everyDepthHasTheSameRules() {
    for (int d = 0; d <= IntegrateRules.DEPTH; d++) {
      assertThat(IntegrateRules.at(d).name()).isEqualTo("integrate");
      assertThat(IntegrateRules.at(d).rules().stream().map(Rule::name).toList())
          .isEqualTo(RuleSets.INTEGRATE.rules().stream().map(Rule::name).toList());
    }
  }
}
