package mathcanvas.engine.rule;

import static mathcanvas.engine.rule.RewriterTest.A;
import static mathcanvas.engine.rule.RewriterTest.XV;
import static mathcanvas.engine.rule.RewriterTest.call;
import static mathcanvas.engine.rule.RewriterTest.rename;
import static mathcanvas.engine.rule.RewriterTest.run;
import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.match.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RewriteCountsTest {

  @AfterEach
  void off() {
    RewriteCounts.stop();
  }

  @Test
  void everyRuleTriedAndEveryRewriteIsCountedWhileCountingIsOn() {
    RewriteCounts counts = RewriteCounts.start();
    // f(f(a)) → h(h(a)): f-to-g and g-to-h each fire twice; g-never is tried at every g(…), where
    // it comes first, and never fires
    Rule never = Rule.of("g-never", Pattern.of(call("g", A, XV)), call("k", XV));
    Rewriter.Result r =
        run(
            call("f", call("f", A)),
            Context.DEFAULT,
            rename("f-to-g", "f", "g"),
            never,
            rename("g-to-h", "g", "h"));
    RewriteCounts.stop();

    assertThat(r.expr()).isEqualTo(call("h", call("h", A)));
    assertThat(counts.rewrites()).isEqualTo(4);
    assertThat(counts.tries()).isEqualTo(6);
    assertThat(counts.mostTried(10))
        .containsExactly(
            new RewriteCounts.Entry("fixture", "f-to-g", 2, 2),
            new RewriteCounts.Entry("fixture", "g-never", 2, 0),
            new RewriteCounts.Entry("fixture", "g-to-h", 2, 2));
    assertThat(counts.mostTried(1)).hasSize(1);
  }

  @Test
  void nothingIsCountedWhileCountingIsOff() {
    RewriteCounts counts = RewriteCounts.start();
    RewriteCounts.stop();
    run(call("f", A), Context.DEFAULT, rename("f-to-g", "f", "g"));

    assertThat(counts.tries()).isZero();
    assertThat(counts.rewrites()).isZero();
    assertThat(counts.mostTried(10)).isEmpty();
    assertThat(RewriteCounts.active()).isNull();
  }
}
