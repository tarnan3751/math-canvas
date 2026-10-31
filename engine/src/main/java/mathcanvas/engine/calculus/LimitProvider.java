package mathcanvas.engine.calculus;

import java.util.List;
import java.util.Optional;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.provider.Limit;
import mathcanvas.engine.provider.Provider;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;

/**
 * The engine's own limits, registered as the {@code default} provider of {@link Limit}: the rule
 * set {@code limit} ({@link RuleSets#LIMIT}) on the {@code Limit} node, each rewrite recorded as a
 * step with its values. It declines when no method decides the limit.
 */
public record LimitProvider() implements Provider<Limit, Expr> {

  @Override
  public String name() {
    return "default";
  }

  @Override
  public Optional<Expr> apply(Limit input, Context ctx, Steps steps) {
    mathcanvas.engine.expr.Limit node =
        mathcanvas.engine.expr.Limit.of(
            input.expr(), input.var(), input.point(), input.direction());
    Rewriter.Result r = Rewriter.rewrite(node, RuleSets.LIMIT, ctx);
    if (r.expr() instanceof mathcanvas.engine.expr.Limit) {
      return Optional.empty();
    }
    if (ctx.traceEnabled()) {
      List<Expr> roots = r.trace().roots();
      Expr before = r.trace().initial();
      for (int i = 0; i < roots.size(); i++) {
        Rewrite w = r.trace().rewrites().get(i);
        steps.record(w.rule(), before, roots.get(i), w.bindings(), w.values());
        before = roots.get(i);
      }
    }
    return Optional.of(r.expr());
  }
}
