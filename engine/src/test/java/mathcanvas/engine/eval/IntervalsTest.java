package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.error;
import static mathcanvas.engine.eval.Run.shown;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Step;
import org.junit.jupiter.api.Test;

/**
 * The confidence intervals ({@code docs/language.md} §14.25): each interval's bounds, the level,
 * the narration, the call left for a symbol, and the errors.
 */
class IntervalsTest {

  private static Outcome.Value run(String script) {
    Session s = new Session(Environment.EMPTY.withSettings(Settings.DEFAULT));
    return (Outcome.Value) s.run(Parser.parseScript(script, s.names())).getLast();
  }

  @Test
  void eachInterval() {
    assertThat(shown("zinterval(6, 2, 25, 0.95)"))
        .isEqualTo("⟨5.216014406183978, 6.783985593816022⟩");
    assertThat(shown("zinterval([4, 5, 6, 7, 8], 2, 95/100)"))
        .isEqualTo("⟨4.246954918846836, 7.753045081153164⟩");
    assertThat(shown("tinterval([4, 5, 6, 7, 8], 0.95)"))
        .isEqualTo("⟨4.036756838522443, 7.963243161477557⟩");
    assertThat(shown("tinterval(6, 2, 25, 0.9)"))
        .isEqualTo("⟨5.315647168036229, 6.684352831963771⟩");
    assertThat(shown("tinterval2([1, 2, 3, 4], [3, 4, 5, 7], 0.95)"))
        .isEqualTo("⟨-4.917220622474006, 0.417220622474006⟩");
    assertThat(shown("tinterval2([1, 2, 3, 4], [3, 4, 5, 7], 0.95, pooled)"))
        .isEqualTo("⟨-4.869262652287431, 0.3692626522874312⟩");
    assertThat(shown("propinterval(60, 100, 0.95)"))
        .isEqualTo("⟨0.5039817664728937, 0.6960182335271062⟩");
    assertThat(shown("propinterval2(60, 100, 45, 100, 0.95)"))
        .isEqualTo("⟨0.01315296221370421, 0.2868470377862958⟩");
    assertThat(run("zinterval(6, 2, 25, 0.95)").numeric()).isTrue();
    assertThat(shown("tinterval([4, 5, 6, 7, 8], c)")).isEqualTo("tinterval(⟨4, 5, 6, 7, 8⟩, c)");
  }

  @Test
  void theNarrationGivesTheCriticalValueAndTheInterval() {
    Outcome.Value v = run("steps(tinterval([4, 5, 6, 7, 8], 0.95))");
    TraceChecks.assertChain(new Rewriter.Result(v.value(), v.trace()));
    List<String> texts =
        Narration.steps(new Evaluator.Result(v.value(), v.trace()), Context.DEFAULT).stream()
            .map(Step::text)
            .toList();
    assertThat(texts)
        .containsExactly(
            "The critical value t* for the level 0.95: 2.776445105197794",
            "The interval 6 ± 2.776445105197794·√2/2, the estimate plus or minus the critical"
                + " value times its standard error: ⟨4.036756838522443, 7.963243161477557⟩");
  }

  @Test
  void wrongArgumentsAreErrors() {
    assertThat(error("zinterval(6, 2, 25, 95)"))
        .isEqualTo("zinterval needs a confidence level between 0 and 1");
    assertThat(error("propinterval(120, 100, 0.95)"))
        .isEqualTo("propinterval needs an integer x with 0 ≤ x ≤ n");
    assertThat(error("tinterval([4], 0.95)")).isEqualTo("tinterval needs at least 2 values");
    assertThat(error("zinterval(6, 0, 25, 0.95)")).isEqualTo("zinterval needs σ > 0");
    assertThat(error("zinterval([1, 2], -2, 0.95)")).isEqualTo("zinterval needs σ > 0");
    assertThat(error("tinterval(6, -1, 25, 0.95)")).isEqualTo("tinterval needs s > 0");
    assertThat(error("tinterval([3, 3, 3], 0.95)")).isEqualTo("tinterval needs s > 0");
  }
}
