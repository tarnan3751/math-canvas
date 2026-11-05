package mathcanvas.engine.script;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.eval.Outcome;
import mathcanvas.engine.eval.Session;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.print.UnicodePrinter;
import mathcanvas.engine.rule.Step;
import mathcanvas.engine.rule.StepText;
import org.junit.jupiter.api.Test;

/** The narration of a traced run ({@code docs/language.md} §19.6). */
class RunNarrationTest {

  /** The last statement's narration, each step as its text, " = " and its after. */
  private static List<String> narrated(String script, boolean tracing) {
    Session s = new Session();
    s.setTracing(tracing);
    Outcome o = s.run(Parser.parseScript(script, s.names())).getLast();
    RunLog log =
        switch (o) {
          case Outcome.Value v -> v.run();
          case Outcome.Executed e -> e.run();
          default -> throw new AssertionError(o);
        };
    List<String> out = new ArrayList<>();
    for (Step step :
        RunNarration.steps(
            log.events(),
            StepText.standard(),
            mathcanvas.engine.print.UnicodePrinter.INSTANCE::print)) {
      out.add(step.text() + " = " + UnicodePrinter.INSTANCE.display(step.after()));
    }
    return out;
  }

  @Test
  void aLoopIsFolded() {
    assertThat(narrated("s := 0\nfor k in 1..5\n  s := s + k\nend", true))
        .containsExactly(
            "Line 2: pass 1 of 5 through the loop, with k = 1",
            "Line 3: s := s + k = 1",
            "Line 2: pass 2 of 5 through the loop, with k = 2",
            "Line 3: s := s + k = 3",
            "Line 2: passes 3 to 4 of 5 repeat the same lines; the last one has k = 4",
            "Line 2: pass 5 of 5 through the loop, with k = 5",
            "Line 3: s := s + k = 15");
  }

  @Test
  void aShortLoopIsWhole() {
    assertThat(narrated("for k in 1..3 do print(k^2) end", true))
        .containsExactly(
            "Line 1: pass 1 of 3 through the loop, with k = 1",
            "Line 1: print k² = 1",
            "Line 1: pass 2 of 3 through the loop, with k = 2",
            "Line 1: print k² = 4",
            "Line 1: pass 3 of 3 through the loop, with k = 3",
            "Line 1: print k² = 9");
  }

  @Test
  void stepsOfACallNarratesItsBody() {
    String collatz =
        "function c(n)\n count := 0\n while n ≠ 1\n"
            + "  if n mod 2 = 0 then n := n/2 else n := 3n + 1 end\n"
            + "  count := count + 1\n end\n return count\nend\n";
    List<String> steps = narrated(collatz + "steps(c(4))", false);
    assertThat(steps)
        .containsExactly(
            "Line 2: count := 0 = 0",
            "Line 3: pass 1 of 2 through the loop; the condition holds = true",
            "Line 4: the condition n mod 2 = 0 = true",
            "Line 4: n := n/2 = 2",
            "Line 5: count := count + 1 = 1",
            "Line 3: pass 2 of 2 through the loop; the condition holds = true",
            "Line 4: the condition n mod 2 = 0 = true",
            "Line 4: n := n/2 = 1",
            "Line 5: count := count + 1 = 2",
            "Line 7: return count = 2");
  }

  @Test
  void onlyTheOutermostCallIsNarrated() {
    List<String> steps =
        narrated(
            "function f(n)\n if n = 0 then return 1 end\n return n f(n - 1)\nend\nsteps(f(3))",
            false);
    assertThat(steps)
        .containsExactly("Line 2: the condition n = 0 = false", "Line 3: return n f(n - 1) = 6");
  }

  @Test
  void aStatementIsWrittenAsTyped() {
    assertThat(narrated("for k in 1..1 do y := if k = 1 then 2 else 1/0 end", true))
        .contains("Line 1: y := if k = 1 then 2 else 1/0 = 2");
  }

  @Test
  void onlyTheKeptIterationsCountTowardTheCap() {
    // seven thousand turns, of which three are kept: the return is still narrated
    List<String> steps =
        narrated(
            "function big(n)\n s := 0\n for k in 1..n do s := s + k end\n return s\nend\n"
                + "steps(big(7000))",
            false);
    assertThat(steps.getLast()).isEqualTo("Line 4: return s = 24503500");
    assertThat(narrated("for k in 1..6000 do k end", true)).hasSize(7);
  }

  @Test
  void aRunPastTheCapSaysWhereItStops() {
    String deep =
        "x := 1; for a in 1..3 do for b in 1..3 do for c in 1..3 do for d in 1..3 do"
            + " for f in 1..3 do for g in 1..3 do for h in 1..3 do for j in 1..3 do j"
            + " end end end end end end end end";
    List<String> steps = narrated("if true then\n" + deep + "\nend", true);
    // eight nested loops keep 3⁸ iterations, past the cap: the outer loop is narrated from its
    // start, its first 500 steps, then where the narration stops
    assertThat(steps).hasSize(RunNarration.MAX_STEPS + 1);
    assertThat(steps.get(0)).isEqualTo("Line 1: the condition true = true");
    assertThat(steps.get(1)).isEqualTo("Line 2: x := 1 = 1");
    assertThat(steps.get(2)).isEqualTo("Line 2: pass 1 of 3 through the loop, with a = 1");
    assertThat(steps.get(3)).isEqualTo("Line 2: pass 1 of 3 through the loop, with b = 1");
    assertThat(steps.getLast()).startsWith("No more steps are shown after these 500 = ");
  }

  @Test
  void untracedRunsRecordNothing() {
    assertThat(narrated("for k in 1..3 do k end", false)).isEmpty();
  }

  @Test
  void aLongRunIsCut() {
    List<String> steps =
        narrated(
            "for a in 1..3 do for b in 1..3 do for c in 1..3 do for d in 1..3 do"
                + " for f in 1..3 do for g in 1..3 do g end end end end end end",
            true);
    assertThat(steps).hasSize(RunNarration.MAX_STEPS);
    // the rest counted whole: 3⁶ iterations kept, each with its header and its statement
    int more = Integer.parseInt(steps.getLast().replaceAll("And (\\d+) more steps.*", "$1"));
    assertThat(more).isGreaterThan(1000);
  }
}
