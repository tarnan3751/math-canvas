package mathcanvas.engine.algebra;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.provider.Providers;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;

/**
 * The algebra's operations on expressions, as the language's commands call them.
 *
 * <p>The heavyweight ones go through the context's {@link Providers}. With the trace on, each
 * provider call is one rewrite, in the rule set {@code provider} named after the provider that
 * answered. Every rewrite changes its node.
 *
 * <p>An argument that is not a polynomial in scalar kernels ({@link Polynomial#of(Expr)}) is
 * refused: {@code expand} and {@code collect} leave it as it is, {@code gcd} and {@code lcm} return
 * empty. Past the algebra's bounds they throw {@link AlgebraLimitException}, the only exception
 * they throw: an {@link ArithmeticException} from within becomes one.
 */
public final class Algebra {

  /** The rule set of the trace events that name a provider. */
  public static final String PROVIDER = "provider";

  private Algebra() {}

  /**
   * Expands {@code e}: the rule set {@link ExpandRules#RULES} to its fixpoint.
   *
   * @param e the expression
   * @param ctx the context; with the trace on, every distribution and power is recorded
   * @return the expanded expression and its trace
   */
  public static Rewriter.Result expand(Expr e, Context ctx) {
    return Rewriter.rewrite(e, ExpandRules.RULES, ctx);
  }

  /**
   * Collects the expansion of {@code e} by powers of {@code v}: {@code Σ c_k·v^k}, each coefficient
   * a canonical expression in the other kernels. {@code v} may be any kernel ({@code sin(x)}); a
   * negative or fractional power of {@code v} is a kernel of its own and stays in a coefficient.
   *
   * @param e the expression
   * @param v the kernel to collect by
   * @param ctx the context
   * @return the collected expression; the expansion itself when {@code v} does not occur in it as a
   *     variable or it is not a polynomial in scalar kernels
   */
  public static Expr collect(Expr e, Expr v, Context ctx) {
    Expr expanded = expand(e, ctx.withTrace(false)).expr();
    Expr kernel = Canonical.of(v, ctx.precision());
    Polynomial p;
    try {
      p = Polynomial.of(expanded);
    } catch (IllegalArgumentException | ArithmeticException | AlgebraLimitException refused) {
      return expanded;
    }
    if (!p.variables().contains(kernel)) {
      return expanded;
    }
    // one pass over the terms, grouped by the exponents of the kernel that occur
    int i = p.variables().indexOf(kernel);
    SortedMap<Integer, Map<Monomial, RealNumeric>> groups =
        new TreeMap<>(Comparator.reverseOrder());
    for (Polynomial.Term t : p.terms()) {
      int[] x = t.monomial().exponents();
      int k = x[i];
      x[i] = 0;
      groups
          .computeIfAbsent(k, unused -> new TreeMap<>(Monomial.ORDER))
          .put(new Monomial(x), t.coefficient());
    }
    List<Expr> terms = new ArrayList<>();
    for (Map.Entry<Integer, Map<Monomial, RealNumeric>> g : groups.entrySet()) {
      int k = g.getKey();
      Expr c = Polynomial.build(p.variables(), g.getValue()).raw();
      terms.add(k == 0 ? c : Product.of(c, k == 1 ? kernel : Power.of(kernel, Num.of(k))));
    }
    return Canonical.of(terms.size() == 1 ? terms.getFirst() : Sum.of(terms), ctx.precision());
  }

  /**
   * Returns the gcd of polynomials in normal form ({@link Gcd}), folded pairwise through the gcd
   * provider.
   *
   * @param args the arguments, at least one
   * @param ctx the context
   * @return the gcd and its trace, or empty when an argument is not a polynomial in scalar kernels
   * @throws AlgebraLimitException past the algebra's bounds
   */
  public static Optional<Rewriter.Result> gcd(List<Expr> args, Context ctx) {
    return limited(() -> fold("gcd", args, ctx, false));
  }

  /**
   * Returns the lcm of polynomials, with a positive sign ({@link Gcd#sign}): {@code a·b/gcd(a, b)}
   * folded pairwise, the gcd through the provider.
   *
   * @param args the arguments, at least one
   * @param ctx the context
   * @return the lcm and its trace, or empty when an argument is not a polynomial in scalar kernels
   * @throws AlgebraLimitException past the algebra's bounds
   */
  public static Optional<Rewriter.Result> lcm(List<Expr> args, Context ctx) {
    return limited(() -> fold("lcm", args, ctx, true));
  }

  private static Optional<Rewriter.Result> fold(
      String name, List<Expr> args, Context ctx, boolean lcm) {
    List<Polynomial> ps = new ArrayList<>(args.size());
    for (Expr a : args) {
      Optional<Polynomial> p = polynomial(a, ctx);
      if (p.isEmpty()) {
        return Optional.empty();
      }
      ps.add(p.get());
    }
    Symbol head = Symbol.of(name);
    Expr initial = Canonical.of(Apply.of(head, args), ctx.precision());
    Recorder r = new Recorder(ctx.traceEnabled());
    Polynomial acc = Gcd.signed(ps.getFirst());
    Expr before = initial;
    for (int i = 1; i < ps.size(); i++) {
      Polynomial p = ps.get(i);
      Providers.Outcome<Polynomial> g =
          ctx.providers().run(new mathcanvas.engine.provider.Gcd(acc, p), ctx, Steps.NONE);
      acc =
          lcm
              ? acc.isZero() || p.isZero()
                  ? Polynomial.ZERO
                  : Gcd.signed(Division.exactQuotient(acc, g.value()).orElseThrow().multiply(p))
              : g.value();
      List<Expr> rest = new ArrayList<>();
      rest.add(acc.raw());
      rest.addAll(args.subList(i + 1, args.size()));
      Expr after =
          Canonical.of(rest.size() == 1 ? rest.getFirst() : Apply.of(head, rest), ctx.precision());
      r.rewrite(List.of(), before, after, PROVIDER, g.provider(), Bindings.EMPTY);
      before = after;
    }
    Expr result = Canonical.of(acc.raw(), ctx.precision());
    return Optional.of(new Rewriter.Result(result, r.trace(initial, result)));
  }

  /** Converts an argument, or empty when it is not a polynomial in scalar kernels. */
  private static Optional<Polynomial> polynomial(Expr e, Context ctx) {
    try {
      return Optional.of(Polynomial.of(Canonical.of(e, ctx.precision())));
    } catch (IllegalArgumentException refused) {
      return Optional.empty();
    }
  }

  /** Runs an operation, an arithmetic failure within it reported as a limit. */
  private static <T> T limited(Supplier<T> operation) {
    try {
      return operation.get();
    } catch (ArithmeticException e) {
      String message = String.valueOf(e.getMessage());
      throw new AlgebraLimitException(
          message.contains("overflow")
              ? "the exponents are too large"
              : "arithmetic failed: " + message);
    }
  }

  /**
   * The events of an operation that is not a rewrite by the rewriter, at their paths. A rewrite
   * that would not change its node is not recorded.
   */
  private static final class Recorder {

    private final boolean on;
    private final List<Rewrite> rewrites = new ArrayList<>();
    private final List<Trace.Normalisation> normalisations = new ArrayList<>();

    Recorder(boolean on) {
      this.on = on;
    }

    void rewrite(
        List<Integer> path, Expr before, Expr after, String ruleSet, String rule, Bindings b) {
      if (on && !before.equals(after)) {
        rewrites.add(new Rewrite(before, after, ruleSet, rule, new Path(path), b));
      }
    }

    void normalised(List<Integer> path, Expr node) {
      if (on) {
        normalisations.add(new Trace.Normalisation(rewrites.size(), new Path(path), node));
      }
    }

    int count() {
      return rewrites.size();
    }

    Rewrite last() {
      return rewrites.getLast();
    }

    Trace trace(Expr initial, Expr result) {
      return on ? new Trace(initial, result, rewrites, normalisations) : Trace.NONE;
    }
  }
}
