package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static mathcanvas.engine.eval.Run.value;
import static mathcanvas.engine.testing.Inputs.canonical;
import static mathcanvas.engine.testing.Inputs.parse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.rule.Trace;
import org.junit.jupiter.api.Test;

class EvaluatorTest {

  // §14.1: exact by default, undefined.

  @Test
  void numbersAreExactUnlessWrittenAsDecimals() {
    assertThat(value("0.5")).isEqualTo(new Num(Real.of("0.5")));
    assertThat(value("1/3 + 1/6")).isEqualTo(canonical("1/2"));
    assertThat(shown("2pi")).isEqualTo("2π");
    assertThat(shown("x^-1")).isEqualTo("1/x");
  }

  @Test
  void undefinedArisesAndPropagates() {
    assertThat(value("1/0")).isEqualTo(Constant.UNDEFINED);
    assertThat(value("0/0")).isEqualTo(Constant.UNDEFINED);
    assertThat(value("0 * inf")).isEqualTo(Constant.UNDEFINED);
    assertThat(value("inf - inf")).isEqualTo(Constant.UNDEFINED);
    assertThat(value("undefined + 1")).isEqualTo(Constant.UNDEFINED);
  }

  // §14.4: relations.

  @Test
  void relationsBetweenNumbersAreDecided() {
    assertThat(value("2 < 3")).isEqualTo(Bool.TRUE);
    assertThat(value("1 = 1.0")).isEqualTo(Bool.TRUE);
    assertThat(value("x < 3")).isEqualTo(canonical("x < 3"));
    assertThat(value("a := 2; a >= 3")).isEqualTo(Bool.FALSE);
  }

  @Test
  void membershipOfNumbersIsDecidedByValue() {
    assertThat(value("2 ∈ ℤ")).isEqualTo(Bool.TRUE);
    assertThat(value("1/2 ∈ ℤ")).isEqualTo(Bool.FALSE);
    assertThat(value("2.0 ∈ ℤ")).isEqualTo(Bool.TRUE);
    assertThat(value("0.5 ∈ ℚ")).isEqualTo(Bool.TRUE);
    assertThat(value("i ∈ ℝ")).isEqualTo(Bool.FALSE);
    assertThat(value("i ∈ ℂ")).isEqualTo(Bool.TRUE);
    assertThat(value("n ∈ ℤ")).isEqualTo(canonical("n ∈ ℤ"));
    assertThat(value("n := 3; n ∈ ℤ")).isEqualTo(Bool.TRUE);
  }

  @Test
  void membershipNeedsADomainOrASet() {
    assertThat(value("2 ∈ {1, 2}")).isEqualTo(Bool.TRUE);
    assertThat(error("x ∈ 2"))
        .isEqualTo("`∈` needs a set or a number domain on its right, as in `2 ∈ {1, 2}`");
  }

  @Test
  void complexNumbersAreNotOrdered() {
    assertThat(error("i < 2")).isEqualTo("complex numbers cannot be compared with < or >");
    assertThat(error("x >= 1 + i")).isEqualTo("complex numbers cannot be compared with < or >");
    assertThat(value("i = 2")).isEqualTo(Bool.FALSE);
  }

  // §13: collections.

  @Test
  void setsDropDuplicates() {
    assertThat(value("{a, b, a}")).isEqualTo(canonical("{a, b}"));
  }

  @Test
  void bracketsAreClassifiedAgainAfterEvaluation() {
    assertThat(value("[a, b]")).isInstanceOf(Vector.class);
    assertThat(value("a := {1, 2}; [a, b]")).isInstanceOf(ListExpr.class);
    assertThat(value("[[1, 2], [3, 4]]")).isInstanceOf(Matrix.class);
    assertThat(value("r := [1, 2]; [r, [3, 4]]")).isInstanceOf(Matrix.class);
    assertThat(value("[[1, 2], [3]]")).isInstanceOf(ListExpr.class);
  }

  // §14.3: definitions.

  @Test
  void definitionsAreDelayed() {
    assertThat(value("a := 1; b := 2a; a := 3; b")).isEqualTo(Num.of(6));
    assertThat(value("a := x + 1; x := 2; a")).isEqualTo(Num.of(3));
  }

  @Test
  void aCycleIsAnError() {
    assertThat(error("a := b; b := a; a")).isEqualTo("`a` is defined in terms of itself");
    assertThat(error("a := a + 1; a")).isEqualTo("`a` is defined in terms of itself");
    assertThat(error("f(x) := x f(x - 1); f(3)"))
        .isEqualTo(
            "`f` is defined in terms of itself: a function that calls itself needs an `if` that stops it, as in `f(n) := if n = 0 then 1 else n f(n - 1)`");
  }

  @Test
  void aDefinitionUsedTwiceIsNoCycle() {
    assertThat(value("a := 2; b := a + a; c := b * b; c")).isEqualTo(Num.of(16));
    StringBuilder chain = new StringBuilder("a0 := 1");
    for (int k = 1; k <= 60; k++) {
      chain.append("; a").append(k).append(" := a").append(k - 1).append(" + a").append(k - 1);
    }
    assertThat(value(chain + "; a60"))
        .isEqualTo(new Num(mathcanvas.engine.number.Int.of(1L << 60)));
  }

  @Test
  void functionsAreCalled() {
    assertThat(value("f(x) := x^2 + 1; f(2)")).isEqualTo(Num.of(5));
    assertThat(value("f := x -> x^2; f(3)")).isEqualTo(Num.of(9));
    assertThat(value("g(x, y) := x - y; g(5, 2)")).isEqualTo(Num.of(3));
    assertThat(value("f(x) := 2x; f(f(f(1)))")).isEqualTo(Num.of(8));
  }

  @Test
  void parametersShadowDefinitions() {
    assertThat(value("x := 3; f(x) := x^2; f(2)")).isEqualTo(Num.of(4));
    assertThat(value("x := 3; f(x) := x^2; f(y)")).isEqualTo(canonical("y^2"));
  }

  @Test
  void aStoredCallOfARedefinedNameFails() {
    assertThat(error("f(t) := t^2; g(x) := f(x); f := 3; g(2)"))
        .isEqualTo("`f` is not a function: define it first, as in `f(x) := x^2`");
    assertThat(error("f(t) := t^2; g(x) := f(x); clear(f); g(2)"))
        .isEqualTo("`f` is not a function: define it first, as in `f(x) := x^2`");
  }

  @Test
  void theArityMustMatch() {
    assertThat(error("f(x) := x^2; f(1, 2)")).isEqualTo("`f` takes 1 argument");
    assertThat(error("g(x, y) := x; g(1)")).isEqualTo("`g` takes 2 arguments");
  }

  @Test
  void aFunctionIsNotAFactor() {
    assertThat(error("(x -> x^2)(3)"))
        .isEqualTo("a function cannot be multiplied: give it a name and call it, as in `f(3)`");
    Environment env = Environment.EMPTY.with("f", new Definition.Function(List.of(X), X));
    assertThatThrownBy(() -> Evaluator.evaluate(Symbol.of("f"), env, false))
        .isInstanceOf(EvaluationException.class)
        .hasMessage("a function cannot be multiplied: give it a name and call it, as in `f(3)`");
  }

  @Test
  void aLambdaValueIsHeld() {
    assertThat(value("a := 1; x -> x + a")).isEqualTo(canonical("x -> x + a"));
  }

  static final Symbol X = Symbol.of("x");

  @Test
  void numbersBeyondTheRangeAreAnError() {
    assertThat(error("1e999999999 * 1e999999999 * 1e999999999"))
        .isEqualTo("a number grew too large or too small for this precision");
  }

  @Test
  void setsListsAndTruthValuesCarryNoArithmetic() {
    assertThat(error("{1, 2} + 1"))
        .isEqualTo("sets cannot be added, multiplied or raised to powers");
    assertThat(error("2 * [x = 1, y = 2]"))
        .isEqualTo("lists cannot be added, multiplied or raised to powers");
    assertThat(error("true^2"))
        .isEqualTo("true and false cannot be added, multiplied or raised to powers");
    assertThat(error("a := 2 < 3; a + 1"))
        .isEqualTo("true and false cannot be added, multiplied or raised to powers");
  }

  @Test
  void aResultTooLargeIsAnError() {
    StringBuilder doubling = new StringBuilder("h0 := 1");
    for (int k = 1; k <= 30; k++) {
      doubling.append("; h").append(k).append(" := [h").append(k - 1).append(", h");
      doubling.append(k - 1).append("]");
    }
    assertThat(error(doubling + "; h30")).isEqualTo("the result is too large");
  }

  // §12.2: commands.

  @Test
  void heldArgumentsReachTheCommandAsSymbols() {
    Expr solve = value("x := 3; solve(x^2 = 4, x)");
    assertThat(solve).isEqualTo(canonical("{-2, 2}"));
    assertThat(value("a := 2; solve(x^2 = a, x)")).isEqualTo(canonical("{-sqrt(2), sqrt(2)}"));
    // diff holds x², and its variable is free in the result 2x, which is then evaluated (§14.10).
    assertThat(value("x := 3; d/dx x^2")).isEqualTo(canonical("6"));
  }

  @Test
  void theBoundsOfABinderAreEvaluated() {
    assertThat(shown("x := 3; integrate(x!, x, 0, x)")).isEqualTo("∫[0, 3] x! dx");
    assertThat(shown("a := 1; lim[x → a] x!")).isEqualTo("lim[x → 1] x!");
    assertThat(shown("x := 7; limit(x!, x, 0, left)")).isEqualTo("lim[x → 0-] x!");
    assertThat(value("k := 5; sum(k, k, 1, 10)")).isEqualTo(Num.of(55));
  }

  @Test
  void aCommandWhoseArgumentsComeToFitIsItsTypedNode() {
    assertThat(value("diff(factorial(x), x, 2)"))
        .isEqualTo(new Derivative(canonical("factorial(x)"), X, 2));
    assertThat(value("diff(x^2, x, 1/2)")).isInstanceOf(Apply.class);
  }

  @Test
  void aBinderKeepsItsNodesAndItsName() {
    Expr raw = parse("sum(slider([[1]], 0, 1), k, 1, 3)");
    Expr call = ((BigSum) raw).body();
    assertThatThrownBy(() -> Evaluator.evaluate(raw, Environment.EMPTY, false))
        .isInstanceOfSatisfying(
            EvaluationException.class, e -> assertThat(e.node()).isSameAs(call));
    // the inner sum is 3 and the outer index its own: 3·3 + 6
    assertThat(shown("sum(sum(k, k, 1, 2) + k, k, 1, 3)")).isEqualTo("15");
    assertThat(shown("k := 5; sum(k^2 + j, k, 1, j)")).isEqualTo("j·(2j² + 9j + 1)/6");
  }

  @Test
  void aFrameValueIsNeitherEvaluatedAgainNorCaptured() {
    assertThat(shown("f(t) := w; g(s) := s; g(f(1)) + sum(f(1), w, 1, 3)")).isEqualTo("4w");
    assertThat(value("f(t) := k; g(s) := s; g(f(1)) + diff(f(1), k)"))
        .isEqualTo(value("k + diff(k, k_1)"));
    assertThat(shown("f(t) := (x -> x + t); f(2)")).isEqualTo("x → x + 2");
    // the k passed in is not the index: 6k, where a capture would give 14
    assertThat(shown("f(t) := sum(t k, k, 1, 3); f(k)")).isEqualTo("6k");
    assertThat(shown("subs(sum(x k, k, 1, 3), x = k)")).isEqualTo("6k");
    assertThat(value("sum(subs(k + x, x = 2), k, 1, n)")).isEqualTo(value("sum(k + 2, k, 1, n)"));
  }

  @Test
  void theCaptureCheckRenamesOnlyWhatItMust() {
    // k names a value definition, always expanded: no capture is possible, so no renaming.
    assertThat(shown("k := 7; b := k^2; sum(k b, k, 1, 3)")).isEqualTo("294");
    assertThat(shown("a := k; k := 3; sum(a, k, 1, 3)")).isEqualTo("9");
    // Unless the name is in a lambda's body, which is held and keeps it free.
    assertThat(shown("k := 7; f(t) := (x -> x + k); sum(f(1), k, 1, 3)"))
        .isEqualTo("∑[k₁ = 1, 3] (x → k + x)");
    // A lambda written in the scope means the bound k: nothing to rename.
    assertThat(shown("k := 7; sum((x -> x + k), k, 1, 3)")).isEqualTo("∑[k = 1, 3] (x → k + x)");
    // A variable already numbered counts from its base.
    assertThat(shown("a := k_1; sum(a, k_1, 1, 3)")).isEqualTo("3k₁");
  }

  @Test
  void aFailedCallCarriesASmallNode() {
    StringBuilder script = new StringBuilder("g0(x) := slider(x, 0, 1)");
    for (int k = 1; k <= 11; k++) {
      script.append("; g").append(k).append("(x) := g").append(k - 1).append("([x, x])");
    }
    Outcome o = Run.last(script + "; g11(1)");
    assertThat(o).isInstanceOf(Outcome.Failed.class);
    EvaluationException e = ((Outcome.Failed) o).error();
    assertThat(e.getMessage())
        .isEqualTo(
            "`slider` works only as a cell of its own in the app, as in `a := slider(0, 5, 0.5)`");
    assertThat(e.node().toString()).hasSizeLessThan(100);
  }

  @Test
  void aDefinitionInsideABinderIsNotCaptured() {
    assertThat(shown("b := k^2; sum(k*b, k, 1, 3)")).isEqualTo("6k²");
    assertThat(shown("f(x) := sum(x*k, k, 1, 3); f(k)")).isEqualTo("6k");
    assertThat(shown("h(x) := (t -> t + x); h(t)")).isEqualTo("t₁ → t + t₁");
    assertThat(shown("f(t) := t + k; sum(f(1), k, 1, 3)")).isEqualTo("3(k + 1)");
    assertThat(shown("f(t) := t + k; sum(f(k), k, 1, 3)")).isEqualTo("3k + 6");
    assertThat(shown("a := k; b := k_1; sum(a + b, k, 1, 3)")).isEqualTo("3(k + k₁)");
    assertThat(shown("a := k; sum(sum(a, k, 1, 2), k, 1, 3)")).isEqualTo("6k");
  }

  @Test
  void laterCommandsReturnTheirApplication() {
    assertThat(value("solve({x + y = 3, x - y = 1}, [x, y])"))
        .isEqualTo(canonical("{[x = 2, y = 1]}"));
  }

  @Test
  void subsSubstitutesInTheHeldParse() {
    assertThat(shown("a := 5; subs(x^2 + a, a = 2)")).isEqualTo("x² + 2");
    assertThat(value("a := 5; b := a + 1; subs(b, a = 2)")).isEqualTo(Num.of(6));
    assertThat(value("a := 5; subs(a + 1, a = 2)")).isEqualTo(Num.of(3));
    assertThat(value("subs(x - y, x = y, y = 2x)")).isEqualTo(canonical("y - 2x"));
    assertThat(value("v := 4; subs(x^2, x = v)")).isEqualTo(Num.of(16));
    assertThat(value("subs(sum(x k, k, 1, n), k = 2)")).isEqualTo(value("sum(x k, k, 1, n)"));
    assertThat(error("subs(x^2, 2)"))
        .isEqualTo("`subs` needs pairs name = value, as in `subs(x^2, x = 3)`");
    assertThat(error("subs(x^2, x^2 = 2)"))
        .isEqualTo("`subs` needs pairs name = value, as in `subs(x^2, x = 3)`");
  }

  @Test
  void simplifyAndStepsGiveTheValue() {
    assertThat(value("simplify(1/3 + 1/6)")).isEqualTo(canonical("1/2"));
    Outcome steps = Run.last("steps(x + x)");
    assertThat(((Outcome.Value) steps).value()).isEqualTo(canonical("2x"));
    assertThat(((Outcome.Value) steps).trace()).isNotEqualTo(Trace.NONE);
    assertThat(((Outcome.Value) Run.last("x + x")).trace()).isEqualTo(Trace.NONE);
    assertThat(value("1 + steps(x)")).isEqualTo(canonical("x + 1"));
  }

  @Test
  void assumeAndClearAreStatements() {
    assertThat(error("1 + assume(x > 0)"))
        .isEqualTo("`assume` works only as a statement of its own, not inside an expression");
    assertThat(error("[clear(x)]"))
        .isEqualTo("`clear` works only as a statement of its own, not inside an expression");
    assertThat(value("assume(x > 0); assumptions()")).isEqualTo(canonical("{x > 0}"));
    assertThat(value("assumptions()")).isEqualTo(new SetExpr(List.of()));
  }

  @Test
  void reservedNamesAreNotAvailable() {
    assertThat(error("slider(0, 1, 2)"))
        .isEqualTo(
            "`slider` works only as a cell of its own in the app, as in `a := slider(0, 5, 0.5)`");
    assertThat(error("plot(x^2, sin(x))"))
        .isEqualTo("`plot` works only as a cell of its own in the app, as in `plot(x^2)`");
  }

  @Test
  void helpGivesTheNameItHolds() {
    assertThat(value("help(sin)")).isEqualTo(Symbol.of("sin"));
    assertThat(value("help(integrate)")).isEqualTo(Symbol.of("integrate"));
    assertThat(value("x := 3; help(x)")).isEqualTo(Symbol.of("x"));
    assertThat(error("help(2 + 3)")).isEqualTo("`help` needs a name, as in `help(sin)`");
  }

  @Test
  void anErrorCarriesTheRawNodeThatFailed() {
    Expr raw = parse("1 + slider([[1, 2], [3, 4]], 0, 1)");
    Expr call = ((mathcanvas.engine.expr.Sum) raw).terms().get(1);
    assertThatThrownBy(() -> Evaluator.evaluate(raw, Environment.EMPTY, false))
        .isInstanceOfSatisfying(
            EvaluationException.class, e -> assertThat(e.node()).isSameAs(call));
    Expr relation = parse("i < 2");
    assertThatThrownBy(() -> Evaluator.evaluate(relation, Environment.EMPTY, false))
        .isInstanceOfSatisfying(
            EvaluationException.class, e -> assertThat(e.node()).isSameAs(relation));
  }

  @Test
  void theValueIsCanonical() {
    Expr v = value("a := y; x a + a x");
    assertThat(v).isEqualTo(canonical("2 x y"));
    assertThat(v).isInstanceOf(Product.class);
    assertThat(value("b := 3; x < b")).isInstanceOf(Relation.class);
    assertThat(value("x -> x")).isInstanceOf(Lambda.class);
  }
}
