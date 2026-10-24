package mathcanvas.engine.algebra;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.provider.Factor;
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
 * <p>The heavyweight ones go through the context's {@link Providers}. With the trace on, a
 * provider's own steps are the trace; a provider that recorded none is one rewrite, in the rule set
 * {@code provider} named after it, from its input to its normalised result. Every rewrite changes
 * its node.
 *
 * <p>An argument that is not a polynomial (or rational function) in scalar kernels ({@link
 * Polynomial#of(Expr)}) is refused: {@code expand}, {@code collect} and {@code cancel} leave it as
 * it is, {@code gcd}, {@code lcm}, {@code factor} and {@code apart} return empty. Past the
 * algebra's bounds they throw {@link AlgebraLimitException}, the only exception they throw: an
 * {@link ArithmeticException} from within becomes one.
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

  /**
   * Factors {@code e} over ℚ through the factor provider ({@link Factoring}), by its structure:
   *
   * <ul>
   *   <li>a sum is put over a common denominator ({@link RationalFunction}), recorded as a step
   *       {@code common-denominator} when that changes its form, and its numerator and denominator
   *       are factored;
   *   <li>a product is factored factor by factor, a power {@code b^n} as {@code b} with
   *       multiplicity {@code n} (a negative {@code n} on the denominator side); the factors are
   *       merged, and the bases common to the numerator and denominator sides are cancelled in one
   *       step {@code cancel} (when either side holds an incomplete base, each pair of bases is
   *       split by its gcd through the gcd provider first, so a factor hidden in an unsplit base
   *       cancels too);
   *   <li>any other power factors its base;
   *   <li>a kernel or a number is returned as it is;
   *   <li>a relation, list, set or vector is factored item by item.
   * </ul>
   *
   * <p>So an input that is already factored records no step. Each part's steps are recorded at its
   * path, and a rebuilt node's canonical form as a normalisation.
   *
   * @param e the expression
   * @param ctx the context; with the trace on, the steps are recorded
   * @return the factored expression and its trace, or empty when a part is not a rational function
   *     in scalar kernels
   * @throws AlgebraLimitException past the algebra's bounds
   */
  public static Optional<Rewriter.Result> factor(Expr e, Context ctx) {
    return limited(
        () -> {
          Expr c = Canonical.of(e, ctx.precision());
          Recorder r = new Recorder(ctx.traceEnabled());
          Expr result;
          try {
            result = factor(c, ctx, r, new ArrayList<>()).expr();
          } catch (IllegalArgumentException refused) {
            return Optional.empty();
          }
          return Optional.of(new Rewriter.Result(result, r.trace(c, result)));
        });
  }

  /**
   * A part factored: its expression, and the factorizations of its numerator and denominator sides.
   */
  private record Split(Expr expr, Factorization num, Factorization den) {}

  private static final Factorization ONE = new Factorization(Int.ONE, List.of(), true);

  /** Factors one part at {@code path}; a refused kernel throws {@link IllegalArgumentException}. */
  private static Split factor(Expr e, Context ctx, Recorder r, List<Integer> path) {
    if (e instanceof Relation
        || e instanceof ListExpr
        || e instanceof SetExpr
        || e instanceof Vector) {
      return items(e, ctx, r, path);
    }
    if (e instanceof Sum s) {
      return sum(s, ctx, r, path);
    }
    if (e instanceof Product p) {
      return product(p, ctx, r, path);
    }
    if (e instanceof Power w && !(w.base() instanceof Num)) {
      Split base = child(w, 0, ctx, r, path);
      Expr expr = rebuilt(Power.of(base.expr(), w.exponent()), ctx, r, path);
      if (w.exponent() instanceof Num n
          && n.value() instanceof Int k
          && k.value().bitLength() < 31) {
        int m = k.value().intValue();
        try {
          return m >= 0
              ? new Split(expr, base.num().pow(m), base.den().pow(m))
              : new Split(expr, base.den().pow(-m), base.num().pow(-m));
        } catch (AlgebraLimitException tooLarge) {
          // the canonical form keeps the power, a large unit's power unfolded; its factors are
          // opaque kernels here, never converted (that would compute the power)
          return new Split(expr, opaque(expr), ONE);
        }
      }
      return new Split(expr, leaf(expr), ONE);
    }
    // a number or a kernel
    return new Split(e, leaf(e), ONE);
  }

  private static Split child(Expr parent, int i, Context ctx, Recorder r, List<Integer> path) {
    path.add(i);
    Split s = factor(Operands.of(parent).get(i), ctx, r, path);
    path.removeLast();
    return s;
  }

  /** The canonical form of a node rebuilt from factored operands, recorded when it differs. */
  private static Expr rebuilt(Expr raw, Context ctx, Recorder r, List<Integer> path) {
    Expr c = Canonical.of(raw, ctx.precision());
    if (!c.equals(raw)) {
      r.normalised(path, c);
    }
    return c;
  }

  /** The factors of a canonical expression as opaque kernel bases, each once: incomplete. */
  private static Factorization opaque(Expr e) {
    List<Factorization.Factor> fs = new ArrayList<>();
    RealNumeric unit = Int.ONE;
    for (Expr f : e instanceof Product p ? p.factors() : List.of(e)) {
      if (f instanceof Num n && n.value() instanceof RealNumeric c && c.isExact()) {
        unit = c;
      } else {
        fs.add(new Factorization.Factor(Polynomial.variable(f), 1));
      }
    }
    return new Factorization(unit, fs, false);
  }

  /** A number or kernel as a factorization: its unit and its kernels' powers. */
  private static Factorization leaf(Expr e) {
    Polynomial p = Polynomial.of(e);
    if (p.terms().size() != 1) {
      return new Factorization(Int.ONE, List.of(new Factorization.Factor(p, 1)), true).normalised();
    }
    Polynomial.Term t = p.terms().getFirst();
    List<Factorization.Factor> fs = new ArrayList<>();
    for (int i = 0; i < p.variables().size(); i++) {
      fs.add(
          new Factorization.Factor(
              Polynomial.variable(p.variables().get(i)), t.monomial().exponent(i)));
    }
    return new Factorization(t.coefficient(), fs, true).normalised();
  }

  private static Split items(Expr e, Context ctx, Recorder r, List<Integer> path) {
    List<Expr> out = new ArrayList<>();
    for (int i = 0; i < Operands.of(e).size(); i++) {
      out.add(child(e, i, ctx, r, path).expr());
    }
    return new Split(rebuilt(Operands.rebuild(e, out), ctx, r, path), ONE, ONE);
  }

  private static Split sum(Sum s, Context ctx, Recorder r, List<Integer> path) {
    RationalFunction q = RationalFunction.of(s);
    Expr form = Canonical.of(q.raw(), ctx.precision());
    r.rewrite(path, s, form, Factoring.RULE_SET, "common-denominator", Bindings.EMPTY);
    Expr denRaw = q.denominator().raw();
    Factorization num = factor(q.numerator(), ctx, r, path, form, f -> quotient(f, denRaw, ctx));
    Expr current = quotient(num.raw(), denRaw, ctx);
    Factorization den =
        factor(q.denominator(), ctx, r, path, current, f -> quotient(num.raw(), f, ctx));
    return new Split(quotient(num.raw(), den.raw(), ctx), num, den);
  }

  private static Split product(Product p, Context ctx, Recorder r, List<Integer> path) {
    List<Expr> factored = new ArrayList<>();
    Factorization num = ONE;
    Factorization den = ONE;
    for (int i = 0; i < p.factors().size(); i++) {
      Split s = child(p, i, ctx, r, path);
      factored.add(s.expr());
      num = num.multiply(s.num());
      den = den.multiply(s.den());
    }
    Expr raw = Product.of(factored);
    // a base left unsplit on either side can hide a factor of the other side
    if (!num.complete() || !den.complete()) {
      Factorization[] split = splitByGcd(num, den, ctx);
      num = split[0];
      den = split[1];
    }
    Factorization[] cancelled = cancel(num, den);
    if (cancelled[0].equals(num) && cancelled[1].equals(den)) {
      return new Split(rebuilt(raw, ctx, r, path), num, den);
    }
    Expr after = quotient(cancelled[0].raw(), cancelled[1].raw(), ctx);
    r.rewrite(path, raw, after, Factoring.RULE_SET, "cancel", Bindings.EMPTY);
    return new Split(after, cancelled[0], cancelled[1]);
  }

  /** Removes the bases common to both sides, the lower multiplicity from each. */
  private static Factorization[] cancel(Factorization num, Factorization den) {
    List<Factorization.Factor> n = new ArrayList<>();
    List<Factorization.Factor> d = new ArrayList<>(den.factors());
    for (Factorization.Factor f : num.factors()) {
      int m = f.multiplicity();
      for (int j = 0; j < d.size(); j++) {
        Factorization.Factor g = d.get(j);
        if (g.base().equals(f.base())) {
          int common = Math.min(m, g.multiplicity());
          m -= common;
          if (g.multiplicity() == common) {
            d.remove(j);
          } else {
            d.set(j, new Factorization.Factor(g.base(), g.multiplicity() - common));
          }
          break;
        }
      }
      if (m > 0) {
        n.add(new Factorization.Factor(f.base(), m));
      }
    }
    return new Factorization[] {
      new Factorization(num.unit(), n, num.complete()),
      new Factorization(den.unit(), d, den.complete())
    };
  }

  /**
   * Splits each numerator base and denominator base by their gcd through the gcd provider, until no
   * two of them share a factor, so that the common factors of unsplit bases cancel. It runs when
   * either side is incomplete, so every pair holds a base from an incomplete factorization.
   */
  private static Factorization[] splitByGcd(Factorization num, Factorization den, Context ctx) {
    List<Factorization.Factor> n = new ArrayList<>(num.factors());
    List<Factorization.Factor> d = new ArrayList<>(den.factors());
    boolean changed = true;
    while (changed) {
      changed = false;
      search:
      for (int i = 0; i < n.size(); i++) {
        for (int j = 0; j < d.size(); j++) {
          Polynomial a = n.get(i).base();
          Polynomial b = d.get(j).base();
          if (a.equals(b)) {
            continue;
          }
          Polynomial g =
              ctx.providers()
                  .run(new mathcanvas.engine.provider.Gcd(a, b), ctx, Steps.NONE)
                  .value();
          if (g.isConstant()) {
            continue;
          }
          int ma = n.get(i).multiplicity();
          int mb = d.get(j).multiplicity();
          n.set(i, new Factorization.Factor(Division.exactQuotient(a, g).orElseThrow(), ma));
          n.add(new Factorization.Factor(g, ma));
          d.set(j, new Factorization.Factor(Division.exactQuotient(b, g).orElseThrow(), mb));
          d.add(new Factorization.Factor(g, mb));
          changed = true;
          break search;
        }
      }
    }
    return new Factorization[] {
      new Factorization(num.unit(), n, num.complete()).normalised(),
      new Factorization(den.unit(), d, den.complete()).normalised()
    };
  }

  /**
   * Factors one polynomial through the provider; {@code whole} places the polynomial's forms in the
   * expression at {@code path}, and {@code before} is that expression now. The provider's steps are
   * recorded; when it recorded none but changed the expression, one rewrite named after it.
   */
  private static Factorization factor(
      Polynomial p,
      Context ctx,
      Recorder r,
      List<Integer> path,
      Expr before,
      UnaryOperator<Expr> whole) {
    if (p.isConstant()) {
      return new Factorization(p.leadingCoefficient(), List.of(), true);
    }
    int recorded = r.count();
    Steps sink =
        r.on
            ? (rule, b, a, bindings) ->
                r.rewrite(path, whole.apply(b), whole.apply(a), Factoring.RULE_SET, rule, bindings)
            : Steps.NONE;
    Providers.Outcome<Factorization> f = ctx.providers().run(new Factor(p), ctx, sink);
    Expr after = whole.apply(f.value().raw());
    // the normalised result can differ from the last step's form only in order or merged bases
    Expr last = r.count() > recorded ? r.last().after() : before;
    r.rewrite(path, last, after, PROVIDER, f.provider(), Bindings.EMPTY);
    return f.value();
  }

  private static Expr quotient(Expr num, Expr den, Context ctx) {
    return Canonical.of(Product.of(num, Power.of(den, Num.MINUS_ONE)), ctx.precision());
  }

  /** Converts over a common denominator, or empty when it is not in scalar kernels. */
  private static Optional<RationalFunction> rational(Expr e, Context ctx) {
    try {
      return Optional.of(RationalFunction.of(Canonical.of(e, ctx.precision())));
    } catch (IllegalArgumentException refused) {
      return Optional.empty();
    }
  }

  /**
   * Cancels the common factors of {@code e} over a common denominator: the canonical expression of
   * its {@link RationalFunction}, numerator and denominator in lowest terms.
   *
   * @param e the expression
   * @param ctx the context
   * @return the cancelled expression; {@code e} itself, canonical, when it is not a rational
   *     function in scalar kernels
   * @throws AlgebraLimitException past the algebra's bounds
   */
  public static Expr cancel(Expr e, Context ctx) {
    return limited(
        () -> {
          Optional<RationalFunction> q = rational(e, ctx);
          return Canonical.of(q.isEmpty() ? e : q.get().raw(), ctx.precision());
        });
  }

  /**
   * Decomposes {@code e} into partial fractions in {@code x} ({@link PartialFractions}), the
   * denominator factored through the factor provider.
   *
   * @param e the expression
   * @param x the variable
   * @param ctx the context
   * @return the sum of the polynomial part and the fractions; {@code e} itself, canonical, when it
   *     has a kernel other than {@code x}; empty when it is not a rational function in scalar
   *     kernels
   * @throws AlgebraLimitException past the algebra's bounds
   */
  public static Optional<Expr> apart(Expr e, Expr x, Context ctx) {
    return limited(
        () -> {
          Expr c = Canonical.of(e, ctx.precision());
          Optional<RationalFunction> q = rational(c, ctx);
          if (q.isEmpty()) {
            return Optional.empty();
          }
          Expr v = Canonical.of(x, ctx.precision());
          if (!List.of(v).containsAll(q.get().variables())) {
            return Optional.of(c);
          }
          Factorization den =
              ctx.providers().run(new Factor(q.get().denominator()), ctx, Steps.NONE).value();
          Optional<PartialFractions.Decomposition> d = PartialFractions.of(q.get(), v, den);
          if (d.isEmpty()) {
            return Optional.of(c);
          }
          List<Expr> terms = new ArrayList<>();
          terms.add(d.get().polynomial().raw());
          for (PartialFractions.Term t : d.get().terms()) {
            terms.add(
                Product.of(t.numerator().raw(), Power.of(t.base().raw(), Num.of(-t.power()))));
          }
          return Optional.of(
              Canonical.of(terms.size() == 1 ? terms.getFirst() : Sum.of(terms), ctx.precision()));
        });
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
