package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Replacement;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;
import mathcanvas.engine.rule.Trace;

/**
 * The final pass on an integral's simplified result, two rules of the rule set {@code integrate}
 * that act on its top level only: {@code distribute} multiplies a constant factor into a sum, the
 * factor of the whole result or of one of its terms, again until none is left, so towers flatten
 * ({@code x² e^x − 2(x e^x − e^x)} → {@code x² e^x − 2x e^x + 2e^x}); for an antiderivative the
 * factor is free of the variable and the sum holds it, for a definite value the factor is a number
 * ({@code −(−cot 1 − 1)} → {@code cot 1 + 1}). Then, for an antiderivative only, {@code
 * constant-term} drops a term free of the variable, since an antiderivative is one up to a constant
 * ({@code x − log|x + 1| + 1} → {@code x − log|x + 1|}). The rules match the result wrapped in an
 * application whose head no expression can name, so nothing inside a function application or a
 * power is touched; the steps are recorded on the result itself.
 */
final class IntegrateResult {

  private IntegrateResult() {}

  /** The head of the wrapper, a name the language cannot write. */
  private static final Symbol TOP = Symbol.of("\u0000antiderivative");

  /** The values {@code distribute} names. */
  static final Set<String> DISTRIBUTE_VALUES = Set.of("factor");

  /** The values {@code constant-term} names. */
  static final Set<String> CONSTANT_VALUES = Set.of("term");

  private static Pattern top() {
    return Pattern.of(Apply.of(TOP, Symbol.of("f_"), Symbol.of("x_"), Symbol.of("d_")));
  }

  /** The two rules, in this order. */
  static final RuleSet RULES =
      new RuleSet(
          "integrate",
          List.of(
              Rule.valued("distribute", top(), DISTRIBUTE_VALUES, IntegrateResult::distribute),
              Rule.valued("constant-term", top(), CONSTANT_VALUES, IntegrateResult::constant)));

  /**
   * The pass on {@code f}, an antiderivative in {@code x} or a definite value.
   *
   * @param f the simplified result
   * @param x the variable
   * @param definite whether {@code f} is a definite integral's value
   * @param context the context; with tracing on, each rewrite is a step of the whole result
   * @return the result and its trace
   */
  static Rewriter.Result apply(Expr f, Symbol x, boolean definite, Context context) {
    Expr wrapped = Apply.of(TOP, f, x, Bool.of(definite));
    Rewriter.Result r = Rewriter.rewrite(wrapped, RULES, context);
    Expr out = ((Apply) r.expr()).args().getFirst();
    if (!context.traceEnabled()) {
      return new Rewriter.Result(out, Trace.NONE);
    }
    List<Rewrite> steps = new ArrayList<>();
    for (Rewrite w : r.trace().rewrites()) {
      steps.add(
          new Rewrite(
              ((Apply) w.before()).args().getFirst(),
              ((Apply) w.after()).args().getFirst(),
              w.ruleSet(),
              w.rule(),
              Path.ROOT,
              w.bindings(),
              w.values()));
    }
    Expr start = Canonical.of(f, context.precision());
    return new Rewriter.Result(out, new Trace(start, out, steps, List.of()));
  }

  /**
   * The constant factor and the one sum of a product: for an antiderivative the factors free of
   * {@code x} and a sum holding {@code x}, for a definite value numbers and a sum; or empty.
   */
  private static Optional<Expr[]> split(Expr e, Symbol x, boolean definite) {
    if (!(e instanceof Product p)) {
      return Optional.empty();
    }
    List<Expr> constant = new ArrayList<>();
    Sum sum = null;
    for (Expr factor : p.factors()) {
      if (factor instanceof Sum s && sum == null && (definite || !Free.of(s, x))) {
        sum = s;
      } else if (definite ? factor instanceof Num : Free.of(factor, x)) {
        constant.add(factor);
      } else if (factor instanceof Sum s && sum == null) {
        sum = s;
      } else {
        return Optional.empty();
      }
    }
    if (sum == null || constant.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new Expr[] {IntegrateSearch.product(constant), sum});
  }

  private static List<Expr> terms(Expr e) {
    return e instanceof Sum s ? s.terms() : List.of(e);
  }

  private static Optional<Replacement.Valued.Result> distribute(Match m) {
    Expr f = m.bindings().get("f_");
    Symbol x = (Symbol) m.bindings().get("x_");
    boolean definite = m.bindings().get("d_").equals(Bool.TRUE);
    List<Expr> terms = terms(f);
    for (int k = 0; k < terms.size(); k++) {
      Optional<Expr[]> s = split(terms.get(k), x, definite);
      if (s.isEmpty()) {
        continue;
      }
      List<Expr> out = new ArrayList<>(terms.subList(0, k));
      for (Expr t : ((Sum) s.get()[1]).terms()) {
        out.add(Product.of(s.get()[0], t));
      }
      out.addAll(terms.subList(k + 1, terms.size()));
      TreeMap<String, Expr> values = new TreeMap<>();
      values.put("factor", s.get()[0]);
      Expr top = Apply.of(TOP, Sum.of(out), x, m.bindings().get("d_"));
      return Optional.of(new Replacement.Valued.Result(top, values));
    }
    return Optional.empty();
  }

  private static Optional<Replacement.Valued.Result> constant(Match m) {
    Expr f = m.bindings().get("f_");
    Symbol x = (Symbol) m.bindings().get("x_");
    if (m.bindings().get("d_").equals(Bool.TRUE) || !(f instanceof Sum s)) {
      return Optional.empty();
    }
    List<Expr> kept = new ArrayList<>();
    List<Expr> dropped = new ArrayList<>();
    for (Expr t : s.terms()) {
      (Free.of(t, x) ? dropped : kept).add(t);
    }
    if (dropped.isEmpty() || kept.isEmpty()) {
      return Optional.empty();
    }
    TreeMap<String, Expr> values = new TreeMap<>();
    values.put("term", Bindings.combine(dropped, Bindings.Origin.SUM));
    return Optional.of(
        new Replacement.Valued.Result(
            Apply.of(TOP, Bindings.combine(kept, Bindings.Origin.SUM), x, Bool.FALSE), values));
  }
}
