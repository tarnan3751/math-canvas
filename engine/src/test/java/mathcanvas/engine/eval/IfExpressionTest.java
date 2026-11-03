package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.LatexPrinter;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.testing.DeepStack;
import org.junit.jupiter.api.Test;

/** The if expression and recursion through it ({@code docs/language.md} §19, §8.3, §14.3). */
class IfExpressionTest {

  @Test
  void theConditionChoosesTheBranch() {
    assertThat(shown("if 2 < 3 then a else b")).isEqualTo("a");
    assertThat(shown("if 2 > 3 then a else b")).isEqualTo("b");
    assertThat(shown("x := 3; if x > 0 then 1 else -1")).isEqualTo("1");
    assertThat(shown("if true and 2 ∈ {1, 2} then 1 else 0")).isEqualTo("1");
    assertThat(shown("y := if 1 = 1.0 then 5 else 6; y + 1")).isEqualTo("6");
  }

  @Test
  void theSimplifierDecidesTheCondition() {
    assertThat(shown("if sin(π/6) = 1/2 then 1 else 0")).isEqualTo("1");
    assertThat(shown("if cos(π) < 0 then 1 else 0")).isEqualTo("1");
  }

  @Test
  void theAssumptionsDecideASignCondition() {
    assertThat(shown("assume(y > 0); if y > 0 then 1 else 0")).isEqualTo("1");
    assertThat(shown("assume(y > 0); if y + 1 ≤ 0 then 1 else 0")).isEqualTo("0");
    assertThat(shown("assume(y > 0); if y ≠ 0 then 1 else 0")).isEqualTo("1");
    assertThat(shown("assume(y < 0); if 0 < y then 1 else 0")).isEqualTo("0");
    assertThat(error("assume(y > 0); if y > 1 then 1 else 0"))
        .isEqualTo("the condition did not decide: y > 1");
  }

  @Test
  void onlyTheChosenBranchIsEvaluated() {
    assertThat(shown("if true then 1 else [1, 2] + [1, 2, 3]")).isEqualTo("1");
    assertThat(shown("if false then [1, 2] + [1, 2, 3] else 2")).isEqualTo("2");
  }

  @Test
  void aConditionThatStaysIsAnError() {
    assertThat(error("if x > 0 then 1 else 0")).isEqualTo("the condition did not decide: x > 0");
    assertThat(error("if 3 then 1 else 0")).isEqualTo("the condition did not decide: 3");
  }

  @Test
  void recursionThroughAnIf() {
    assertThat(shown("f(n) := if n = 0 then 1 else n f(n - 1); f(10)")).isEqualTo("3628800");
    assertThat(shown("fib(n) := if n < 2 then n else fib(n - 1) + fib(n - 2); fib(15)"))
        .isEqualTo("610");
    assertThat(
            shown(
                "odd(n) := 0; even(n) := if n = 0 then true else odd(n - 1);"
                    + " odd(n) := if n = 0 then false else even(n - 1); even(10)"))
        .isEqualTo("true");
  }

  @Test
  void recursionWithoutAConditionIsStillTheCycleError() {
    assertThat(error("f(x) := x f(x - 1); f(3)")).isEqualTo("f is defined in terms of itself");
    assertThat(error("a := b; b := a; a")).isEqualTo("a is defined in terms of itself");
  }

  @Test
  void theCallLimit() {
    assertThat(DeepStack.run(() -> shown("f(n) := if n = 0 then 0 else f(n - 1); f(999)")))
        .isEqualTo("0");
    assertThat(DeepStack.run(() -> error("f(n) := if n = 0 then 0 else f(n - 1); f(1000)")))
        .isEqualTo("recursion deeper than 1000 calls");
  }

  @Test
  void printed() {
    var f = Parser.parseExpression("if n = 0 then 1 else n f(n - 1)", n -> n.equals("f"));
    assertThat(UnicodePrinter.INSTANCE.print(f)).isEqualTo("if n = 0 then 1 else n f(n - 1)");
    assertThat(LatexPrinter.INSTANCE.print(f))
        .isEqualTo("\\text{if } n = 0 \\text{ then } 1 \\text{ else } n f(n-1)");
    var nested = Parser.parseExpression("(if p then 1 else 2) + 1", Names.NONE);
    assertThat(UnicodePrinter.INSTANCE.print(nested)).isEqualTo("(if p then 1 else 2) + 1");
    var lambda = Parser.parseExpression("x -> if x < 0 then -x else x", Names.NONE);
    assertThat(UnicodePrinter.INSTANCE.print(lambda)).isEqualTo("x → if x < 0 then -x else x");
  }
}
