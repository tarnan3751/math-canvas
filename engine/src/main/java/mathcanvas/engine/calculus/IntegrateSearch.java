package mathcanvas.engine.calculus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.AlgebraLimitException;
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
 * The searching rule of {@code integrate}: substitution. It reads the integrand, tries the integral
 * its technique leads to, and fires only when that integral is found whole, so a technique that
 * would leave an integral it cannot finish changes nothing. The trials go through the call's {@link
 * IntegrateRules.Search}, which remembers them.
 *
 * <ul>
 *   <li>{@code substitution}: for a candidate inner function {@code g} of the integrand {@code f},
 *       {@code f/g′} written in a fresh {@code u} for {@code g} must be free of {@code x}; a linear
 *       {@code g = ax + b} that is the base of a power is substituted through {@code x = (u − b)/a}
 *       instead. The integral in {@code u} is tried one depth lower, {@code u} real when {@code g}
 *       is built of real parts ({@link #realParts}) and complex otherwise, and the result is its
 *       antiderivative with {@code g} put back for {@code u}.
 * </ul>
 */
final class IntegrateSearch {

  private IntegrateSearch() {}

  /** The values {@code substitution} names. */
  static final Set<String> SUBSTITUTION_VALUES =
      Set.of("substitution", "transformed", "antiderivative");

  /** The most candidate inner functions one substitution tries. */
  static final int MAX_CANDIDATES = 8;

  // Substitution.

  static Optional<Replacement.Valued.Result> substitute(
      Match m, int depth, IntegrateRules.Search search) {
    if (depth == 0) {
      return Optional.empty();
    }
    Expr f = m.bindings().get("f_");
    Symbol x = (Symbol) m.bindings().get("x_");
    Context ctx = m.context().withTrace(false);
    Symbol u = fresh(f);
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

  /** {@code u}, or {@code u_1}, {@code u_2}, … : the first that does not occur in {@code e}. */
  private static Symbol fresh(Expr e) {
    Set<String> names = new LinkedHashSet<>();
    symbols(e, names);
    String name = "u";
    for (int k = 1; names.contains(name); k++) {
      name = "u_" + k;
    }
    return Symbol.of(name);
  }

  private static void symbols(Expr e, Set<String> out) {
    if (e instanceof Symbol s) {
      out.add(s.name());
    }
    for (Expr o : Operands.of(e)) {
      symbols(o, out);
    }
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
}
