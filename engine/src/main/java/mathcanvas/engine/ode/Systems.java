package mathcanvas.engine.ode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.linalg.Linalg;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.ode.Dsolve.Chain;
import mathcanvas.engine.ode.Dsolve.General;
import mathcanvas.engine.ode.Dsolve.Solution;

/**
 * Linear first-order systems with a constant matrix, {@code X' = A X + b(t)}. For each eigenvalue
 * {@code λ} of algebraic multiplicity {@code m}, each vector {@code w} of a basis of the null space
 * of {@code (A − λI)^m} gives the solution {@code e^(λt) Σ_{j<m} tʲ/j! (A − λI)ʲ w}, which covers
 * defective matrices; for a complex pair the real and imaginary parts of one member's solutions are
 * the two real ones. A forcing term goes through variation of parameters, {@code X_p = Φ ∫ Φ⁻¹ b
 * dt}, with {@code Φ(t)⁻¹ = Φ(0)⁻¹ Φ(−t) Φ(0)⁻¹} since {@code Φ(t) = e^(At) Φ(0)}.
 */
final class Systems {

  private final Dsolve d;
  private final Symbol t;

  Systems(Dsolve d) {
    this.d = d;
    this.t = d.x;
  }

  private Tools tl() {
    return d.tools;
  }

  General solve(List<Expr> eqs) {
    List<Symbol> fs = new ArrayList<>();
    List<Symbol> derivatives = new ArrayList<>();
    for (Expr e : eqs) {
      if (Tools.inexact(e)) {
        return null;
      }
    }
    // the functions, from the equations' order: first order only
    Dsolve.Problem p = problem();
    for (Symbol f : p.functions()) {
      fs.add(f);
      derivatives.add(Dsolve.sym(f, 1));
      for (Expr e : eqs) {
        if (Tools.occurs(e, Dsolve.sym(f, 2))) {
          return null;
        }
      }
    }
    Optional<List<Map<Symbol, Expr>>> solved = tl().solveSystem(eqs, derivatives);
    if (solved.isEmpty() || solved.get().size() != 1) {
      return null;
    }
    int n = fs.size();
    List<Expr> rhs = new ArrayList<>();
    for (Symbol dv : derivatives) {
      Expr r = solved.get().getFirst().get(dv);
      if (r == null || !freeOfDerivatives(r, derivatives)) {
        return null;
      }
      rhs.add(r);
    }
    Map<Symbol, Expr> zero = new LinkedHashMap<>();
    for (Symbol f : fs) {
      zero.put(f, Num.ZERO);
    }
    List<List<Expr>> a = new ArrayList<>();
    List<Expr> b = new ArrayList<>();
    boolean forced = false;
    for (int i = 0; i < n; i++) {
      List<Expr> row = new ArrayList<>();
      List<Expr> sum = new ArrayList<>();
      for (int k = 0; k < n; k++) {
        Expr aik = tl().simplify(tl().d(rhs.get(i), fs.get(k)));
        if (Tools.occurs(aik, t) || !d.freeOfFunctions(aik)) {
          return null;
        }
        row.add(aik);
        sum.add(Product.of(aik, fs.get(k)));
      }
      Expr bi = tl().simplify(tl().subs(rhs.get(i), zero));
      forced |= !bi.equals(Num.ZERO);
      sum.add(bi);
      if (!tl().isZero(Exprs.sub(rhs.get(i), Tools.sum(sum)))) {
        return null;
      }
      a.add(row);
      b.add(bi);
    }
    Matrix matrix = Matrix.of(a);
    Chain chain = d.new Chain();
    List<Expr> system = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      system.add(d.eq(derivatives.get(i), rhs.get(i)));
    }
    chain.step("system", ListExpr.of(system), "a_", matrix);
    Expr values = Linalg.apply(Apply.of(Symbol.of("eigenvalues"), matrix), tl().context()).expr();
    List<Expr> lambdas =
        switch (values) {
          case Vector v -> v.items();
          default -> null;
        };
    if (lambdas == null || Tools.inexact(values)) {
      return null;
    }
    List<List<Expr>> solutions = new ArrayList<>();
    List<Expr> done = new ArrayList<>();
    for (Expr lambda : lambdas) {
      if (done.contains(lambda)) {
        continue;
      }
      done.add(lambda);
      int m = 0;
      for (Expr other : lambdas) {
        m += other.equals(lambda) ? 1 : 0;
      }
      Optional<Expr[]> parts = tl().split(lambda);
      if (parts.isEmpty()) {
        return null;
      }
      Expr alpha = parts.get()[0];
      Expr beta = parts.get()[1];
      boolean complex = !beta.equals(Num.ZERO);
      if (complex) {
        Expr conjugate = tl().simplify(Exprs.sub(alpha, Product.of(beta, new Num(Complex.I))));
        Expr partner = tl().find(lambdas, conjugate);
        if (partner == null) {
          return null;
        }
        if (Tools.negativeLooking(beta)) {
          // its conjugate gives the real solutions
          continue;
        }
        done.add(partner);
      }
      List<List<Expr>> shifted = minus(a, lambda);
      List<List<Expr>> power = identity(n);
      for (int k = 0; k < m; k++) {
        power = times(power, shifted);
      }
      Expr basis =
          Linalg.apply(Apply.of(Symbol.of("nullspace"), Matrix.of(power)), tl().context()).expr();
      List<List<Expr>> ws = rows(basis, n);
      if (ws == null || ws.size() != m) {
        return null;
      }
      for (List<Expr> w : ws) {
        List<Expr> polynomial = new ArrayList<>();
        for (int i = 0; i < n; i++) {
          polynomial.add(Num.ZERO);
        }
        List<Expr> term = w;
        for (int j = 0; j < m; j++) {
          Expr scale =
              Product.of(
                  Power.of(t, new Num(Int.of(j))),
                  new Num(Rational.of(java.math.BigInteger.ONE, factorial(j))));
          for (int i = 0; i < n; i++) {
            polynomial.set(i, Dsolve.plus(polynomial.get(i), Product.of(scale, term.get(i))));
          }
          term = apply(shifted, term);
        }
        if (!complex) {
          List<Expr> sol = new ArrayList<>();
          for (Expr c : polynomial) {
            sol.add(tl().simplify(Product.of(Tools.exp(Product.of(lambda, t)), c)));
          }
          solutions.add(sol);
        } else {
          Expr rotation =
              Dsolve.plus(
                  Apply.of(Symbol.of("cos"), Product.of(beta, t)),
                  Product.of(new Num(Complex.I), Apply.of(Symbol.of("sin"), Product.of(beta, t))));
          List<Expr> re = new ArrayList<>();
          List<Expr> im = new ArrayList<>();
          for (Expr c : polynomial) {
            Optional<Expr[]> split = tl().split(Product.of(rotation, c));
            if (split.isEmpty()) {
              return null;
            }
            Expr grow = Tools.exp(Product.of(alpha, t));
            re.add(tl().simplify(Product.of(grow, split.get()[0])));
            im.add(tl().simplify(Product.of(grow, split.get()[1])));
          }
          solutions.add(re);
          solutions.add(im);
        }
      }
    }
    if (solutions.size() != n) {
      return null;
    }
    List<Expr> general = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      List<Expr> terms = new ArrayList<>();
      for (int k = 0; k < n; k++) {
        terms.add(Product.of(d.constant(k), solutions.get(k).get(i)));
      }
      general.add(tl().simplify(Tools.sum(terms)));
    }
    chain.step("eigen", d.shown(new Solution(general, null)), "values_", values);
    if (forced) {
      List<Expr> particular = particular(solutions, b);
      if (particular == null) {
        return null;
      }
      for (int i = 0; i < n; i++) {
        general.set(i, tl().simplify(Dsolve.plus(general.get(i), particular.get(i))));
      }
      chain.step(
          "variation",
          d.shown(new Solution(general, null)),
          "yp_",
          d.shown(new Solution(particular, null)));
    }
    return new General(chain, List.of(new Solution(general, null)));
  }

  private Dsolve.Problem problem() {
    return d.problem();
  }

  private static boolean freeOfDerivatives(Expr e, List<Symbol> derivatives) {
    for (Symbol s : derivatives) {
      if (Tools.occurs(e, s)) {
        return false;
      }
    }
    return true;
  }

  /** {@code X_p = Φ ∫ Φ⁻¹ b dt}, or null when an integral is not found. */
  private List<Expr> particular(List<List<Expr>> solutions, List<Expr> b) {
    int n = b.size();
    List<List<Expr>> phi = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      List<Expr> row = new ArrayList<>();
      for (int k = 0; k < n; k++) {
        row.add(solutions.get(k).get(i));
      }
      phi.add(row);
    }
    List<List<Expr>> phi0 = map(phi, e -> tl().simplify(tl().subs(e, Map.of(t, Num.ZERO))));
    List<List<Expr>> phiNeg = map(phi, e -> tl().simplify(tl().subs(e, Map.of(t, Exprs.neg(t)))));
    Expr inv;
    try {
      inv = Linalg.inverse(Matrix.of(phi0), tl().context());
    } catch (IllegalArgumentException singular) {
      return null;
    }
    if (!(inv instanceof Matrix im)) {
      return null;
    }
    List<List<Expr>> inv0 = im.rows();
    List<List<Expr>> phiInv = times(times(inv0, phiNeg), inv0);
    List<Expr> rate = apply(phiInv, b);
    List<Expr> u = new ArrayList<>();
    for (Expr r : rate) {
      Optional<Expr> integral = tl().integrate(tl().simplify(r), t);
      if (integral.isEmpty()) {
        return null;
      }
      u.add(integral.get());
    }
    List<Expr> out = new ArrayList<>();
    for (Expr e : apply(phi, u)) {
      out.add(tl().expand(e));
    }
    return out;
  }

  private static java.math.BigInteger factorial(int j) {
    java.math.BigInteger f = java.math.BigInteger.ONE;
    for (int k = 2; k <= j; k++) {
      f = f.multiply(java.math.BigInteger.valueOf(k));
    }
    return f;
  }

  /** The rows of a null-space basis as vectors of length {@code n}, or null. */
  private static List<List<Expr>> rows(Expr basis, int n) {
    List<List<Expr>> out = new ArrayList<>();
    switch (basis) {
      case Matrix m -> out.addAll(m.rows());
      case Vector v when v.items().size() == n -> out.add(v.items());
      case ListExpr l -> {
        for (Expr item : l.items()) {
          if (item instanceof Vector v) {
            out.add(v.items());
          } else {
            return null;
          }
        }
      }
      default -> {
        return null;
      }
    }
    for (List<Expr> row : out) {
      if (row.size() != n) {
        return null;
      }
    }
    return out;
  }

  private List<List<Expr>> minus(List<List<Expr>> a, Expr lambda) {
    List<List<Expr>> out = new ArrayList<>();
    for (int i = 0; i < a.size(); i++) {
      List<Expr> row = new ArrayList<>();
      for (int k = 0; k < a.size(); k++) {
        row.add(tl().simplify(i == k ? Exprs.sub(a.get(i).get(k), lambda) : a.get(i).get(k)));
      }
      out.add(row);
    }
    return out;
  }

  private static List<List<Expr>> identity(int n) {
    List<List<Expr>> out = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      List<Expr> row = new ArrayList<>();
      for (int k = 0; k < n; k++) {
        row.add(i == k ? Num.ONE : Num.ZERO);
      }
      out.add(row);
    }
    return out;
  }

  private List<List<Expr>> times(List<List<Expr>> p, List<List<Expr>> q) {
    List<List<Expr>> out = new ArrayList<>();
    for (List<Expr> row : p) {
      List<Expr> r = new ArrayList<>();
      for (int k = 0; k < q.getFirst().size(); k++) {
        List<Expr> terms = new ArrayList<>();
        for (int j = 0; j < row.size(); j++) {
          terms.add(Product.of(row.get(j), q.get(j).get(k)));
        }
        r.add(tl().simplify(Tools.sum(terms)));
      }
      out.add(r);
    }
    return out;
  }

  private List<Expr> apply(List<List<Expr>> m, List<Expr> v) {
    List<Expr> out = new ArrayList<>();
    for (List<Expr> row : m) {
      List<Expr> terms = new ArrayList<>();
      for (int j = 0; j < row.size(); j++) {
        terms.add(Product.of(row.get(j), v.get(j)));
      }
      out.add(tl().simplify(Tools.sum(terms)));
    }
    return out;
  }

  private static List<List<Expr>> map(
      List<List<Expr>> m, java.util.function.UnaryOperator<Expr> f) {
    List<List<Expr>> out = new ArrayList<>();
    for (List<Expr> row : m) {
      List<Expr> r = new ArrayList<>();
      for (Expr e : row) {
        r.add(f.apply(e));
      }
      out.add(r);
    }
    return out;
  }
}
