package mathcanvas.engine.solve;

import static mathcanvas.engine.solve.Chain.bind;
import static mathcanvas.engine.solve.Support.canon;
import static mathcanvas.engine.solve.Support.contains;
import static mathcanvas.engine.solve.Support.set;
import static mathcanvas.engine.solve.Support.simplify;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.Division;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.algebra.RationalFunction;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.solve.Support.Declined;

/**
 * Systems of equations linear in the unknowns, by fraction-free Gaussian elimination (Bareiss,
 * "Sylvester's identity and multistep integer-preserving Gaussian elimination", Math. Comp. 22
 * (1968)) on the augmented matrix, whose entries are polynomials in the other symbols, then
 * back-substitution with {@code cancel}. Each row operation is a step, so a later row reduction can
 * narrate the same trace.
 */
final class LinearSystems {

  private final List<Symbol> vars;
  private final Context ctx;
  private final Chain chain;

  private LinearSystems(List<Symbol> vars, Context ctx, Chain chain) {
    this.vars = vars;
    this.ctx = ctx;
    this.chain = chain;
  }

  /**
   * Whether every equation is linear in the unknowns: a rational function whose denominator is free
   * of them and whose numerator has total degree at most 1 in them.
   */
  static boolean isLinear(List<Expr> eqs, List<Symbol> vars, Context ctx) {
    try {
      for (Expr eq : eqs) {
        row(eq, vars, ctx);
      }
      return true;
    } catch (Declined d) {
      return false;
    }
  }

  /**
   * Solves the system.
   *
   * @return the set of solutions: for one unknown its values, for several one list of equations
   *     {@code x = v} (a free unknown has none), or the empty set
   */
  static SetExpr solve(List<Expr> eqs, List<Symbol> vars, Context ctx, Chain chain) {
    return new LinearSystems(vars, ctx, chain).run(eqs);
  }

  /** One equation as a row: the coefficient of each unknown, then the right side. */
  private static List<Polynomial> row(Expr eq, List<Symbol> vars, Context ctx) {
    Relation r = (Relation) eq;
    RationalFunction f;
    try {
      f = RationalFunction.of(canon(Exprs.sub(r.left(), r.right()), ctx));
    } catch (IllegalArgumentException refused) {
      throw new Declined("not-linear-system");
    }
    for (Symbol v : vars) {
      if (contains(f.denominator().toExpr(), v)) {
        throw new Declined("not-linear-system");
      }
    }
    Polynomial n = f.numerator();
    for (Expr k : n.variables()) {
      if (!vars.contains(k)) {
        for (Symbol v : vars) {
          if (contains(k, v)) {
            throw new Declined("not-linear-system");
          }
        }
      }
    }
    for (Polynomial.Term t : n.terms()) {
      int degree = 0;
      for (int i = 0; i < n.variables().size(); i++) {
        if (vars.contains(n.variables().get(i))) {
          degree += t.monomial().exponent(i);
        }
      }
      if (degree > 1) {
        throw new Declined("not-linear-system");
      }
    }
    List<Polynomial> out = new ArrayList<>(vars.size() + 1);
    Polynomial constant = n;
    for (Symbol v : vars) {
      out.add(n.coefficient(v, 1));
      constant = constant.coefficient(v, 0);
    }
    out.add(constant.negate());
    return out;
  }

  private SetExpr run(List<Expr> eqs) {
    int n = vars.size();
    List<List<Polynomial>> a = new ArrayList<>();
    for (Expr eq : eqs) {
      a.add(new ArrayList<>(row(eq, vars, ctx)));
    }
    chain.step("augment", matrix(a), bind());
    int m = a.size();
    Polynomial previous = Polynomial.ONE;
    List<Integer> pivotColumns = new ArrayList<>();
    List<Expr> conditions = new ArrayList<>();
    int r = 0;
    for (int c = 0; c < n && r < m; c++) {
      int pivot = pivot(a, r, c);
      if (pivot < 0) {
        continue;
      }
      if (pivot != r) {
        List<Polynomial> t = a.get(r);
        a.set(r, a.get(pivot));
        a.set(pivot, t);
        chain.step("swap-rows", matrix(a), bind("i_", Num.of(r + 1), "j_", Num.of(pivot + 1)));
      }
      Polynomial p = a.get(r).get(c);
      Expr condition = nonZero(p);
      if (condition != null) {
        conditions.add(condition);
      }
      for (int k = r + 1; k < m; k++) {
        List<Polynomial> rowK = a.get(k);
        Polynomial factor = rowK.get(c);
        for (int j = c + 1; j <= n; j++) {
          Polynomial value = p.multiply(rowK.get(j)).subtract(factor.multiply(a.get(r).get(j)));
          rowK.set(j, Division.exactQuotient(value, previous).orElseThrow());
        }
        rowK.set(c, Polynomial.ZERO);
        chain.step(
            "eliminate",
            matrix(a),
            bind(
                "conditions_",
                set(condition == null ? List.of() : List.of(condition), ctx),
                "i_",
                Num.of(k + 1),
                "j_",
                Num.of(r + 1),
                "p_",
                p.toExpr()));
      }
      previous = p;
      pivotColumns.add(c);
      r++;
    }
    // the rows left have no coefficient: each right side must vanish
    for (int k = r; k < m; k++) {
      Polynomial rhs = a.get(k).get(n);
      Tri zero = isZero(rhs);
      if (zero != Tri.TRUE) {
        List<Expr> unless = new ArrayList<>();
        if (zero == Tri.UNKNOWN) {
          unless.add(new Relation(Relation.Op.NE, rhs.toExpr(), Num.ZERO));
        }
        SetExpr none = (SetExpr) set(List.of(), ctx);
        chain.step("roots", none, bind("conditions_", set(unless, ctx), "solutions_", none));
        return none;
      }
    }
    // back-substitution, from the last pivot up; free unknowns stay themselves
    Expr[] values = new Expr[n];
    for (int k = pivotColumns.size() - 1; k >= 0; k--) {
      int c = pivotColumns.get(k);
      List<Expr> rest = new ArrayList<>();
      rest.add(a.get(k).get(n).toExpr());
      for (int j = c + 1; j < n; j++) {
        Expr coefficient = a.get(k).get(j).toExpr();
        Expr unknown = values[j] == null ? vars.get(j) : values[j];
        rest.add(Exprs.neg(Product.of(coefficient, unknown)));
      }
      values[c] =
          Algebra.cancel(
              Exprs.div(
                  rest.size() == 1 ? rest.getFirst() : Sum.of(rest), a.get(k).get(c).toExpr()),
              ctx);
    }
    List<Expr> free = new ArrayList<>();
    List<Expr> solved = new ArrayList<>();
    for (int j = 0; j < n; j++) {
      if (values[j] == null) {
        free.add(vars.get(j));
      } else {
        solved.add(new Relation(Relation.Op.EQ, vars.get(j), values[j]));
      }
    }
    chain.step(
        free.isEmpty() ? "back-substitute" : "free-variables",
        canon(new ListExpr(solved), ctx),
        bind("conditions_", set(conditions, ctx), "free_", free.isEmpty() ? null : set(free, ctx)));
    List<Expr> out = new ArrayList<>();
    for (Expr s : solved) {
      Relation eq = (Relation) s;
      out.add(new Relation(Relation.Op.EQ, eq.left(), simplify(eq.right(), ctx)));
    }
    SetExpr result;
    if (vars.size() == 1) {
      result = (SetExpr) set(out.stream().map(e -> ((Relation) e).right()).toList(), ctx);
    } else {
      result = (SetExpr) set(List.of(new ListExpr(out)), ctx);
    }
    chain.step("roots", result, bind("solutions_", result));
    return result;
  }

  /**
   * The row to pivot on in column {@code c} from row {@code r}: a non-zero number first, then an
   * entry known to be non-zero, then any entry not known to be zero; -1 when every entry is zero.
   */
  private int pivot(List<List<Polynomial>> a, int r, int c) {
    int known = -1;
    int any = -1;
    for (int k = r; k < a.size(); k++) {
      Polynomial e = a.get(k).get(c);
      Tri zero = isZero(e);
      if (zero == Tri.TRUE) {
        continue;
      }
      if (e.isConstant()) {
        return k;
      }
      if (known < 0 && zero == Tri.FALSE) {
        known = k;
      }
      if (any < 0) {
        any = k;
      }
    }
    return known >= 0 ? known : any;
  }

  /** Whether an entry is zero: structurally, after folding numeric powers, or by the facts. */
  private Tri isZero(Polynomial p) {
    if (p.isZero()) {
      return Tri.TRUE;
    }
    Expr e = canon(p.toExpr(), ctx);
    if (e instanceof Num num) {
      return num.value().isZero() ? Tri.TRUE : Tri.FALSE;
    }
    Tri t = ctx.assumptions().isZero(e);
    return t == Tri.TRUE ? Tri.TRUE : ctx.assumptions().isNonZero(e) == Tri.TRUE ? Tri.FALSE : t;
  }

  /** The condition {@code p ≠ 0} a symbolic pivot needs, or null. */
  private Expr nonZero(Polynomial p) {
    if (p.isConstant() || isZero(p) == Tri.FALSE) {
      return null;
    }
    return new Relation(Relation.Op.NE, p.toExpr(), Num.ZERO);
  }

  private Expr matrix(List<List<Polynomial>> a) {
    List<List<Expr>> rows = new ArrayList<>();
    for (List<Polynomial> row : a) {
      rows.add(row.stream().map(Polynomial::toExpr).toList());
    }
    return canon(Matrix.of(rows), ctx);
  }
}
