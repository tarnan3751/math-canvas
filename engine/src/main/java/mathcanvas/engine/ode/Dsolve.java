package mathcanvas.engine.ode;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.function.Supplier;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;

/**
 * {@code dsolve}: ordinary differential equations solved symbolically ({@code
 * docs/reference/engine.md}, <b>Ordinary differential equations</b>). An equation in one unknown
 * function goes through the classes in a fixed order, the first that applies giving the general
 * solution: for first order, linear (an integrating factor), separable, exact and Bernoulli; for
 * any order, linear with constant coefficients (the characteristic roots, and undetermined
 * coefficients for a forcing term). Linear first-order systems with a constant matrix go through
 * its eigenvalues and generalised eigenvectors, a forcing term through variation of parameters. The
 * initial conditions solve for the constants of integration. Every solution is substituted back
 * into the equations, and one whose residual is not shown to vanish, symbolically or at fixed
 * points, is not returned; when nothing is left, or no class applies, the call stays.
 *
 * <p>The unknown functions are worked on as symbols: {@code y(x)} as {@code y}, {@code y'(x)} as
 * {@code y'}, and so on, names that cannot occur otherwise in a {@code dsolve} call, where the
 * declared names are functions.
 */
public final class Dsolve {

  /** The rule set of the steps, and the narration bundle's key prefix. */
  public static final String RULE_SET = "dsolve";

  /** The steps, each narrated by the key {@code dsolve.<step>}. */
  public static final List<String> STEPS =
      List.of(
          "linear",
          "integrating-factor",
          "integrate",
          "separate",
          "exponentiate",
          "implicit",
          "exact-test",
          "potential",
          "bernoulli",
          "root-branch",
          "invert",
          "characteristic",
          "basis",
          "particular",
          "variation",
          "system",
          "eigen",
          "solution",
          "conditions",
          "unsolved",
          "unverified",
          "unmet");

  /** The highest order handled. */
  public static final int MAX_ORDER = 8;

  /** The points of the numeric check, as numerator and denominator pairs. */
  private static final int[][] POINTS = {
    {1, 3}, {-1, 2}, {3, 4}, {-6, 5}, {3, 2}, {-7, 4}, {2, 1}, {-5, 2}
  };

  /** The points that must be defined for the numeric check to decide. */
  private static final int ENOUGH = 5;

  /**
   * A problem.
   *
   * @param equations the equations, evaluated, the unknowns applied to the variable
   * @param functions the unknown functions, distinct
   * @param variable the independent variable
   * @param conditions the initial conditions {@code y⁽ʲ⁾(a) = b}
   */
  public record Problem(
      List<Relation> equations,
      List<Symbol> functions,
      Symbol variable,
      List<Relation> conditions) {

    /** Copies the lists. */
    public Problem {
      equations = List.copyOf(equations);
      functions = List.copyOf(functions);
      conditions = List.copyOf(conditions);
    }
  }

  /**
   * Whether a relation is an initial condition for the functions: its left side one of them or a
   * primed one applied to a value free of them and of the variable, its right side likewise.
   *
   * @param r the relation, evaluated
   * @param functions the unknown functions
   * @param variable the independent variable
   * @return whether it is one
   */
  public static boolean isCondition(Relation r, List<Symbol> functions, Symbol variable) {
    return r.left() instanceof Apply a
        && functions.contains(Symbol.of(base(a.head().name())))
        && a.args().size() == 1
        && free(a.args().getFirst(), functions)
        && !Tools.occurs(a.args().getFirst(), variable)
        && free(r.right(), functions)
        && !Tools.occurs(r.right(), variable);
  }

  /**
   * Solves a problem.
   *
   * @param call the call, evaluated, which is the result when nothing is found
   * @param problem the problem
   * @param constants the fresh constants of integration, in order
   * @param ctx the assumptions (without facts about the unknowns), precision, trace switch, cap,
   *     providers, numerics and cancellation
   * @return the solutions, or the call, with the trace
   */
  public static Rewriter.Result solve(
      Expr call, Problem problem, Supplier<Symbol> constants, Context ctx) {
    return new Dsolve(call, problem, constants, ctx).run();
  }

  /** The name without its primes. */
  static String base(String name) {
    int prime = name.indexOf('\'');
    return prime < 0 ? name : name.substring(0, prime);
  }

  /** The order a primed name stands for. */
  static int order(String name) {
    return name.length() - base(name).length();
  }

  private static boolean free(Expr e, List<Symbol> functions) {
    for (Symbol f : functions) {
      for (int n = 0; n <= MAX_ORDER + 1; n++) {
        if (Tools.occurs(e, Symbol.of(f.name() + "'".repeat(n)))) {
          return false;
        }
      }
    }
    return true;
  }

  /** A form no class here handles: the call stays. */
  static final class Unsupported extends RuntimeException {
    private static final long serialVersionUID = 1L;

    Unsupported() {
      super(null, null, false, false);
    }
  }

  /**
   * The states a class passes through, each replacing the whole current state, recorded only when
   * it changes it.
   */
  final class Chain {
    final List<Rewrite> steps = new ArrayList<>();
    Expr current = call;

    void step(String rule, Expr after, Object... pairs) {
      Expr state = tools.canon(functions(after));
      if (!traced || state.equals(current)) {
        current = state;
        return;
      }
      Bindings b = Bindings.EMPTY;
      for (int i = 0; i < pairs.length; i += 2) {
        b =
            b.with(
                new Bindings.Binding(
                    (String) pairs[i], tools.canon(functions((Expr) pairs[i + 1])), null, null));
      }
      steps.add(new Rewrite(current, state, RULE_SET, rule, Path.ROOT, b));
      current = state;
    }

    /** A chain with this one's steps so far, which goes on apart from it. */
    Chain copy() {
      Chain out = new Chain();
      out.steps.addAll(steps);
      out.current = current;
      return out;
    }
  }

  /**
   * A solution: a value per function, in symbols of the variable and the constants, or an implicit
   * {@code Φ(x, y) = c} with {@code y} the function's symbol; an explicit one may hold only where
   * its {@code guard} is not negative (a root of {@code y} taken as a power), and is then checked
   * there alone, at points about the condition's abscissa {@code at}.
   */
  record Solution(List<Expr> values, Relation implicit, Expr guard, Expr at) {
    Solution(List<Expr> values, Relation implicit) {
      this(values, implicit, null, null);
    }
  }

  /**
   * A general solution a class found, with its steps, and for a first-order class its potential
   * {@code Φ(x, y) = C}, from which an initial condition gives the constant directly; {@code local}
   * is an explicit branch with a guard, tried first through a condition where the guard is
   * positive; {@code fallback} is the general solution to take, with its own steps, when no
   * solution of this one passes the check.
   */
  record General(
      Chain chain,
      List<Solution> solutions,
      Relation potential,
      Expr lost,
      Solution local,
      General fallback) {
    General(Chain chain, List<Solution> solutions) {
      this(chain, solutions, null, null, null, null);
    }

    General(Chain chain, List<Solution> solutions, Relation potential) {
      this(chain, solutions, potential, null, null, null);
    }

    /** This general solution with {@code h(y)}, whose zeros are the solutions dividing lost. */
    General losing(Expr h) {
      return new General(
          chain, solutions, potential, h, local, fallback == null ? null : fallback.losing(h));
    }
  }

  private final Expr call;
  private final Problem p;
  final Symbol x;
  private final Supplier<Symbol> supplier;
  private final List<Symbol> pool = new ArrayList<>();
  private final boolean traced;
  Tools tools;

  private Dsolve(Expr call, Problem p, Supplier<Symbol> supplier, Context ctx) {
    this.call = call;
    this.p = p;
    this.x = p.variable();
    this.supplier = supplier;
    this.traced = ctx.traceEnabled();
    this.tools = new Tools(ctx);
  }

  /** The problem. */
  Problem problem() {
    return p;
  }

  /** The i-th constant of integration (from 0), drawn when first needed and real. */
  Symbol constant(int i) {
    while (pool.size() <= i) {
      Symbol c = supplier.get();
      pool.add(c);
      Context ctx = tools.context();
      tools =
          tools.with(
              ctx.withAssumptions(
                  ctx.assumptions().with(Relation.of(Relation.Op.IN, c, Constant.REALS))));
    }
    return pool.get(i);
  }

  /** The symbol of the {@code n}-th derivative of a function. */
  static Symbol sym(Symbol f, int n) {
    return Symbol.of(f.name() + "'".repeat(n));
  }

  /** The function applied to the variable, with its primes. */
  Expr applied(Symbol s) {
    return Apply.of(s, x);
  }

  // Conversions between the applied functions and their symbols.

  /** The functions applied to the variable, and their derivatives, as their symbols. */
  private Expr symbols(Expr e) {
    return symbols(e, p.functions(), x);
  }

  /**
   * The unknown functions applied to the variable, and their derivatives in it, as the symbols the
   * classes work on: {@code y(x)} as {@code y}, {@code y'(x)} and {@code diff(y(x), x)} as {@code
   * y'}.
   *
   * @throws Unsupported for an unknown applied to anything but the variable
   */
  static Expr symbols(Expr e, List<Symbol> functions, Symbol x) {
    if (e instanceof Apply a && functions.contains(Symbol.of(base(a.head().name())))) {
      if (!a.args().equals(List.of(x))) {
        throw new Unsupported();
      }
      return a.head();
    }
    if (e instanceof Derivative d) {
      Expr inner = symbols(d.expr(), functions, x);
      if (d.var().equals(x)
          && inner instanceof Symbol s
          && functions.contains(Symbol.of(base(s.name())))) {
        return Symbol.of(s.name() + "'".repeat(d.order()));
      }
      if (!free(inner, functions)) {
        throw new Unsupported();
      }
      return e;
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>();
    for (Expr op : ops) {
      out.add(symbols(op, functions, x));
    }
    return Operands.rebuild(e, out);
  }

  /** The symbols of the functions as the functions applied to the variable. */
  Expr functions(Expr e) {
    if (e instanceof Symbol s && p.functions().contains(Symbol.of(base(s.name())))) {
      return applied(s);
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>();
    for (Expr op : ops) {
      out.add(functions(op));
    }
    return Operands.rebuild(e, out);
  }

  /** The highest order of a function in {@code e}, -1 when it does not occur. */
  private int maxOrder(Expr e, Symbol f) {
    int n = -1;
    for (int k = 0; k <= MAX_ORDER + 1; k++) {
      if (Tools.occurs(e, sym(f, k))) {
        n = k;
      }
    }
    return n;
  }

  // The operation.

  private Rewriter.Result run() {
    General general;
    List<Expr> eqs = new ArrayList<>();
    try {
      for (Relation r : p.equations()) {
        eqs.add(tools.canon(Exprs.sub(symbols(r.left()), symbols(r.right()))));
      }
      general = p.functions().size() == 1 && eqs.size() == 1 ? single(eqs.getFirst()) : null;
      if (general == null && p.functions().size() > 1 && eqs.size() == p.functions().size()) {
        general = new Systems(this).solve(eqs);
      }
    } catch (Unsupported unsupported) {
      general = null;
    }
    if (general == null) {
      return note("unsolved");
    }
    // the general solution, then without its guarded branch, then its fallback, until one is
    // shown to satisfy the equation
    List<General> tries = new ArrayList<>();
    for (General g = general; g != null; g = g.fallback()) {
      tries.add(g);
      if (g.local() != null) {
        tries.add(new General(g.chain(), g.solutions(), g.potential(), g.lost(), null, null));
      }
    }
    for (General g : tries) {
      Rewriter.Result r = attempt(g, eqs);
      if (r != null) {
        return r;
      }
    }
    return note("unverified");
  }

  /** The solution {@code general} gives, or null when none of its solutions passes the check. */
  private Rewriter.Result attempt(General general, List<Expr> eqs) {
    Chain chain = general.chain();
    List<Solution> solutions = general.solutions();
    if (!p.conditions().isEmpty()) {
      solutions = conditions(general);
      if (solutions == null) {
        return note("unsolved");
      }
    }
    List<Solution> checked = new ArrayList<>();
    for (Solution s : solutions) {
      if (check(eqs, s)) {
        checked.add(s);
      }
    }
    if (checked.isEmpty() && !solutions.isEmpty()) {
      return null;
    }
    if (!p.conditions().isEmpty()) {
      // every solution meets its conditions, which also guards against a spurious branch; one
      // whose conditions cannot be decided leaves the call, as one that misses them all does
      List<Solution> met = new ArrayList<>();
      boolean undecided = false;
      for (Solution s : checked) {
        switch (meets(s)) {
          case ZERO -> met.add(s);
          case UNDECIDED -> undecided = true;
          case NONZERO -> {}
        }
      }
      if (undecided || (met.isEmpty() && !checked.isEmpty())) {
        return note("unmet");
      }
      checked = met;
    }
    Expr result = tools.canon(result(checked));
    if (p.conditions().isEmpty()) {
      chain.step("solution", result);
    } else {
      chain.step("conditions", result);
    }
    return done(chain.steps, result);
  }

  private Rewriter.Result note(String rule) {
    List<Rewrite> steps = new ArrayList<>();
    if (traced) {
      steps.add(new Rewrite(call, call, RULE_SET, rule, Path.ROOT, Bindings.EMPTY));
    }
    return done(steps, call);
  }

  private Rewriter.Result done(List<Rewrite> steps, Expr result) {
    Trace trace = traced ? new Trace(call, result, steps, List.of()) : Trace.NONE;
    return new Rewriter.Result(result, trace);
  }

  /** The value: one relation, a set of them, a list per solution of a system, or {@code {}}. */
  private Expr result(List<Solution> solutions) {
    List<Expr> out = new ArrayList<>();
    for (Solution s : solutions) {
      out.add(shown(s));
    }
    return out.size() == 1 ? out.getFirst() : new SetExpr(out);
  }

  /** One solution as the language shows it. */
  Expr shown(Solution s) {
    if (s.implicit() != null) {
      return Relation.of(
          Relation.Op.EQ, functions(s.implicit().left()), functions(s.implicit().right()));
    }
    List<Expr> rels = new ArrayList<>();
    for (int k = 0; k < s.values().size(); k++) {
      rels.add(Relation.of(Relation.Op.EQ, applied(p.functions().get(k)), s.values().get(k)));
    }
    return rels.size() == 1 ? rels.getFirst() : ListExpr.of(rels);
  }

  /** The general solution shown: one, or the set of the branches. */
  Expr shown(List<Solution> solutions) {
    if (solutions.size() == 1) {
      return shown(solutions.getFirst());
    }
    List<Expr> out = new ArrayList<>();
    for (Solution s : solutions) {
      out.add(shown(s));
    }
    return new SetExpr(out);
  }

  /** One equation in one function: the classes in order, the first that applies. */
  private General single(Expr e) {
    Symbol y = p.functions().getFirst();
    int n = maxOrder(e, y);
    if (n < 1 || n > MAX_ORDER) {
      return null;
    }
    FirstOrder first = new FirstOrder(this, y);
    if (n == 1) {
      Expr f = first.solvedForDerivative(e);
      General g = f == null ? null : first.linear(f);
      if (g == null && f != null) {
        g = first.separable(f);
      }
      if (g == null) {
        g = first.exact(e);
      }
      if (g == null && f != null) {
        g = first.bernoulli(f);
      }
      if (g != null) {
        return g;
      }
    }
    return new ConstantCoefficients(this, y).solve(e, n);
  }

  // Initial conditions.

  /** The solutions the conditions select: null when they cannot be solved for the constants. */
  private List<Solution> conditions(General g) {
    boolean regular =
        p.functions().size() == 1
            && p.conditions().size() == 1
            && order(((Apply) p.conditions().getFirst().left()).head().name()) == 0
            && maxOrder(p.equations(), p.functions().getFirst()) == 1;
    if (regular && g.lost() != null) {
      // an equilibrium y = b that separating the variables divided away
      Expr b = p.conditions().getFirst().right();
      if (tools.isZero(tools.subs(g.lost(), Map.of(p.functions().getFirst(), b)))) {
        return List.of(new Solution(List.of(b), null));
      }
    }
    List<Solution> solved = solveConditions(g);
    // a first-order equation with one regular condition always has a local solution: when none is
    // found, nothing is claimed
    return regular && solved != null && solved.isEmpty() ? null : solved;
  }

  /** The highest order of a function over the equations as given. */
  private int maxOrder(List<Relation> equations, Symbol f) {
    int n = -1;
    for (Relation r : equations) {
      n = Math.max(n, maxOrder(symbols(Exprs.sub(r.left(), r.right())), f));
    }
    return n;
  }

  /** The solutions the conditions select: null when they cannot be solved for the constants. */
  private List<Solution> solveConditions(General g) {
    if (g.potential() != null) {
      List<Solution> fromPotential = fromPotential(g);
      if (fromPotential != null) {
        return fromPotential;
      }
    }
    List<Solution> out = new ArrayList<>();
    boolean failed = false;
    for (Solution s : g.solutions()) {
      List<Expr> eqs = new ArrayList<>();
      for (Relation r : p.conditions()) {
        Apply left = (Apply) r.left();
        Symbol f = Symbol.of(base(left.head().name()));
        int j = order(left.head().name());
        Expr at = left.args().getFirst();
        if (s.implicit() != null) {
          if (j != 0) {
            return null;
          }
          continue;
        }
        Expr value = s.values().get(p.functions().indexOf(f));
        Expr derivative = tools.d(value, x, j);
        eqs.add(tools.simplify(Exprs.sub(tools.at(derivative, x, at), r.right())));
      }
      if (s.implicit() != null) {
        if (!(s.implicit().right() instanceof Symbol)) {
          return null;
        }
        // Φ(aᵢ, bᵢ) at every condition: one value fixes the constant, two that differ none
        Expr phi = s.implicit().left();
        Expr value = null;
        for (Relation r : p.conditions()) {
          Expr v = potentialAt(phi, r);
          if (value == null) {
            value = v;
            continue;
          }
          Decision d = decide(Exprs.sub(value, v));
          if (d == Decision.UNDECIDED) {
            return null;
          }
          if (d == Decision.NONZERO) {
            value = null;
            break;
          }
        }
        if (value != null) {
          out.add(new Solution(null, Relation.of(Relation.Op.EQ, phi, value)));
        }
        continue;
      }
      List<Symbol> cs = new ArrayList<>();
      for (Symbol c : pool) {
        for (Expr v : s.values()) {
          if (Tools.occurs(v, c) && !cs.contains(c)) {
            cs.add(c);
          }
        }
      }
      if (cs.isEmpty()) {
        boolean holds = true;
        for (Expr eq : eqs) {
          holds &= tools.isZero(eq);
        }
        if (holds) {
          out.add(s);
        }
        continue;
      }
      Optional<List<Map<Symbol, Expr>>> found = tools.solveSystem(eqs, cs);
      if (found.isEmpty()) {
        failed = true;
        continue;
      }
      for (Map<Symbol, Expr> m : found.get()) {
        List<Expr> values = new ArrayList<>();
        for (Expr v : s.values()) {
          values.add(tools.dropZeroTerms(tools.simplify(tools.subs(v, m))));
        }
        Solution one = new Solution(values, null);
        if (!out.contains(one)) {
          out.add(one);
        }
      }
    }
    // a branch whose constants did not solve may hold a solution: then nothing is claimed
    return failed && out.isEmpty() ? null : out;
  }

  /**
   * One condition {@code y(a) = b} on a first-order solution with a potential: {@code C = Φ(a, b)},
   * then the explicit branches through {@code (a, b)}, or the implicit solution when none is shown
   * to pass through it; null when the conditions are not of that kind.
   */
  private List<Solution> fromPotential(General g) {
    if (p.conditions().size() != 1
        || order(((Apply) p.conditions().getFirst().left()).head().name()) != 0
        || !(g.potential().right() instanceof Symbol c)) {
      return null;
    }
    Symbol y = p.functions().getFirst();
    Expr a = ((Apply) p.conditions().getFirst().left()).args().getFirst();
    Expr b = p.conditions().getFirst().right();
    Expr phi = g.potential().left();
    Expr value = tools.simplify(tools.at(tools.subs(phi, Map.of(y, b)), x, a));
    if (value.equals(Constant.UNDEFINED)) {
      return null;
    }
    List<Solution> out = new ArrayList<>();
    if (g.local() != null
        && tools.context().assumptions().isPositive(b) == mathcanvas.engine.assume.Tri.TRUE) {
      // a branch through (a, b) that holds where its guard, positive at a, stays non-negative
      Expr branch = tools.simplify(tools.subs(g.local().values().getFirst(), Map.of(c, value)));
      Expr guard = tools.simplify(tools.subs(g.local().guard(), Map.of(c, value)));
      if (decide(Exprs.sub(tools.at(branch, x, a), b)) == Decision.ZERO) {
        out.add(new Solution(List.of(branch), null, guard, a));
        return out;
      }
    }
    for (Solution s : g.solutions()) {
      if (s.implicit() != null) {
        continue;
      }
      Expr branch = tools.simplify(tools.subs(s.values().getFirst(), Map.of(c, value)));
      if (decide(Exprs.sub(tools.at(branch, x, a), b)) == Decision.ZERO) {
        out.add(new Solution(List.of(branch), null));
      }
    }
    // an implicit solution only with a constant known to be real
    if (out.isEmpty()
        && tools.context().assumptions().isReal(value) == mathcanvas.engine.assume.Tri.TRUE) {
      out.add(new Solution(null, Relation.of(Relation.Op.EQ, phi, value)));
    }
    return out;
  }

  /** {@code Φ(a, b)} for the condition {@code y(a) = b}. */
  private Expr potentialAt(Expr phi, Relation condition) {
    Expr a = ((Apply) condition.left()).args().getFirst();
    return tools.simplify(
        tools.at(tools.subs(phi, Map.of(p.functions().getFirst(), condition.right())), x, a));
  }

  /**
   * Whether a solution meets every condition, decided as {@link #decide} does: {@code y⁽ʲ⁾(a) − b}
   * for an explicit one, {@code Φ(a, b)} less the right side for an implicit one.
   */
  private Decision meets(Solution s) {
    Decision all = Decision.ZERO;
    for (Relation r : p.conditions()) {
      Apply left = (Apply) r.left();
      Expr residual;
      if (s.implicit() != null) {
        if (order(left.head().name()) != 0) {
          return Decision.UNDECIDED;
        }
        residual = Exprs.sub(potentialAt(s.implicit().left(), r), s.implicit().right());
      } else {
        Symbol f = Symbol.of(base(left.head().name()));
        Expr value = s.values().get(p.functions().indexOf(f));
        Expr derivative = tools.d(value, x, order(left.head().name()));
        residual = Exprs.sub(tools.at(derivative, x, left.args().getFirst()), r.right());
      }
      Decision d = decide(residual);
      if (d == Decision.NONZERO) {
        return d;
      }
      if (d == Decision.UNDECIDED) {
        all = d;
      }
    }
    return all;
  }

  // The check.

  /** Whether the solution satisfies every equation. */
  private boolean check(List<Expr> eqs, Solution s) {
    for (Expr e : eqs) {
      Map<Symbol, Expr> put = new LinkedHashMap<>();
      if (s.implicit() != null) {
        Symbol y = p.functions().getFirst();
        Expr phi = s.implicit().left();
        put.put(sym(y, 1), Exprs.neg(Tools.div(tools.d(phi, x), tools.d(phi, y))));
      } else {
        for (int k = 0; k < p.functions().size(); k++) {
          Symbol f = p.functions().get(k);
          int n = maxOrder(e, f);
          for (int j = 0; j <= n; j++) {
            put.put(sym(f, j), tools.d(s.values().get(k), x, j));
          }
        }
      }
      if (!vanishes(tools.subs(e, put), s.guard(), s.at())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Whether a residual vanishes: it simplifies to 0, or else it is within the precision of 0, less
   * ten digits, relative to its largest term, at the fixed points where it is defined, at least
   * {@link #ENOUGH} of them; each symbol takes its own value at each point, of either sign, as the
   * variable does. The context's numerics are in radians. A guard, when given, limits the points to
   * those where it is not negative.
   */
  private boolean vanishes(Expr r, Expr guard, Expr at) {
    return decide(r, guard, at) == Decision.ZERO;
  }

  /** Whether a value is 0, shown not to be, or neither. */
  enum Decision {
    ZERO,
    NONZERO,
    UNDECIDED
  }

  /**
   * Decides whether a value is 0 as {@link #vanishes} does: {@link Decision#ZERO} when it
   * simplifies to 0 or is within the tolerance at enough fixed points, {@link Decision#NONZERO}
   * when it is beyond the tolerance at a point where it is defined, {@link Decision#UNDECIDED} when
   * it is defined at too few.
   */
  private Decision decide(Expr r) {
    return decide(r, null, null);
  }

  /** As {@link #decide(Expr)}, at the points where {@code guard}, when given, is not negative. */
  private Decision decide(Expr r, Expr guard, Expr center) {
    if (tools.isZero(r)) {
      return Decision.ZERO;
    }
    TreeSet<String> names = new TreeSet<>();
    collect(r, names);
    names.remove(x.name());
    MathContext mc = tools.context().precision();
    Real tolerance = Real.of(BigDecimal.ONE.scaleByPowerOfTen(-(mc.getPrecision() - 10)));
    int defined = 0;
    for (int i = 0; i < POINTS.length; i++) {
      Map<Symbol, Expr> at = new LinkedHashMap<>();
      Expr point = new Num(Rational.of(POINTS[i][0], POINTS[i][1]));
      // a guarded solution is local: its points lie within 1/6 of the condition's abscissa
      at.put(
          x,
          center == null
              ? point
              : tools.canon(
                  plus(
                      center,
                      mathcanvas.engine.expr.Product.of(point, Num.of(Rational.of(1, 16))))));
      int k = 0;
      for (String name : names) {
        long sign = (k + i) % 2 == 0 ? 1 : -1;
        at.put(Symbol.of(name), new Num(Rational.of(sign * (2 + k + 3L * i), 3 + 2L * k + i)));
        k++;
      }
      if (guard != null) {
        Optional<Numeric> g = tools.context().numerics().value(tools.subs(guard, at), mc);
        if (g.isEmpty()
            || !(g.get() instanceof mathcanvas.engine.number.RealNumeric gr)
            || gr.signum() < 0) {
          continue;
        }
      }
      Expr closed = tools.subs(r, at);
      Optional<Numeric> value = tools.context().numerics().value(closed, mc);
      if (value.isEmpty()) {
        continue;
      }
      Numeric scale = Rational.of(1, 1);
      boolean termsDefined = true;
      for (Expr term : Tools.terms(closed)) {
        Optional<Numeric> t = tools.context().numerics().value(term, mc);
        if (t.isEmpty()) {
          termsDefined = false;
          break;
        }
        if (t.get().modulus(mc).compareValue(scale.modulus(mc)) > 0) {
          scale = t.get().modulus(mc);
        }
      }
      if (!termsDefined) {
        continue;
      }
      defined++;
      if (value.get().modulus(mc).compareValue(tolerance.multiply(scale, mc).modulus(mc)) > 0) {
        return Decision.NONZERO;
      }
    }
    return defined >= ENOUGH ? Decision.ZERO : Decision.UNDECIDED;
  }

  private static void collect(Expr e, TreeSet<String> names) {
    if (e instanceof Symbol s) {
      names.add(s.name());
    }
    for (Expr op : Operands.of(e)) {
      collect(op, names);
    }
  }

  /** Whether a sum or other expression is free of every function symbol. */
  boolean freeOfFunctions(Expr e) {
    return free(e, p.functions());
  }

  /** The canonical relation {@code a = b}. */
  Expr eq(Expr a, Expr b) {
    return Relation.of(Relation.Op.EQ, a, b);
  }

  /** The sum of the terms, raw. */
  static Expr plus(Expr a, Expr b) {
    return Sum.of(a, b);
  }
}
