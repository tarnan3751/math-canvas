package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;
import mathcanvas.engine.simplify.Simplifier;

/**
 * The chain rule in several variables, for the derivative of a defined function of several
 * parameters applied to arguments ({@code docs/language.md} §14.10): {@code d/dx f(a₁, …, aₙ) = Σ
 * f_{pₖ}(a)·∂aₖ/∂x}. Every derivative is the rule set {@code diff}'s ({@link
 * Calculus#differentiate}) and every substitution the matcher's ({@link IntegrateRules#replace}),
 * so the value is the plain derivative's; what this adds is the textbook's working. The steps are
 * rewrites of the whole expression in the rule set {@link #RULE_SET}: {@code partials}, from the
 * derivative of the composite to the sum of the partial derivatives at the arguments times the
 * inner derivatives still to take, naming the formula and the partials; {@code inner}, those inner
 * derivatives taken; then the simplifier's.
 */
public final class ChainRule {

  private ChainRule() {}

  /** The rule set of the steps, and the narration bundle's key prefix. */
  public static final String RULE_SET = "chain";

  /** The steps it records, each narrated by the key {@code chain.<step>}. */
  public static final List<String> STEPS = List.of("partials", "inner");

  /**
   * The derivative of {@code f(args)} in {@code x}.
   *
   * @param f the function's name
   * @param params its parameters, distinct, none of them occurring in an argument
   * @param body its body in the parameters, evaluated
   * @param args the arguments, evaluated, as many as the parameters
   * @param x the variable
   * @param context the assumptions (without the facts about {@code x}), precision, trace switch and
   *     cap
   * @return the canonical derivative and its trace
   * @throws IllegalArgumentException if the counts differ
   */
  public static Rewriter.Result of(
      Symbol f, List<Symbol> params, Expr body, List<Expr> args, Symbol x, Context context) {
    if (params.size() != args.size() || params.isEmpty()) {
      throw new IllegalArgumentException("one argument per parameter");
    }
    Context quiet = context.withTrace(false);
    Expr composite = body;
    for (int k = 0; k < params.size(); k++) {
      composite = IntegrateRules.replace(composite, params.get(k), args.get(k), quiet);
    }
    List<Expr> outerTerms = new ArrayList<>();
    List<Expr> innerTerms = new ArrayList<>();
    List<Expr> formula = new ArrayList<>();
    List<Expr> partials = new ArrayList<>();
    List<Expr> inners = new ArrayList<>();
    for (int k = 0; k < params.size(); k++) {
      Symbol p = params.get(k);
      Expr partial = Calculus.differentiate(body, p, 1, quiet).expr();
      Expr at = partial;
      for (int j = 0; j < params.size(); j++) {
        at = IntegrateRules.replace(at, params.get(j), args.get(j), quiet);
      }
      Expr inner = Calculus.differentiate(args.get(k), x, 1, quiet).expr();
      outerTerms.add(Product.of(at, Derivative.of(args.get(k), x, 1)));
      innerTerms.add(Product.of(at, inner));
      Symbol fp = Symbol.of(f.name() + "_" + p.name());
      Symbol px = Symbol.of(p.name() + "_" + x.name());
      formula.add(Product.of(fp, px));
      partials.add(Relation.of(Relation.Op.EQ, fp, partial));
      inners.add(Relation.of(Relation.Op.EQ, px, inner));
    }
    var mc = context.precision();
    Expr start = Canonical.of(Derivative.of(composite, x, 1), mc);
    Expr first = Canonical.of(Sum.of(outerTerms), mc);
    Expr second = Canonical.of(Sum.of(innerTerms), mc);
    Rewriter.Result simple = Simplifier.simplify(second, context);
    if (!context.traceEnabled()) {
      return new Rewriter.Result(simple.expr(), Trace.NONE);
    }
    TreeMap<String, Expr> v1 = new TreeMap<>();
    v1.put("function", Canonical.of(Apply.of(f, params), mc));
    v1.put("formula", Canonical.of(Sum.of(formula), mc));
    v1.put("partials", Canonical.of(ListExpr.of(partials), mc));
    TreeMap<String, Expr> v2 = new TreeMap<>();
    v2.put("inner", Canonical.of(ListExpr.of(inners), mc));
    List<Rewrite> rewrites = new ArrayList<>();
    rewrites.add(new Rewrite(start, first, RULE_SET, "partials", Path.ROOT, Bindings.EMPTY, v1));
    if (!second.equals(first)) {
      rewrites.add(new Rewrite(first, second, RULE_SET, "inner", Path.ROOT, Bindings.EMPTY, v2));
    }
    Trace own = new Trace(start, second, rewrites, List.of());
    return new Rewriter.Result(simple.expr(), Trace.concat(own, simple.trace()));
  }
}
