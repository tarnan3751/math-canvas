package mathcanvas.engine.ode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Occurrence;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.ode.Dsolve.Chain;
import mathcanvas.engine.ode.Dsolve.General;
import mathcanvas.engine.ode.Dsolve.Solution;

/**
 * The first-order classes, on {@code y' = f(x, y)} or on {@code M + N y' = 0}: linear (an
 * integrating factor), separable, exact (a potential) and Bernoulli (a power of {@code y} that
 * makes it linear). Each gives its general solution with one constant of integration, explicit when
 * {@code solve} gives {@code y}, implicit otherwise; a class that cannot finish (an integral not
 * found) gives null, and the next is tried.
 */
final class FirstOrder {

  private final Dsolve d;
  private final Symbol y;
  private final Symbol p;
  private final Symbol x;

  FirstOrder(Dsolve d, Symbol y) {
    this.d = d;
    this.y = y;
    this.p = Dsolve.sym(y, 1);
    this.x = d.x;
  }

  private Tools t() {
    return d.tools;
  }

  /** {@code f} with {@code y' = f}, from {@code e = 0}; null unless one value solves it. */
  Expr solvedForDerivative(Expr e) {
    Optional<List<Expr>> f = t().solveFor(e, p);
    if (f.isEmpty() || f.get().size() != 1 || Occurrence.mentions(f.get().getFirst(), p)) {
      return null;
    }
    return f.get().getFirst();
  }

  /** {@code y' + P y = Q}: {@code y = (∫μQ dx + C)/μ} with {@code μ = e^∫P dx}. */
  General linear(Expr f) {
    Expr a = t().d(f, y);
    if (Occurrence.mentions(a, y)) {
      return null;
    }
    Expr b = t().simplify(t().subs(f, Map.of(y, Num.ZERO)));
    if (!t().isZero(Exprs.sub(f, Dsolve.plus(Product.of(a, y), b)))) {
      return null;
    }
    Chain chain = d.new Chain();
    Symbol c = d.constant(0);
    if (a.equals(Num.ZERO)) {
      Optional<Expr> g = t().integrate(b, x);
      if (g.isEmpty()) {
        return null;
      }
      Expr value = t().simplify(Dsolve.plus(g.get(), c));
      chain.step("integrate", d.eq(d.applied(y), value));
      return new General(chain, List.of(new Solution(List.of(value), null)));
    }
    Expr pp = t().simplify(Exprs.neg(a));
    Expr value = integratingFactor(chain, pp, b, y, c, "linear");
    return value == null ? null : new General(chain, List.of(new Solution(List.of(value), null)));
  }

  /**
   * Solves {@code u' + P u = Q} for {@code u}, which is {@code shown} in the steps, recording the
   * steps from {@code first}; null when an integral is not found.
   */
  private Expr integratingFactor(Chain chain, Expr pp, Expr q, Expr shown, Symbol c, String first) {
    Optional<Expr> ip = t().integrate(pp, x);
    if (ip.isEmpty()) {
      return null;
    }
    Expr mu = t().simplify(unabs(exponential(t().simplify(ip.get()))));
    Optional<Expr> integral = t().integrate(Product.of(mu, q), x);
    if (integral.isEmpty()) {
      return null;
    }
    Expr u = d.functions(shown);
    if (first.equals("linear")) {
      chain.step(
          "linear", d.eq(Dsolve.plus(Dsolve.sym(y, 1), Product.of(pp, y)), q), "p_", pp, "q_", q);
    }
    chain.step(
        "integrating-factor",
        d.eq(Derivative.of(Product.of(mu, u), x, 1), Product.of(mu, q)),
        "mu_",
        mu);
    lastMu = mu;
    lastIntegral = integral.get();
    Expr c1 = Dsolve.plus(integral.get(), c);
    chain.step("integrate", d.eq(Product.of(mu, u), c1));
    return t().simplify(Tools.div(c1, mu));
  }

  /**
   * The integrating factor and the integral of {@code μQ} of the last {@link #integratingFactor}.
   */
  private Expr lastMu;

  private Expr lastIntegral;

  /** {@code y' = g(x) h(y)}: {@code ∫dy/h = ∫g dx + C}, then {@code y} where it solves. */
  General separable(Expr f) {
    if (!Occurrence.mentions(f, y)) {
      return null;
    }
    Expr[] gh = split(f);
    if (gh == null) {
      Optional<mathcanvas.engine.rule.Rewriter.Result> factored = Algebra.factor(f, t().context());
      if (factored.isPresent()) {
        gh = split(t().canon(factored.get().expr()));
      }
    }
    if (gh == null) {
      return null;
    }
    Expr g = gh[0];
    Expr h = gh[1];
    Optional<Expr> hh = t().integrate(Tools.div(Num.ONE, h), y);
    Optional<Expr> gg = t().integrate(g, x);
    if (hh.isEmpty() || gg.isEmpty()) {
      return null;
    }
    Chain chain = d.new Chain();
    Symbol c = d.constant(0);
    chain.step("separate", d.eq(Tools.div(Dsolve.sym(y, 1), h), g), "g_", g, "h_", h);
    Expr right = Dsolve.plus(gg.get(), c);
    chain.step("integrate", d.eq(hh.get(), right));
    Expr u = logProduct(t().simplify(hh.get()));
    Expr equation;
    Expr phi;
    if (u != null) {
      Expr ce = Product.of(c, Tools.exp(gg.get()));
      chain.step("exponentiate", d.eq(u, ce));
      equation = Exprs.sub(u, ce);
      phi = t().simplify(Product.of(u, Tools.exp(Exprs.neg(gg.get()))));
    } else {
      equation = Exprs.sub(hh.get(), right);
      phi = t().simplify(Exprs.sub(hh.get(), gg.get()));
    }
    // y = b where h(b) = 0 is a solution the division by h lost
    return explicitOrImplicit(chain, equation, phi, c).losing(h);
  }

  /** The branches of {@code y} that solve {@code equation = 0}, or {@code phi = c} implicitly. */
  private General explicitOrImplicit(Chain chain, Expr equation, Expr phi, Symbol c) {
    Optional<List<Expr>> ys = t().solveFor(equation, y);
    General implicit = null;
    if (ys.isEmpty() || ys.get().isEmpty()) {
      Relation inverted = invertAtan(equation);
      if (inverted != null) {
        Chain before = chain.copy();
        ys = t().solveFor(Exprs.sub(inverted.left(), inverted.right()), y);
        if (ys.isPresent() && !ys.get().isEmpty()) {
          chain.step("invert", d.eq(d.functions(inverted.left()), d.functions(inverted.right())));
          // the tangent holds only while its angle stays within (−π/2, π/2): when the check
          // refuses it, the implicit solution stands, as without the inversion
          implicit = explicitOrImplicit(before, Optional.empty(), phi, c, null);
        }
      }
    }
    return explicitOrImplicit(chain, ys, phi, c, implicit);
  }

  private General explicitOrImplicit(
      Chain chain, Optional<List<Expr>> ys, Expr phi, Symbol c, General fallback) {
    List<Solution> out = new ArrayList<>();
    if (ys.isPresent() && !ys.get().isEmpty()) {
      for (Expr v : ys.get()) {
        out.add(new Solution(List.of(t().simplify(v)), null));
      }
    } else {
      Relation implicit = Relation.of(Relation.Op.EQ, phi, c);
      out.add(new Solution(null, implicit));
      chain.step("implicit", d.shown(out.getFirst()));
    }
    return new General(chain, out, Relation.of(Relation.Op.EQ, phi, c), null, null, fallback);
  }

  /**
   * {@code c·atan(w) + r = 0}, with {@code r} free of {@code y} and {@code c} a number, as {@code w
   * = tan(−r/c)}: the tangent inverts {@code atan}, and the solution it gives is checked by
   * substitution; null when the equation has another shape.
   */
  private Relation invertAtan(Expr equation) {
    Expr atan = null;
    Expr coefficient = Num.ONE;
    List<Expr> rest = new ArrayList<>();
    for (Expr term : Tools.terms(t().canon(equation))) {
      if (!Occurrence.mentions(term, y)) {
        rest.add(term);
        continue;
      }
      if (atan != null) {
        return null;
      }
      Expr kernel = term;
      if (term instanceof Product p
          && p.factors().size() == 2
          && p.factors().getFirst() instanceof Num n) {
        coefficient = n;
        kernel = p.factors().get(1);
      }
      if (!(kernel instanceof Apply a && a.head().name().equals("atan") && a.args().size() == 1)) {
        return null;
      }
      atan = a.args().getFirst();
    }
    if (atan == null) {
      return null;
    }
    Expr angle = t().canon(Tools.div(Exprs.neg(Tools.sum(rest)), coefficient));
    return Relation.of(Relation.Op.EQ, atan, Apply.of(Symbol.of("tan"), angle));
  }

  /** {@code f} as {@code [g(x), h(y)]}, or null when its factors do not separate. */
  private Expr[] split(Expr f) {
    List<Expr> g = new ArrayList<>();
    List<Expr> h = new ArrayList<>();
    for (Expr factor : Tools.factors(f)) {
      if (!Occurrence.mentions(factor, y)) {
        g.add(factor);
      } else if (!Occurrence.mentions(factor, x)) {
        h.add(factor);
      } else if (factor instanceof Power pw
          && pw.base().equals(mathcanvas.engine.expr.Constant.E)
          && pw.exponent() instanceof mathcanvas.engine.expr.Sum s) {
        // e^(a(x) + b(y)) = e^a(x)·e^b(y)
        List<Expr> gx = new ArrayList<>();
        List<Expr> hy = new ArrayList<>();
        for (Expr term : s.terms()) {
          if (!Occurrence.mentions(term, y)) {
            gx.add(term);
          } else if (!Occurrence.mentions(term, x)) {
            hy.add(term);
          } else {
            return null;
          }
        }
        g.add(Tools.exp(Tools.sum(gx)));
        h.add(Tools.exp(Tools.sum(hy)));
      } else {
        return null;
      }
    }
    if (h.isEmpty()) {
      return null;
    }
    return new Expr[] {t().canon(Tools.product(g)), t().canon(Tools.product(h))};
  }

  /**
   * {@code Π uᵢ^cᵢ} for a sum of logarithms {@code Σ cᵢ log|uᵢ|} with rational {@code cᵢ}, or null:
   * exponentiating it absorbs the sign and {@code e^C} into the constant.
   */
  private Expr logProduct(Expr h) {
    List<Expr> factors = new ArrayList<>();
    for (Expr term : Tools.terms(h)) {
      Expr[] log = logTerm(term);
      if (log == null) {
        return null;
      }
      factors.add(Power.of(log[1], log[0]));
    }
    return t().simplify(Tools.product(factors));
  }

  /**
   * {@code e^u} with each term {@code c log|v|} of {@code u}, {@code c} rational, written {@code
   * v^c}, the absolute value dropped: a constant multiple of an integrating factor is one too.
   */
  private Expr exponential(Expr u) {
    List<Expr> factors = new ArrayList<>();
    List<Expr> rest = new ArrayList<>();
    for (Expr term : Tools.terms(u)) {
      Expr[] log = logTerm(term);
      if (log == null) {
        rest.add(term);
      } else {
        factors.add(Power.of(log[1], log[0]));
      }
    }
    factors.add(Tools.exp(Tools.sum(rest)));
    return t().simplify(Tools.product(factors));
  }

  /**
   * {@code [c, v]} for a term {@code c log|v|} or {@code c log(v)}, {@code c} rational, or null.
   */
  private static Expr[] logTerm(Expr term) {
    Expr coefficient = Num.ONE;
    Expr log = term;
    if (term instanceof Product pr
        && pr.factors().size() == 2
        && pr.factors().getFirst() instanceof Num n
        && n.value().isExact()
        && n.value() instanceof mathcanvas.engine.number.RealNumeric) {
      coefficient = n;
      log = pr.factors().get(1);
    }
    if (!(log instanceof Apply a)
        || !(a.head().name().equals("log") || a.head().name().equals("ln"))
        || a.args().size() != 1) {
      return null;
    }
    Expr arg = a.args().getFirst();
    if (arg instanceof Apply abs && abs.head().name().equals("abs")) {
      arg = abs.args().getFirst();
    }
    return new Expr[] {coefficient, arg};
  }

  /** Every {@code |u|} as {@code u}: a constant multiple of an integrating factor is one too. */
  private static Expr unabs(Expr e) {
    if (e instanceof Apply a && a.head().name().equals("abs") && a.args().size() == 1) {
      return unabs(a.args().getFirst());
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>();
    for (Expr op : ops) {
      out.add(unabs(op));
    }
    return Operands.rebuild(e, out);
  }

  /** {@code M + N y' = 0} with {@code ∂M/∂y = ∂N/∂x}: the potential {@code Φ = C}. */
  General exact(Expr e) {
    Expr n = t().simplify(t().d(e, p));
    if (Occurrence.mentions(n, p) || n.equals(Num.ZERO)) {
      return null;
    }
    Expr m = t().simplify(t().subs(e, Map.of(p, Num.ZERO)));
    if (!t().isZero(Exprs.sub(e, Dsolve.plus(m, Product.of(n, p))))) {
      return null;
    }
    Expr my = t().simplify(t().d(m, y));
    if (!t().isZero(Exprs.sub(my, t().d(n, x)))) {
      return null;
    }
    Optional<Expr> phi1 = t().integrate(m, x);
    if (phi1.isEmpty()) {
      return null;
    }
    Expr hp = t().expand(Exprs.sub(n, t().d(phi1.get(), y)));
    if (Occurrence.mentions(hp, x)) {
      return null;
    }
    Optional<Expr> h = hp.equals(Num.ZERO) ? Optional.of(Num.ZERO) : t().integrate(hp, y);
    if (h.isEmpty()) {
      return null;
    }
    Expr phi = t().simplify(Dsolve.plus(phi1.get(), h.get()));
    Chain chain = d.new Chain();
    Symbol c = d.constant(0);
    chain.step(
        "exact-test", d.eq(Dsolve.plus(m, Product.of(n, p)), Num.ZERO), "d_", my, "m_", m, "n_", n);
    chain.step("potential", d.eq(phi, c));
    return explicitOrImplicit(chain, Exprs.sub(phi, c), phi, c);
  }

  /** {@code y' = a(x) y + b(x) yⁿ}: {@code v = y^(1−n)} is linear. */
  General bernoulli(Expr f) {
    Map<Numeric, List<Expr>> byPower = new TreeMap<>(FirstOrder::compare);
    for (Expr term : Tools.terms(t().expand(f))) {
      Numeric k = null;
      List<Expr> coefficient = new ArrayList<>();
      for (Expr factor : Tools.factors(term)) {
        if (factor.equals(y)) {
          k = Numeric_ONE;
        } else if (factor instanceof Power pw
            && pw.base().equals(y)
            && pw.exponent() instanceof Num e
            && e.value().isExact()
            && e.value() instanceof mathcanvas.engine.number.RealNumeric) {
          k = e.value();
        } else if (Occurrence.mentions(factor, y)) {
          return null;
        } else {
          coefficient.add(factor);
        }
      }
      if (k == null) {
        return null;
      }
      byPower.computeIfAbsent(k, any -> new ArrayList<>()).add(Tools.product(coefficient));
    }
    if (byPower.size() != 2 || !byPower.containsKey(Numeric_ONE)) {
      return null;
    }
    Numeric n = null;
    for (Numeric k : byPower.keySet()) {
      if (compare(k, Numeric_ONE) != 0) {
        n = k;
      }
    }
    if (n == null || n.isZero()) {
      return null;
    }
    Expr a = t().simplify(Tools.sum(byPower.get(Numeric_ONE)));
    Expr b = t().simplify(Tools.sum(byPower.get(n)));
    Expr m = t().canon(Exprs.sub(Num.ONE, new Num(n)));
    Expr pp = t().simplify(Exprs.neg(Product.of(m, a)));
    Expr q = t().simplify(Product.of(m, b));
    Chain chain = d.new Chain();
    Symbol c = d.constant(0);
    Expr v = Power.of(y, m);
    chain.step(
        "bernoulli",
        d.eq(Dsolve.plus(Derivative.of(d.functions(v), x, 1), Product.of(pp, v)), q),
        "n_",
        new Num(n));
    Expr value = integratingFactor(chain, pp, q, v, c, "bernoulli");
    if (value == null) {
      return null;
    }
    Expr solved = t().simplify(Power.of(value, Tools.div(Num.ONE, m)));
    List<Solution> branches = new ArrayList<>();
    branches.add(new Solution(List.of(solved), null));
    if (m instanceof Num mn
        && mn.value() instanceof mathcanvas.engine.number.Int k
        && !k.value().testBit(0)) {
      // 1/(1 − n) of even denominator: y = ±v^(1/(1−n))
      branches.add(new Solution(List.of(t().simplify(Exprs.neg(solved))), null));
    }
    // μ y^(1−n) − ∫μQ dx = C gives the constant from a condition; y = 0 solves for n > 0
    Relation potential =
        Relation.of(
            Relation.Op.EQ,
            t().simplify(Exprs.sub(Product.of(lastMu, Power.of(y, m)), lastIntegral)),
            c);
    General g = new General(chain, branches, potential);
    if (!(n instanceof mathcanvas.engine.number.Int)) {
      // y^(1−n) = v is a principal root of y, so y = v^(1/(1−n)) holds only where v ≥ 0: the
      // explicit branch is kept with v as its guard for a condition, and when the check refuses
      // the explicit general solution the potential stands
      Chain implicitChain = chain.copy();
      Solution implicit = new Solution(null, potential);
      implicitChain.step("root-branch", d.shown(implicit));
      g =
          new General(
              chain,
              branches,
              potential,
              null,
              new Solution(List.of(solved), null, value, null),
              new General(implicitChain, List.of(implicit), potential));
    }
    return ((mathcanvas.engine.number.RealNumeric) n).signum() > 0 ? g.losing(y) : g;
  }

  private static final Numeric Numeric_ONE = mathcanvas.engine.number.Int.ONE;

  private static int compare(Numeric a, Numeric b) {
    return ((mathcanvas.engine.number.RealNumeric) a)
        .compareValue((mathcanvas.engine.number.RealNumeric) b);
  }
}
