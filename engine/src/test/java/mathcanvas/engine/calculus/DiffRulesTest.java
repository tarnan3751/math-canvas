package mathcanvas.engine.calculus;

import static mathcanvas.engine.calculus.DiffCases.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Piecewise;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.rule.DefaultNarrator;
import mathcanvas.engine.rule.Rule;
import org.junit.jupiter.api.Test;

class DiffRulesTest {

  private static final Symbol X = Symbol.of("x");

  private static final ResourceBundle BUNDLE =
      ResourceBundle.getBundle(DefaultNarrator.BUNDLE, Locale.ROOT);

  @Test
  void everyRuleHasAPositiveAndANegativeCase() {
    DiffCases c = new DiffCases();
    c.fires("higher-order-constant", "diff(a^2, x, 5)", "0")
        .fires("higher-order-constant", "diff(x^4, x, 2147483647)", "0")
        .skips("higher-order-constant", "diff(x^4, x, 2)")
        .fires("higher-order", "diff(x^3, x, 2)", "6x")
        .skips("higher-order", "diff(factorial(x), x, 2)")
        .fires("merge-orders", "diff(diff(factorial(x), x), x)", "diff(factorial(x), x, 2)")
        .skips("merge-orders", "diff(diff(x^3, y), x)")
        .fires("undefined", "diff(undefined, x)", "undefined")
        .skips("undefined", "diff(x, x)")
        .fires("constant", "diff(a^2 + pi, x)", "0")
        .fires("constant", "diff(sum(x^2, x, 1, 3), x)", "0")
        .skips("constant", "diff(a x, x)")
        .skips("constant", "diff(y = 1, x)")
        .fires("vector", "diff([x, x^2], x)", "[1, 2x]")
        .skips("vector", "diff(x, x)")
        .fires("matrix", "diff([[x, 1], [x^2, y]], x)", "[[1, 0], [2x, 0]]")
        .skips("matrix", "diff([x, 1], x)")
        .fires("variable", "diff(x, x)", "1")
        .skips("variable", "diff(y, x)")
        .fires("sum-rule", "diff(x^2 + x, x)", "2x + 1")
        .skips("sum-rule", "diff(x^2, x)")
        .fires("constant-multiple", "diff(3 sin(x), x)", "3 cos(x)")
        .skips("constant-multiple", "diff(x sin(x), x)")
        .fires("quotient-rule", "diff(sin(x)/x, x)", "(x cos(x) - sin(x))/x^2")
        .skips("quotient-rule", "diff(x^2 sin(x), x)")
        .skips("quotient-rule", "diff(sin(x)/a, x)")
        .fires("product-rule", "diff(x^2 sin(x), x)", "x^2 cos(x) + 2x sin(x)")
        .skips("product-rule", "diff(2x, x)")
        .fires("exp-rule", "diff(e^(2x), x)", "2 e^(2x)")
        .skips("exp-rule", "diff(2^x, x)")
        .fires("power-rule", "diff(x^3, x)", "3x^2")
        .fires("power-rule", "diff(sqrt(x), x)", "1/(2 sqrt(x))")
        .fires("power-rule", "diff(1/x, x)", "-1/x^2")
        .skips("power-rule", "diff(x^x, x)")
        .fires("exponential-rule", "diff(2^x, x)", "2^x log(2)")
        .fires("exponential-rule", "diff(a^(x^2), x)", "2x a^(x^2) log(a)")
        .skips("exponential-rule", "diff(x^2, x)")
        .skips("exponential-rule", "diff(0^x, x)")
        .fires("general-power-rule", "diff(x^x, x)", "x^x (log(x) + 1)")
        .skips("general-power-rule", "diff(0^x, x)");
    c.fires("exp", "diff(exp(x), x)", "exp(x)")
        .fires("ln", "diff(ln(x), x)", "1/x")
        .fires("log", "diff(log(x), x)", "1/x")
        .fires("log10", "diff(log10(x), x)", "1/(x log(10))")
        .fires("log2", "diff(log2(x), x)", "1/(x log(2))")
        .fires("cbrt", "diff(cbrt(x), x)", "1/(3 cbrt(x)^2)")
        .fires("sin", "diff(sin(x), x)", "cos(x)")
        .fires("cos", "diff(cos(x), x)", "-sin(x)")
        .fires("tan", "diff(tan(x), x)", "sec(x)^2")
        .fires("cot", "diff(cot(x), x)", "-csc(x)^2")
        .fires("sec", "diff(sec(x), x)", "sec(x) tan(x)")
        .fires("csc", "diff(csc(x), x)", "-csc(x) cot(x)")
        .fires("asin", "diff(asin(x), x)", "1/sqrt(1 - x^2)")
        .fires("acos", "diff(acos(x), x)", "-1/sqrt(1 - x^2)")
        .fires("atan", "diff(atan(x), x)", "1/(1 + x^2)")
        .fires("acot", "diff(acot(x), x)", "-1/(1 + x^2)")
        .fires("asec", "diff(asec(x), x)", "1/(abs(x) sqrt(x^2 - 1))")
        .fires("acsc", "diff(acsc(x), x)", "-1/(abs(x) sqrt(x^2 - 1))")
        .fires("sinh", "diff(sinh(x), x)", "cosh(x)")
        .fires("cosh", "diff(cosh(x), x)", "sinh(x)")
        .fires("tanh", "diff(tanh(x), x)", "sech(x)^2")
        .fires("coth", "diff(coth(x), x)", "-csch(x)^2")
        .fires("sech", "diff(sech(x), x)", "-sech(x) tanh(x)")
        .fires("csch", "diff(csch(x), x)", "-csch(x) coth(x)")
        .fires("asinh", "diff(asinh(x), x)", "1/sqrt(x^2 + 1)")
        .fires("acosh", "diff(acosh(x), x)", "1/(sqrt(x - 1) sqrt(x + 1))")
        .fires("atanh", "diff(atanh(x), x)", "1/(1 - x^2)")
        .fires("acoth", "diff(acoth(x), x)", "1/(1 - x^2)")
        .fires("asech", "diff(asech(x), x)", "-1/(x^2 sqrt(1/x - 1) sqrt(1/x + 1))")
        .fires("acsch", "diff(acsch(x), x)", "-1/(abs(x) sqrt(x^2 + 1))")
        // Off the reals, |u| is not differentiable: the principal forms.
        .fires("asec", "diff(asec(x), x)", "1/(x^2 sqrt(1 - 1/x^2))", "x in Complexes")
        .fires("acsc", "diff(acsc(x), x)", "-1/(x^2 sqrt(1 - 1/x^2))", "x in Complexes")
        .fires("acsch", "diff(acsch(x), x)", "-1/(x^2 sqrt(1 + 1/x^2))", "x in Complexes")
        .fires("abs", "diff(abs(x), x)", "sign(x)")
        .fires("sign", "diff(sign(x), x)", "0")
        .fires("floor", "diff(floor(x), x)", "0")
        .fires("ceil", "diff(ceil(x), x)", "0")
        .fires("round", "diff(round(x), x)", "0")
        .fires("trunc", "diff(trunc(x), x)", "0");
    for (String f :
        new String[] {
          "exp", "ln", "log", "log10", "log2", "cbrt", "sin", "cos", "tan", "cot", "sec", "csc",
          "asin", "acos", "atan", "acot", "asec", "acsc", "sinh", "cosh", "tanh", "coth", "sech",
          "csch", "asinh", "acosh", "atanh", "acoth", "asech", "acsch"
        }) {
      // An argument other than the variable goes through the chain rule.
      c.skips(f, "diff(" + f + "(2x), x)");
    }
    for (String f : new String[] {"abs", "sign", "floor", "ceil", "round", "trunc"}) {
      // These need a real argument.
      c.skips(f, "diff(" + f + "(x), x)", "x in Complexes");
    }
    c.fires("chain-rule", "diff(sin(x^2), x)", "2x cos(x^2)")
        .fires("chain-rule", "diff(abs(2x), x)", "2 sign(2x)")
        .skips("chain-rule", "diff(sin(x), x)")
        .skips("chain-rule", "diff(factorial(x^2), x)")
        .skips("chain-rule", "diff(abs(x^2 z), x)", "z in Complexes")
        .fires("log-base-constant", "diff(log(x, 2), x)", "1/(x log(2))")
        .skips("log-base-constant", "diff(log(2, x), x)")
        .fires("log-base", "diff(log(2, x), x)", "-log(2)/(x log(x)^2)")
        .skips("log-base", "diff(log(x, 2), x)")
        .fires("root", "diff(root(x, 4), x)", "root(x, 4)^(-3)/4")
        .skips("root", "diff(root(2, x), x)")
        .fires("atan2", "diff(atan2(x, 1), x)", "1/(x^2 + 1)")
        .skips("atan2", "diff(atan2(1, 2), x)")
        .fires("big-sum", "diff(sum(x^k, k, 1, n), x)", "sum(k x^(k - 1), k, 1, n)")
        .skips("big-sum", "diff(sum(k, k, 1, x), x)")
        .fires(
            "piecewise",
            Derivative.of(
                Piecewise.of(
                    List.of(new Piecewise.Piece(parse("x^2"), parse("x > 0"))), parse("-x")),
                X,
                1),
            Piecewise.of(List.of(new Piecewise.Piece(parse("2x"), parse("x > 0"))), parse("-1")))
        .skips("piecewise", "diff(x^2, x)")
        .covered();
  }

  @Test
  void everyRuleIsNarratedWithItsOwnVariables() {
    Pattern argument = Pattern.compile("\\{(\\d+)}");
    for (Rule r : RuleSets.DIFF.rules()) {
      String key = RuleSets.DIFF.name() + "." + r.name();
      assertThat(BUNDLE.containsKey(key)).as(key).isTrue();
      Matcher m = argument.matcher(BUNDLE.getString(key));
      while (m.find()) {
        assertThat(Integer.parseInt(m.group(1))).as(key).isLessThan(r.pattern().variables().size());
      }
    }
  }
}
