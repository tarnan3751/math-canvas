package mathcanvas.engine.simplify;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Elementary;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.testing.NumericEvaluator;
import org.junit.jupiter.api.Test;

/** One positive and one negative case for every rule of the trig set, run through that set. */
class TrigRulesTest {

  @Test
  void everyRule() {
    new RuleCases(RuleSets.TRIG)
        .fires("special-angle", "sin(pi/6)", "1/2")
        .skips("special-angle", "sin(pi/5)")
        .fires("inverse-special-value", "asin(1/2)", "pi/6")
        .skips("inverse-special-value", "asin(1/3)")
        .fires("shift-by-multiple-of-pi", "sin(x + pi)", "-sin(x)")
        .skips("shift-by-multiple-of-pi", "sin(x + pi y)")
        .fires("reduce-rational-shift", "sin(x + 7pi/3)", "sin(x + pi/3)")
        .skips("reduce-rational-shift", "sin(x + 2pi/3)")
        .fires("sin-plus-half-pi", "sin(x + pi/2)", "cos(x)")
        .skips("sin-plus-half-pi", "sin(x + pi/3)")
        .fires("sin-minus-half-pi", "sin(x - pi/2)", "-cos(x)")
        .skips("sin-minus-half-pi", "sin(pi/2)")
        .fires("cos-plus-half-pi", "cos(x + pi/2)", "-sin(x)")
        .skips("cos-plus-half-pi", "cos(x + pi/4)")
        .fires("cos-minus-half-pi", "cos(x - pi/2)", "sin(x)")
        .skips("cos-minus-half-pi", "cos(x - pi/4)")
        .fires("tan-plus-half-pi", "tan(x + pi/2)", "-cot(x)")
        .skips("tan-plus-half-pi", "tan(x + pi/4)")
        .fires("cot-plus-half-pi", "cot(x + pi/2)", "-tan(x)")
        .skips("cot-plus-half-pi", "cot(x)")
        .fires("sec-plus-half-pi", "sec(x + pi/2)", "-csc(x)")
        .skips("sec-plus-half-pi", "sec(x)")
        .fires("sec-minus-half-pi", "sec(x - pi/2)", "csc(x)")
        .skips("sec-minus-half-pi", "sec(x - pi/3)")
        .fires("csc-plus-half-pi", "csc(x + pi/2)", "sec(x)")
        .skips("csc-plus-half-pi", "csc(x)")
        .fires("csc-minus-half-pi", "csc(x - pi/2)", "-sec(x)")
        .skips("csc-minus-half-pi", "csc(x - pi/6)")
        .fires("odd-function", "sin(-2x)", "-sin(2x)")
        .skips("odd-function", "sin(x - 1)")
        .fires("even-function", "cos(-x)", "cos(x)")
        .skips("even-function", "cos(x)")
        .fires("acos-of-negative", "acos(-x)", "pi - acos(x)")
        .skips("acos-of-negative", "acos(1 - x)")
        .fires("asec-of-negative", "asec(-x)", "pi - asec(x)")
        .skips("asec-of-negative", "asec(x)")
        .fires("sin-of-asin", "sin(asin(x))", "x")
        .skips("sin-of-asin", "sin(acos(x) + 1)")
        .fires("cos-of-acos", "cos(acos(x))", "x")
        .skips("cos-of-acos", "cos(asin(x) + 1)")
        .fires("tan-of-atan", "tan(atan(x))", "x")
        .skips("tan-of-atan", "tan(asin(x))")
        .fires("sin-of-acos", "sin(acos(x))", "sqrt(1 - x^2)")
        .skips("sin-of-acos", "sin(acos(x) + 1)")
        .fires("cos-of-asin", "cos(asin(x))", "sqrt(1 - x^2)")
        .skips("cos-of-asin", "cos(asin(x) + 1)")
        .fires("sin-of-atan", "sin(atan(x))", "x/sqrt(1 + x^2)")
        .skips("sin-of-atan", "sin(atan(z))", "z ∈ ℂ")
        .fires("cos-of-atan", "cos(atan(x))", "1/sqrt(1 + x^2)")
        .skips("cos-of-atan", "cos(atan(z))", "z ∈ ℂ")
        .fires("asin-of-sin", "asin(sin(x))", "x", "x >= -1", "x <= 3/2")
        .skips("asin-of-sin", "asin(sin(x))", "x >= -1", "x <= 2")
        .fires("atan-of-tan", "atan(tan(x))", "x", "x >= 0", "x <= 1")
        .skips("atan-of-tan", "atan(tan(x))")
        .fires("atan-of-reciprocal", "atan(1/x)", "pi/2 - atan(x)", "x > 0")
        .skips("atan-of-reciprocal", "atan(1/x)")
        .fires("combine-trig-factors", "sin(x)/cos(x)", "tan(x)")
        .skips("combine-trig-factors", "sin(x) cos(y)")
        .fires("sine-double-angle", "2 sin(x) cos(x)", "sin(2x)")
        .skips("sine-double-angle", "sin(x) cos(x)")
        .fires("tanh-of-quotient", "sinh(x)/cosh(x)", "tanh(x)")
        .skips("tanh-of-quotient", "sinh(x)/cosh(y)")
        .fires("pythagorean", "sin(x)^2 + cos(x)^2 + y", "1 + y")
        .skips("pythagorean", "2 sin(x)^2 + 3 cos(x)^2")
        .fires("one-minus-sin-squared", "1 - sin(x)^2", "cos(x)^2")
        .skips("one-minus-sin-squared", "1 + sin(x)^2")
        .fires("one-minus-cos-squared", "1 - cos(x)^2", "sin(x)^2")
        .skips("one-minus-cos-squared", "2 - cos(x)^2")
        .fires("one-plus-tan-squared", "1 + tan(x)^2", "sec(x)^2")
        .skips("one-plus-tan-squared", "1 - tan(x)^2")
        .fires("one-plus-cot-squared", "1 + cot(x)^2", "csc(x)^2")
        .skips("one-plus-cot-squared", "2 + cot(x)^2")
        .fires("cosine-double-angle", "cos(x)^2 - sin(x)^2", "cos(2x)")
        .skips("cosine-double-angle", "cos(x)^2 - sin(y)^2")
        .fires("cosine-double-angle-from-cos", "2 cos(x)^2 - 1", "cos(2x)")
        .skips("cosine-double-angle-from-cos", "2 cos(x)^2 + 1")
        .fires("cosine-double-angle-from-sin", "1 - 2 sin(x)^2", "cos(2x)")
        .skips("cosine-double-angle-from-sin", "1 - 3 sin(x)^2")
        .fires("hyperbolic-pythagorean", "cosh(x)^2 - sinh(x)^2", "1")
        .skips("hyperbolic-pythagorean", "cosh(x)^2 + sinh(x)^2")
        .fires("one-plus-sinh-squared", "1 + sinh(x)^2", "cosh(x)^2")
        .skips("one-plus-sinh-squared", "2 + sinh(x)^2")
        .fires("cosh-squared-minus-one", "cosh(x)^2 - 1", "sinh(x)^2")
        .skips("cosh-squared-minus-one", "cosh(x)^2 + 1")
        .fires("one-minus-tanh-squared", "1 - tanh(x)^2", "sech(x)^2")
        .skips("one-minus-tanh-squared", "1 + tanh(x)^2")
        .fires("coth-squared-minus-one", "coth(x)^2 - 1", "csch(x)^2")
        .skips("coth-squared-minus-one", "coth(x)^2 + 1")
        // degrees: a symbolic plain-typed argument takes the unit's half turn, a closed one stays
        // radian, and a symbolic one with π takes neither
        .halfTurn(180)
        .fires("shift-by-half-turns", "sin(x + 180)", "-sin(x)")
        .skips("shift-by-half-turns", "sin(x + pi)")
        .skips("shift-by-multiple-of-pi", "sin(x + pi)")
        .fires("shift-by-multiple-of-pi", "sin(sqrt(2) + pi)", "-sin(sqrt(2))")
        .fires("reduce-half-turn-shift", "sin(x + 420)", "sin(x + 60)")
        .skips("reduce-half-turn-shift", "sin(x + 60)")
        .fires("quarter-turn-in-unit", "sin(x + 90)", "cos(x)")
        .skips("quarter-turn-in-unit", "sin(x + 60)")
        .skips("sin-plus-half-pi", "sin(x + pi/2)")
        .fires("acos-of-negative-in-unit", "acos(-x)", "180 - acos(x)")
        .skips("acos-of-negative-in-unit", "acos(-1/3)")
        .fires("acos-of-negative", "acos(-1/3)", "pi - acos(1/3)")
        .fires("asec-of-negative-in-unit", "asec(-x)", "180 - asec(x)")
        .skips("asec-of-negative-in-unit", "asec(1 - x)")
        .fires("atan-of-reciprocal-in-unit", "atan(1/x)", "90 - atan(x)", "x > 0")
        .skips("atan-of-reciprocal-in-unit", "atan(1/x)")
        // a whole number of turns times an integer symbol leaves a plain number, converted as the
        // evaluator converts it (sin(30) is sin(π/6) in degrees)
        .fires("shift-by-half-turns", "sin(360n + 30)", "1/2", "n ∈ ℤ")
        .fires("shift-by-half-turns", "cos(180n + 60)", "(-1)^n/2", "n ∈ ℤ")
        .skips("shift-by-half-turns", "cos(360.0n + 60.0)", "n ∈ ℤ")
        // constant-typed: the radian rules keep an argument read in radians, and leave none that
        // would be read in degrees
        .fires("shift-by-multiple-of-pi", "cos(pi k)", "(-1)^k", "k ∈ ℤ")
        .fires("reduce-rational-shift", "sin(x + 7pi/3)", "sin(x + pi/3)")
        .skips("shift-by-multiple-of-pi", "cos(x + 2 pi k)", "k ∈ ℤ")
        .skips("sin-plus-half-pi", "sin(pi/2 + x)")
        .skips("shift-by-multiple-of-pi", "sin(1.0 + pi)")
        .skips("shift-by-half-turns", "sin(sqrt(x) + 180)")
        // a remainder with a plain term beside a π whose coefficient can be 0 is plain there
        .skips("sin-plus-half-pi", "sin(pi y + x + pi/2)")
        .skips("sin-plus-half-pi", "sin(pi x + 2 + pi/2)")
        .skips("shift-by-multiple-of-pi", "cos(pi k + 2 pi + x)", "k ∈ ℤ")
        .skips("shift-by-multiple-of-pi", "sin(pi k + pi y + x)", "k ∈ ℤ")
        .fires("shift-by-multiple-of-pi", "sin(pi k + pi y)", "(-1)^k sin(pi y)", "k ∈ ℤ")
        // a reflection of a constant-typed argument is 0 where its coefficient vanishes
        .skips("acos-of-negative", "acos(-pi x)")
        .skips("asec-of-negative", "asec(-pi x)")
        .halfTurn(200)
        .fires("quarter-turn-in-unit", "cos(x - 100)", "sin(x)")
        .fires("shift-by-half-turns", "cos(x + 400)", "cos(x)")
        .halfTurn(0)
        .skips("shift-by-half-turns", "sin(x + 180)")
        .covered();
  }

  @Test
  void exactValues() {
    new RuleCases(RuleSets.TRIG)
        .fires("special-angle", "cos(pi/4)", "sqrt(2)/2")
        .fires("special-angle", "tan(pi/3)", "sqrt(3)")
        .fires("special-angle", "sin(pi/12)", "(sqrt(6) - sqrt(2))/4")
        .fires("special-angle", "cos(5pi/4)", "-sqrt(2)/2")
        .fires("special-angle", "tan(pi/2)", "undefined")
        .fires("special-angle", "cot(0)", "undefined")
        .fires("special-angle", "sec(pi/2)", "undefined")
        .fires("special-angle", "csc(-pi)", "undefined")
        .fires("special-angle", "sin(0)", "0")
        .fires("special-angle", "sin(7pi/5)", "-sin(2pi/5)")
        .fires("special-angle", "cos(3pi/5)", "-cos(2pi/5)")
        .fires("special-angle", "tan(-2pi/5)", "-tan(2pi/5)")
        .fires("special-angle", "sin(1000001 pi/6)", "1/2")
        .skips("special-angle", "sin(1)")
        .skips("special-angle", "sin(0.5 pi)")
        .fires("inverse-special-value", "acos(-sqrt(2)/2)", "3pi/4")
        .fires("inverse-special-value", "atan(sqrt(3) - 2)", "-pi/12")
        .fires("inverse-special-value", "asec(2)", "pi/3")
        .fires("inverse-special-value", "acot(0)", "pi/2")
        .fires("inverse-special-value", "acsc(-sqrt(2))", "-pi/4")
        .fires("inverse-special-value", "asin(sqrt(6)/4 - sqrt(2)/4)", "pi/12")
        .skips("inverse-special-value", "atan(x)")
        .fires("inverse-special-value", "asin((sqrt(2) - sqrt(6))/4)", "-pi/12")
        .fires("inverse-special-value", "asin(-(sqrt(6) - sqrt(2))/4)", "-pi/12")
        .fires("inverse-special-value", "acos((sqrt(2) - sqrt(6))/4)", "7pi/12")
        .fires("inverse-special-value", "acos(sqrt(2)/4 - sqrt(6)/4)", "7pi/12")
        .skips("inverse-special-value", "acot(x - (x + 1) + 1)")
        .skips("inverse-special-value", "acot(1 - y^2 - (1 - y^2))");
  }

  @Test
  void shiftsAndParity() {
    new RuleCases(RuleSets.TRIG)
        .fires("shift-by-multiple-of-pi", "cos(x + 2 pi k)", "cos(x)", "k ∈ ℤ")
        .fires("shift-by-multiple-of-pi", "cos(pi n)", "(-1)^n", "n ∈ ℤ")
        .fires("shift-by-multiple-of-pi", "sin(pi n)", "0", "n ∈ ℤ")
        .fires("shift-by-multiple-of-pi", "sin(pi/2 + pi n)", "(-1)^n", "n ∈ ℤ")
        .fires("shift-by-multiple-of-pi", "sin(x - pi n)", "(-1)^n sin(x)", "n ∈ ℤ")
        .fires("shift-by-multiple-of-pi", "tan(x - 3pi)", "tan(x)")
        .fires("reduce-rational-shift", "tan(x + 2pi/3)", "tan(x - pi/3)")
        .fires("sin-plus-half-pi", "sin(pi/2 - x)", "cos(x)")
        .fires("cos-plus-half-pi", "cos(pi/2 - x)", "sin(x)")
        .fires("shift-by-multiple-of-pi", "sin(pi - x)", "sin(x)")
        .fires("shift-by-multiple-of-pi", "cos(pi - x)", "-cos(x)")
        .fires("odd-function", "sin(y - x)", "-sin(x - y)")
        .fires("even-function", "cos(pi/3 - x)", "cos(x - pi/3)")
        .skips("odd-function", "sin(x - pi/3)")
        .fires("odd-function", "asinh(-x)", "-asinh(x)")
        .fires("odd-function", "acot(-x)", "-acot(x)", "x > 0")
        // acot(0) is π/2, so acot is odd only away from 0: a hidden zero must not flip.
        .skips("odd-function", "acot(-x)")
        .skips("odd-function", "acot(1 - y^2 - (1 - y^2))");
  }

  @Test
  void products() {
    new RuleCases(RuleSets.TRIG)
        .fires("combine-trig-factors", "1/cos(x)", "sec(x)")
        .fires("combine-trig-factors", "1/cos(x)^2", "sec(x)^2")
        .fires("combine-trig-factors", "sin(x)/cos(x)^2", "tan(x) sec(x)")
        .fires("combine-trig-factors", "sin(x)^2/cos(x)", "tan(x) sin(x)")
        .fires("combine-trig-factors", "tan(x) cos(x)", "sin(x)")
        .fires("combine-trig-factors", "cot(x) sin(x)", "cos(x)")
        .fires("combine-trig-factors", "sec(x) cos(x) y", "y")
        .fires("combine-trig-factors", "csc(x) sin(x)", "1")
        .fires("combine-trig-factors", "1/(sin(x) cos(x))", "csc(x) sec(x)")
        .fires("combine-trig-factors", "tan(x)^-1", "cot(x)")
        .skips("combine-trig-factors", "tan(x) sec(x)")
        .skips("combine-trig-factors", "sin(x)^(1/2) cos(x)^(-1/2)")
        .fires("sine-double-angle", "6 x sin(x) cos(x)", "3 x sin(2x)")
        .fires("sine-double-angle", "-2 sin(x) cos(x)", "-sin(2x)")
        .fires("tanh-of-quotient", "cosh(x)^2/sinh(x)^2", "tanh(x)^-2");
  }

  @Test
  void sums() {
    new RuleCases(RuleSets.TRIG)
        .fires("pythagorean", "3 sin(x)^2 + 3 cos(x)^2", "3")
        .fires("pythagorean", "a sin(x)^2 + a cos(x)^2", "a")
        .fires("pythagorean", "1 - sin(x)^2 - cos(x)^2", "0")
        .fires("one-minus-sin-squared", "a - a sin(x)^2", "a cos(x)^2")
        .fires("cosine-double-angle-from-cos", "cos(x)^2 - 1/2", "cos(2x)/2")
        .fires("one-plus-tan-squared", "1 + sin(x)^2/cos(x)^2", "sec(x)^2")
        .fires("hyperbolic-pythagorean", "sinh(x)^2 - cosh(x)^2", "-1")
        .fires("one-plus-sinh-squared", "a + a sinh(2x)^2", "a cosh(2x)^2")
        .fires("cosh-squared-minus-one", "3 cosh(x)^2 - 3", "3 sinh(x)^2")
        .fires("one-minus-tanh-squared", "2 - 2 tanh(x)^2", "2 sech(x)^2");
  }

  @Test
  void aZeroNotWrittenAsZeroKeepsTheValueOfAcotAtZero() {
    // acot(0) is π/2; the hidden zeros must not become -acot(0) = -π/2.
    MathContext mc = MathContext.DECIMAL128;
    BigDecimal halfPi = Elementary.pi(mc).value().divide(BigDecimal.TWO, mc);
    for (String source : List.of("acot(x - (x + 1) + 1)", "acot(1 - y^2 - (1 - y^2))")) {
      Expr e = Simplifier.simplify(RuleCases.parse(source), Context.DEFAULT).expr();
      Map<Symbol, Numeric> point =
          Map.of(Symbol.of("x"), Rational.of(3, 2), Symbol.of("y"), Rational.of(-5, 7));
      BigDecimal value = NumericEvaluator.eval(e, point, mc).orElseThrow().re().toReal(mc).value();
      assertThat(value.subtract(halfPi).abs()).as(source).isLessThan(new BigDecimal("1e-30"));
    }
  }
}
