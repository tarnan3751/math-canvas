package mathcanvas.engine.discrete;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import mathcanvas.engine.calculus.Calculus;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.ode.Tools;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;

/**
 * {@code rsolve}: linear recurrences ({@code docs/language.md} §14.24), as {@code dsolve} solves
 * differential equations. The recurrence is one equation in the unknown {@code a(n + s)} for
 * integer shifts {@code s}; after shifting {@code n} so the least shift is 0 it must be linear,
 * {@code Σ cⱼ(n) a(n + j) = f(n)}, of order {@code d} from 1 to {@link #MAX_ORDER}. With constant
 * coefficients the roots of the characteristic polynomial {@code Σ cⱼ rʲ} give the homogeneous
 * basis ({@code nᵏ rⁿ}, and {@code nᵏ ρⁿ cos(θn)}, {@code nᵏ ρⁿ sin(θn)} for a pair {@code
 * ρe^(±iθ)}, in radians), and a forcing term made of polynomials times exponentials {@code bⁿ} a
 * particular solution by undetermined coefficients, multiplied by {@code nˢ} when {@code b} is a
 * root of multiplicity {@code s}. A first-order recurrence {@code a(n + 1) = p(n) a(n) + q(n)} with
 * variable coefficients is solved by {@code P(n) = Π p(k)} and {@code Σ q(j)/P(j + 1)} when the
 * product and the sum close. The constants are {@code C₁, C₂, …}; conditions {@code a(k) = v} fix
 * them. Every solution is substituted back at the first {@link #CHECKED} indices, exactly or else
 * numerically, before it is returned; when no class applies or the check fails, the call stays.
 */
public final class Rsolve {

  /** The rule set of the steps, and the narration bundle's key prefix. */
  public static final String RULE_SET = "rsolve";

  /** The steps, each narrated by the key {@code rsolve.<step>}. */
  public static final List<String> STEPS =
      List.of(
          "characteristic",
          "basis",
          "particular",
          "product",
          "conditions",
          "unsolved",
          "unverified",
          "unmet");

  /** The highest order handled. */
  public static final int MAX_ORDER = 8;

  /** The indices at which a solution is substituted back. */
  public static final int CHECKED = 6;

  /**
   * A problem.
   *
   * @param equation the recurrence, evaluated
   * @param function the unknown function
   * @param variable the index
   * @param conditions the conditions {@code a(k) = v}
   */
  public record Problem(
      Relation equation, Symbol function, Symbol variable, List<Relation> conditions) {

    /** Copies the conditions. */
    public Problem {
      conditions = List.copyOf(conditions);
    }
  }

  /** A form no class handles: the call stays. */
  private static final class Unsupported extends RuntimeException {
    private static final long serialVersionUID = 1L;

    Unsupported() {
      super(null, null, false, false);
    }
  }

  private final Expr call;
  private final Problem p;
  private final Supplier<Symbol> constants;
  private final Context ctx;
  private final Tools tools;
  private final Symbol n;
  private final List<Rewrite> steps = new ArrayList<>();
  private final List<Symbol> handed = new ArrayList<>();
  private Expr current;

  private Rsolve(Expr call, Problem p, Supplier<Symbol> constants, Context ctx) {
    this.call = call;
    this.p = p;
    this.constants = constants;
    this.ctx = ctx;
    this.tools = new Tools(ctx);
    this.n = p.variable();
    this.current = call;
  }

  /**
   * Solves a problem.
   *
   * @param call the call, evaluated, which is the value when nothing is found
   * @param problem the problem
   * @param constants the fresh constants, in order
   * @param ctx the assumptions, precision, trace switch, cap, providers, numerics (in radians) and
   *     cancellation
   * @return {@code a(n) = …}, {@code {}} for conditions no solution meets, or the call, with the
   *     steps
   * @throws IllegalArgumentException if the equation is not a recurrence or a condition is not at
   *     an integer index
   */
  public static Rewriter.Result solve(
      Expr call, Problem problem, Supplier<Symbol> constants, Context ctx) {
    return new Rsolve(call, problem, constants, ctx).run();
  }

  private void step(String rule, Expr after, Object... pairs) {
    Expr state = tools.canon(after);
    if (ctx.traceEnabled() && !state.equals(current)) {
      TreeMap<String, Expr> values = new TreeMap<>();
      for (int i = 0; i < pairs.length; i += 2) {
        values.put((String) pairs[i], tools.canon((Expr) pairs[i + 1]));
      }
      steps.add(
          new Rewrite(
              current,
              state,
              RULE_SET,
              rule,
              Path.ROOT,
              mathcanvas.engine.match.Bindings.EMPTY,
              values));
    }
    current = state;
  }

  private Rewriter.Result stays(String rule) {
    if (ctx.traceEnabled()) {
      steps.clear();
      steps.add(
          new Rewrite(
              call, call, RULE_SET, rule, Path.ROOT, mathcanvas.engine.match.Bindings.EMPTY));
      return new Rewriter.Result(call, new Trace(call, call, steps, List.of()));
    }
    return new Rewriter.Result(call, Trace.NONE);
  }

  private Rewriter.Result done(Expr value) {
    Trace trace =
        ctx.traceEnabled() && !steps.isEmpty()
            ? new Trace(call, current, steps, List.of())
            : Trace.NONE;
    return new Rewriter.Result(value, trace);
  }

  private Expr applied() {
    return Apply.of(p.function(), n);
  }

  private Rewriter.Result run() {
    Expr e = tools.canon(Sum.of(p.equation().left(), Exprs.neg(p.equation().right())));
    TreeSet<Integer> shifts = new TreeSet<>();
    try {
      collectShifts(e, shifts);
    } catch (Unsupported u) {
      return stays("unsolved");
    }
    if (shifts.size() < 2) {
      throw new IllegalArgumentException(
          "`rsolve` needs a recurrence: a(n) with at least two shifts, as a(n + 1) and a(n)");
    }
    for (Relation c : p.conditions()) {
      if (!(((Apply) c.left()).args().getFirst() instanceof Num k && k.value() instanceof Int)) {
        throw new IllegalArgumentException("`rsolve`'s conditions are a(k) = v at integers k");
      }
    }
    int s0 = shifts.first();
    int d = shifts.last() - s0;
    try {
      if (d > MAX_ORDER) {
        throw new Unsupported();
      }
      Expr general = general(e, s0, d);
      Expr solution = conditions(general);
      if (solution == null) {
        return done(SetExpr.of());
      }
      if (!check(e, solution)) {
        return stays("unverified");
      }
      for (Relation c : p.conditions()) {
        Expr at = valueAt(solution, ((Apply) c.left()).args().getFirst());
        if (!tools.isZero(Sum.of(at, Exprs.neg(c.right())))) {
          return stays("unmet");
        }
      }
      return done(tools.canon(Relation.of(Relation.Op.EQ, applied(), solution)));
    } catch (Unsupported u) {
      return stays("unsolved");
    }
  }

  /** The shifts {@code s} of the applications {@code a(n + s)}; another argument is refused. */
  private void collectShifts(Expr e, TreeSet<Integer> shifts) {
    if (e instanceof Apply a && a.head().equals(p.function())) {
      if (a.args().size() != 1) {
        throw new Unsupported();
      }
      Expr s = tools.canon(Sum.of(a.args().getFirst(), Exprs.neg(n)));
      if (!(s instanceof Num num
          && num.value() instanceof Int i
          && i.value().bitLength() < 31
          && Tools.occurs(a.args().getFirst(), n))) {
        throw new Unsupported();
      }
      shifts.add(i.value().intValue());
      return;
    }
    for (Expr op : Operands.of(e)) {
      collectShifts(op, shifts);
    }
  }

  /** {@code e} with each {@code a(n + s)} replaced by {@code y}'s {@code s − s0}-th symbol. */
  private Expr replaceShifts(Expr e, int s0, List<Symbol> ys) {
    if (e instanceof Apply a && a.head().equals(p.function())) {
      int s =
          ((Int) ((Num) tools.canon(Sum.of(a.args().getFirst(), Exprs.neg(n)))).value())
              .value()
              .intValue();
      return ys.get(s - s0);
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    return Operands.rebuild(e, ops.stream().map(o -> replaceShifts(o, s0, ys)).toList());
  }

  /** The general solution, in the constants. */
  private Expr general(Expr e, int s0, int d) {
    List<Symbol> ys = new ArrayList<>();
    for (int j = 0; j <= d; j++) {
      ys.add(Symbol.of("\u0000y" + j));
    }
    // a(n + s0 + j) is y_j; n is shifted so that the least shift is 0
    Expr shifted = tools.subs(replaceShifts(e, s0, ys), Map.of(n, Sum.of(n, Num.of(Int.of(-s0)))));
    List<Expr> c = new ArrayList<>();
    Map<Symbol, Expr> zero = new LinkedHashMap<>();
    for (Symbol y : ys) {
      zero.put(y, Num.ZERO);
    }
    List<Expr> linear = new ArrayList<>();
    for (Symbol y : ys) {
      Expr cj = tools.simplify(tools.d(shifted, y));
      for (Symbol other : ys) {
        if (Tools.occurs(cj, other)) {
          throw new Unsupported();
        }
      }
      c.add(cj);
      linear.add(Product.of(cj, y));
    }
    Expr g = tools.simplify(tools.subs(shifted, zero));
    if (!tools.isZero(Sum.of(shifted, Exprs.neg(Sum.of(Tools.sum(linear), g))))) {
      throw new Unsupported();
    }
    if (tools.isZero(c.getFirst()) || tools.isZero(c.getLast()) || Tools.inexact(shifted)) {
      throw new Unsupported();
    }
    Expr f = tools.simplify(Exprs.neg(g));
    boolean constant = c.stream().noneMatch(x -> Tools.occurs(x, n));
    if (constant) {
      return constantCoefficients(c, f);
    }
    if (d == 1) {
      return firstOrder(c, f);
    }
    throw new Unsupported();
  }

  // Constant coefficients.

  private Expr constantCoefficients(List<Expr> c, Expr f) {
    int d = c.size() - 1;
    Symbol r = Symbol.of("\u0000r");
    List<Expr> terms = new ArrayList<>();
    for (int j = 0; j <= d; j++) {
      terms.add(Product.of(c.get(j), Power.of(r, Num.of(j))));
    }
    Expr poly = tools.simplify(Tools.sum(terms));
    Symbol shown = Tools.occurs(call, Symbol.of("r")) ? Symbol.of("λ") : Symbol.of("r");
    step(
        "characteristic",
        Relation.of(Relation.Op.EQ, tools.subs(poly, Map.of(r, shown)), Num.ZERO));
    List<Expr> roots = tools.solveFor(poly, r).orElseThrow(Unsupported::new);
    List<Expr> basis = new ArrayList<>();
    for (Expr root : roots) {
      if (Tools.inexact(root) || tools.isZero(root)) {
        throw new Unsupported();
      }
      int m = multiplicity(poly, r, root);
      Expr[] parts = tools.split(root).orElseThrow(Unsupported::new);
      Expr alpha = parts[0];
      Expr beta = parts[1];
      if (tools.isZero(beta)) {
        for (int k = 0; k < m; k++) {
          basis.add(Product.of(Power.of(n, Num.of(k)), Power.of(root, n)));
        }
        continue;
      }
      if (Tools.negativeLooking(beta)) {
        // its conjugate gives the pair
        continue;
      }
      Expr rho =
          tools.simplify(
              Power.of(
                  Sum.of(Power.of(alpha, Num.of(2)), Power.of(beta, Num.of(2))),
                  Num.of(Rational.of(1, 2))));
      Expr theta = angle(alpha, beta);
      for (int k = 0; k < m; k++) {
        Expr common = Product.of(Power.of(n, Num.of(k)), Power.of(rho, n));
        basis.add(Product.of(common, Apply.of(Symbol.of("cos"), Product.of(theta, n))));
        basis.add(Product.of(common, Apply.of(Symbol.of("sin"), Product.of(theta, n))));
      }
    }
    if (basis.size() != d) {
      throw new Unsupported();
    }
    List<Expr> combination = new ArrayList<>();
    for (Expr b : basis) {
      combination.add(Product.of(fresh(), tools.simplify(b)));
    }
    Expr homogeneous = tools.simplify(Tools.sum(combination));
    step("basis", Relation.of(Relation.Op.EQ, applied(), homogeneous), "roots", new SetExpr(roots));
    if (tools.isZero(f)) {
      return homogeneous;
    }
    Expr particular = particular(c, poly, r, f);
    Expr value = tools.simplify(Sum.of(homogeneous, particular));
    step("particular", Relation.of(Relation.Op.EQ, applied(), value), "yp", particular);
    return value;
  }

  /**
   * The angle of {@code α + βi} with {@code β > 0}, in radians: {@code atan(β/α)}, plus π for α <
   * 0.
   */
  private Expr angle(Expr alpha, Expr beta) {
    if (tools.isZero(alpha)) {
      return tools.simplify(Product.of(Num.of(Rational.of(1, 2)), Constant.PI));
    }
    Optional<Numeric> a = ctx.numerics().value(alpha, ctx.precision());
    if (a.isEmpty() || !a.get().im().isZero()) {
      throw new Unsupported();
    }
    Expr atan = Apply.of(Symbol.of("atan"), Product.of(beta, Power.of(alpha, Num.MINUS_ONE)));
    return tools.simplify(a.get().re().signum() < 0 ? Sum.of(atan, Constant.PI) : atan);
  }

  private int multiplicity(Expr poly, Symbol r, Expr root) {
    int m = 0;
    Expr q = poly;
    while (m < MAX_ORDER && tools.isZero(tools.subs(q, Map.of(r, root)))) {
      m++;
      q = tools.d(q, r);
    }
    return Math.max(m, 1);
  }

  /** A family of forcing terms {@code poly(n) bⁿ}: its base and the degree of its polynomial. */
  private record Family(Expr base, int degree) {}

  /**
   * A particular solution of {@code Σ cⱼ y(n + j) = f(n)} by undetermined coefficients: {@code f} a
   * sum of polynomials in {@code n} times exponentials {@code bⁿ}; the trial for a family of degree
   * {@code D} is {@code nˢ (A₀ + … + A_D nᴰ) bⁿ}, {@code s} the multiplicity of {@code b} as a
   * root, and the coefficients solve the recurrence at enough consecutive indices.
   */
  private Expr particular(List<Expr> c, Expr poly, Symbol r, Expr f) {
    List<Family> families = new ArrayList<>();
    for (Expr term : Tools.terms(tools.expand(f))) {
      Family family = family(term);
      boolean merged = false;
      for (int i = 0; i < families.size(); i++) {
        if (tools.isZero(Sum.of(families.get(i).base(), Exprs.neg(family.base())))) {
          families.set(
              i,
              new Family(
                  families.get(i).base(), Math.max(families.get(i).degree(), family.degree())));
          merged = true;
          break;
        }
      }
      if (!merged) {
        families.add(family);
      }
    }
    List<Symbol> unknowns = new ArrayList<>();
    List<Expr> trial = new ArrayList<>();
    int points = c.size();
    for (Family family : families) {
      int s =
          tools.isZero(tools.subs(poly, Map.of(r, family.base())))
              ? multiplicity(poly, r, family.base())
              : 0;
      for (int i = 0; i <= family.degree(); i++) {
        Symbol a = Symbol.of("\u0000A" + unknowns.size());
        unknowns.add(a);
        trial.add(Product.of(a, Power.of(n, Num.of(s + i)), Power.of(family.base(), n)));
      }
      points += s + family.degree() + 1;
    }
    Expr yp = Tools.sum(trial);
    List<Expr> lhs = new ArrayList<>();
    for (int j = 0; j < c.size(); j++) {
      lhs.add(Product.of(c.get(j), tools.subs(yp, Map.of(n, Sum.of(n, Num.of(j))))));
    }
    Expr residual = Sum.of(Tools.sum(lhs), Exprs.neg(f));
    List<Expr> equations = new ArrayList<>();
    for (int k = 0; k < points; k++) {
      ctx.cancellation().check();
      equations.add(tools.simplify(tools.subs(residual, Map.of(n, Num.of(k)))));
    }
    List<Map<Symbol, Expr>> solved =
        tools.solveSystem(equations, unknowns).orElseThrow(Unsupported::new);
    if (solved.isEmpty()) {
      throw new Unsupported();
    }
    Map<Symbol, Expr> values = new LinkedHashMap<>();
    for (Symbol a : unknowns) {
      values.put(a, solved.getFirst().getOrDefault(a, Num.ZERO));
    }
    return tools.simplify(tools.subs(yp, values));
  }

  /** The family of one term of the forcing: its exponential base and its degree in {@code n}. */
  private Family family(Expr term) {
    Expr base = Num.ONE;
    int degree = 0;
    for (Expr factor : term instanceof Product pr ? pr.factors() : List.of(term)) {
      if (!Tools.occurs(factor, n)) {
        continue;
      }
      if (factor.equals(n)) {
        degree += 1;
        continue;
      }
      if (factor instanceof Power w
          && w.base().equals(n)
          && w.exponent() instanceof Num k
          && k.value() instanceof Int i
          && i.signum() > 0
          && i.value().bitLength() < 31) {
        degree += i.value().intValue();
        continue;
      }
      if (factor instanceof Power w && !Tools.occurs(w.base(), n)) {
        Expr u = tools.simplify(tools.d(w.exponent(), n));
        if (Tools.occurs(u, n)) {
          throw new Unsupported();
        }
        base = Product.of(base, Power.of(w.base(), u));
        continue;
      }
      throw new Unsupported();
    }
    return new Family(tools.simplify(base), degree);
  }

  // First order, variable coefficients.

  private Expr firstOrder(List<Expr> c, Expr f) {
    Expr ratio =
        tools.simplify(Exprs.neg(Product.of(c.getFirst(), Power.of(c.get(1), Num.MINUS_ONE))));
    Expr q = tools.simplify(Product.of(f, Power.of(c.get(1), Num.MINUS_ONE)));
    Expr start = Num.of(start());
    Symbol k = Symbol.of("\u0000k");
    Expr product =
        closed(new BigProduct(tools.subs(ratio, Map.of(n, k)), k, start, Sum.of(n, Num.MINUS_ONE)));
    Symbol constant = fresh();
    Expr general = Product.of(product, constant);
    if (!tools.isZero(q)) {
      Expr atNext = tools.subs(product, Map.of(n, Sum.of(k, Num.ONE)));
      Expr sum =
          closed(
              new BigSum(
                  tools.simplify(
                      Product.of(tools.subs(q, Map.of(n, k)), Power.of(atNext, Num.MINUS_ONE))),
                  k,
                  start,
                  Sum.of(n, Num.MINUS_ONE)));
      general = Product.of(product, Sum.of(constant, sum));
    }
    Expr value = tools.simplify(general);
    step("product", Relation.of(Relation.Op.EQ, applied(), value), "p", ratio, "q", q);
    return value;
  }

  /** The least index of a condition, or 0. */
  private long start() {
    long least = Long.MAX_VALUE;
    for (Relation c : p.conditions()) {
      Int k = (Int) ((Num) ((Apply) c.left()).args().getFirst()).value();
      least = Math.min(least, k.value().longValue());
    }
    return least == Long.MAX_VALUE ? 0 : least;
  }

  /** A product or sum in closed form, or unsupported. */
  private Expr closed(Expr node) {
    Expr r = Calculus.sum(tools.canon(node), ctx.withTrace(false)).expr();
    if (Tools.has(r, BigSum.class) || Tools.has(r, BigProduct.class)) {
      throw new Unsupported();
    }
    return tools.simplify(r);
  }

  // Conditions and the check.

  /** A fresh constant, recorded: only these are fixed by the conditions or set in the check. */
  private Symbol fresh() {
    Symbol c = constants.get();
    handed.add(c);
    return c;
  }

  /** The constants handed out that occur in {@code e}, in order. */
  private List<Symbol> constantsIn(Expr e) {
    return handed.stream().filter(c -> Tools.occurs(e, c)).toList();
  }

  private Expr valueAt(Expr solution, Expr index) {
    return tools.simplify(
        ctx.numerics().exact(tools.subs(solution, Map.of(n, index)), ctx.precision()));
  }

  /**
   * The solution with its constants fixed by the conditions, the general one without conditions, or
   * null when no constants meet them.
   */
  private Expr conditions(Expr general) {
    if (p.conditions().isEmpty()) {
      return general;
    }
    List<Symbol> cs = constantsIn(general);
    List<Expr> equations = new ArrayList<>();
    for (Relation c : p.conditions()) {
      Expr at = valueAt(general, ((Apply) c.left()).args().getFirst());
      equations.add(Relation.of(Relation.Op.EQ, at, c.right()));
    }
    if (cs.isEmpty()) {
      return general;
    }
    List<Map<Symbol, Expr>> solved = tools.solveSystem(equations, cs).orElseThrow(Unsupported::new);
    if (solved.isEmpty()) {
      return null;
    }
    Expr value = tools.simplify(tools.subs(general, solved.getFirst()));
    step("conditions", Relation.of(Relation.Op.EQ, applied(), value));
    return value;
  }

  /**
   * Whether the solution satisfies the recurrence at the first {@link #CHECKED} indices from the
   * least condition's (or 0): exactly, or else numerically with its constants at fixed values.
   */
  private boolean check(Expr e, Expr solution) {
    long from = start();
    List<Symbol> cs = constantsIn(solution);
    for (int i = 0; i < CHECKED; i++) {
      ctx.cancellation().check();
      Expr at = Num.of(Int.of(java.math.BigInteger.valueOf(from + i)));
      Expr residual = tools.subs(substitute(e, solution), Map.of(n, at));
      residual = tools.simplify(ctx.numerics().exact(residual, ctx.precision()));
      if (tools.isZero(residual)) {
        continue;
      }
      if (!numericallyZero(residual, cs)) {
        return false;
      }
    }
    return true;
  }

  /** {@code e} with each {@code a(m)} replaced by the solution at {@code m}. */
  private Expr substitute(Expr e, Expr solution) {
    if (e instanceof Apply a && a.head().equals(p.function())) {
      return tools.subs(solution, Map.of(n, a.args().getFirst()));
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    return Operands.rebuild(e, ops.stream().map(o -> substitute(o, solution)).toList());
  }

  private boolean numericallyZero(Expr residual, List<Symbol> cs) {
    Map<Symbol, Expr> at = new LinkedHashMap<>();
    for (int k = 0; k < cs.size(); k++) {
      at.put(cs.get(k), Num.of(Rational.of(k % 2 == 0 ? 2 + k : -(2 + k), 3 + 2L * k)));
    }
    MathContext mc = ctx.precision();
    Expr closed = tools.subs(residual, at);
    Optional<Numeric> value = ctx.numerics().value(closed, mc);
    if (value.isEmpty()) {
      return false;
    }
    Numeric scale = Rational.of(1, 1);
    for (Expr term : Tools.terms(closed)) {
      Optional<Numeric> t = ctx.numerics().value(term, mc);
      if (t.isEmpty()) {
        return false;
      }
      if (t.get().modulus(mc).compareValue(scale.modulus(mc)) > 0) {
        scale = t.get().modulus(mc);
      }
    }
    Real tolerance = Real.of(BigDecimal.ONE.scaleByPowerOfTen(-(mc.getPrecision() - 10)));
    return value.get().modulus(mc).compareValue(tolerance.multiply(scale, mc).modulus(mc)) <= 0;
  }
}
