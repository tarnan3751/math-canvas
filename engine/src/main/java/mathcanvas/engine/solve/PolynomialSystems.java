package mathcanvas.engine.solve;

import static mathcanvas.engine.solve.Support.canon;
import static mathcanvas.engine.solve.Support.contains;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.AlgebraLimitException;
import mathcanvas.engine.algebra.Division;
import mathcanvas.engine.algebra.Factoring;
import mathcanvas.engine.algebra.Factorization;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.algebra.RationalFunction;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.RewriteLimitException;
import mathcanvas.engine.solve.Support.Declined;

/**
 * Small systems of polynomial equations, by elimination ({@code docs/reference/engine.md},
 * <b>Polynomial systems</b>): each equation's numerator over a denominator free of the unknowns is
 * a polynomial in them, and the system is reduced branch by branch, in this order of preference:
 *
 * <ol>
 *   <li>an equation linear in an unknown whose coefficient is a non-zero constant gives that
 *       unknown's value in the others, put into every other equation;
 *   <li>an equation in one unknown alone is solved by {@link Univariate}, and each root is a branch
 *       with the root put in;
 *   <li>an unknown that an equation holds in one term {@code c·uᵏ} ({@code c} a constant), and the
 *       others only in powers of {@code uᵏ}, has {@code uᵏ = −d/c} put into the others, the
 *       equation kept;
 *   <li>an equation whose polynomial factors is a branch per factor (the equation that factor = 0);
 *   <li>an unknown that an equation holds linearly with a coefficient {@code c} in the other
 *       unknowns is two branches: {@code c = 0} with the rest of the equation, and {@code c ≠ 0}
 *       with the unknown put in, the other equations' numerators taken and every factor {@code c}
 *       divided out of them.
 * </ol>
 *
 * Each step removes an unknown, an unknown from the other equations, or replaces a polynomial by
 * polynomials of smaller degree or fewer unknowns, so the reduction ends; past {@link
 * #MAX_BRANCHES} branches, or when no step applies, the method declines. A branch whose equations
 * leave an unknown free declines too: the method gives isolated solutions only. The values are put
 * back from the last unknown eliminated, simplified, and every candidate is substituted into the
 * original equations: kept when each side's difference simplifies to 0, or, free of symbols, is
 * within {@code 10^-(p−5)} of 0 numerically, dropped when it is a constant that is not; a candidate
 * that can be neither confirmed nor refused leaves the call ({@code not-polynomial-system}).
 */
final class PolynomialSystems {

  /** The most branches one system is split into. */
  static final int MAX_BRANCHES = 64;

  private final List<Symbol> vars;
  private final Symbol parameter;
  private final Context ctx;
  private int branches;

  private PolynomialSystems(List<Symbol> vars, Symbol parameter, Context ctx) {
    this.vars = vars;
    this.parameter = parameter;
    this.ctx = ctx;
  }

  /**
   * Whether every equation is polynomial in the unknowns: a rational function whose denominator is
   * free of them and whose numerator holds them only as its variables, never inside another kernel.
   */
  static boolean applies(List<Expr> eqs, List<Symbol> vars, Context ctx) {
    for (Expr eq : eqs) {
      Relation r = (Relation) eq;
      if (numerator(Exprs.sub(r.left(), r.right()), vars, ctx) == null) {
        return false;
      }
    }
    return true;
  }

  /**
   * Solves the system.
   *
   * @return the set of solutions: for one unknown its values, for several lists of equations {@code
   *     x = v} in the unknowns' order
   * @throws Declined when the method does not apply or cannot finish
   */
  static SetExpr solve(
      List<Expr> eqs, List<Symbol> vars, Symbol parameter, Context ctx, Chain chain) {
    SetExpr result = new PolynomialSystems(vars, parameter, ctx).run(eqs);
    chain.step("eliminate-polynomial", result, Chain.bind("solutions_", result));
    return result;
  }

  /** The numerator of {@code e} as a polynomial, or null when {@code e} is not polynomial. */
  private static Polynomial numerator(Expr e, List<Symbol> vars, Context ctx) {
    return numerator(e, vars, ctx, false);
  }

  /**
   * The numerator of {@code e} as a polynomial in {@code vars}, or null when a kernel holds one
   * inside it or, unless {@code anyDenominator}, the denominator holds one. Inside a branch the
   * values put in may bring denominators (a coefficient known not to be zero there); their zeros
   * are never solutions of the equation, so the numerator's solutions include the equation's, and
   * the final check against the original equations drops any other.
   */
  private static Polynomial numerator(
      Expr e, List<Symbol> vars, Context ctx, boolean anyDenominator) {
    RationalFunction f;
    try {
      f = RationalFunction.of(canon(e, ctx), ctx.cancellation());
    } catch (IllegalArgumentException refused) {
      return null;
    }
    for (Symbol v : vars) {
      if (!anyDenominator && contains(f.denominator().toExpr(), v)) {
        return null;
      }
    }
    Polynomial n = f.numerator();
    for (Expr k : n.variables()) {
      if (!vars.contains(k)) {
        for (Symbol v : vars) {
          if (contains(k, v)) {
            return null;
          }
        }
      }
    }
    return n;
  }

  private SetExpr run(List<Expr> eqs) {
    List<Expr> polys = new ArrayList<>();
    for (Expr eq : eqs) {
      Relation r = (Relation) eq;
      polys.add(numerator(Exprs.sub(r.left(), r.right()), vars, ctx).toExpr());
    }
    List<Map<Symbol, Expr>> found = branch(polys, new LinkedHashMap<>(), new ArrayList<>(vars));
    List<Expr> solutions = new ArrayList<>();
    for (Map<Symbol, Expr> assigned : found) {
      Map<Symbol, Expr> values = resolve(assigned);
      if (!satisfies(eqs, values)) {
        continue;
      }
      if (vars.size() == 1) {
        solutions.add(values.get(vars.getFirst()));
      } else {
        List<Expr> items = new ArrayList<>();
        for (Symbol v : vars) {
          items.add(Relation.of(Relation.Op.EQ, v, values.get(v)));
        }
        solutions.add(ListExpr.of(items));
      }
    }
    return (SetExpr) Canonical.of(new SetExpr(solutions), ctx.precision());
  }

  /** The solutions of a branch: the unknowns assigned so far, and those left. */
  private List<Map<Symbol, Expr>> branch(
      List<Expr> equations, Map<Symbol, Expr> assigned, List<Symbol> left) {
    ctx.cancellation().check();
    if (++branches > MAX_BRANCHES) {
      throw new Declined("not-polynomial-system");
    }
    List<Polynomial> ps = new ArrayList<>();
    for (Expr e : equations) {
      Polynomial p = numerator(e, left, ctx, true);
      if (p == null) {
        throw new Declined("not-polynomial-system");
      }
      if (p.isZero()) {
        continue;
      }
      if (free(p, left)) {
        Expr c = Support.simplify(p.toExpr(), ctx);
        if (c.equals(Num.ZERO)) {
          continue;
        }
        if (Support.isConstant(c) || ctx.assumptions().isNonZero(c) == Tri.TRUE) {
          return List.of();
        }
        throw new Declined("depends-on-parameters");
      }
      ps.add(p);
    }
    if (ps.isEmpty()) {
      if (!left.isEmpty()) {
        throw new Declined("not-polynomial-system");
      }
      return List.of(assigned);
    }
    // 1: an unknown a constant coefficient makes linear
    for (Polynomial p : ps) {
      for (Symbol u : left) {
        if (p.degree(u) != 1) {
          continue;
        }
        Polynomial c = p.coefficient(u, 1);
        if (!free(c, left) || !nonZero(c.toExpr())) {
          continue;
        }
        Expr value =
            Support.simplify(
                Product.of(
                    Num.MINUS_ONE,
                    p.coefficient(u, 0).toExpr(),
                    Power.of(c.toExpr(), Num.MINUS_ONE)),
                ctx);
        return assign(ps, p, u, value, assigned, left);
      }
    }
    // 2: an equation in one unknown
    for (Polynomial p : ps) {
      List<Symbol> in = new ArrayList<>();
      for (Symbol u : left) {
        if (p.degree(u) > 0) {
          in.add(u);
        }
      }
      if (in.size() != 1) {
        continue;
      }
      Symbol u = in.getFirst();
      SetExpr roots =
          Univariate.solve(
              Relation.of(Relation.Op.EQ, p.toExpr(), Num.ZERO),
              u,
              parameter,
              ctx.withTrace(false),
              new Chain(Steps.NONE, Relation.of(Relation.Op.EQ, p.toExpr(), Num.ZERO)));
      List<Map<Symbol, Expr>> out = new ArrayList<>();
      for (Expr root : roots.items()) {
        if (contains(root, parameter)) {
          throw new Declined("not-polynomial-system");
        }
        out.addAll(assign(ps, p, u, root, assigned, left));
      }
      return out;
    }
    // 3: an unknown an equation holds in one term c·u^k (c a constant) and the others only in
    // powers of u^k: u^k = −d/c put into the others, the equation kept
    for (Polynomial p : ps) {
      for (Symbol u : left) {
        int k = p.degree(u);
        if (k < 2 || !p.coefficient(u, k).isConstant() || p.coefficient(u, k).isZero()) {
          continue;
        }
        boolean single = true;
        for (int j = 1; j < k; j++) {
          single &= p.coefficient(u, j).isZero();
        }
        boolean others = false;
        boolean powers = true;
        for (Polynomial q : ps) {
          if (q == p || q.degree(u) == 0) {
            continue;
          }
          others = true;
          for (int j = 1; j <= q.degree(u); j++) {
            powers &= j % k == 0 || q.coefficient(u, j).isZero();
          }
        }
        if (!single || !others || !powers) {
          continue;
        }
        Polynomial power =
            p.coefficient(u, 0)
                .negate()
                .multiply(Support.div(Support.q(1), p.coefficient(u, k).constantTerm()));
        List<Expr> next = new ArrayList<>();
        for (Polynomial q : ps) {
          if (q == p) {
            next.add(q.toExpr());
            continue;
          }
          Polynomial out = Polynomial.ZERO;
          for (int j = 0; j <= q.degree(u); j += k) {
            out = out.add(q.coefficient(u, j).multiply(power.pow(j / k)));
          }
          next.add(out.toExpr());
        }
        return branch(next, assigned, left);
      }
    }
    // 4: an equation whose polynomial factors
    for (Polynomial p : ps) {
      Factorization f = Factoring.factor(p, Steps.NONE, ctx.cancellation());
      List<Polynomial> bases = new ArrayList<>();
      for (Factorization.Factor factor : f.factors()) {
        if (!free(factor.base(), left)) {
          bases.add(factor.base());
        }
      }
      if (bases.size() < 2
          && (bases.isEmpty() || bases.getFirst().totalDegree() >= p.totalDegree())) {
        continue;
      }
      List<Map<Symbol, Expr>> out = new ArrayList<>();
      for (Polynomial b : bases) {
        List<Expr> next = new ArrayList<>();
        for (Polynomial q : ps) {
          next.add(q == p ? b.toExpr() : q.toExpr());
        }
        out.addAll(branch(next, assigned, left));
      }
      return out;
    }
    // 5: an unknown an equation holds linearly with a coefficient c in the others: c = 0 with the
    // rest of the equation, or c ≠ 0 and the unknown put in, the factors of c then dropped
    for (Polynomial p : ps) {
      for (Symbol u : left) {
        if (p.degree(u) != 1) {
          continue;
        }
        Polynomial c = p.coefficient(u, 1);
        Polynomial d = p.coefficient(u, 0);
        List<Map<Symbol, Expr>> out = new ArrayList<>();
        List<Expr> zero = new ArrayList<>();
        for (Polynomial q : ps) {
          if (q != p) {
            zero.add(q.toExpr());
          }
        }
        zero.add(c.toExpr());
        zero.add(d.toExpr());
        out.addAll(branch(zero, assigned, left));
        Expr value =
            Support.simplify(
                Product.of(Num.MINUS_ONE, d.toExpr(), Power.of(c.toExpr(), Num.MINUS_ONE)), ctx);
        List<Expr> next = new ArrayList<>();
        List<Symbol> rest = new ArrayList<>(left);
        rest.remove(u);
        for (Polynomial q : ps) {
          if (q == p) {
            continue;
          }
          Polynomial n = numerator(Scan.replace(q.toExpr(), u, value), rest, ctx, true);
          if (n == null) {
            throw new Declined("not-polynomial-system");
          }
          next.add(withoutFactor(n, c).toExpr());
        }
        Map<Symbol, Expr> more = new LinkedHashMap<>(assigned);
        more.put(u, value);
        out.addAll(branch(next, more, rest));
        return out;
      }
    }
    throw new Declined("not-polynomial-system");
  }

  /** {@code n} divided by {@code c} as long as {@code c} divides it, {@code c} not constant. */
  private static Polynomial withoutFactor(Polynomial n, Polynomial c) {
    if (c.isConstant() || n.isZero()) {
      return n;
    }
    Polynomial out = n;
    Optional<Polynomial> q;
    while ((q = Division.exactQuotient(out, c)).isPresent()) {
      out = q.get();
    }
    return out;
  }

  /** The branch with {@code u = value} put into every equation but {@code p}. */
  private List<Map<Symbol, Expr>> assign(
      List<Polynomial> ps,
      Polynomial p,
      Symbol u,
      Expr value,
      Map<Symbol, Expr> assigned,
      List<Symbol> left) {
    List<Expr> next = new ArrayList<>();
    for (Polynomial q : ps) {
      if (q != p) {
        next.add(canon(Scan.replace(q.toExpr(), u, value), ctx));
      }
    }
    Map<Symbol, Expr> more = new LinkedHashMap<>(assigned);
    more.put(u, value);
    List<Symbol> rest = new ArrayList<>(left);
    rest.remove(u);
    return branch(next, more, rest);
  }

  /** The values put back, from the last unknown assigned, each simplified. */
  private Map<Symbol, Expr> resolve(Map<Symbol, Expr> assigned) {
    List<Symbol> order = new ArrayList<>(assigned.keySet());
    Map<Symbol, Expr> out = new LinkedHashMap<>();
    for (int k = order.size() - 1; k >= 0; k--) {
      Expr v = assigned.get(order.get(k));
      for (Map.Entry<Symbol, Expr> later : out.entrySet()) {
        v = Scan.replace(v, later.getKey(), later.getValue());
      }
      out.put(order.get(k), tidy(canon(v, ctx)));
    }
    return out;
  }

  /**
   * A value as {@code solve} gives one: simplified, and expanded with each denominator {@code a +
   * b√r} rationalised, taken when that clears the roots from the denominator or is no larger
   * ({@code -(-(√73 − 9)/2 − 9)} is {@code √73/2 + 9/2}, {@code 2/(√5 − 1)} is {@code (√5 + 1)/2}).
   */
  private Expr tidy(Expr v) {
    Expr simple = Support.simplify(v, ctx);
    if (size(simple) > TIDY_SIZE || !onlySquareRoots(simple)) {
      // a cubic's radicals, or a large value: expanding them costs more than it tidies
      return simple;
    }
    Expr expanded;
    try {
      expanded = Algebra.expand(v, ctx.withTrace(false)).expr();
      // a denominator with nested roots takes a rationalisation per root
      for (int k = 0; k < 3; k++) {
        Expr next = rationalised(expanded);
        if (next.equals(expanded)) {
          break;
        }
        expanded = next;
      }
      expanded = Support.simplify(expanded, ctx);
    } catch (AlgebraLimitException | RewriteLimitException tooLarge) {
      // the expanded form is too large to tidy (a cubic's radicals): the simplified one stands
      return simple;
    }
    boolean cleared = !rootInDenominator(expanded) && rootInDenominator(simple);
    return cleared || size(expanded) <= size(simple) ? expanded : simple;
  }

  /** {@code n/(a + b√r)} as {@code n(a − b√r)/(a² − b²r)}, expanded; anything else as it is. */
  private Expr rationalised(Expr e) {
    RationalFunction f;
    try {
      f = RationalFunction.of(canon(e, ctx), ctx.cancellation());
    } catch (IllegalArgumentException refused) {
      return e;
    }
    Polynomial den = f.denominator();
    for (Expr k : den.variables()) {
      if (k instanceof Power root
          && root.exponent().equals(Num.of(Rational.of(1, 2)))
          && Support.isConstant(root.base())
          && den.degree(k) == 1) {
        Expr a = den.coefficient(k, 0).toExpr();
        Expr b = den.coefficient(k, 1).toExpr();
        Expr conjugate = Exprs.sub(a, Product.of(b, k));
        Expr norm =
            Exprs.sub(Power.of(a, Num.of(2)), Product.of(Power.of(b, Num.of(2)), root.base()));
        Context quiet = ctx.withTrace(false);
        Expr top =
            Algebra.expand(canon(Product.of(f.numerator().toExpr(), conjugate), ctx), quiet).expr();
        Expr bottom = Algebra.expand(canon(norm, ctx), quiet).expr();
        if (!Support.isConstant(bottom) || bottom.equals(Num.ZERO)) {
          return e;
        }
        return Algebra.expand(canon(Product.of(top, Power.of(bottom, Num.MINUS_ONE)), ctx), quiet)
            .expr();
      }
    }
    return e;
  }

  /** The largest value {@link #tidy} expands. */
  private static final int TIDY_SIZE = 60;

  /**
   * Whether every fractional power in {@code e} is of a number or is a square root or its inverse:
   * a cube root of a sum (a cubic's radicals) is not.
   */
  private static boolean onlySquareRoots(Expr e) {
    if (e instanceof Power p
        && !(p.base() instanceof Num)
        && p.exponent() instanceof Num n
        && !(n.value() instanceof mathcanvas.engine.number.Int)
        && !n.equals(Num.of(Rational.of(1, 2)))
        && !n.equals(Num.of(Rational.of(-1, 2)))) {
      return false;
    }
    for (Expr o : mathcanvas.engine.match.Operands.of(e)) {
      if (!onlySquareRoots(o)) {
        return false;
      }
    }
    return true;
  }

  /** Whether a denominator of {@code e} holds a square root of a constant. */
  private boolean rootInDenominator(Expr e) {
    try {
      Polynomial den = RationalFunction.of(canon(e, ctx), ctx.cancellation()).denominator();
      for (Expr k : den.variables()) {
        if (k instanceof Power root
            && root.exponent().equals(Num.of(Rational.of(1, 2)))
            && Support.isConstant(root.base())) {
          return true;
        }
      }
      return false;
    } catch (IllegalArgumentException refused) {
      return false;
    }
  }

  /** The number of nodes of a tree. */
  private static int size(Expr e) {
    int n = 1;
    for (Expr o : mathcanvas.engine.match.Operands.of(e)) {
      n += size(o);
    }
    return n;
  }

  /** Whether the values satisfy every original equation; see the class comment. */
  private boolean satisfies(List<Expr> eqs, Map<Symbol, Expr> values) {
    for (Expr eq : eqs) {
      Relation r = (Relation) eq;
      Expr d = Exprs.sub(r.left(), r.right());
      for (Map.Entry<Symbol, Expr> v : values.entrySet()) {
        d = Scan.replace(d, v.getKey(), v.getValue());
      }
      Expr residual = Support.simplify(canon(d, ctx), ctx);
      if (residual.equals(Num.ZERO) || ctx.assumptions().isZero(residual) == Tri.TRUE) {
        continue;
      }
      if (undefined(residual)) {
        // a candidate of a c ≠ 0 branch at which c is 0 after all: not a solution
        return false;
      }
      if (!Support.isConstant(residual)) {
        throw new Declined("not-polynomial-system");
      }
      // a constant with no numeric value here leaves the call rather than drop a solution
      if (!numericallyZero(residual)) {
        return false;
      }
    }
    return true;
  }

  private static boolean undefined(Expr e) {
    if (e.equals(mathcanvas.engine.expr.Constant.UNDEFINED)) {
      return true;
    }
    for (Expr o : mathcanvas.engine.match.Operands.of(e)) {
      if (undefined(o)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Whether a constant is within {@code 10^-(p−5)} of 0 at the precision plus ten digits; empty
   * when it has no numeric value.
   */
  private Optional<Boolean> numericValue(Expr c) {
    try {
      return Optional.of(numericallyZero(c));
    } catch (Declined noValue) {
      return Optional.empty();
    }
  }

  /** Whether a constant is within {@code 10^-(p−5)} of 0 at the precision plus ten digits. */
  private boolean numericallyZero(Expr c) {
    int p = ctx.precision().getPrecision();
    MathContext w = new MathContext(p + 10, RoundingMode.HALF_EVEN);
    Optional<Numeric> v;
    try {
      v = ctx.numerics().value(c, w);
    } catch (ArithmeticException outOfRange) {
      throw new Declined("not-polynomial-system");
    }
    if (v.isEmpty()) {
      throw new Declined("not-polynomial-system");
    }
    BigDecimal bound = BigDecimal.ONE.movePointLeft(Math.max(p - 5, 1));
    if (v.get() instanceof RealNumeric r) {
      return r.toReal(w).value().abs().compareTo(bound) < 0;
    }
    if (v.get() instanceof Complex z) {
      return z.re().toReal(w).value().abs().compareTo(bound) < 0
          && z.im().toReal(w).value().abs().compareTo(bound) < 0;
    }
    throw new Declined("not-polynomial-system");
  }

  /** Whether a constant coefficient is known not to be zero. */
  private boolean nonZero(Expr c) {
    Expr s = Support.simplify(c, ctx);
    if (s instanceof Num n) {
      return !s.equals(Num.ZERO);
    }
    if (ctx.assumptions().isNonZero(s) == Tri.TRUE) {
      return true;
    }
    // a constant the simplifier cannot fold (3·2^(2/3)) that is clearly not 0 numerically
    return Support.isConstant(s) && numericValue(s).map(v -> !v).orElse(false);
  }

  /** Whether a polynomial holds none of the unknowns left. */
  private static boolean free(Polynomial p, List<Symbol> left) {
    for (Symbol u : left) {
      if (p.degree(u) > 0) {
        return false;
      }
    }
    return true;
  }
}
