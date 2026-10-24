package mathcanvas.engine.simplify;

import org.junit.jupiter.api.Test;

/** One positive and one negative case for every rule of the basic set, run through that set. */
class BasicRulesTest {

  @Test
  void everyRule() {
    new RuleCases(RuleSets.BASIC)
        .fires("undefined-argument", "abs(undefined)", "undefined")
        .skips("undefined-argument", "abs(x)")
        .fires("abs-of-abs", "abs(abs(log(x)))", "abs(log(x))")
        .skips("abs-of-abs", "abs(log(x))")
        .fires("abs-of-nonnegative", "abs(x^2)", "x^2")
        .skips("abs-of-nonnegative", "abs(x)")
        .fires("abs-of-nonpositive", "abs(x)", "-x", "x <= 0")
        .skips("abs-of-nonpositive", "abs(x)")
        .fires("abs-of-complex-number", "abs(3 + 4i)", "5")
        .skips("abs-of-complex-number", "abs(2)")
        .fires("abs-of-product", "abs(-3y)", "3 abs(y)")
        .skips("abs-of-product", "abs(x + y)")
        .fires("abs-of-power", "abs(x^3)", "abs(x)^3")
        .skips("abs-of-power", "abs(x^y)", "y ∈ ℂ")
        .fires("abs-to-even-power", "abs(x)^2", "x^2")
        .skips("abs-to-even-power", "abs(x)^3")
        .fires("sign-of-positive", "sign(x^2 + 1)", "1")
        .skips("sign-of-positive", "sign(x)")
        .fires("sign-of-negative", "sign(-pi)", "-1")
        .skips("sign-of-negative", "sign(x)")
        .fires("sign-of-zero", "sign(x)", "0", "x >= 0", "x <= 0")
        .skips("sign-of-zero", "sign(x)")
        .fires("sign-of-product", "sign(-2y)", "-sign(y)")
        .skips("sign-of-product", "sign(2z)", "z ∈ ℂ")
        .fires("sign-times-abs", "3 sign(x) abs(x)", "3x")
        .skips("sign-times-abs", "sign(x) abs(y)")
        .fires("power-of-power-nonnegative", "sqrt(x^2)", "x", "x >= 0")
        .skips("power-of-power-nonnegative", "sqrt(x^2)")
        .fires("power-of-even-power", "sqrt(x^2)", "abs(x)")
        .skips("power-of-even-power", "(x^3)^(1/3)")
        .fires("power-of-product", "sqrt(4y)", "2 sqrt(y)")
        .skips("power-of-product", "sqrt(x y)")
        .fires("minus-one-to-even-power", "(-1)^n", "1", "mod(n, 2) = 0")
        .skips("minus-one-to-even-power", "(-1)^n", "n ∈ ℤ")
        .fires("minus-one-to-odd-power", "(-1)^n", "-1", "mod(n, 2) = 1")
        .skips("minus-one-to-odd-power", "(-1)^n", "n ∈ ℤ")
        .fires("zero-to-positive-power", "0^y", "0", "y > 0")
        .skips("zero-to-positive-power", "0^y")
        .skips("zero-to-positive-power", "0^y", "y >= 0")
        .covered();
  }

  @Test
  void complexSymbolsKeepTheirForms() {
    RuleCases c = new RuleCases(RuleSets.BASIC);
    c.skips("power-of-even-power", "sqrt(z^2)", "z ∈ ℂ")
        .skips("abs-to-even-power", "abs(z)^2", "z ∈ ℂ")
        .skips("abs-of-nonnegative", "abs(z^2)", "z ∈ ℂ")
        .skips("sign-times-abs", "sign(z) abs(z)", "z ∈ ℂ");
  }

  @Test
  void matrixExponentsAreLeftToTheEvaluator() {
    new RuleCases(RuleSets.BASIC)
        .skips("power-of-power-nonnegative", "(x^2)^[[1, 2]]", "x >= 0")
        .skips("power-of-even-power", "(x^2)^[[1, 2]]")
        .skips("power-of-product", "(4y)^[[1, 2]]");
  }

  @Test
  void anEvenPowerUnderARootIsAPowerOfTheAbsoluteValue() {
    new RuleCases(RuleSets.BASIC)
        .fires("power-of-even-power", "(x^4)^(1/2)", "x^2")
        .fires("power-of-even-power", "(x^2)^(1/3)", "abs(x)^(2/3)")
        .fires("power-of-power-nonnegative", "(sqrt(x))^(1/3)", "x^(1/6)", "x >= 0")
        .fires("power-of-product", "sqrt(x y)", "sqrt(x) sqrt(y)", "x >= 0");
  }
}
