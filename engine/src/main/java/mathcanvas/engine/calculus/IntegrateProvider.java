package mathcanvas.engine.calculus;

import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.provider.Integrate;
import mathcanvas.engine.provider.Provider;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;

/**
 * The engine's own integration, registered as the {@code default} provider of {@link Integrate}:
 * the rule set {@code integrate} ({@link RuleSets#INTEGRATE}) on the {@code Integral} node. It
 * answers only with an antiderivative or a value found whole, recording each rewrite as a step from
 * the whole expression before to the whole expression after, the integral it rewrote and what
 * replaced it named {@code integral} and {@code result}. When an integral in the variable stays it
 * records the terminal step {@code none} on the integral as it came, and declines. It rewrites with
 * a search of its own ({@link IntegrateRules.Search}).
 *
 * <p>It is constructed while the registry's class initialises, so it reads no rule set and no
 * context until it is applied.
 */
public record IntegrateProvider() implements Provider<Integrate, Expr> {

  @Override
  public String name() {
    return "default";
  }

  @Override
  public Optional<Expr> apply(Integrate input, Context ctx, Steps steps) {
    Integral node =
        input.isDefinite()
            ? Integral.definite(input.integrand(), input.var(), input.lower(), input.upper())
            : Integral.indefinite(input.integrand(), input.var());
    IntegrateRules.Search search = new IntegrateRules.Search();
    Rewriter.Result r = Rewriter.rewrite(node, search.at(IntegrateRules.DEPTH), ctx);
    if (IntegrateRules.complete(r.expr(), input.var())) {
      record(r.trace(), ctx, steps);
      return Optional.of(r.expr());
    }
    Expr start = Canonical.of(node, ctx.precision());
    steps.record("none", start, start, Bindings.EMPTY);
    return Optional.empty();
  }

  /** Each rewrite as a step of whole expressions, its integral and result named. */
  private static void record(Trace trace, Context ctx, Steps steps) {
    List<Rewrite> rewrites = trace.rewrites();
    if (rewrites.isEmpty()) {
      return;
    }
    List<Expr> roots = trace.roots();
    Expr before = trace.initial();
    for (int i = 0; i < rewrites.size(); i++) {
      Rewrite rw = rewrites.get(i);
      Expr after = Canonical.of(roots.get(i), ctx.precision());
      TreeMap<String, Expr> values = new TreeMap<>(rw.values());
      values.put("integral", rw.before());
      values.put("result", rw.after());
      steps.record(rw.rule(), before, after, rw.bindings(), values);
      before = after;
    }
  }
}
