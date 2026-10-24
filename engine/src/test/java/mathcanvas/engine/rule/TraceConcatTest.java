package mathcanvas.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.match.Pattern;
import org.junit.jupiter.api.Test;

class TraceConcatTest {

  private static final Symbol X = Symbol.of("x");
  private static final Symbol Y = Symbol.of("y");
  private static final Symbol XV = Symbol.of("x_");
  private static final Context TRACED = Context.DEFAULT.withTrace(true);

  private static Apply call(String head, Expr... args) {
    return Apply.of(Symbol.of(head), args);
  }

  private static RuleSet set(String name, String from, String to) {
    return new RuleSet(
        name, List.of(Rule.of(from + "-to-" + to, Pattern.of(call(from, XV)), call(to, XV))));
  }

  @Test
  void theJoinedTraceReplaysThroughBothOperations() {
    // f → g, then g → h, each under a sum and a product that the first rewrite rebuilds.
    Expr e = Sum.of(Product.of(Num.of(2), call("f", X)), call("f", Y), call("g", X));
    Rewriter.Result first = Rewriter.rewrite(e, set("one", "f", "g"), TRACED);
    Rewriter.Result second = Rewriter.rewrite(first.expr(), set("two", "g", "h"), TRACED);
    Trace joined = Trace.concat(first.trace(), second.trace());

    assertThat(joined.initial()).isEqualTo(first.trace().initial());
    assertThat(joined.result()).isEqualTo(second.expr());
    assertThat(joined.rewrites())
        .hasSize(first.trace().rewrites().size() + second.trace().rewrites().size());
    List<Expr> roots = joined.roots();
    for (int i = 0; i < roots.size(); i++) {
      Rewrite w = joined.rewrites().get(i);
      Expr at = roots.get(i);
      for (int k : w.path().indices()) {
        at = Operands.of(at).get(k);
      }
      assertThat(at).as("rewrite %d", i).isEqualTo(w.after());
    }
    assertThat(Canonical.of(roots.getLast())).isEqualTo(second.expr());
  }

  @Test
  void anEmptySecondTraceKeepsTheFirstRewrites() {
    Rewriter.Result first = Rewriter.rewrite(call("f", X), set("one", "f", "g"), TRACED);
    Rewriter.Result second = Rewriter.rewrite(first.expr(), set("two", "k", "h"), TRACED);
    Trace joined = Trace.concat(first.trace(), second.trace());
    assertThat(joined.rewrites()).isEqualTo(first.trace().rewrites());
    assertThat(joined.roots().getLast()).isEqualTo(call("g", X));
  }

  @Test
  void theSecondTraceMustStartFromTheFirstResult() {
    Rewriter.Result first = Rewriter.rewrite(call("f", X), set("one", "f", "g"), TRACED);
    Rewriter.Result other = Rewriter.rewrite(call("f", Y), set("one", "f", "g"), TRACED);
    assertThatThrownBy(() -> Trace.concat(first.trace(), other.trace()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Trace.concat(Trace.NONE, first.trace()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
