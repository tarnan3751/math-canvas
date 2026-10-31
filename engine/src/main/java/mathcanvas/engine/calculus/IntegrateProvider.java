package mathcanvas.engine.calculus;

import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
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
import mathcanvas.engine.simplify.Simplifier;

/**
 * The engine's own integration, registered as the {@code default} provider of {@link Integrate}:
 * the rule set {@code integrate} ({@link RuleSets#INTEGRATE}) on the {@code Integral} node. It
 * answers only with an antiderivative or a value found whole, recording each rewrite as a step from
 * the whole expression before to the whole expression after, the integral it rewrote and what
 * replaced it named {@code integral} and {@code result}. When an integral in the variable stays it
 * records a terminal step on the integral as it came, which says why ({@link #reason}), and
 * declines. A search of its own ({@link IntegrateRules.Search}) remembers the integrals the rules
 * try during the call.
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
    IntegrateRules.Search search = new IntegrateRules.Search(true);
    Rewriter.Result r = Rewriter.rewrite(node, search.at(IntegrateRules.DEPTH), ctx);
    if (IntegrateRules.complete(r.expr(), input.var())) {
      record(r.trace(), ctx, steps);
      return Optional.of(r.expr());
    }
    Expr start = Canonical.of(node, ctx.precision());
    steps.record(reason(input, search, ctx), start, start, Bindings.EMPTY);
    return Optional.empty();
  }

  /**
   * Why an integral stays: {@code angle-mode} for a definite integral with a circular function in
   * degrees or gradians; for a definite one whose antiderivative was found, {@code improper} when a
   * bound is infinite or the integrand has no value at one, and {@code singular} otherwise (its
   * continuity between the bounds was refused or not decided); {@code none} when no antiderivative
   * was found.
   */
  private static String reason(Integrate input, IntegrateRules.Search search, Context ctx) {
    if (!input.isDefinite()) {
      return "none";
    }
    Optional<Expr> antiderivative =
        search.attempt(input.integrand(), input.var(), IntegrateRules.DEPTH, ctx);
    if (!ctx.numerics().radians()
        && (Definite.circular(input.integrand())
            || antiderivative.map(Definite::circular).orElse(false))) {
      return "angle-mode";
    }
    if (antiderivative.isEmpty()) {
      return "none";
    }
    for (Expr bound : List.of(input.lower(), input.upper())) {
      if (!Definite.bound(bound, ctx) || undefinedAt(input, bound, ctx)) {
        return "improper";
      }
    }
    return "singular";
  }

  /** Whether the integrand, simplified at {@code bound}, has no value there. */
  private static boolean undefinedAt(Integrate input, Expr bound, Context ctx) {
    Expr at = IntegrateRules.replace(input.integrand(), input.var(), bound, ctx);
    Expr v = Simplifier.simplify(at, ctx.withTrace(false)).expr();
    return v.equals(Constant.UNDEFINED) || Definite.has(v, Constant.INFINITY);
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
