package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.AlgebraLimitException;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Occurrence;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Replacement;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;

/**
 * The searching rules of {@code integrate}: substitution and integration by parts. Each reads the
 * integrand, tries the integral its technique leads to, and fires only when that integral is found
 * whole, so a technique that would leave an integral it cannot finish changes nothing. The trials
 * go through the call's {@link IntegrateRules.Search}, which remembers them.
 *
 * <ul>
 *   <li>{@code substitution}: for a candidate inner function {@code g} of the integrand {@code f},
 *       {@code f/g′} written in a fresh {@code u} for {@code g} must be free of {@code x}; a linear
 *       {@code g = ax + b} that is the base of a power is substituted through {@code x = (u − b)/a}
 *       instead. The integral in {@code u} is tried one depth lower, {@code u} real when {@code g}
 *       is built of real parts ({@link #realParts}) and complex otherwise, and the result is its
 *       antiderivative with {@code g} put back for {@code u}.
 *   <li>{@code by-parts}: {@code ∫ u dv = u v − ∫ v du}, with {@code u} chosen by LIATE among two
 *       classes only: a polynomial times an exponential, sine, cosine or hyperbolic sine or cosine
 *       of a linear argument (or an exponential times a sine or cosine), and a logarithm or an
 *       inverse function times a power of {@code x} or a polynomial. The residual {@code ∫ v du} is
 *       tried at the same depth, since it is smaller in the measure (logarithms and inverse
 *       functions, then the polynomial's degree), and its antiderivative from the trial is the
 *       rule's. A positive power of a sum among the polynomial factors is left to {@code expand}.
 *   <li>{@code by-parts-twice}: an exponential times a sine or cosine, integrated by parts twice,
 *       comes back as {@code k} times itself; the equation {@code I = R − kI} is solved.
 * </ul>
 */
final class IntegrateSearch {

  private IntegrateSearch() {}

  /** The values {@code substitution} names. */
  static final Set<String> SUBSTITUTION_VALUES =
      Set.of("substitution", "transformed", "antiderivative");

  /** The values {@code by-parts} names. */
  static final Set<String> PARTS_VALUES = Set.of("u", "dv", "residual");

  /** The values {@code by-parts-twice} names. */
  static final Set<String> CYCLE_VALUES = Set.of("first", "second", "factor");

  /** The most candidate inner functions one substitution tries. */
  static final int MAX_CANDIDATES = 8;

  private static final Set<String> INVERSE =
      Set.of(
          "log", "asin", "acos", "atan", "acot", "asec", "acsc", "asinh", "acosh", "atanh", "acoth",
          "asech", "acsch");

  private static final Set<String> CLOSED = Set.of("sin", "cos", "sinh", "cosh");

  // Substitution.

  static Optional<Replacement.Valued.Result> substitute(
      Match m, int depth, IntegrateRules.Search search) {
    if (depth == 0) {
      return Optional.empty();
    }
    Expr f = m.bindings().get("f_");
    Symbol x = (Symbol) m.bindings().get("x_");
    Context ctx = m.context().withTrace(false);
    Symbol u = Occurrence.fresh("u", f);
    for (Expr g : candidates(f, x, ctx)) {
      ctx.cancellation().check();
      Optional<Expr> q = transformed(f, x, g, u, ctx);
      if (q.isEmpty()) {
        continue;
      }
      // u is real when g is built of real parts (the real form wins on the real domain), complex
      // otherwise, so the table takes log(u) for a complex g
      Assumptions facts = ctx.assumptions().without(u);
      if (!realParts(g, ctx)) {
        facts = facts.with(Relation.of(Relation.Op.IN, u, Constant.COMPLEXES));
      }
      Optional<Expr> h = search.attempt(q.get(), u, depth - 1, ctx.withAssumptions(facts));
      if (h.isEmpty()) {
        continue;
      }
      TreeMap<String, Expr> values = new TreeMap<>();
      values.put("substitution", Relation.of(Relation.Op.EQ, u, g));
      values.put("transformed", Integral.indefinite(q.get(), u));
      values.put("antiderivative", h.get());
      return Optional.of(
          new Replacement.Valued.Result(IntegrateRules.replace(h.get(), u, g, ctx), values));
    }
    return Optional.empty();
  }

  /**
   * Whether {@code g} holds no non-real constant and every symbol in it is real under the facts (a
   * symbol is real unless assumed complex). A part with no symbol in it is real when the facts say
   * so or the numerics give it a real value at 20 and at 40 digits ({@code log 2}, {@code √2}), and
   * not otherwise ({@code asin(2)}, {@code (−1)^(1/3)}); only the parts that hold a symbol are
   * looked into. Where such a {@code g} is not real for a real {@code x} (a logarithm or a root of
   * a negative value) the integrand is not real either, so the real form of an antiderivative
   * ({@code log|u|}) holds wherever the integrand is real.
   */
  static boolean realParts(Expr g, Context ctx) {
    if (g instanceof Symbol s) {
      return ctx.assumptions().isReal(s) == Tri.TRUE;
    }
    if (!holdsSymbol(g)) {
      return ctx.assumptions().isReal(g) == Tri.TRUE || realValue(g, ctx);
    }
    for (Expr o : Operands.of(g)) {
      if (!realParts(o, ctx)) {
        return false;
      }
    }
    return true;
  }

  private static boolean holdsSymbol(Expr e) {
    if (e instanceof Symbol) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (holdsSymbol(o)) {
        return true;
      }
    }
    return false;
  }

  /** Whether the numerics give {@code c} a real value at 20 and at 40 digits. */
  private static boolean realValue(Expr c, Context ctx) {
    for (int digits : new int[] {20, 40}) {
      Optional<mathcanvas.engine.number.Numeric> v =
          ctx.numerics().value(c, new java.math.MathContext(digits));
      if (v.isEmpty() || !(v.get() instanceof mathcanvas.engine.number.RealNumeric)) {
        return false;
      }
    }
    return true;
  }

  /**
   * The candidate inner functions, in the order of a walk of the integrand: the arguments of
   * applications and the applications, the bases and the exponents of powers and powers of a
   * constant base; those that depend on {@code x} and are not {@code x}, a linear one only as the
   * base of a power; at most {@link #MAX_CANDIDATES}.
   */
  private static List<Expr> candidates(Expr f, Symbol x, Context ctx) {
    Set<Expr> out = new LinkedHashSet<>();
    collect(f, x, ctx, out);
    return out.stream().limit(MAX_CANDIDATES).toList();
  }

  private static void collect(Expr e, Symbol x, Context ctx, Set<Expr> out) {
    switch (e) {
      case Apply a -> {
        for (Expr arg : a.args()) {
          if (nonlinear(arg, x, ctx)) {
            out.add(arg);
          }
        }
        if (!Free.of(a, x)) {
          out.add(a);
        }
      }
      case Power p -> {
        Expr b = p.base();
        if (!Free.of(b, x) && !b.equals(x)) {
          out.add(b);
        }
        if (nonlinear(p.exponent(), x, ctx)) {
          out.add(p.exponent());
        }
        if (Free.of(b, x) && !Free.of(p.exponent(), x)) {
          out.add(p);
        }
        // x^n: the powers x^d for the divisors d of n, so x⁴ can be read as (x²)²
        if (b.equals(x)
            && p.exponent() instanceof Num n
            && n.value() instanceof Int k
            && k.signum() > 0
            && k.value().bitLength() < 8) {
          int m = k.value().intValue();
          for (int d = 2; d <= m; d++) {
            if (m % d == 0) {
              out.add(Power.of(x, Num.of(d)));
            }
          }
        }
      }
      default -> {}
    }
    for (Expr o : Operands.of(e)) {
      collect(o, x, ctx, out);
    }
  }

  private static boolean nonlinear(Expr e, Symbol x, Context ctx) {
    return !Free.of(e, x) && IntegrateRules.slope(e, x, ctx.assumptions()).isEmpty();
  }

  /** {@code f dx} written in {@code u = g}, free of {@code x}, or empty. */
  private static Optional<Expr> transformed(Expr f, Symbol x, Expr g, Symbol u, Context ctx) {
    Optional<Expr> slope = IntegrateRules.slope(g, x, ctx.assumptions());
    if (slope.isPresent()) {
      // x = (u − b)/a, dx = du/a
      Expr a = slope.get();
      Expr b = Canonical.of(Sum.of(g, Product.of(Num.MINUS_ONE, a, x)));
      Expr inverse =
          Product.of(Sum.of(u, Product.of(Num.MINUS_ONE, b)), Power.of(a, Num.MINUS_ONE));
      Expr q =
          Canonical.of(
              Product.of(IntegrateRules.replace(f, x, inverse, ctx), Power.of(a, Num.MINUS_ONE)));
      return Free.of(q, x) ? Optional.of(q) : Optional.empty();
    }
    Expr derivative = Calculus.differentiate(g, x, 1, ctx).expr();
    // dividing by g′ needs it not identically zero: each factor of it free of x known non-zero
    if (!IntegrateRules.complete(derivative, x)) {
      return Optional.empty();
    }
    for (Expr factor : derivative instanceof Product p ? p.factors() : List.of(derivative)) {
      if (Free.of(factor, x) && ctx.assumptions().isNonZero(factor) != Tri.TRUE) {
        return Optional.empty();
      }
    }
    Expr q = Canonical.of(Product.of(f, Power.of(derivative, Num.MINUS_ONE)));
    Expr inU = inU(q, g, u, ctx);
    if (Free.of(inU, x)) {
      return Optional.of(inU);
    }
    try {
      inU = inU(Algebra.cancel(q, ctx), g, u, ctx);
    } catch (AlgebraLimitException tooLarge) {
      return Optional.empty();
    }
    return Free.of(inU, x) ? Optional.of(inU) : Optional.empty();
  }

  /**
   * {@code q} with {@code g} written as {@code u}: {@code g} itself, and for a power {@code b^w}
   * its powers {@code b^(k w)} as {@code u^k}, with {@code k} an integer, or any number when {@code
   * b} is positive ({@code x} is not {@code (x²)^(1/2)}).
   */
  private static Expr inU(Expr q, Expr g, Symbol u, Context ctx) {
    Expr q1 = IntegrateRules.replace(q, g, u, ctx);
    if (!(g instanceof Power p) || !Free.of(p.base(), u)) {
      return q1;
    }
    Rule power =
        Rule.computed(
            "power-to-u",
            Pattern.parse("b_^w_")
                .where(List.of("b_", "w_"), (b, a) -> b.get("b_").equals(p.base())),
            m -> {
              Expr k =
                  Canonical.of(
                      Product.of(m.bindings().get("w_"), Power.of(p.exponent(), Num.MINUS_ONE)));
              // (b^w)^k is b^(kw) for an integer k, or for any k when b is positive
              boolean sound =
                  k instanceof Num n
                      && (n.value() instanceof Int
                          || ctx.assumptions().isPositive(p.base()) == Tri.TRUE);
              return sound ? Optional.of(Power.of(u, k)) : Optional.empty();
            });
    return Rewriter.rewrite(q1, new RuleSet("substitute", List.of(power)), ctx).expr();
  }

  // By parts.

  static Optional<Replacement.Valued.Result> byParts(
      Match m, int depth, IntegrateRules.Search search) {
    Expr f = m.bindings().get("f_");
    Symbol x = (Symbol) m.bindings().get("x_");
    Context ctx = m.context().withTrace(false);
    List<Expr> factors = f instanceof Product p ? p.factors() : List.of(f);
    Optional<Expr[]> split = split(factors, x, ctx);
    if (split.isEmpty()) {
      return Optional.empty();
    }
    Expr u = split.get()[0];
    Expr dv = split.get()[1];
    Optional<Expr> v = search.attempt(dv, x, depth, ctx);
    if (v.isEmpty() || (inverse(u) && hasInverse(v.get()))) {
      return Optional.empty();
    }
    Expr du = Calculus.differentiate(u, x, 1, ctx).expr();
    Expr residual = Canonical.of(Product.of(v.get(), du));
    // the trial's antiderivative of the residual is kept, not derived again
    Optional<Expr> w = search.attempt(residual, x, depth, ctx);
    if (w.isEmpty()) {
      return Optional.empty();
    }
    TreeMap<String, Expr> values = new TreeMap<>();
    values.put("u", u);
    values.put("dv", dv);
    values.put("residual", Integral.indefinite(residual, x));
    Expr result = Sum.of(Product.of(u, v.get()), Product.of(Num.MINUS_ONE, w.get()));
    return Optional.of(new Replacement.Valued.Result(result, values));
  }

  /**
   * {@code u} and {@code dv} of the two classes: polynomial factors times one exponential, sine,
   * cosine, hyperbolic sine or cosine of a linear argument (or an exponential and a sine or
   * cosine), {@code u} the polynomial; or one logarithm or inverse function times powers of {@code
   * x} and polynomials, {@code u} the logarithm or inverse function.
   */
  private static Optional<Expr[]> split(List<Expr> factors, Symbol x, Context ctx) {
    List<Expr> polynomial = new ArrayList<>();
    List<Expr> closed = new ArrayList<>();
    List<Expr> algebraic = new ArrayList<>();
    List<Expr> inverse = new ArrayList<>();
    for (Expr fac : factors) {
      if (Free.of(fac, x)) {
        return Optional.empty();
      }
      if (fac instanceof Power w && w.base() instanceof Sum && polynomial(fac, x)) {
        // a power of a sum is expanded first (rule expand), not integrated by parts as a whole
        return Optional.empty();
      }
      if (polynomial(fac, x)) {
        polynomial.add(fac);
        algebraic.add(fac);
      } else if (closed(fac, x, ctx)) {
        closed.add(fac);
      } else if (fac instanceof Power w && w.base().equals(x) && w.exponent() instanceof Num) {
        algebraic.add(fac);
      } else if (inverse(fac)) {
        inverse.add(fac);
      } else {
        return Optional.empty();
      }
    }
    if (inverse.isEmpty() && !polynomial.isEmpty() && pair(closed)) {
      return Optional.of(new Expr[] {product(polynomial), product(closed)});
    }
    if (inverse.size() == 1 && closed.isEmpty() && algebraic.size() + 1 == factors.size()) {
      return Optional.of(
          new Expr[] {inverse.getFirst(), algebraic.isEmpty() ? Num.ONE : product(algebraic)});
    }
    return Optional.empty();
  }

  /** One closed factor, or an exponential and a sine or cosine. */
  private static boolean pair(List<Expr> closed) {
    if (closed.size() == 1) {
      return true;
    }
    return closed.size() == 2
        && closed.stream().filter(c -> c instanceof Power).count() == 1
        && closed.stream()
                .filter(c -> c instanceof Apply a && Set.of("sin", "cos").contains(a.head().name()))
                .count()
            == 1;
  }

  /** A polynomial of positive degree in {@code x}, its other kernels free of {@code x}. */
  private static boolean polynomial(Expr e, Symbol x) {
    Polynomial p;
    try {
      p = Polynomial.of(e);
    } catch (RuntimeException refused) {
      return false;
    }
    if (!p.variables().contains(x) || p.degree(x) < 1) {
      return false;
    }
    for (Expr k : p.variables()) {
      if (!k.equals(x) && !Free.of(k, x)) {
        return false;
      }
    }
    return true;
  }

  /**
   * An exponential with a constant positive base or a sine, cosine, hyperbolic sine or cosine of a
   * linear argument: a factor whose antiderivatives stay in its family.
   */
  private static boolean closed(Expr e, Symbol x, Context ctx) {
    return switch (e) {
      case Power p ->
          Free.of(p.base(), x)
              && (p.base().equals(Constant.E) || ctx.assumptions().isPositive(p.base()) == Tri.TRUE)
              && IntegrateRules.slope(p.exponent(), x, ctx.assumptions()).isPresent();
      case Apply a ->
          CLOSED.contains(a.head().name())
              && IntegrateRules.slope(a.args().getFirst(), x, ctx.assumptions()).isPresent();
      default -> false;
    };
  }

  /** A logarithm or inverse function, or a positive integer power of a logarithm. */
  private static boolean inverse(Expr e) {
    if (e instanceof Apply a && a.args().size() == 1 && INVERSE.contains(a.head().name())) {
      return true;
    }
    return e instanceof Power p
        && p.base() instanceof Apply a
        && a.head().name().equals("log")
        && a.args().size() == 1
        && p.exponent() instanceof Num n
        && n.value() instanceof Int k
        && k.signum() > 0;
  }

  /** Whether a logarithm or inverse function occurs in {@code e}. */
  private static boolean hasInverse(Expr e) {
    if (e instanceof Apply a && INVERSE.contains(a.head().name())) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (hasInverse(o)) {
        return true;
      }
    }
    return false;
  }

  /** An exponential times a sine or cosine: two rounds by parts, then the equation solved. */
  static Optional<Replacement.Valued.Result> byPartsTwice(
      Match m, int depth, IntegrateRules.Search search) {
    Expr f = m.bindings().get("f_");
    Symbol x = (Symbol) m.bindings().get("x_");
    Context ctx = m.context().withTrace(false);
    Optional<Expr[]> first = round(f, x, depth, ctx, search);
    if (first.isEmpty()) {
      return Optional.empty();
    }
    Optional<Expr[]> second = round(first.get()[1], x, depth, ctx, search);
    if (second.isEmpty()) {
      return Optional.empty();
    }
    // I = R1 − J1, J1 = R2 − J2, J2 = k I
    Expr k = Canonical.of(Product.of(second.get()[1], Power.of(f, Num.MINUS_ONE)));
    Expr denominator = Canonical.of(Sum.of(Num.ONE, Product.of(Num.MINUS_ONE, k)));
    if (!Free.of(k, x) || ctx.assumptions().isNonZero(denominator) != Tri.TRUE) {
      return Optional.empty();
    }
    Expr r1 = first.get()[0];
    Expr r2 = second.get()[0];
    Expr result =
        Product.of(Sum.of(r1, Product.of(Num.MINUS_ONE, r2)), Power.of(denominator, Num.MINUS_ONE));
    TreeMap<String, Expr> values = new TreeMap<>();
    values.put("first", r1);
    values.put("second", r2);
    values.put("factor", k);
    return Optional.of(new Replacement.Valued.Result(result, values));
  }

  /**
   * One round by parts on {@code c·E·T}, {@code E} an exponential and {@code T} a sine or cosine of
   * linear arguments, {@code u = T}: the part {@code c T V} and the residual integrand {@code c V
   * T′}, {@code V} the antiderivative of {@code E}.
   */
  private static Optional<Expr[]> round(
      Expr f, Symbol x, int depth, Context ctx, IntegrateRules.Search search) {
    List<Expr> factors = f instanceof Product p ? p.factors() : List.of(f);
    List<Expr> constants = new ArrayList<>();
    Expr exp = null;
    Expr trig = null;
    for (Expr fac : factors) {
      if (Free.of(fac, x)) {
        constants.add(fac);
      } else if (fac instanceof Power && exp == null && closed(fac, x, ctx)) {
        exp = fac;
      } else if (fac instanceof Apply a
          && trig == null
          && Set.of("sin", "cos").contains(a.head().name())
          && closed(fac, x, ctx)) {
        trig = fac;
      } else {
        return Optional.empty();
      }
    }
    if (exp == null || trig == null) {
      return Optional.empty();
    }
    Optional<Expr> v = search.attempt(exp, x, depth, ctx);
    if (v.isEmpty()) {
      return Optional.empty();
    }
    Expr c = product(constants);
    Expr dt = Calculus.differentiate(trig, x, 1, ctx).expr();
    return Optional.of(
        new Expr[] {
          Canonical.of(Product.of(c, trig, v.get())), Canonical.of(Product.of(c, v.get(), dt))
        });
  }

  /** The product of {@code factors}: 1 for none, the factor itself for one. */
  static Expr product(List<Expr> factors) {
    return Bindings.combine(factors, Bindings.Origin.PRODUCT);
  }
}
