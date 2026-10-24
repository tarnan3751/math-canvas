package mathcanvas.engine.rule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Piecewise;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Matcher;
import mathcanvas.engine.match.Operands;

/**
 * Applies a rule set to a fixpoint, innermost first.
 *
 * <p>The expression is canonicalised first. At each node the rewriter visits the operands, in index
 * order, skipping the bound positions ({@link Operands#isBound}: a lambda's parameters, the
 * variable of a derivative, integral, limit, Σ or Π); the children of unevaluated forms and
 * collections are visited like any other. If an operand changed, the node is rebuilt and
 * canonicalised, and its operands are visited again until nothing changes. Then the rules are tried
 * in order, each on its first match only: the replacement is instantiated (or computed),
 * canonicalised, and if it differs from the node it replaces the node and the visit restarts at
 * that node. A computed replacement that is empty, or a result equal to the node, means the rule
 * does not apply there. The node is done when no rule applies. More than the context's cap of
 * rewrites in one call throws {@link RewriteLimitException}.
 */
public final class Rewriter {

  /**
   * The result of a rewrite.
   *
   * @param expr the canonical result
   * @param trace its trace, {@link Trace#NONE} when tracing is off
   */
  public record Result(Expr expr, Trace trace) {}

  private final RuleSet rules;
  private Context context;
  private final List<Rewrite> rewrites;
  private final List<Trace.Normalisation> normalisations;
  private int[] path = new int[16];
  private int count;

  private Rewriter(RuleSet rules, Context context) {
    this.rules = rules;
    this.context = context;
    this.rewrites = context.traceEnabled() ? new ArrayList<>() : null;
    this.normalisations = context.traceEnabled() ? new ArrayList<>() : null;
  }

  /**
   * Rewrites {@code e} with {@code rules} to a fixpoint.
   *
   * @param e the expression
   * @param rules the rule set
   * @param context the assumptions, precision, trace switch and cap
   * @return the result and its trace
   * @throws RewriteLimitException if more rewrites than the cap are needed
   */
  public static Result rewrite(Expr e, RuleSet rules, Context context) {
    Rewriter r = new Rewriter(rules, context);
    Expr start = Canonical.of(e, context.precision());
    Expr out = r.visit(start, 0);
    Trace trace =
        context.traceEnabled() ? new Trace(start, out, r.rewrites, r.normalisations) : Trace.NONE;
    return new Result(out, trace);
  }

  private Expr visit(Expr e, int depth) {
    while (true) {
      e = visitOperands(e, depth);
      Expr after = applyRules(e, depth);
      if (after == null) {
        return e;
      }
      e = after;
    }
  }

  private Expr visitOperands(Expr e, int depth) {
    if (depth == path.length) {
      path = Arrays.copyOf(path, depth * 2);
    }
    while (true) {
      List<Expr> ops = Operands.of(e);
      List<Expr> changed = null;
      for (int i = 0; i < ops.size(); i++) {
        if (Operands.isBound(e, i)) {
          continue;
        }
        path[depth] = i;
        Expr before = ops.get(i);
        Context outer = context;
        context = scoped(e, i);
        Expr after;
        try {
          after = visit(before, depth + 1);
        } finally {
          context = outer;
        }
        if (after != before) {
          if (changed == null) {
            changed = new ArrayList<>(ops);
          }
          changed.set(i, after);
        }
      }
      if (changed == null) {
        return e;
      }
      Expr raw = Operands.rebuild(e, changed);
      Expr canonical = Canonical.of(raw, context.precision());
      if (canonical.equals(raw)) {
        return raw;
      }
      if (normalisations != null) {
        normalisations.add(
            new Trace.Normalisation(rewrites.size(), Path.of(path, depth), canonical));
      }
      e = canonical;
    }
  }

  /**
   * The context for operand {@code i} of {@code e}: inside the body of a binder, the facts about
   * the symbols it binds do not hold, since the body's symbol is the binder's own; its bounds and a
   * limit's point are outside that scope.
   */
  private Context scoped(Expr e, int i) {
    List<Symbol> bound =
        switch (e) {
          case Lambda l -> i == l.params().size() ? l.params() : List.of();
          case Derivative d -> i == 0 ? List.of(d.var()) : List.of();
          case Integral in -> i == 0 ? List.of(in.var()) : List.of();
          case Limit l -> i == 0 ? List.of(l.var()) : List.of();
          case BigSum s -> i == 0 ? List.of(s.var()) : List.of();
          case BigProduct p -> i == 0 ? List.of(p.var()) : List.of();
          case Num _,
              Constant _,
              Bool _,
              Symbol _,
              Sum _,
              Product _,
              Power _,
              Apply _,
              Relation _,
              ListExpr _,
              Vector _,
              SetExpr _,
              Matrix _,
              Interval _,
              Piecewise _ ->
              List.of();
        };
    Assumptions a = context.assumptions();
    for (Symbol x : bound) {
      a = a.without(x);
    }
    return a == context.assumptions() ? context : context.withAssumptions(a);
  }

  private Expr applyRules(Expr e, int depth) {
    for (Rule rule : rules.rules()) {
      Optional<Bindings> match = Matcher.match(rule.pattern(), e, context.assumptions());
      if (match.isEmpty()) {
        continue;
      }
      Bindings b = match.get();
      Optional<Expr> raw =
          switch (rule.replacement()) {
            case Replacement.Template t -> Optional.of(b.substituteInto(t.template()));
            case Replacement.Computed c -> c.function().apply(new Match(b, e, context));
          };
      if (raw.isEmpty()) {
        continue;
      }
      Expr after = Canonical.of(raw.get(), context.precision());
      if (after.equals(e)) {
        continue;
      }
      if (++count > context.rewriteCap()) {
        throw new RewriteLimitException(
            rules.name(), rule.name(), Path.of(path, depth), context.rewriteCap());
      }
      if (rewrites != null) {
        rewrites.add(new Rewrite(e, after, rules.name(), rule.name(), Path.of(path, depth), b));
      }
      return after;
    }
    return null;
  }
}
