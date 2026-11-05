package mathcanvas.engine.simplify;

import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.testing.Inputs;
import org.junit.jupiter.api.Test;

/** One positive and one negative case for every rule of the exp-log set, run through that set. */
class ExpLogRulesTest {

  @Test
  void everyRule() {
    new RuleCases(RuleSets.EXPLOG)
        .fires("exp-to-power", "exp(2x)", "e^(2x)")
        .skips("exp-to-power", "e^x")
        .fires("ln-to-log", "ln(x)", "log(x)")
        .skips("ln-to-log", "log(x)")
        .fires("log10-to-base", "log10(x)", "log(x)/log(10)")
        .skips("log10-to-base", "log(x, 10)")
        .fires("log2-to-base", "log2(x)", "log(x)/log(2)")
        .skips("log2-to-base", "log(x, 2)")
        .fires("log-to-base-one", "log(x, 1)", "undefined")
        .skips("log-to-base-one", "log(x, 2)")
        .fires("log-base-exact", "log(8, 2)", "3")
        .skips("log-base-exact", "log(3, 2)")
        .fires("log-change-of-base", "log(3, 2)", "log(3)/log(2)")
        .skips("log-change-of-base", "log(x)")
        .fires("log-of-zero-to-base", "log(0, 2)", "undefined")
        .skips("log-of-zero-to-base", "log(1, 2)")
        .fires("log-of-zero", "log(0)", "undefined")
        .skips("log-of-zero", "log(1)")
        .fires("log-of-one", "log(1)", "0")
        .skips("log-of-one", "log(1.0)")
        .fires("log-of-e", "log(e)", "1")
        .fires("sinh-of-zero", "sinh(0)", "0")
        .skips("sinh-of-zero", "sinh(1)")
        .fires("cosh-of-zero", "cosh(0)", "1")
        .skips("cosh-of-zero", "cosh(1)")
        .fires("tanh-of-zero", "tanh(0)", "0")
        .skips("tanh-of-zero", "tanh(x)")
        .fires("sech-of-zero", "sech(0)", "1")
        .skips("sech-of-zero", "sech(2)")
        .fires("csch-of-zero", "csch(0)", "undefined")
        .skips("csch-of-zero", "csch(1)")
        .fires("coth-of-zero", "coth(0)", "undefined")
        .skips("coth-of-zero", "coth(x)")
        .skips("log-of-e", "log(pi)")
        .fires("log-of-exp", "log(e^(2y))", "2y")
        .skips("log-of-exp", "log(e^y)", "y ∈ ℂ")
        .fires("exp-of-log", "e^(2 log(x) + y)", "x^2 e^y", "x > 0")
        .skips("exp-of-log", "e^log(x)")
        .fires("log-of-power", "log(x^3)", "3 log(x)", "x > 0")
        .skips("log-of-power", "log(x^3)")
        .fires("log-of-even-power", "log(x^2)", "2 log(abs(x))")
        .skips("log-of-even-power", "log(x^3)")
        .fires("log-of-product", "log(a b)", "log(a) + log(b)", "a > 0", "b > 0")
        .skips("log-of-product", "log(a b)")
        .fires("log-of-reciprocal", "log(1/2)", "-log(2)")
        .skips("log-of-reciprocal", "log(3/2)")
        .fires("log-of-negative", "log(-2)", "log(2) + i pi")
        .skips("log-of-negative", "log(x)")
        .fires("log-of-perfect-power", "log(2) - log(4)", "-log(2)")
        .fires("log-of-perfect-power", "log(8)", "3 log(2)")
        .fires("log-of-perfect-power", "log(4/9)", "-2 log(3/2)")
        .skips("log-of-perfect-power", "log(6)")
        .skips("log-of-perfect-power", "log(3/2)")
        .covered();
  }

  @Test
  void exactLogarithms() {
    new RuleCases(RuleSets.EXPLOG)
        .fires("log-base-exact", "log10(1000)", "3")
        .fires("log-base-exact", "log(1/4, 2)", "-2")
        .fires("log-base-exact", "log(4, 8)", "2/3")
        .fires("log-base-exact", "log(8, 1/2)", "-3")
        .fires("log-base-exact", "log(1/27, 9)", "-3/2")
        .fires("log-change-of-base", "log(e^3, e)", "3")
        .skips("log-base-exact", "log(-8, 2)")
        .skips("log-base-exact", "log(8, -2)")
        .skips("log-base-exact", "log(12, 2)");
  }

  @Test
  void theBaseOneIsUndefinedEvenWhenAFormReachesItLater() {
    // log(e) becomes 1 first; the change of base would give log(1)/log(1), which the canonical
    // form takes for 1 before log(1) becomes 0.
    new RuleCases(RuleSets.EXPLOG).fires("log-to-base-one", "log(1, log(e))", "undefined");
  }

  @Test
  void anEvenPowerSplitsOnlyWhenItsBaseOrExponentIsNotZero() {
    // log(x^n) is 0 at n = 0, x = 0, where n log|x| is undefined.
    new RuleCases(RuleSets.EXPLOG)
        .skips("log-of-even-power", "log(x^n)", "mod(n, 2) = 0")
        .fires("log-of-even-power", "log(x^n)", "n log(abs(x))", "mod(n, 2) = 0", "x != 0")
        .fires("log-of-even-power", "log(x^n)", "n log(abs(x))", "mod(n, 2) = 0", "n > 0");
  }

  @Test
  void exactLogarithmsOfLargePowersAreQuick() {
    // The exponent comes from the bit lengths, not from dividing one power at a time.
    // A whole-run budget after a warm-up, as the timing tests of the base have it.
    RuleCases c = new RuleCases(RuleSets.EXPLOG);
    String[][] cases = {
      {"log(1000, 1001/1000)", "3 log(10)/log(1001/1000)"},
      {"log(2^20, 1001/1000)", "20 log(2)/log(1001/1000)"},
      {"log(2, 1000001/1000000)", "log(2)/log(1000001/1000000)"},
      {"log((1001/1000)^400, 1001/1000)", "400"},
      {"log((1000/1001)^400, (1001/1000)^2)", "-200"}
    };
    for (String[] q : cases) {
      assertThat(c.rewrite(q[0]).expr()).as(q[0]).isEqualTo(Inputs.canonical(q[1]));
    }
    long start = System.nanoTime();
    for (String[] q : cases) {
      c.rewrite(q[0]);
    }
    // typically milliseconds locally (docs/reference/engine.md, Timing budgets)
    assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(120_000);
  }

  @Test
  void quotientsAndNegativeArguments() {
    new RuleCases(RuleSets.EXPLOG)
        .fires("log-of-product", "log(x/y)", "log(x) - log(y)", "x > 0", "y > 0")
        .fires("log-of-product", "log(2x)", "log(2) + log(x)")
        .fires("log-of-negative", "log(-2x)", "log(2) + log(x) + i pi", "x > 0")
        .fires("log-of-power", "log(sqrt(2))", "log(2)/2")
        .fires("exp-of-log", "e^log(x)", "x", "x > 0")
        .skips("log-of-power", "log(x^y)", "x > 0", "y ∈ ℂ")
        .skips("exp-of-log", "e^([[1, 2]] log(x))", "x > 0");
  }
}
