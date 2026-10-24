package mathcanvas.engine.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.match.Pattern;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class TraceProperties {

  private static final Symbol XV = Symbol.of("x_");

  /** f(u) → g(u), then g(u) → u + 1: terminates, and its results recombine in the parents. */
  private static final RuleSet RULES =
      new RuleSet(
          "fixture",
          List.of(
              Rule.of(
                  "f-to-g", Pattern.of(Apply.of(Symbol.of("f"), XV)), Apply.of(Symbol.of("g"), XV)),
              Rule.of("g-to-sum", Pattern.of(Apply.of(Symbol.of("g"), XV)), Sum.of(XV, Num.ONE))));

  /** Trees whose canonical form is a fixpoint, since a result is compared with its own form. */
  @Provide
  Arbitrary<Expr> exprs() {
    return ExprArbitraries.exprs()
        .filter(e -> Canonical.of(Canonical.of(e)).equals(Canonical.of(e)));
  }

  @Property(tries = 2000)
  void theTraceReplaysToTheResult(@ForAll("exprs") Expr e) {
    Rewriter.Result traced = Rewriter.rewrite(e, RULES, Context.DEFAULT.withTrace(true));
    Rewriter.Result plain = Rewriter.rewrite(e, RULES, Context.DEFAULT);
    assertThat(plain.expr()).isEqualTo(traced.expr());
    Trace t = traced.trace();
    List<Expr> roots = t.roots();
    assertThat(roots).hasSameSizeAs(t.rewrites());
    for (int i = 0; i < roots.size(); i++) {
      Rewrite w = t.rewrites().get(i);
      Expr at = roots.get(i);
      for (int k : w.path().indices()) {
        at = Operands.of(at).get(k);
      }
      assertThat(at).isEqualTo(w.after());
    }
    Expr last = roots.isEmpty() ? t.initial() : roots.getLast();
    assertThat(Canonical.of(last)).isEqualTo(traced.expr());
  }
}
