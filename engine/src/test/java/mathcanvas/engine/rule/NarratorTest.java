package mathcanvas.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.ListResourceBundle;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Pattern;
import org.junit.jupiter.api.Test;

class NarratorTest {

  private static final Symbol A = Symbol.of("a");
  private static final Symbol B = Symbol.of("b");

  private static Apply call(String head, Expr... args) {
    return Apply.of(Symbol.of(head), args);
  }

  /** A test bundle: the engine's own has no keys until the rule sets land. */
  public static final class Texts extends ListResourceBundle {
    @Override
    protected Object[][] getContents() {
      return new Object[][] {{"fixture.swap", "Swap {0} and {1} in f''s arguments"}};
    }
  }

  private static final RuleSet RULES =
      new RuleSet(
          "fixture",
          List.of(
              Rule.of(
                  "swap",
                  Pattern.of(call("f", Symbol.of("y_"), Symbol.of("x_"))),
                  call("g", Symbol.of("x_"), Symbol.of("y_"))),
              Rule.of(
                  "unnarrated",
                  Pattern.of(call("g", Symbol.of("x_"), Symbol.of("y_"))),
                  Symbol.of("x_"))));

  @Test
  void oneStepPerRewriteWithTheBundlesTextOrTheRuleName() {
    Trace trace = Rewriter.rewrite(call("f", A, B), RULES, Context.DEFAULT.withTrace(true)).trace();
    List<Step> steps =
        new DefaultNarrator(new Texts(), e -> ((Symbol) e).name()).narrate(trace, Context.DEFAULT);
    // {0} is x_, {1} is y_: alphabetical by variable name, not by position in the pattern.
    assertThat(steps)
        .extracting(Step::text)
        .containsExactly("Swap b and a in f's arguments", "unnarrated");
    assertThat(steps.getFirst().before()).isEqualTo(call("f", A, B));
    assertThat(steps.getFirst().after()).isEqualTo(call("g", B, A));
    assertThat(steps.getFirst().rewrites()).containsExactly(trace.rewrites().getFirst());
  }

  @Test
  void theEnginesBundleLoads() {
    Trace trace = Rewriter.rewrite(call("f", A, B), RULES, Context.DEFAULT.withTrace(true)).trace();
    assertThat(new DefaultNarrator().narrate(trace, Context.DEFAULT))
        .extracting(Step::text)
        .containsExactly("swap", "unnarrated");
    assertThat(new DefaultNarrator().narrate(Trace.NONE, Context.DEFAULT)).isEmpty();
  }
}
