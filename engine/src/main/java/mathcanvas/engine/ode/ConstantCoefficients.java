package mathcanvas.engine.ode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.ode.Dsolve.Chain;
import mathcanvas.engine.ode.Dsolve.General;
import mathcanvas.engine.ode.Dsolve.Solution;

/**
 * Linear equations with constant coefficients, {@code Σ aⱼ y⁽ʲ⁾ = g(x)}, of any order: the roots of
 * the characteristic polynomial with their multiplicities give the homogeneous basis ({@code xᵏ
 * e^(rx)} for a real root, {@code xᵏ e^(αx) cos(βx)} and {@code xᵏ e^(αx) sin(βx)} for a pair
 * {@code α ± βi}); a forcing term made of polynomials times exponentials times sines and cosines
 * gets a particular solution by undetermined coefficients, each family's trial multiplied by {@code
 * xˢ} when {@code α + βi} is a root of multiplicity {@code s}.
 */
final class ConstantCoefficients {

  private final Dsolve d;
  private final Symbol y;
  private final Symbol x;

  ConstantCoefficients(Dsolve d, Symbol y) {
    this.d = d;
    this.y = y;
    this.x = d.x;
  }

  private Tools t() {
    return d.tools;
  }

  /** A family of forcing terms: {@code x^m e^(αx)} times {@code cos(βx)} or {@code sin(βx)}. */
  private record Family(Expr alpha, Expr beta) {}

  General solve(Expr e, int n) {
    List<Expr> a = new ArrayList<>();
    Map<Symbol, Expr> zero = new LinkedHashMap<>();
    for (int j = 0; j <= n; j++) {
      zero.put(Dsolve.sym(y, j), Num.ZERO);
    }
    List<Expr> lhs = new ArrayList<>();
    for (int j = 0; j <= n; j++) {
      Expr aj = t().simplify(t().d(e, Dsolve.sym(y, j)));
      if (Tools.occurs(aj, x) || !d.freeOfFunctions(aj) || Tools.inexact(aj)) {
        return null;
      }
      a.add(aj);
      lhs.add(Product.of(aj, Dsolve.sym(y, j)));
    }
    if (a.get(n).equals(Num.ZERO)) {
      return null;
    }
    Expr g = t().simplify(Exprs.neg(t().subs(e, zero)));
    if (!t().isZero(Exprs.sub(e, Exprs.sub(Tools.sum(lhs), g)))) {
      return null;
    }
    Symbol r = fresh(e);
    List<Expr> terms = new ArrayList<>();
    for (int j = 0; j <= n; j++) {
      terms.add(Product.of(a.get(j), Power.of(r, new Num(Int.of(j)))));
    }
    Expr poly = t().simplify(Tools.sum(terms));
    Optional<List<Expr>> roots = t().solveFor(poly, r);
    if (roots.isEmpty()) {
      return null;
    }
    List<Expr> basis = new ArrayList<>();
    List<Expr> handled = new ArrayList<>();
    for (Expr root : roots.get()) {
      if (Tools.inexact(root) || handled.contains(root)) {
        continue;
      }
      Optional<Expr[]> parts = t().split(root);
      if (parts.isEmpty()) {
        return null;
      }
      int m = multiplicity(poly, r, root);
      Expr alpha = parts.get()[0];
      Expr beta = parts.get()[1];
      handled.add(root);
      if (beta.equals(Num.ZERO)) {
        for (int k = 0; k < m; k++) {
          basis.add(t().simplify(Product.of(pow(k), Tools.exp(Product.of(root, x)))));
        }
        continue;
      }
      Expr conjugate = t().simplify(Exprs.sub(alpha, Product.of(beta, new Num(Complex.I))));
      Expr partner = t().find(roots.get(), conjugate);
      if (partner == null) {
        return null;
      }
      handled.add(partner);
      if (Tools.negativeLooking(beta)) {
        beta = t().simplify(Exprs.neg(beta));
      }
      for (int k = 0; k < m; k++) {
        Expr common = Product.of(pow(k), Tools.exp(Product.of(alpha, x)));
        basis.add(
            t().simplify(Product.of(common, Apply.of(Symbol.of("cos"), Product.of(beta, x)))));
        basis.add(
            t().simplify(Product.of(common, Apply.of(Symbol.of("sin"), Product.of(beta, x)))));
      }
    }
    if (basis.size() != n) {
      return null;
    }
    List<Expr> combination = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      combination.add(Product.of(d.constant(i), basis.get(i)));
    }
    Expr homogeneous = t().simplify(Tools.sum(combination));
    Chain chain = d.new Chain();
    chain.step("characteristic", d.eq(poly, Num.ZERO));
    chain.step(
        "basis", d.eq(d.applied(y), homogeneous), "roots_", t().canon(new SetExpr(roots.get())));
    if (g.equals(Num.ZERO)) {
      return new General(chain, List.of(new Solution(List.of(homogeneous), null)));
    }
    Expr particular = particular(a, poly, r, g);
    if (particular == null) {
      return null;
    }
    Expr value = t().simplify(Dsolve.plus(homogeneous, particular));
    chain.step("particular", d.eq(d.applied(y), value), "yp_", particular);
    return new General(chain, List.of(new Solution(List.of(value), null)));
  }

  /** {@code x^k}, raw. */
  private Expr pow(int k) {
    return k == 0 ? Num.ONE : Power.of(x, new Num(Int.of(k)));
  }

  /** The multiplicity of a root of the polynomial in {@code r}, 0 when it is not one. */
  private int multiplicity(Expr poly, Symbol r, Expr root) {
    Expr q = poly;
    for (int m = 0; m <= Dsolve.MAX_ORDER; m++) {
      if (!t().isZero(t().subs(q, Map.of(r, root)))) {
        return m;
      }
      q = t().d(q, r);
    }
    return Dsolve.MAX_ORDER;
  }

  /** A name for the characteristic variable: {@code r}, or {@code r_1}, … not in {@code e}. */
  private static Symbol fresh(Expr e) {
    for (int k = 0; ; k++) {
      Symbol r = Symbol.of(k == 0 ? "r" : "r_" + k);
      if (!Tools.occurs(e, r)) {
        return r;
      }
    }
  }

  /** The particular solution of {@code L y = g} by undetermined coefficients, or null. */
  private Expr particular(List<Expr> a, Expr poly, Symbol r, Expr g) {
    Map<Family, Integer> degrees = new LinkedHashMap<>();
    for (Expr term : Tools.terms(t().expand(g))) {
      Expr[] shape = classify(term);
      if (shape == null) {
        return null;
      }
      Family f = new Family(shape[0], shape[1]);
      int m = ((Num) shape[2]).value() instanceof Int k ? k.value().intValueExact() : 0;
      degrees.merge(f, m, Math::max);
    }
    List<Symbol> unknowns = new ArrayList<>();
    List<Expr> trial = new ArrayList<>();
    for (Map.Entry<Family, Integer> entry : degrees.entrySet()) {
      Family f = entry.getKey();
      Expr root = t().simplify(Dsolve.plus(f.alpha(), Product.of(f.beta(), new Num(Complex.I))));
      int s = multiplicity(poly, r, root);
      Expr common = Product.of(pow(s), Tools.exp(Product.of(f.alpha(), x)));
      for (int k = 0; k <= entry.getValue(); k++) {
        Symbol u = Symbol.of("\u0000u" + unknowns.size());
        unknowns.add(u);
        Expr cos =
            f.beta().equals(Num.ZERO)
                ? Num.ONE
                : Apply.of(Symbol.of("cos"), Product.of(f.beta(), x));
        trial.add(Product.of(u, pow(k), common, cos));
        if (!f.beta().equals(Num.ZERO)) {
          Symbol v = Symbol.of("\u0000u" + unknowns.size());
          unknowns.add(v);
          trial.add(
              Product.of(v, pow(k), common, Apply.of(Symbol.of("sin"), Product.of(f.beta(), x))));
        }
      }
    }
    Expr yp = t().simplify(Tools.sum(trial));
    List<Expr> lhs = new ArrayList<>();
    for (int j = 0; j < a.size(); j++) {
      if (!a.get(j).equals(Num.ZERO)) {
        lhs.add(Product.of(a.get(j), t().d(yp, x, j)));
      }
    }
    Expr residual = t().expand(Exprs.sub(Tools.sum(lhs), g));
    Map<Expr, List<Expr>> byShape = new LinkedHashMap<>();
    for (Expr term : Tools.terms(residual)) {
      List<Expr> shape = new ArrayList<>();
      List<Expr> coefficient = new ArrayList<>();
      for (Expr factor : Tools.factors(term)) {
        (Tools.occurs(factor, x) ? shape : coefficient).add(factor);
      }
      byShape
          .computeIfAbsent(t().canon(Tools.product(shape)), any -> new ArrayList<>())
          .add(Tools.product(coefficient));
    }
    List<Expr> equations = new ArrayList<>();
    for (List<Expr> coefficients : byShape.values()) {
      equations.add(t().simplify(Tools.sum(coefficients)));
    }
    Optional<List<Map<Symbol, Expr>>> found = t().solveSystem(equations, unknowns);
    if (found.isEmpty() || found.get().size() != 1) {
      return null;
    }
    Map<Symbol, Expr> values = new LinkedHashMap<>(found.get().getFirst());
    for (Symbol u : unknowns) {
      values.putIfAbsent(u, Num.ZERO);
    }
    Expr out = t().simplify(t().subs(yp, values));
    for (Symbol u : unknowns) {
      if (Tools.occurs(out, u)) {
        return null;
      }
    }
    return out;
  }

  /**
   * A forcing term as {@code [α, β, m]} for {@code c xᵐ e^(αx)} times {@code cos(βx)}, {@code
   * sin(βx)} or neither ({@code β = 0}), {@code c} constant; null for any other shape.
   */
  private Expr[] classify(Expr term) {
    Expr alpha = Num.ZERO;
    Expr beta = Num.ZERO;
    int m = 0;
    boolean trig = false;
    for (Expr factor : Tools.factors(term)) {
      if (!Tools.occurs(factor, x)) {
        continue;
      }
      if (factor.equals(x)) {
        m += 1;
      } else if (factor instanceof Power pw
          && pw.base().equals(x)
          && pw.exponent() instanceof Num n
          && n.value() instanceof Int k
          && k.value().signum() > 0) {
        m += k.value().intValueExact();
      } else if (factor instanceof Power pw && pw.base().equals(Constant.E)) {
        Expr rate = t().simplify(t().d(pw.exponent(), x));
        if (Tools.occurs(rate, x)) {
          return null;
        }
        alpha = t().simplify(Dsolve.plus(alpha, rate));
      } else if (factor instanceof Apply ap
          && (ap.head().name().equals("sin") || ap.head().name().equals("cos"))
          && ap.args().size() == 1
          && !trig) {
        Expr u = ap.args().getFirst();
        Expr rate = t().simplify(t().d(u, x));
        if (Tools.occurs(rate, x) || !t().isZero(t().subs(u, Map.of(x, Num.ZERO)))) {
          return null;
        }
        trig = true;
        beta = Tools.negativeLooking(rate) ? t().simplify(Exprs.neg(rate)) : rate;
      } else {
        return null;
      }
    }
    if (m > Dsolve.MAX_ORDER * 2) {
      return null;
    }
    return new Expr[] {alpha, beta, new Num(Int.of(m))};
  }
}
