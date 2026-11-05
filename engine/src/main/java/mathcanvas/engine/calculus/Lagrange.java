package mathcanvas.engine.calculus;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;
import mathcanvas.engine.solve.Solver;

/**
 * Lagrange multipliers ({@code docs/language.md} §14.20): {@code lagrange(f, [g₁ = c₁, …], [x, y,
 * …])} sets up {@code ∂f/∂x = Σ λᵢ ∂gᵢ/∂x} for each variable, with the constraints, every partial
 * derivative by the rule set {@code diff}, and solves the system through {@link Solver#solve} (so
 * through the {@code Solve} providers) in the variables and the multipliers. The multiplier is
 * {@code λ} for one constraint and {@code λ₁, λ₂, …} for several, each the first of {@code λ},
 * {@code λ_1}, {@code λ_2}, … that occurs nowhere in the call and is not taken where it stands. The
 * critical points are the solutions with every coordinate real, decided numerically at the working
 * precision for a constant one; the steps, in the rule set {@link #RULE_SET}, are {@code system}
 * (the call to the set of equations) and {@code solve} (the equations to the points), or {@code
 * unsolved} (the equations back to the call) when the solver leaves the system.
 */
public final class Lagrange {

  private Lagrange() {}

  /** The rule set of the steps. */
  public static final String RULE_SET = "lagrange";

  /** The steps, {@code lagrange.<step>}. */
  public static final List<String> STEPS = List.of("system", "solve", "unsolved");

  /**
   * Evaluates a call whose function and constraints have been evaluated in the scope of its
   * variables.
   *
   * @param call {@code lagrange(f, constraints, variables)}
   * @param context the assumptions (without facts about the variables), precision, trace, cap,
   *     providers and numerics
   * @param taken the names a multiplier may not have besides those in the call: names with a value
   *     or a fact where the call stands
   * @return the set of critical points, each a list {@code [x = …, …, λ = …]}, or the call, and the
   *     trace
   * @throws IllegalArgumentException with the language's message for a wrong argument
   */
  public static Rewriter.Result of(Apply call, Context context, Predicate<Symbol> taken) {
    Expr f = call.args().getFirst();
    if (!Calculus.isScalar(f)) {
      throw new IllegalArgumentException("`lagrange` needs a scalar function");
    }
    List<Relation> constraints = constraints(call.args().get(1));
    List<Symbol> vars = variables(call.args().get(2));
    List<Symbol> multipliers = multipliers(call, constraints.size(), taken);
    Context quiet = context.withTrace(false);
    List<Expr> equations = new ArrayList<>();
    for (Symbol v : vars) {
      List<Expr> right = new ArrayList<>();
      for (int i = 0; i < constraints.size(); i++) {
        Relation g = constraints.get(i);
        Expr dg = Calculus.differentiate(Exprs.sub(g.left(), g.right()), v, 1, quiet).expr();
        right.add(Product.of(multipliers.get(i), dg));
      }
      Expr df = Calculus.differentiate(f, v, 1, quiet).expr();
      equations.add(
          Relation.of(Relation.Op.EQ, df, right.size() == 1 ? right.getFirst() : Sum.of(right)));
    }
    equations.addAll(constraints);
    var mc = context.precision();
    Expr system = Canonical.of(new SetExpr(equations), mc);
    List<Symbol> unknowns = new ArrayList<>(vars);
    unknowns.addAll(multipliers);
    Rewriter.Result solved = Solver.solve(system, new Vector(List.copyOf(unknowns)), quiet);
    Expr start = Canonical.of(call, mc);
    Expr result;
    String step;
    if (solved.expr() instanceof SetExpr points) {
      List<Expr> real = new ArrayList<>();
      for (Expr p : points.items()) {
        if (!nonReal(p, context)) {
          real.add(p);
        }
      }
      result = Canonical.of(new SetExpr(real), mc);
      step = "solve";
    } else {
      result = start;
      step = "unsolved";
    }
    if (!context.traceEnabled()) {
      return new Rewriter.Result(result, Trace.NONE);
    }
    List<Rewrite> rewrites = new ArrayList<>();
    TreeMap<String, Expr> none = new TreeMap<>();
    rewrites.add(new Rewrite(start, system, RULE_SET, "system", Path.ROOT, Bindings.EMPTY, none));
    rewrites.add(new Rewrite(system, result, RULE_SET, step, Path.ROOT, Bindings.EMPTY, none));
    return new Rewriter.Result(result, new Trace(start, result, rewrites, List.of()));
  }

  /**
   * Whether a point has a coordinate that is not real: a complex number in it, or, free of symbols,
   * a numeric value at the working precision whose imaginary part is beyond {@code 10^-(p−5)} of
   * its size ({@code √(−√5 + 2)}).
   */
  private static boolean nonReal(Expr point, Context ctx) {
    for (Expr item : ((ListExpr) point).items()) {
      Expr v = ((Relation) item).right();
      if (complex(v) || numericallyComplex(v, ctx)) {
        return true;
      }
    }
    return false;
  }

  private static boolean numericallyComplex(Expr v, Context ctx) {
    if (!constant(v)) {
      return false;
    }
    int p = ctx.precision().getPrecision();
    MathContext w = new MathContext(p + 10, RoundingMode.HALF_EVEN);
    Optional<Numeric> value;
    try {
      value = ctx.numerics().value(v, w);
    } catch (ArithmeticException outOfRange) {
      return false;
    }
    if (value.isEmpty() || !(value.get() instanceof Complex z)) {
      return false;
    }
    BigDecimal im = z.im().toReal(w).value().abs();
    BigDecimal re = z.re().toReal(w).value().abs();
    BigDecimal bound = BigDecimal.ONE.max(re).movePointLeft(Math.max(p - 5, 1));
    return im.compareTo(bound) > 0;
  }

  private static boolean constant(Expr e) {
    if (e instanceof Symbol) {
      return false;
    }
    for (Expr o : Operands.of(e)) {
      if (!constant(o)) {
        return false;
      }
    }
    return true;
  }

  private static boolean complex(Expr e) {
    if (e instanceof Num n && n.value() instanceof Complex) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (complex(o)) {
        return true;
      }
    }
    return false;
  }

  private static List<Relation> constraints(Expr e) {
    List<Expr> items =
        e instanceof ListExpr l
            ? l.items()
            : e instanceof Vector v ? v.items() : e instanceof Relation r ? List.of(r) : List.of();
    List<Relation> out = new ArrayList<>();
    for (Expr item : items) {
      if (!(item instanceof Relation r) || r.op() != Relation.Op.EQ) {
        out.clear();
        break;
      }
      out.add(r);
    }
    if (out.isEmpty()) {
      throw new IllegalArgumentException("`lagrange` needs constraints g = c in a list");
    }
    return out;
  }

  private static List<Symbol> variables(Expr e) {
    List<Expr> items =
        e instanceof ListExpr l ? l.items() : e instanceof Vector v ? v.items() : List.of();
    List<Symbol> out = new ArrayList<>();
    for (Expr item : items) {
      if (!(item instanceof Symbol s) || out.contains(s)) {
        out.clear();
        break;
      }
      out.add(s);
    }
    if (out.isEmpty()) {
      throw new IllegalArgumentException("`lagrange` needs a list of distinct variables");
    }
    return out;
  }

  /** {@code λ} for one constraint, {@code λ₁, …} for several, none in the call or taken. */
  private static List<Symbol> multipliers(Apply call, int count, Predicate<Symbol> taken) {
    List<Symbol> out = new ArrayList<>();
    int k = count == 1 ? 0 : 1;
    while (out.size() < count) {
      Symbol s = Symbol.of(k == 0 ? "λ" : "λ_" + k);
      k++;
      if (!occurs(call, s) && !taken.test(s)) {
        out.add(s);
      }
    }
    return out;
  }

  private static boolean occurs(Expr e, Symbol s) {
    if (e.equals(s)) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (occurs(o, s)) {
        return true;
      }
    }
    return false;
  }
}
