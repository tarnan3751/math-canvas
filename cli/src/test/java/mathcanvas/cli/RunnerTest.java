package mathcanvas.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Outcome;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.print.AsciiPrinter;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Trace;
import org.junit.jupiter.api.Test;

class RunnerTest {

  private static final Symbol X = Symbol.of("x");

  @Test
  void stepsAreNumberedEachWithTheWholeExpressionAfterIt() {
    Expr start = Sum.of(Product.of(Num.ONE, X), Num.ZERO);
    Rewrite one =
        new Rewrite(Product.of(Num.ONE, X), X, "basic", "one-times", path(0), Bindings.EMPTY);
    Rewrite zero =
        new Rewrite(Sum.of(X, Num.ZERO), X, "basic", "plus-zero", Path.ROOT, Bindings.EMPTY);
    Trace trace = new Trace(start, X, List.of(one, zero), List.of());
    Runner runner = new Runner(UnicodePrinter.INSTANCE, 34, AngleMode.RADIANS, false);
    assertThat(runner.valueLines(new Outcome.Value(X, trace, false)))
        .containsExactly("  step 1: one-times", "  = x", "  step 2: plus-zero", "  = x", "x");
    assertThat(runner.valueLines(new Outcome.Value(X, Trace.NONE, false))).containsExactly("x");
  }

  @Test
  void helpPrintsTheEntryOfTheReference() {
    Runner runner = new Runner(UnicodePrinter.INSTANCE, 34, AngleMode.RADIANS, false);
    List<String> abs = runner.run(new Units.Unit("help(abs)", 1), false).getFirst().lines();
    assertThat(abs)
        .containsExactly(
            "abs — Functions, §12.1 of the language reference",
            "  arguments: 1",
            "  also written |x|",
            "  examples:",
            "  abs(-3) → 3");
    assertThat(runner.run(new Units.Unit("help(nosuchname)", 1), false).getFirst().lines())
        .containsExactly(
            "no help for nosuchname: help takes a function's or a command's name; the operators,"
                + " mod included, are in the app's Help window");
    assertThat(runner.run(new Units.Unit("help(abs);", 1), false).getFirst().lines()).isEmpty();
    assertThat(runner.run(new Units.Unit("[help(abs)]", 1), false).getFirst().lines())
        .containsExactly("⟨abs⟩");
  }

  @Test
  void aTracedValueWithNoRewritesSaysSo() {
    Trace trace = new Trace(X, X, List.of(), List.of());
    Runner runner = new Runner(UnicodePrinter.INSTANCE, 34, AngleMode.RADIANS, false);
    assertThat(runner.valueLines(new Outcome.Value(X, trace, false)))
        .containsExactly("  (no steps to show)", "x");
  }

  @Test
  void stepsUseThePrinter() {
    Expr start = Sum.of(Product.of(Num.ONE, X), Num.of(2));
    Rewrite one =
        new Rewrite(Product.of(Num.ONE, X), X, "basic", "one-times", path(0), Bindings.EMPTY);
    Trace trace = new Trace(start, Sum.of(X, Num.of(2)), List.of(one), List.of());
    Runner runner = new Runner(AsciiPrinter.INSTANCE, 34, AngleMode.RADIANS, false);
    assertThat(runner.valueLines(new Outcome.Value(Sum.of(X, Num.of(2)), trace, false)))
        .containsExactly("  step 1: one-times", "  = x + 2", "x + 2");
  }

  @Test
  void aLongDisplayInAMessageIsCut() {
    String shown = "x".repeat(150) + "π".repeat(100);
    assertThat(Runner.shorten("not an assumption about a symbol: " + shown))
        .isEqualTo("not an assumption about a symbol: " + "x".repeat(150) + "π".repeat(50) + "…");
    String fits = "y".repeat(200);
    assertThat(Runner.shorten("gist: " + fits)).isEqualTo("gist: " + fits);
    assertThat(Runner.shorten("a is defined in terms of itself"))
        .isEqualTo("a is defined in terms of itself");
  }

  @Test
  void aFailureFormatsWithACaretUnderItsColumn() {
    assertThat(new Runner.Failure(3, 4, "boom", "\tab").format("f.mc"))
        .isEqualTo("f.mc:3:4: boom\n\tab\n\t  ^\n");
    assertThat(new Runner.Failure(1, 3, "expected `)`", "(1").format("<eval>"))
        .isEqualTo("<eval>:1:3: expected `)`\n(1\n  ^\n");
  }

  private static Path path(int... indices) {
    return new Path(java.util.Arrays.stream(indices).boxed().toList());
  }
}
