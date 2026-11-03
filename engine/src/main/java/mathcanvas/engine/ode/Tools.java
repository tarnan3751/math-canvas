package mathcanvas.engine.ode;

import java.math.MathContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.calculus.Calculus;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.simplify.Simplifier;
import mathcanvas.engine.solve.Solver;

/**
 * The operations the classes are built from, each under one context with its trace off: the
 * canonical form, the standard simplifier, differentiation, integration, substitution, solving for
 * one unknown and the split of an exact value into its real and imaginary parts. Public for the
 * recurrence solver ({@code discrete.Rsolve}), which is built from the same operations.
 */
public final class Tools {

  /** The integer parameter a general solution of {@code solve} may hold, put to 0 here. */
  private static final Symbol PARAMETER = Symbol.of("\u0000k");

  private final Context ctx;
  private final MathContext mc;

  /**
   * Tools under a context, its trace off.
   *
   * @param ctx the context
   */
  public Tools(Context ctx) {
    this.ctx = ctx.withTrace(false);
    this.mc = ctx.precision();
  }

  Context context() {
    return ctx;
  }

  Tools with(Context other) {
    return new Tools(other);
  }

  /**
   * The canonical form.
   *
   * @param e the expression
   * @return its canonical form
   */
  public Expr canon(Expr e) {
    return Canonical.of(e, mc, ctx.cancellation());
  }

  /**
   * The standard simplification.
   *
   * @param e the expression
   * @return it simplified
   */
  public Expr simplify(Expr e) {
    return Simplifier.simplify(canon(e), ctx).expr();
  }

  /**
   * The expanded form, simplified.
   *
   * @param e the expression
   * @return it expanded
   */
  public Expr expand(Expr e) {
    return simplify(Algebra.expand(canon(e), ctx).expr());
  }

  /**
   * The derivative in {@code s}, simplified.
   *
   * @param e the expression
   * @param s the variable
   * @return the derivative
   */
  public Expr d(Expr e, Symbol s) {
    return Calculus.differentiate(canon(e), s, 1, ctx).expr();
  }

  /** The {@code n}-th derivative in {@code s}, the expression itself for 0. */
  Expr d(Expr e, Symbol s, int n) {
    return n == 0 ? canon(e) : Calculus.differentiate(canon(e), s, n, ctx).expr();
  }

  /** An antiderivative in {@code s}, or empty when one is not found. */
  Optional<Expr> integrate(Expr f, Symbol s) {
    Expr r = Calculus.integrate(canon(f), s, null, null, ctx).expr();
    return has(r, Integral.class) ? Optional.empty() : Optional.of(r);
  }

  /**
   * Whether {@code e} simplifies to 0 (or a decimal 0), expanded if it must be.
   *
   * @param e the expression
   * @return true when it is 0
   */
  public boolean isZero(Expr e) {
    Expr s = simplify(e);
    if (zero(s)) {
      return true;
    }
    return zero(simplify(Algebra.expand(s, ctx).expr()));
  }

  /**
   * {@code e} without the terms of its sums whose numeric coefficient is a decimal 0, which a
   * constant solved to {@code 0.0} leaves ({@code 1.5 cos(x) + 0.0 sin(x)} is {@code 1.5 cos(x)}),
   * canonical.
   */
  Expr dropZeroTerms(Expr e) {
    return canon(dropZeros(e));
  }

  private static Expr dropZeros(Expr e) {
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>();
    for (Expr op : ops) {
      Expr r = dropZeros(op);
      if (e instanceof Sum && factors(r).stream().anyMatch(Tools::zero)) {
        continue;
      }
      out.add(r);
    }
    if (e instanceof Sum) {
      return sum(out);
    }
    return Operands.rebuild(e, out);
  }

  /** Whether a value is the number 0, exact or a decimal 0. */
  private static boolean zero(Expr e) {
    return e instanceof Num n && n.value().isZero();
  }

  /**
   * The substitution of each key by its value, simultaneous, canonical.
   *
   * @param e the expression
   * @param map the symbols and their values
   * @return the canonical result
   */
  public Expr subs(Expr e, Map<Symbol, Expr> map) {
    return canon(replace(e, map));
  }

  private static Expr replace(Expr e, Map<Symbol, Expr> map) {
    if (e instanceof Symbol s && map.containsKey(s)) {
      return map.get(s);
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>(ops.size());
    boolean changed = false;
    for (Expr op : ops) {
      Expr r = replace(op, map);
      changed |= r != op;
      out.add(r);
    }
    return changed ? Operands.rebuild(e, out) : e;
  }

  /**
   * {@code e} at {@code s = a}: substituted, the builtin functions of numbers evaluated as
   * evaluation does, simplified.
   */
  Expr at(Expr e, Symbol s, Expr a) {
    return simplify(ctx.numerics().exact(subs(e, Map.of(s, a)), mc));
  }

  /**
   * The values of {@code v} that solve {@code e = 0}, each free of {@code v}; a general solution's
   * integer parameter put to 0. Empty when {@code solve} leaves the equation or a value is not
   * free.
   *
   * @param e the expression equal to 0
   * @param v the unknown
   * @return the values
   */
  public Optional<List<Expr>> solveFor(Expr e, Symbol v) {
    Context c = ctx.withAssumptions(ctx.assumptions().without(v));
    Expr r =
        Solver.solve(Relation.of(Relation.Op.EQ, simplify(e), Num.ZERO), v, PARAMETER, c).expr();
    if (!(r instanceof SetExpr set)) {
      return Optional.empty();
    }
    List<Expr> out = new ArrayList<>();
    if (inexact(set) && !inexact(e)) {
      // a numeric root of an exact equation: not a closed form
      return Optional.empty();
    }
    for (Expr value : set.items()) {
      Expr one = subs(value, Map.of(PARAMETER, Num.ZERO));
      if (occurs(one, v)) {
        return Optional.empty();
      }
      if (!out.contains(one)) {
        out.add(one);
      }
    }
    return Optional.of(out);
  }

  /**
   * The solutions of equations in several unknowns, each as a map; empty when {@code solve} leaves
   * them. An unknown a solution leaves free is absent from its map.
   *
   * @param equations the equations, or expressions equal to 0
   * @param unknowns the unknowns
   * @return the solutions
   */
  public Optional<List<Map<Symbol, Expr>>> solveSystem(
      List<Expr> equations, List<Symbol> unknowns) {
    Context c = ctx;
    for (Symbol v : unknowns) {
      c = c.withAssumptions(c.assumptions().without(v));
    }
    List<Expr> eqs = new ArrayList<>();
    for (Expr e : equations) {
      eqs.add(
          e instanceof Relation r
              ? Relation.of(Relation.Op.EQ, simplify(Exprs.sub(r.left(), r.right())), Num.ZERO)
              : Relation.of(Relation.Op.EQ, simplify(e), Num.ZERO));
    }
    Expr r =
        Solver.solve(
                canon(new SetExpr(eqs)),
                unknowns.size() == 1 ? unknowns.getFirst() : ListExpr.of(unknowns),
                PARAMETER,
                c)
            .expr();
    if (!(r instanceof SetExpr set) || (inexact(set) && !inexact(new SetExpr(eqs)))) {
      return Optional.empty();
    }
    List<Map<Symbol, Expr>> out = new ArrayList<>();
    for (Expr item : set.items()) {
      Map<Symbol, Expr> one = new java.util.LinkedHashMap<>();
      if (unknowns.size() == 1) {
        one.put(unknowns.getFirst(), subs(item, Map.of(PARAMETER, Num.ZERO)));
      } else {
        List<Expr> rels =
            switch (item) {
              case ListExpr l -> l.items();
              case Vector v -> v.items();
              default -> List.of(item);
            };
        for (Expr rel : rels) {
          if (rel instanceof Relation q && q.left() instanceof Symbol s && !q.right().equals(s)) {
            one.put(s, subs(q.right(), Map.of(PARAMETER, Num.ZERO)));
          }
        }
      }
      out.add(one);
    }
    return Optional.of(out);
  }

  /** The member of {@code values} equal to {@code v} once simplified, or null. */
  Expr find(List<Expr> values, Expr v) {
    for (Expr other : values) {
      if (other.equals(v) || isZero(Exprs.sub(other, v))) {
        return other;
      }
    }
    return null;
  }

  /**
   * Whether the symbol occurs in {@code e}.
   *
   * @param e the expression
   * @param s the symbol
   * @return true when it occurs
   */
  public static boolean occurs(Expr e, Symbol s) {
    Deque<Expr> todo = new ArrayDeque<>();
    todo.push(e);
    while (!todo.isEmpty()) {
      Expr n = todo.pop();
      if (n.equals(s)) {
        return true;
      }
      if (n instanceof Apply a && a.head().equals(s)) {
        return true;
      }
      for (Expr op : Operands.of(n)) {
        todo.push(op);
      }
    }
    return false;
  }

  /**
   * Whether a node of the kind occurs in {@code e}.
   *
   * @param e the expression
   * @param kind the kind
   * @return true when one occurs
   */
  public static boolean has(Expr e, Class<? extends Expr> kind) {
    Deque<Expr> todo = new ArrayDeque<>();
    todo.push(e);
    while (!todo.isEmpty()) {
      Expr n = todo.pop();
      if (kind.isInstance(n)) {
        return true;
      }
      for (Expr op : Operands.of(n)) {
        todo.push(op);
      }
    }
    return false;
  }

  /**
   * Whether an inexact number occurs in {@code e}.
   *
   * @param e the expression
   * @return true when one occurs
   */
  public static boolean inexact(Expr e) {
    Deque<Expr> todo = new ArrayDeque<>();
    todo.push(e);
    while (!todo.isEmpty()) {
      Expr n = todo.pop();
      if (n instanceof Num num && !num.value().isExact()) {
        return true;
      }
      for (Expr op : Operands.of(n)) {
        todo.push(op);
      }
    }
    return false;
  }

  /**
   * The terms of a sum, or the expression alone.
   *
   * @param e the expression
   * @return the terms
   */
  public static List<Expr> terms(Expr e) {
    return e instanceof Sum s ? s.terms() : List.of(e);
  }

  /** The factors of a product, or the expression alone. */
  static List<Expr> factors(Expr e) {
    return e instanceof Product p ? p.factors() : List.of(e);
  }

  /** The product of the factors, 1 for none, raw. */
  static Expr product(List<Expr> factors) {
    return factors.isEmpty()
        ? Num.ONE
        : factors.size() == 1 ? factors.getFirst() : Product.of(factors);
  }

  /**
   * The sum of the terms, 0 for none, raw.
   *
   * @param terms the terms
   * @return the sum
   */
  public static Expr sum(List<Expr> terms) {
    return terms.isEmpty() ? Num.ZERO : terms.size() == 1 ? terms.getFirst() : Sum.of(terms);
  }

  /**
   * The real and imaginary parts of an exact value whose only non-real parts are numbers, taken
   * through sums, products and integer powers (a quotient by its conjugate); empty for any other
   * shape, a function or a fractional power of a value with a non-real part.
   *
   * @param e the value
   * @return its real and imaginary parts
   */
  public Optional<Expr[]> split(Expr e) {
    Expr[] parts = parts(canon(e));
    return parts == null
        ? Optional.empty()
        : Optional.of(new Expr[] {expand(parts[0]), expand(parts[1])});
  }

  private Expr[] parts(Expr e) {
    if (!hasComplex(e)) {
      return new Expr[] {e, Num.ZERO};
    }
    switch (e) {
      case Num n -> {
        return new Expr[] {new Num(n.value().re()), new Num(n.value().im())};
      }
      case Sum s -> {
        List<Expr> re = new ArrayList<>();
        List<Expr> im = new ArrayList<>();
        for (Expr t : s.terms()) {
          Expr[] p = parts(t);
          if (p == null) {
            return null;
          }
          re.add(p[0]);
          im.add(p[1]);
        }
        return new Expr[] {sum(re), sum(im)};
      }
      case Product p -> {
        Expr[] acc = {Num.ONE, Num.ZERO};
        for (Expr f : p.factors()) {
          Expr[] q = parts(f);
          if (q == null) {
            return null;
          }
          acc = times(acc, q);
        }
        return acc;
      }
      case Power pw
          when pw.exponent() instanceof Num n
              && n.value() instanceof mathcanvas.engine.number.Int k
              && k.value().bitLength() < 8 -> {
        Expr[] b = parts(pw.base());
        if (b == null) {
          return null;
        }
        int m = k.value().intValue();
        if (m < 0) {
          Expr norm = Sum.of(Power.of(b[0], Num.of(2)), Power.of(b[1], Num.of(2)));
          b = new Expr[] {Exprs.div(b[0], norm), Exprs.neg(Exprs.div(b[1], norm))};
          m = -m;
        }
        Expr[] acc = {Num.ONE, Num.ZERO};
        for (int j = 0; j < m; j++) {
          acc = times(acc, b);
        }
        return acc;
      }
      default -> {
        return null;
      }
    }
  }

  private static Expr[] times(Expr[] a, Expr[] b) {
    return new Expr[] {
      Exprs.sub(Product.of(a[0], b[0]), Product.of(a[1], b[1])),
      Sum.of(Product.of(a[0], b[1]), Product.of(a[1], b[0]))
    };
  }

  private static boolean hasComplex(Expr e) {
    Deque<Expr> todo = new ArrayDeque<>();
    todo.push(e);
    while (!todo.isEmpty()) {
      Expr n = todo.pop();
      if (n instanceof Num num && num.value() instanceof Complex) {
        return true;
      }
      for (Expr op : Operands.of(n)) {
        todo.push(op);
      }
    }
    return false;
  }

  /**
   * Whether a value reads as negative: a negative number, or a product led by one.
   *
   * @param e the value
   * @return true when it reads as negative
   */
  public static boolean negativeLooking(Expr e) {
    Expr lead = e instanceof Product p ? p.factors().getFirst() : e;
    return lead instanceof Num n
        && n.value() instanceof mathcanvas.engine.number.RealNumeric r
        && r.signum() < 0;
  }

  /** {@code e^u}, raw. */
  static Expr exp(Expr u) {
    return Power.of(mathcanvas.engine.expr.Constant.E, u);
  }

  /** {@code a/b}, raw. */
  static Expr div(Expr a, Expr b) {
    return Exprs.div(a, b);
  }
}
