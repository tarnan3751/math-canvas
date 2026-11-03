package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.testing.DeepStack;
import org.junit.jupiter.api.Test;

/** Programs ({@code docs/language.md} §19): blocks, control flow, functions, scope and guards. */
class ProgramTest {

  static final String FACT =
      """
      function fact(n)
        r := 1
        for k in 1..n
          r := r k
        end
        return r
      end
      """;

  static final String COLLATZ =
      """
      function collatz(n)
        count := 0
        while n ≠ 1
          if n mod 2 = 0 then n := n/2 else n := 3n + 1 end
          count := count + 1
        end
        return count
      end
      """;

  /** The outcomes of a script, in a new session. */
  static List<Outcome> outcomes(String script) {
    Session s = new Session();
    return s.run(Parser.parseScript(script, s.names()));
  }

  @Test
  void theBriefsExamples() {
    assertThat(shown(FACT + "fact(5)")).isEqualTo("120");
    assertThat(shown(FACT + "fact(0)")).isEqualTo("1");
    assertThat(shown(COLLATZ + "collatz(27)")).isEqualTo("111");
  }

  @Test
  void aFunctionEchoesItsHead() {
    Outcome o = outcomes(FACT).getFirst();
    assertThat(o).isInstanceOf(Outcome.DefinedFunction.class);
    assertThat(((Outcome.DefinedFunction) o).echo().name().name()).isEqualTo("fact");
  }

  @Test
  void topLevelBlocksWriteTheDocument() {
    assertThat(shown("s := 0\nfor k in 1..10\n  s := s + k\nend\ns")).isEqualTo("55");
    assertThat(shown("for k in 1..3 do t := k^2 end; t")).isEqualTo("9");
    // a loop has no value; an if whose branch is an expression has
    assertThat(outcomes("for k in 1..3 do k end").getLast()).isInstanceOf(Outcome.Executed.class);
    assertThat(shown("x := 5\nif x > 3 then\n  big\nelse\n  small\nend")).isEqualTo("big");
    assertThat(shown("if 1 > 3 then a else if 2 > 3 then b else c end")).isEqualTo("c");
    assertThat(outcomes("if 1 > 3 then a end").getLast()).isInstanceOf(Outcome.Executed.class);
  }

  @Test
  void aComputedValueIsNotEvaluatedAgain() {
    Settings degrees = Settings.DEFAULT.withAngleMode(AngleMode.DEGREES);
    assertThat(shown("if true then a := asin(t) end; a", degrees))
        .isEqualTo(shown("asin(t)", degrees));
    // a top-level assignment outside a block stays delayed
    assertThat(shown("n := 1; b := n + 1; for k in 1..2 do n := n + 1 end; b")).isEqualTo("4");
    assertThat(shown("n := 1; for k in 1..1 do b := n + 1 end; n := 5; b")).isEqualTo("2");
  }

  @Test
  void rangesAndItems() {
    assertThat(shown("s := 0; for k in 10..1 step -3 do s := s + k end; s")).isEqualTo("22");
    assertThat(shown("s := 0; for k in 5..1 do s := s + k end; s")).isEqualTo("0");
    assertThat(shown("s := 0; for k in 0x2..4 do s := s + k end; s")).isEqualTo("9");
    assertThat(shown("s := 0; for v in [a, b, a] do s := s + v end; s")).isEqualTo("2a + b");
    assertThat(shown("s := 0; for v in {3, 1, 2} do s := 10 s + v end; s")).isEqualTo("123");
    assertThat(shown("s := 0; for v in ⟨1, 2⟩ do s := s + v end; s")).isEqualTo("3");
    assertThat(shown("s := 0; for r in [[1, 2], [3, 4]] do s := s + r end; s")).isEqualTo("⟨4, 6⟩");
    assertThat(error("for k in 1..2.5 do k end")).isEqualTo("a range needs integers");
    assertThat(error("for k in 1..5 step 0 do k end"))
        .isEqualTo("the step of a range must not be 0");
    assertThat(error("for k in x do k end"))
        .isEqualTo("for iterates a list, a vector, a set or a range a..b");
  }

  @Test
  void aLoopVariableIsLocalToItsLoop() {
    assertThat(shown("k := 7; for k in 1..3 do k end; k")).isEqualTo("7");
    assertThat(shown("for k in 1..3 do k end; k")).isEqualTo("k");
    assertThat(shown("s := 0; for i in 1..3 do s := s + i end; s + i")).isEqualTo("6 + i");
    assertThat(shown("n := 0; for k in 1..5 do k := 10; n := n + k end; n")).isEqualTo("50");
  }

  @Test
  void breakAndContinue() {
    assertThat(shown("s := 0; for k in 1..10 do if k > 4 then break end; s := s + k end; s"))
        .isEqualTo("10");
    assertThat(
            shown("s := 0; for k in 1..6 do if k mod 2 = 0 then continue end; s := s + k end; s"))
        .isEqualTo("9");
    assertThat(shown("n := 0; while true do n := n + 1; if n = 5 then break end end; n"))
        .isEqualTo("5");
  }

  @Test
  void functionScope() {
    // parameters and assigned names are local; others are the document's
    assertThat(shown("r := 9; function f(x)\n r := x + 1\n return r\nend; f(1) + r"))
        .isEqualTo("11");
    assertThat(shown("a := 5; function f(x) return x + a end; f(1)")).isEqualTo("6");
    assertThat(error("r := 9; function f(x)\n y := r\n r := 1\n return y\nend; f(1)"))
        .isEqualTo("in f, line 2: `r` is read before it is assigned");
    // global writes the document, and may create a name
    assertThat(
            shown(
                "count := 0; function tick()\n global count\n count := count + 1\nend;"
                    + " tick(); tick(); count"))
        .isEqualTo("2");
    assertThat(shown("function g() global made; made := 4 end; g(); made")).isEqualTo("4");
    // a function's body sees no loop variable of its caller
    assertThat(shown("function h() return k end; for k in 1..2 do v := h() end; v")).isEqualTo("k");
  }

  @Test
  void valuesOfCalls() {
    assertThat(shown("function f(x) x^2 end; f(3)")).isEqualTo("9");
    assertThat(shown("function f(x) if x > 0 then 1 else -1 end end; f(-2)")).isEqualTo("-1");
    assertThat(outcomes("function f(x) y := x end; f(3)").getLast())
        .isInstanceOf(Outcome.Executed.class);
    assertThat(error("function f(x) y := x end; f(3) + 1")).isEqualTo("f has no value");
    assertThat(shown("function zero() return 0 end; zero() + 1")).isEqualTo("1");
    assertThat(error("function f(x) return x end; f(1, 2)")).isEqualTo("f takes 1 argument");
  }

  @Test
  void recursionThroughFunctions() {
    String fib =
        "function fib(n)\n if n < 2 then return n end\n return fib(n - 1) + fib(n - 2)\nend\n";
    assertThat(shown(fib + "fib(12)")).isEqualTo("144");
    assertThat(
            DeepStack.run(
                () -> shown("function d(n) if n = 0 then 0 else d(n - 1) end end; d(999)")))
        .isEqualTo("0");
    assertThat(
            DeepStack.run(
                () -> error("function d(n) if n = 0 then 0 else d(n - 1) end end; d(1000)")))
        .endsWith("recursion deeper than 1000 calls");
  }

  @Test
  void theBudget() {
    assertThat(error("while true do end")).isEqualTo("the program ran more than 1000000 steps");
    // a call is a step, so a recursion that branches widely ends at the budget too
    assertThat(error("fib(n) := if n < 2 then n else fib(n - 1) + fib(n - 2); fib(30)"))
        .isEqualTo("the program ran more than 1000000 steps");
  }

  @Test
  void aOneLineIfEndedBySemicolonIsQuiet() {
    Outcome o = outcomes("if 1 < 2 then 3 else 4;").getLast();
    assertThat(o).isInstanceOfSatisfying(Outcome.Value.class, v -> assertThat(v.quiet()).isTrue());
    Outcome shown = outcomes("if 1 < 2 then 3 else 4").getLast();
    assertThat(shown)
        .isInstanceOfSatisfying(Outcome.Value.class, v -> assertThat(v.quiet()).isFalse());
    // a ; between a branch's statements is not the statement's
    assertThat(outcomes("if 1 < 2 then a := 1; 5").getLast())
        .isInstanceOfSatisfying(Outcome.Value.class, v -> assertThat(v.quiet()).isFalse());
  }

  @Test
  void theAssumptionsDecideAStatementsCondition() {
    assertThat(shown("assume(y > 0)\nif y > 0 then 1 else 0")).isEqualTo("1");
    assertThat(shown("assume(y > 0); n := 0; while y < 0 do n := n + 1 end; n")).isEqualTo("0");
  }

  @Test
  void cancellationStopsALoop() throws Exception {
    Session session = new Session();
    session.run(Parser.parseScript("n := 0", session.names()));
    java.util.concurrent.atomic.AtomicBoolean stop =
        new java.util.concurrent.atomic.AtomicBoolean();
    Thread stopper =
        new Thread(
            () -> {
              try {
                Thread.sleep(100);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              stop.set(true);
            });
    stopper.start();
    long start = System.nanoTime();
    List<Outcome> out =
        session.run(Parser.parseScript("while true do n := n + 1 end", session.names()), stop::get);
    long millis = (System.nanoTime() - start) / 1_000_000;
    stopper.join();
    assertThat(out.getLast()).isInstanceOf(Outcome.Cancelled.class);
    assertThat(millis).isLessThan(1000);
    // nothing of the stopped statement stays
    assertThat(session.environment().definition("n").orElseThrow())
        .isInstanceOf(Definition.Value.class);
  }

  /** The printed values of an outcome, in Unicode. */
  private static List<String> printed(Outcome o) {
    var log =
        switch (o) {
          case Outcome.Value v -> v.run();
          case Outcome.Executed e -> e.run();
          case Outcome.Failed f -> f.run();
          default -> throw new AssertionError(o);
        };
    return log.printed().stream().map(UnicodePrinter.INSTANCE::display).toList();
  }

  @Test
  void printAppendsToTheOutput() {
    Outcome loop = outcomes("for p in primes(1, 10) do print(p^2) end").getLast();
    assertThat(loop).isInstanceOf(Outcome.Executed.class);
    assertThat(printed(loop)).containsExactly("4", "9", "25", "49");
    // ; hides the value, not the output
    assertThat(printed(outcomes("for k in 1..2 do print(k) end;").getLast()))
        .containsExactly("1", "2");
    // outside a program print(e) is e
    Outcome top = outcomes("print(2 + 3)").getLast();
    assertThat(shown("print(2 + 3)")).isEqualTo("5");
    assertThat(printed(top)).isEmpty();
    // a function called from an expression prints into the statement's output
    Outcome call = outcomes("function f(x)\n print(x)\n return x + 1\nend; f(1) + f(2)").getLast();
    assertThat(((Outcome.Value) call).value()).hasToString(Run.canonical("5").toString());
    assertThat(printed(call)).containsExactly("1", "2");
    // a failure keeps what was printed before it
    Outcome failed =
        outcomes("for k in 1..3 do print(k); if k = 2 then [1] + [1, 2] end end").getLast();
    assertThat(failed).isInstanceOf(Outcome.Failed.class);
    assertThat(printed(failed)).containsExactly("1", "2");
  }

  @Test
  void conditionsMustDecide() {
    assertThat(error("if y > 0 then 1 end")).isEqualTo("the condition did not decide: y > 0");
    assertThat(error("while y do end")).isEqualTo("the condition did not decide: y");
  }

  @Test
  void errorsInsideAFunctionArePlacedAtTheCall() {
    assertThat(error("function f(x)\n y := 1\n return [1] + [1, 2]\nend; f(1)"))
        .isEqualTo("in f, line 3: cannot add vectors of lengths 1 and 2");
    assertThat(
            error(
                "function g(x)\n return [1] + [1, 2]\nend\nfunction f(x)\n return g(x)\nend\nf(1)"))
        .isEqualTo("in g, line 2: cannot add vectors of lengths 1 and 2");
  }

  @Test
  void aFailedBlockLeavesTheEnvironment() {
    assertThat(shown("q := 1; for k in 1..3 do q := k; [1] + [1, 2] end; q")).isEqualTo("1");
  }

  @Test
  void theFunctionIsPrinted() {
    Outcome o = outcomes("function f(x, y) return x end").getFirst();
    var echo = ((Outcome.DefinedFunction) o).echo();
    assertThat(
            UnicodePrinter.INSTANCE.print(
                mathcanvas.engine.expr.Apply.of(echo.name(), echo.params())))
        .isEqualTo("f(x, y)");
  }
}
