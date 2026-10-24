package mathcanvas.engine.solve;

import static mathcanvas.engine.solve.Chain.bind;
import static mathcanvas.engine.solve.Support.canon;
import static mathcanvas.engine.solve.Support.contains;
import static mathcanvas.engine.solve.Support.equation;
import static mathcanvas.engine.solve.Support.set;
import static mathcanvas.engine.solve.Support.simplify;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.Division;
import mathcanvas.engine.algebra.Factoring;
import mathcanvas.engine.algebra.Factorization;
import mathcanvas.engine.algebra.Gcd;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.algebra.RationalFunction;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.solve.Support.Declined;

/**
 * One equation in one unknown: the denominators cleared and their zeros excluded, then the linear
 * path, the polynomial path (factoring over ℚ, each factor by its degree) or the numeric fallback.
 */
final class Univariate {

  private final Symbol x;
  private final Context ctx;
  private final Chain chain;

  private Univariate(Symbol x, Context ctx, Chain chain) {
    this.x = x;
    this.ctx = ctx;
    this.chain = chain;
  }

  /**
   * Solves {@code eq} for {@code x}, recording its steps on {@code chain}, whose state is {@code
   * eq}.
   *
   * @return the set of solutions
   * @throws Declined when no method applies
   */
  static SetExpr solve(Relation eq, Symbol x, Context ctx, Chain chain) {
    return new Univariate(x, ctx, chain).run(eq);
  }

  private SetExpr run(Relation eq) {
    Expr lhs = eq.left();
    Expr rhs = eq.right();
    Expr e = canon(Exprs.sub(lhs, rhs), ctx);
    Polynomial n;
    try {
      List<Expr> bases = new ArrayList<>();
      denominators(lhs, bases);
      denominators(rhs, bases);
      Polynomial d = Polynomial.ONE;
      for (Expr b : bases) {
        d = Gcd.lcm(d, Polynomial.of(b));
      }
      if (d.isConstant()) {
        n = Polynomial.of(e);
      } else {
        Expr de = d.toExpr();
        Expr left = Algebra.cancel(Product.of(lhs, de), ctx);
        Expr right = Algebra.cancel(Product.of(rhs, de), ctx);
        chain.step("clear-denominators", equation(left, right, ctx), bind("d_", de));
        RationalFunction r = RationalFunction.of(canon(Exprs.sub(left, right), ctx));
        if (contains(r.denominator().toExpr(), x)) {
          throw new Declined("not-polynomial");
        }
        n = r.numerator();
        Polynomial reduced = n;
        Polynomial g = Gcd.gcd(reduced, d);
        while (!g.isConstant()) {
          reduced = Division.exactQuotient(reduced, g).orElseThrow();
          g = Gcd.gcd(reduced, d);
        }
        if (!reduced.equals(n)) {
          chain.step("exclude", equation(reduced.toExpr(), Num.ZERO, ctx), bind("d_", de));
          n = reduced;
        }
      }
    } catch (IllegalArgumentException refused) {
      throw new Declined("not-polynomial");
    }
    for (Expr k : n.variables()) {
      if (!k.equals(x) && contains(k, x)) {
        return nonPolynomial(e);
      }
    }
    int degree = n.degree(x);
    while (degree > 0 && ctx.assumptions().isZero(n.coefficient(x, degree).toExpr()) == Tri.TRUE) {
      degree--;
    }
    if (degree <= 0) {
      return free(n.coefficient(x, 0).toExpr());
    }
    if (degree < n.degree(x)) {
      n = truncate(n, degree);
    }
    if (degree == 1) {
      return linear(n);
    }
    return polynomial(n);
  }

  /** The bases of the negative integer powers containing {@code x}, through sums and products. */
  private void denominators(Expr e, List<Expr> out) {
    switch (e) {
      case Sum s -> s.terms().forEach(t -> denominators(t, out));
      case Product p -> p.factors().forEach(f -> denominators(f, out));
      case Power w
          when w.exponent() instanceof Num k && k.value() instanceof Int i && contains(w, x) -> {
        if (i.signum() < 0) {
          out.add(w.base());
        } else {
          denominators(w.base(), out);
        }
      }
      default -> {}
    }
  }

  private Polynomial truncate(Polynomial n, int degree) {
    Polynomial out = Polynomial.ZERO;
    for (int k = 0; k <= degree; k++) {
      out = out.add(n.coefficient(x, k).multiply(Polynomial.variable(x).pow(k)));
    }
    return out;
  }

  /** An equation free of {@code x}: every value when it holds, none when it fails. */
  private SetExpr free(Expr c) {
    Tri zero = ctx.assumptions().isZero(c);
    if (zero == Tri.TRUE) {
      throw new Declined("all-values");
    }
    if (zero == Tri.UNKNOWN) {
      throw new Declined("depends-on-parameters", bind("c_", c));
    }
    return roots(List.of(), null);
  }

  /** The closing step: the solutions, simplified, as a set. */
  private SetExpr roots(List<Expr> values, List<String> methods) {
    List<Expr> out = new ArrayList<>(values.size());
    for (Expr v : values) {
      out.add(simplify(v, ctx));
    }
    SetExpr s = (SetExpr) set(out, ctx);
    Expr ms = null;
    if (methods != null) {
      List<Expr> names = new ArrayList<>();
      methods.forEach(m -> names.add(Symbol.of(m)));
      ms = set(names, ctx);
    }
    Expr digits = methods != null && methods.contains("numeric") ? digits() : null;
    chain.step("roots", s, bind("solutions_", s, "methods_", ms, "digits_", digits));
    return s;
  }

  // Linear.

  private SetExpr linear(Polynomial n) {
    Relation current = (Relation) chain.current();
    Polynomial l = Polynomial.of(current.left());
    Polynomial r = Polynomial.of(current.right());
    Expr a = n.coefficient(x, 1).toExpr();
    Expr l0 = l.coefficient(x, 0).toExpr();
    Expr r0 = r.coefficient(x, 0).toExpr();
    if (!l.coefficient(x, 1).subtract(r.coefficient(x, 1)).equals(n.coefficient(x, 1))) {
      // the cleared form is the numerator up to a constant: work on n = 0
      l0 = n.coefficient(x, 0).toExpr();
      r0 = Num.ZERO;
    }
    Expr ax = Product.of(a, x);
    chain.step("collect", equation(Sum.of(ax, l0), r0, ctx), bind());
    Expr rest = canon(Exprs.sub(r0, l0), ctx);
    chain.step("isolate", equation(ax, rest, ctx), bind());
    Expr value = rest;
    if (!canon(a, ctx).equals(Num.ONE)) {
      value = Algebra.cancel(Exprs.div(rest, a), ctx);
      chain.step(
          "divide", equation(x, value, ctx), bind("a_", canon(a, ctx), "conditions_", nonZero(a)));
    }
    return roots(List.of(value), null);
  }

  /**
   * The conditions a step assumes: {@code {e ≠ 0}}, or the empty set when {@code e} is a number or
   * known to be non-zero.
   */
  private Expr nonZero(Expr e) {
    Expr c = canon(e, ctx);
    if (c instanceof Num || ctx.assumptions().isNonZero(c) == Tri.TRUE) {
      return set(List.of(), ctx);
    }
    return set(List.of(new Relation(Relation.Op.NE, c, Num.ZERO)), ctx);
  }

  // Polynomials.

  private SetExpr polynomial(Polynomial n) {
    Factorization f = Factoring.factor(n, Steps.NONE);
    List<Polynomial> withX = new ArrayList<>();
    List<Expr> conditions = new ArrayList<>();
    boolean single = true;
    for (Factorization.Factor fac : f.factors()) {
      if (fac.base().degree(x) > 0) {
        withX.add(fac.base());
        single &= fac.multiplicity() == 1;
      } else {
        single = false;
        conditions.addAll(((SetExpr) nonZero(fac.base().toExpr())).items());
      }
    }
    if (single && withX.size() == 1) {
      return byDegree(n, true);
    }
    chain.step(
        "factor", equation(f.toExpr(), Num.ZERO, ctx), bind("conditions_", set(conditions, ctx)));
    List<Expr> values = new ArrayList<>();
    List<String> methods = new ArrayList<>();
    for (Polynomial base : withX) {
      Solved s = solveFactor(base);
      values.addAll(s.roots());
      if (!methods.contains(s.method())) {
        methods.add(s.method());
      }
    }
    return roots(values, methods);
  }

  /** A factor's roots and the method that found them. */
  private record Solved(List<Expr> roots, String method) {}

  private Solved solveFactor(Polynomial p) {
    int d = p.degree(x);
    if (d == 1) {
      Expr root = Exprs.div(Exprs.neg(p.coefficient(x, 0).toExpr()), p.coefficient(x, 1).toExpr());
      return new Solved(List.of(Algebra.cancel(root, ctx)), "linear");
    }
    if (d == 2) {
      return new Solved(
          Formulas.quadratic(p.coefficient(x, 2), p.coefficient(x, 1), p.coefficient(x, 0), x)
              .roots(),
          "quadratic");
    }
    if (rational(p) && d <= 4) {
      RealNumericCoefficients c = new RealNumericCoefficients(p, d);
      return d == 3
          ? new Solved(Formulas.cubic(c.values()).roots(), "cubic")
          : new Solved(Formulas.quartic(c.values()), "quartic");
    }
    return numericFactor(p);
  }

  /** Solves {@code n}, a single factor, with its method's own steps. */
  private SetExpr byDegree(Polynomial n, boolean traced) {
    int d = n.degree(x);
    if (d == 2) {
      Polynomial a = n.coefficient(x, 2);
      Formulas.Quadratic quad = Formulas.quadratic(a, n.coefficient(x, 1), n.coefficient(x, 0), x);
      Expr delta = simplify(quad.delta(), ctx);
      chain.step(
          "discriminant",
          equation(quad.squareLeft(), quad.squareRight(), ctx),
          bind("d_", delta, "conditions_", nonZero(a.toExpr())));
      chain.step("formula", solved(quad.roots()), bind());
      return roots(quad.roots(), null);
    }
    if (rational(n) && d <= 4) {
      RealNumericCoefficients c = new RealNumericCoefficients(n, d);
      if (d == 3) {
        Formulas.Cubic cubic = Formulas.cubic(c.values());
        chain.step("cubic", solved(cubic.roots()), bind("form_", Symbol.of(cubic.form())));
        return roots(cubic.roots(), null);
      }
      List<Expr> quartic = Formulas.quartic(c.values());
      chain.step("quartic", solved(quartic), bind());
      return roots(quartic, null);
    }
    Solved s = numericFactor(n);
    chain.step("numeric", solved(s.roots()), bind("digits_", digits()));
    return roots(s.roots(), null);
  }

  /** The list of equations {@code x = r} for each root, the state before the set. */
  private Expr solved(List<Expr> roots) {
    List<Expr> eqs = new ArrayList<>(roots.size());
    for (Expr r : roots) {
      eqs.add(new Relation(Relation.Op.EQ, x, r));
    }
    return canon(new ListExpr(eqs), ctx);
  }

  /** Whether {@code p} has rational coefficients: its only kernel is {@code x}. */
  private boolean rational(Polynomial p) {
    return p.variables().equals(List.of(x));
  }

  /** The coefficients of a polynomial in {@code x} alone. */
  private record RealNumericCoefficients(mathcanvas.engine.number.RealNumeric[] values) {
    RealNumericCoefficients(Polynomial p, int degree) {
      this(Formulas.coefficients(p, degree));
    }
  }

  // The numeric fallback.

  private Solved numericFactor(Polynomial p) {
    for (Expr k : p.variables()) {
      if (!k.equals(x) && !Support.isConstant(k)) {
        throw new Declined("symbolic-degree", bind("n_", Num.of(p.degree(x))));
      }
    }
    return new Solved(NumericRoots.roots(p, x, ctx), "numeric");
  }

  /** An equation that is not a polynomial in {@code x}: the numeric scan. */
  private SetExpr nonPolynomial(Expr e) {
    List<Expr> found = Scan.roots(e, x, ctx);
    chain.step("numeric", solved(found), bind("digits_", digits(), "interval_", Scan.interval()));
    return roots(found, null);
  }

  private Num digits() {
    return Num.of(ctx.precision().getPrecision());
  }
}
