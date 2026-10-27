package mathcanvas.engine.simplify;

import org.junit.jupiter.api.Test;

/** One positive and one negative case for every rule of the radical set, run through that set. */
class RadicalRulesTest {

  @Test
  void everyRule() {
    new RuleCases(RuleSets.RADICAL)
        .fires("negative-radicand", "sqrt(-8)", "2 i sqrt(2)")
        .skips("negative-radicand", "(-1)^(1/3)")
        .fires("split-integer-exponent", "2^(3/2)", "2 sqrt(2)")
        .skips("split-integer-exponent", "2^(1/2)")
        .fires("rationalise-denominator", "1/sqrt(2)", "sqrt(2)/2")
        .skips("rationalise-denominator", "sqrt(2)")
        .fires("split-fraction-radicand", "sqrt(8/9)", "2 sqrt(2)/3")
        .skips("split-fraction-radicand", "sqrt(2)")
        .fires("reduce-radicand-power", "4^(1/3)", "2^(2/3)")
        .skips("reduce-radicand-power", "2^(1/3)")
        .fires("extract-perfect-power", "sqrt(12)", "2 sqrt(3)")
        .skips("extract-perfect-power", "sqrt(6)")
        .fires("combine-numeric-radicals", "sqrt(2) sqrt(3)", "sqrt(6)")
        .skips("combine-numeric-radicals", "sqrt(2) * 3^(1/3)")
        .fires("denest-square-root", "sqrt(5 + 2 sqrt(6))", "sqrt(2) + sqrt(3)")
        .skips("denest-square-root", "sqrt(1 + sqrt(2))")
        .fires("extract-radicand-content", "sqrt(4x + 4)", "2 sqrt(x + 1)")
        .skips("extract-radicand-content", "sqrt(2x + 2)")
        .fires("root-of-odd-power", "root(x^3, 3)", "x")
        .skips("root-of-odd-power", "root(x^3, 3)", "x ∈ ℂ")
        .fires("root-of-nonnegative", "root(x, 4)", "x^(1/4)", "x >= 0")
        .skips("root-of-nonnegative", "root(x, 4)")
        .fires("root-of-negative", "root(-16, 3)", "-2 * 2^(1/3)")
        .skips("root-of-negative", "root(-16, 4)")
        .fires("root-of-negative-even", "root(-16, 4)", "undefined")
        .skips("root-of-negative-even", "root(-16, 3)")
        .fires("cbrt-of-cube", "cbrt(x^3)", "x")
        .skips("cbrt-of-cube", "cbrt(x^3)", "x ∈ ℂ")
        .fires("cbrt-of-nonnegative", "cbrt(16)", "2 * 2^(1/3)")
        .skips("cbrt-of-nonnegative", "cbrt(x)")
        .fires("cbrt-of-negative", "cbrt(-x)", "-x^(1/3)", "x > 0")
        .skips("cbrt-of-negative", "cbrt(x)")
        .covered();
  }

  @Test
  void theBriefsExamples() {
    new RuleCases(RuleSets.RADICAL)
        .fires("extract-perfect-power", "12^(1/2)", "2 sqrt(3)")
        .fires("extract-perfect-power", "54^(1/3)", "3 * 2^(1/3)")
        .fires("reduce-radicand-power", "4^(2/3)", "2 * 2^(1/3)")
        .fires("reduce-radicand-power", "4^(1/3) - 2^(2/3)", "0")
        .fires("reduce-radicand-power", "sqrt(8)", "2 sqrt(2)")
        .fires("rationalise-denominator", "3/sqrt(5)", "3 sqrt(5)/5")
        .fires("split-fraction-radicand", "sqrt(2/3)", "sqrt(6)/3")
        .fires("combine-numeric-radicals", "cbrt(2) cbrt(3)", "6^(1/3)")
        .fires("denest-square-root", "sqrt(3 - 2 sqrt(2))", "sqrt(2) - 1")
        .fires("negative-radicand", "(-16)^(1/3)", "2 * (-1)^(1/3) * 2^(1/3)")
        .skips("extract-perfect-power", "2^(2/3)");
  }

  @Test
  void aSumRadicandGivesUpItsContent() {
    new RuleCases(RuleSets.RADICAL)
        .fires("extract-radicand-content", "sqrt(4x + 4)/2", "sqrt(x + 1)")
        .fires("extract-radicand-content", "sqrt(12x + 12)", "2 sqrt(3x + 3)")
        .fires("extract-radicand-content", "sqrt(x/4 + 1/4)", "sqrt(x + 1)/2")
        .fires("extract-radicand-content", "(8x^3 + 16)^(1/3)", "2 (x^3 + 2)^(1/3)")
        .fires("extract-radicand-content", "1/sqrt(4x + 4)", "1/(2 sqrt(x + 1))")
        .fires("extract-radicand-content", "sqrt(-4x - 4)", "2 sqrt(-x - 1)")
        .fires("extract-radicand-content", "sqrt(x^2/9 + 1/9)", "sqrt(x^2 + 1)/3")
        // no perfect-power content, an inexact coefficient, another exponent, a product base
        .skips("extract-radicand-content", "sqrt(6x + 3)")
        .skips("extract-radicand-content", "sqrt(4.0 x + 4)")
        .skips("extract-radicand-content", "(4x + 4)^(3/2)")
        .skips("extract-radicand-content", "(4x + 4)^(2/3)")
        .skips("extract-radicand-content", "sqrt(4 (x + 1))");
  }

  @Test
  void largeRadicands() {
    // A square of a prime above the trial bound is found when it is the whole cofactor.
    new RuleCases(RuleSets.RADICAL)
        .fires("extract-perfect-power", "sqrt(2 * 10007^2)", "10007 sqrt(2)")
        .fires("extract-perfect-power", "sqrt(10007^2 * 10009^2 * 3)", "10007 * 10009 sqrt(3)")
        .skips("extract-perfect-power", "2^(1/1000000)")
        .skips("extract-perfect-power", "3^(999/1000)");
  }
}
