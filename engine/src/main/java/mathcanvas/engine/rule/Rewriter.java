package mathcanvas.engine.rule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
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
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.match.PatternNode;

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
 *
 * <p>Only the rules whose pattern can match the node are tried, in their order: the rules are
 * grouped, once per call and lazily, by the kind of node and, for an application, the head their
 * pattern's root can match. A head variable matches any application; a root that is a variable or a
 * power pattern with a default exponent is in every group; a sum or product pattern is in its own
 * kind's group, and in another kind's only when it can match such a node as its only operand (all
 * its operands but one can take nothing, and that one can match the node), so {@code d___ * f(u_)^2
 * + rest__} is tried on sums alone. The grouping only skips rules that would fail to match, so the
 * result and the trace are those of trying every rule.
 *
 * <p>The context's {@link Context#cancellation} is checked at every node visit and before every
 * rule is tried; a request to stop throws {@code CancelledException} there.
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
  private final boolean indexed;

  /** The rules that can match a node, by its kind, or for an application by its head's name. */
  private final Map<Object, List<Rule>> candidates = new HashMap<>();

  private Rewriter(RuleSet rules, Context context, boolean indexed) {
    this.rules = rules;
    this.context = context;
    this.indexed = indexed;
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
   * @throws mathcanvas.engine.CancelledException if the context's cancellation is requested
   */
  public static Result rewrite(Expr e, RuleSet rules, Context context) {
    return rewrite(e, rules, context, true);
  }

  /**
   * Rewrites {@code e} trying every rule at every node, for the tests that check the grouping
   * changes nothing.
   */
  static Result rewriteTryingEveryRule(Expr e, RuleSet rules, Context context) {
    return rewrite(e, rules, context, false);
  }

  private static Result rewrite(Expr e, RuleSet rules, Context context, boolean indexed) {
    Rewriter r = new Rewriter(rules, context, indexed);
    Expr start = Canonical.of(e, context.precision());
    Expr out = r.visit(start, 0);
    Trace trace =
        context.traceEnabled() ? new Trace(start, out, r.rewrites, r.normalisations) : Trace.NONE;
    return new Result(out, trace);
  }

  private Expr visit(Expr e, int depth) {
    while (true) {
      context.cancellation().check();
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
    for (Rule rule : candidates(e)) {
      context.cancellation().check();
      Optional<Bindings> match = Matcher.match(rule.pattern(), e, context.assumptions());
      if (match.isEmpty()) {
        continue;
      }
      Bindings b = match.get();
      SortedMap<String, Expr> values = NO_VALUES;
      Optional<Expr> raw;
      switch (rule.replacement()) {
        case Replacement.Template t -> raw = Optional.of(b.substituteInto(t.template()));
        case Replacement.Computed c -> raw = c.function().apply(new Match(b, e, context));
        case Replacement.Valued v -> {
          Optional<Replacement.Valued.Result> r = v.function().apply(new Match(b, e, context));
          raw = r.map(Replacement.Valued.Result::expr);
          if (r.isPresent()) {
            values = r.get().values();
            if (!v.names().containsAll(values.keySet())) {
              throw new IllegalStateException(
                  "rule " + rule.name() + " named values it does not declare: " + values.keySet());
            }
          }
        }
      }
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
        rewrites.add(
            new Rewrite(
                e, after, rules.name(), rule.name(), Path.of(path, depth), b, canonical(values)));
      }
      return after;
    }
    return null;
  }

  private static final SortedMap<String, Expr> NO_VALUES = Collections.emptySortedMap();

  /** A rule's named values in canonical form, like every other expression on a rewrite. */
  private SortedMap<String, Expr> canonical(SortedMap<String, Expr> values) {
    if (values.isEmpty()) {
      return values;
    }
    SortedMap<String, Expr> out = new TreeMap<>();
    values.forEach((k, v) -> out.put(k, Canonical.of(v, context.precision())));
    return out;
  }

  private List<Rule> candidates(Expr e) {
    if (!indexed) {
      return rules.rules();
    }
    Object key = e instanceof Apply a ? a.head().name() : e.getClass();
    return candidates.computeIfAbsent(
        key, k -> rules.rules().stream().filter(r -> canMatch(r.pattern(), e)).toList());
  }

  /**
   * Whether {@code p} can match a node of {@code e}'s kind and, for an application, head: false
   * only where the matcher would fail at the root.
   */
  private static boolean canMatch(Pattern p, Expr e) {
    return canMatch(p.root(), p, e);
  }

  private static boolean canMatch(PatternNode n, Pattern p, Expr e) {
    return switch (n) {
      case PatternNode.Var _, PatternNode.Seq _ -> true;
      case PatternNode.SumP s -> e instanceof Sum || onlyOperand(s.operands(), s.rest(), p, e);
      case PatternNode.ProductP q ->
          e instanceof Product || onlyOperand(q.operands(), q.rest(), p, e);
      case PatternNode.PowerP pw ->
          e instanceof Power
              || pw.exponent() instanceof PatternNode.Var v && p.defaults().containsKey(v.name());
      case PatternNode.ApplyP a ->
          e instanceof Apply sa
              && (a.head() instanceof PatternNode.Var
                  || a.head() instanceof PatternNode.Literal l && l.expr().equals(sa.head()));
      case PatternNode.Literal l ->
          l.expr().getClass() == e.getClass()
              && (!(l.expr() instanceof Apply la) || la.head().equals(((Apply) e).head()));
      case PatternNode.Structural st -> st.shape().getClass() == e.getClass();
    };
  }

  /**
   * Whether a sum or product pattern can match a node of another kind, which counts as its only
   * operand: every operand but at most one must be able to take no operand (a variable with a
   * default, or a sequence variable that may be empty), and that one must be able to match the
   * node.
   */
  private static boolean onlyOperand(
      List<PatternNode> operands, PatternNode.Seq rest, Pattern p, Expr e) {
    List<PatternNode> required = new ArrayList<>();
    for (PatternNode o : operands) {
      if (!(o instanceof PatternNode.Var v && p.defaults().containsKey(v.name()))) {
        required.add(o);
      }
    }
    if (rest != null && !rest.allowEmpty()) {
      required.add(rest);
    }
    return required.size() <= 1 && (required.isEmpty() || canMatch(required.getFirst(), p, e));
  }
}
