package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.provider.Integrate;
import mathcanvas.engine.provider.Providers;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;
import mathcanvas.engine.simplify.Simplifier;

/** The calculus operations: differentiation, integration, Taylor polynomials, sums and products. */
public final class Calculus {

  private Calculus() {}

  /**
   * The {@code n}-th derivative of {@code e} in {@code x}: {@link RuleSets#DIFF} applied to {@code
   * Derivative(e, x, n)}, then the standard simplifier once. What cannot be differentiated stays a
   * {@code Derivative} in the result. With tracing on, the trace is the differentiation's rewrites
   * followed by the simplifier's ({@link Trace#concat}).
   *
   * @param e the expression
   * @param x the variable
   * @param n the order, at least 1
   * @param context the assumptions, precision, trace switch and cap, for each of the two runs
   * @return the canonical result and its trace
   * @throws IllegalArgumentException if {@code n} is below 1
   * @throws mathcanvas.engine.rule.RewriteLimitException if either run needs more rewrites than the
   *     cap
   */
  public static Rewriter.Result differentiate(Expr e, Symbol x, int n, Context context) {
    Rewriter.Result d = Rewriter.rewrite(Derivative.of(e, x, n), RuleSets.DIFF, context);
    Rewriter.Result s = Simplifier.simplify(d.expr(), context);
    Trace trace = context.traceEnabled() ? Trace.concat(d.trace(), s.trace()) : Trace.NONE;
    return new Rewriter.Result(s.expr(), trace);
  }

  /** The rule set of the integration steps, and the narration bundle's key prefix. */
  public static final String INTEGRATE = "integrate";

  /** The rule set of the step that names a provider which recorded no steps of its own. */
  public static final String PROVIDER = "provider";

  /**
   * The integral of {@code f} in {@code x}, indefinite when both bounds are null: the standard
   * simplifier on the integral (which simplifies its integrand and bounds), then the providers of
   * {@link Integrate} in order, then the simplifier on their answer and the final pass of {@link
   * IntegrateResult} (a constant factor distributed over a sum, and for an indefinite integral a
   * term free of {@code x} dropped). When every provider declines the integral is the result as it
   * came, canonical but its integrand not simplified, with one step: the note the provider recorded
   * ({@code integrate.none}, {@code singular}, {@code improper}, {@code angle-mode}), or {@code
   * integrate.none} if it recorded none; so it is, with {@code integrate.none} and no provider
   * asked, when the integrand is not a scalar expression (a domain, truth value, collection,
   * relation or {@code ∞} in it). With tracing on, the trace is the three parts' ({@link
   * Trace#concat}): the simplifier's rewrites, the provider's steps as rewrites of the whole
   * expression in the rule set {@code integrate} (one step in the rule set {@code provider}, named
   * after it, when it recorded none), and the simplifier's again.
   *
   * @param f the integrand
   * @param x the variable
   * @param lower the lower bound, or null
   * @param upper the upper bound, or null
   * @param context the assumptions (facts about {@code x} hold for an indefinite integral, whose
   *     variable is free in its result, and are dropped for a definite one), precision, trace
   *     switch, cap, providers and numerics
   * @return the canonical result and its trace
   * @throws IllegalArgumentException if exactly one bound is null
   * @throws mathcanvas.engine.rule.RewriteLimitException if a run needs more rewrites than the cap
   */
  public static Rewriter.Result integrate(
      Expr f, Symbol x, Expr lower, Expr upper, Context context) {
    Integral node =
        lower == null && upper == null
            ? Integral.indefinite(f, x)
            : Integral.definite(f, x, lower, upper);
    if (node.isDefinite()) {
      context = context.withAssumptions(context.assumptions().without(x));
    }
    Rewriter.Result simplified = Simplifier.simplify(node, context);
    if (!(simplified.expr() instanceof Integral start)) {
      return simplified;
    }
    boolean traced = context.traceEnabled();
    List<Rewrite> rewrites = new ArrayList<>();
    if (!start.integrand().equals(Constant.UNDEFINED) && !Polynomial.isScalar(start.integrand())) {
      // a domain, truth value, collection, relation or ∞ in the integrand: no technique applies
      return stays(node, "none", context);
    }
    Steps sink =
        traced
            ? new Steps() {
              @Override
              public void record(String rule, Expr before, Expr after, Bindings bindings) {
                rewrites.add(new Rewrite(before, after, INTEGRATE, rule, Path.ROOT, bindings));
              }

              @Override
              public void record(
                  String rule,
                  Expr before,
                  Expr after,
                  Bindings bindings,
                  SortedMap<String, Expr> values) {
                rewrites.add(
                    new Rewrite(before, after, INTEGRATE, rule, Path.ROOT, bindings, values));
              }
            }
            : Steps.NONE;
    Optional<Providers.Outcome<Expr>> outcome =
        context
            .providers()
            .attempt(
                new Integrate(
                    start.integrand(),
                    start.var(),
                    start.lower(),
                    start.upper(),
                    context.precision()),
                context,
                sink);
    Expr reached = rewrites.isEmpty() ? start : rewrites.getLast().after();
    Rewriter.Result last;
    if (outcome.isPresent()) {
      Expr value = outcome.get().value();
      if (traced && !reached.equals(value)) {
        rewrites.add(
            new Rewrite(
                reached, value, PROVIDER, outcome.get().provider(), Path.ROOT, Bindings.EMPTY));
      }
      Rewriter.Result simple = Simplifier.simplify(value, context);
      Rewriter.Result tidied =
          IntegrateResult.apply(simple.expr(), start.var(), start.isDefinite(), context);
      last =
          new Rewriter.Result(
              tidied.expr(), traced ? Trace.concat(simple.trace(), tidied.trace()) : Trace.NONE);
    } else {
      // the integral as it came, its integrand unsimplified, with the note that says why
      return stays(node, rewrites.isEmpty() ? "none" : rewrites.getLast().rule(), context);
    }
    if (!traced) {
      return new Rewriter.Result(last.expr(), Trace.NONE);
    }
    Trace own = new Trace(start, outcome.get().value(), rewrites, List.of());
    return new Rewriter.Result(
        last.expr(), Trace.concat(Trace.concat(simplified.trace(), own), last.trace()));
  }

  /**
   * The integral as it came, canonical, and with tracing on the one note {@code integrate.<rule>}.
   */
  private static Rewriter.Result stays(Integral node, String rule, Context context) {
    Expr given = Canonical.of(node, context.precision());
    if (!context.traceEnabled()) {
      return new Rewriter.Result(given, Trace.NONE);
    }
    Rewrite note = new Rewrite(given, given, INTEGRATE, rule, Path.ROOT, Bindings.EMPTY);
    return new Rewriter.Result(given, new Trace(given, given, List.of(note), List.of()));
  }

  /**
   * The Taylor polynomial of degree {@code n} of {@code f} about {@code x = a}: {@link
   * RuleSets#TAYLOR} applied to {@code taylor(f, x, a, n)}, then the standard simplifier once. With
   * tracing on, the trace is the polynomial's rewrite followed by the simplifier's.
   *
   * @param f the expression
   * @param x the variable
   * @param a the point, finite and free of {@code x}
   * @param n the degree, at least 0
   * @param context the assumptions (those of the point; the facts about {@code x} are dropped in
   *     {@code f}), precision, trace switch and cap
   * @return the canonical polynomial and its trace
   * @throws TaylorException if a derivative it needs is undefined at the point or could not be
   *     computed
   * @throws mathcanvas.engine.rule.RewriteLimitException if a run needs more rewrites than the cap
   */
  public static Rewriter.Result taylor(Expr f, Symbol x, Expr a, int n, Context context) {
    if (n < 0) {
      throw new IllegalArgumentException("a negative order: " + n);
    }
    Expr call = Apply.of(Symbol.of("taylor"), f, x, a, Num.of(n));
    Rewriter.Result t = Rewriter.rewrite(call, RuleSets.TAYLOR, context);
    Rewriter.Result s = Simplifier.simplify(t.expr(), context);
    Trace trace = context.traceEnabled() ? Trace.concat(t.trace(), s.trace()) : Trace.NONE;
    return new Rewriter.Result(s.expr(), trace);
  }

  /**
   * The value of a sum or a product: {@link RuleSets#SUM} or {@link RuleSets#PRODUCT} applied to
   * the node, then the standard simplifier once when a rule evaluated it. When none did and tracing
   * is on, the trace is one step from the body to the node saying why it stays: {@code
   * too-many-terms} for integer bounds with more than 100,000 terms, {@code bounds} for numeric
   * bounds that are not integers with the upper at least the lower minus one, {@code none}
   * otherwise.
   *
   * @param node a {@code BigSum} or {@code BigProduct}, evaluated
   * @param context the assumptions (those of the bounds; the facts about the index are dropped in
   *     the body), precision, trace switch, cap, numerics
   * @return the canonical value, or the node, and its trace
   * @throws IllegalArgumentException if {@code node} is neither
   * @throws mathcanvas.engine.rule.RewriteLimitException if a run needs more rewrites than the cap
   */
  public static Rewriter.Result sum(Expr node, Context context) {
    boolean sum = node instanceof BigSum;
    if (!sum && !(node instanceof BigProduct)) {
      throw new IllegalArgumentException("not a sum or a product: " + node);
    }
    // the body simplified first, without the facts about the index (the rewriter drops them)
    Rewriter.Result pre = Simplifier.simplify(node, context);
    Expr start = pre.expr();
    if (!(start instanceof BigSum || start instanceof BigProduct)) {
      return pre;
    }
    Rewriter.Result r = Rewriter.rewrite(start, sum ? RuleSets.SUM : RuleSets.PRODUCT, context);
    if (r.expr().equals(start)) {
      if (!context.traceEnabled()) {
        return r;
      }
      // a note: the node stays as it is
      Rewrite note =
          new Rewrite(
              start, start, sum ? "sum" : "product", stays(start), Path.ROOT, Bindings.EMPTY);
      return new Rewriter.Result(
          start, Trace.concat(pre.trace(), new Trace(start, start, List.of(note), List.of())));
    }
    Rewriter.Result s = Simplifier.simplify(r.expr(), context);
    Trace trace =
        context.traceEnabled()
            ? Trace.concat(Trace.concat(pre.trace(), r.trace()), s.trace())
            : Trace.NONE;
    return new Rewriter.Result(s.expr(), trace);
  }

  /** The most terms or factors of integer bounds the evaluator takes one by one. */
  public static final int MAX_TERMS = 10_000;

  /**
   * Whether an expression is arithmetic over numbers and symbols, with functions: no collection,
   * lambda, truth value, relation, interval, piecewise expression or domain in it. Only such a
   * value is a term of a sum or a product, or the body or point of a limit.
   *
   * @param e the expression
   * @return true if it is
   */
  public static boolean isScalar(Expr e) {
    return Signs.scalar(e);
  }

  /** Why a sum or product stays: the note's name. */
  private static String stays(Expr node) {
    List<Expr> ops = Operands.of(node);
    Expr from = ops.get(2);
    Expr to = ops.get(3);
    if (from instanceof Num a && to instanceof Num b) {
      if (a.value() instanceof Int i
          && b.value() instanceof Int j
          && j.value().subtract(i.value()).add(java.math.BigInteger.ONE).signum() >= 0) {
        return j.value().subtract(i.value()).compareTo(java.math.BigInteger.valueOf(MAX_TERMS)) >= 0
            ? "too-many-terms"
            : "none";
      }
      return "bounds";
    }
    return "none";
  }
}
