package mathcanvas.engine.ode;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mathcanvas.engine.compile.Compiled;
import mathcanvas.engine.compile.UnsupportedException;
import mathcanvas.engine.eval.AngleMode;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.provider.OdeSolution;
import mathcanvas.engine.provider.OdeSolve;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;

/**
 * {@code odesolve}: an initial value problem solved numerically ({@code docs/reference/engine.md},
 * <b>Numeric ODEs</b>). Each equation is solved by {@code solve} for the highest derivative of its
 * unknowns, the system reduced to first order (the state is each unknown and its derivatives below
 * the highest), the right sides compiled to {@code double} evaluators ({@link Compiled}), and the
 * {@link OdeSolve} provider integrates from the conditions' point to the times wanted. The result
 * is a matrix of rows {@code [t, y₁(t), …]} at the grid's points, or the unknown's value (a vector
 * of the unknowns' for a system) at one point, every entry a {@code Real} of at most {@link
 * #DIGITS} digits, a {@code double}'s.
 */
public final class NumericOde {

  /** The rule set of the value's step. */
  public static final String RULE_SET = "numeric";

  /** The value's step, narrated by {@code numeric.ode}. */
  public static final String STEP = "ode";

  /** The most significant digits of a result's entry: a {@code double}'s. */
  public static final int DIGITS = 16;

  /** The grid's intervals when {@code steps} is not given. */
  public static final int DEFAULT_STEPS = 100;

  /** The most intervals of a grid. */
  public static final int MAX_STEPS = 100_000;

  /** The relative tolerance of a step. */
  public static final double RELATIVE_TOLERANCE = 1e-10;

  /** The absolute tolerance of a step. */
  public static final double ABSOLUTE_TOLERANCE = 1e-12;

  private NumericOde() {}

  /**
   * Solves an initial value problem numerically.
   *
   * @param problem the equations, the unknowns, the variable and the conditions, all at one point,
   *     one for each unknown and each order below its highest
   * @param a the interval's lower end
   * @param b the interval's upper end, above {@code a}
   * @param steps the grid's intervals, or 0 for one point
   * @param at the one point, when {@code steps} is 0
   * @param session the session's settings; the functions are compiled in radians, whatever their
   *     angle mode, as {@code dsolve}, {@code diff} and {@code integrate} work
   * @param ctx the assumptions, precision, providers and cancellation
   * @return the matrix of the grid's rows, or the value at the point
   * @throws IllegalArgumentException with the language's message for a problem it cannot integrate
   */
  public static Expr solve(
      Dsolve.Problem problem, Expr a, Expr b, int steps, Expr at, Settings session, Context ctx) {
    Settings settings = session.withAngleMode(AngleMode.RADIANS);
    Tools tools = new Tools(ctx);
    List<Symbol> functions = problem.functions();
    Symbol x = problem.variable();
    List<Expr> eqs = new ArrayList<>();
    try {
      for (Relation r : problem.equations()) {
        eqs.add(
            tools.canon(
                Exprs.sub(
                    Dsolve.symbols(r.left(), functions, x),
                    Dsolve.symbols(r.right(), functions, x))));
      }
    } catch (Dsolve.Unsupported unsupported) {
      throw new IllegalArgumentException("odesolve needs the unknowns applied to " + x.name());
    }
    if (eqs.size() != functions.size()) {
      throw new IllegalArgumentException("odesolve needs one equation per unknown");
    }
    // the orders, and the highest derivatives solved for
    int[] orders = new int[functions.size()];
    List<Symbol> highest = new ArrayList<>();
    for (int k = 0; k < functions.size(); k++) {
      for (int n = 1; n <= Dsolve.MAX_ORDER; n++) {
        for (Expr e : eqs) {
          if (Tools.occurs(e, Dsolve.sym(functions.get(k), n))) {
            orders[k] = n;
          }
        }
      }
      if (orders[k] == 0) {
        throw new IllegalArgumentException(
            "odesolve needs a derivative of " + functions.get(k).name());
      }
      highest.add(Dsolve.sym(functions.get(k), orders[k]));
    }
    Optional<List<Map<Symbol, Expr>>> solved = tools.solveSystem(eqs, highest);
    if (solved.isEmpty()
        || solved.get().size() != 1
        || !solved.get().getFirst().keySet().containsAll(highest)) {
      throw new IllegalArgumentException(
          "odesolve needs equations that solve for the highest derivatives");
    }
    List<Symbol> state = new ArrayList<>();
    for (int k = 0; k < functions.size(); k++) {
      for (int j = 0; j < orders[k]; j++) {
        state.add(Dsolve.sym(functions.get(k), j));
      }
    }
    List<Symbol> arguments = new ArrayList<>();
    arguments.add(x);
    arguments.addAll(state);
    Compiled[] rates = new Compiled[functions.size()];
    for (int k = 0; k < functions.size(); k++) {
      Expr rate = solved.get().getFirst().get(highest.get(k));
      for (Symbol h : highest) {
        if (Tools.occurs(rate, h)) {
          throw new IllegalArgumentException(
              "odesolve needs equations that solve for the highest derivatives");
        }
      }
      try {
        rates[k] = Compiled.compile(rate, arguments, settings, ctx.cancellation());
      } catch (UnsupportedException e) {
        throw new IllegalArgumentException(
            "odesolve needs equations in " + x.name() + " and the unknowns only, with real values");
      }
    }
    // the conditions: one point, each unknown and each order below its highest once
    Map<Symbol, Double> initial = new LinkedHashMap<>();
    Expr point = null;
    for (Relation r : problem.conditions()) {
      Apply left = (Apply) r.left();
      Symbol s = left.head();
      Expr where = tools.canon(left.args().getFirst());
      if (point != null && !point.equals(where)) {
        throw new IllegalArgumentException("odesolve needs every initial condition at one point");
      }
      point = where;
      if (!state.contains(s) || initial.containsKey(s)) {
        throw new IllegalArgumentException(conditionsMessage(functions, orders));
      }
      initial.put(s, number(r.right(), settings, "an initial value"));
    }
    if (initial.size() != state.size()) {
      throw new IllegalArgumentException(conditionsMessage(functions, orders));
    }
    double t0 = number(point, settings, "the initial point");
    double lo = number(a, settings, "odesolve's interval");
    double hi = number(b, settings, "odesolve's interval");
    if (!(lo < hi)) {
      throw new IllegalArgumentException("odesolve needs a < b");
    }
    if (t0 < lo || t0 > hi) {
      throw new IllegalArgumentException("odesolve needs the initial point in [a, b]");
    }
    double[] times;
    if (steps == 0) {
      double t = number(at, settings, "odesolve's point");
      if (t < lo || t > hi) {
        throw new IllegalArgumentException("odesolve needs its point in [a, b]");
      }
      times = new double[] {t};
    } else {
      times = new double[steps + 1];
      for (int k = 0; k <= steps; k++) {
        times[k] = k == steps ? hi : lo + (hi - lo) * k / steps;
      }
    }
    double[] y0 = new double[state.size()];
    for (int i = 0; i < y0.length; i++) {
      y0[i] = initial.get(state.get(i));
    }
    // the derivative of each state: the next order's state, or the rate of the highest
    int[] next = new int[state.size()];
    int[] rateOf = new int[state.size()];
    int index = 0;
    for (int k = 0; k < functions.size(); k++) {
      for (int j = 0; j < orders[k]; j++) {
        next[index] = j + 1 < orders[k] ? index + 1 : -1;
        rateOf[index] = k;
        index++;
      }
    }
    OdeSolve.Rhs rhs =
        (t, y, dy) -> {
          double[] args = new double[y.length + 1];
          args[0] = t;
          System.arraycopy(y, 0, args, 1, y.length);
          for (int i = 0; i < y.length; i++) {
            dy[i] = next[i] >= 0 ? y[next[i]] : rates[rateOf[i]].eval(args);
          }
        };
    OdeSolution s =
        ctx.providers()
            .run(
                new OdeSolve(rhs, t0, y0, times, RELATIVE_TOLERANCE, ABSOLUTE_TOLERANCE),
                ctx,
                Steps.NONE)
            .value();
    switch (s.status()) {
      case OK -> {}
      case NOT_FINITE ->
          throw new IllegalArgumentException(
              "odesolve's solution is not finite on the interval: it blows up or is undefined");
      case FAILED -> throw new IllegalArgumentException("odesolve did not finish: " + s.message());
    }
    MathContext out =
        new MathContext(Math.min(ctx.precision().getPrecision(), DIGITS), RoundingMode.HALF_EVEN);
    List<Integer> columns = new ArrayList<>();
    index = 0;
    for (int k = 0; k < functions.size(); k++) {
      columns.add(index);
      index += orders[k];
    }
    if (steps == 0) {
      double[] value = s.states()[0];
      if (columns.size() == 1) {
        return real(value[0], out);
      }
      List<Expr> items = new ArrayList<>();
      for (int c : columns) {
        items.add(real(value[c], out));
      }
      return new Vector(items);
    }
    List<List<Expr>> rows = new ArrayList<>();
    for (int k = 0; k < times.length; k++) {
      List<Expr> row = new ArrayList<>();
      row.add(real(times[k], out));
      for (int c : columns) {
        row.add(real(s.states()[k][c], out));
      }
      rows.add(row);
    }
    return new Matrix(rows);
  }

  private static String conditionsMessage(List<Symbol> functions, int[] orders) {
    List<String> wanted = new ArrayList<>();
    for (int k = 0; k < functions.size(); k++) {
      for (int j = 0; j < orders[k]; j++) {
        wanted.add(functions.get(k).name() + "'".repeat(j) + "(a)");
      }
    }
    return "odesolve needs the initial conditions " + String.join(", ", wanted) + ", once each";
  }

  /** A closed real value as a {@code double}. */
  private static double number(Expr e, Settings settings, String what) {
    double v;
    try {
      v = Compiled.compile(e, List.of(), settings).eval();
    } catch (UnsupportedException unsupported) {
      v = Double.NaN;
    }
    if (!Double.isFinite(v)) {
      throw new IllegalArgumentException(what + " must be a finite real number");
    }
    return v;
  }

  private static Expr real(double v, MathContext out) {
    return Num.of(Real.of(BigDecimal.valueOf(v).round(out)));
  }
}
