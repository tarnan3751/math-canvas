package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;
import mathcanvas.engine.simplify.Simplifier;

/**
 * Line and surface integrals ({@code docs/language.md} §14.20): each is reduced to an ordinary or
 * an iterated integral in the parameters, which {@link Calculus#integrate} evaluates, so nothing
 * here integrates by its own means. The field is written in {@code x}, {@code y} (and {@code z});
 * the path or the surface gives them as functions of the parameters, put in by the matcher ({@link
 * IntegrateRules#replace}), every derivative by the rule set {@code diff}.
 *
 * <ul>
 *   <li>{@code lineintegral(f, r, t, a, b)}: a scalar field {@code f} gives {@code ∫ f(r(t))
 *       |r′(t)| dt}, a field {@code [P, Q(, R)]} gives {@code ∫ F(r(t))·r′(t) dt}; the step {@code
 *       lineintegral.parametrise}.
 *   <li>{@code surfaceintegral(f, r, u, a, b, v, c, d)}: the normal {@code n = r_u × r_v}; a scalar
 *       field gives {@code ∫∫ f(r) |n| du dv}, a field {@code [P, Q, R]} the flux {@code ∫∫ F(r)·n
 *       du dv}, the first bracket the inner integral; the step {@code surfaceintegral.normal}, then
 *       {@code surfaceintegral.inner} when the inner integral has a closed form.
 * </ul>
 *
 * The rest of the trace is the integral's own. An integral that stays is the result, for the
 * numeric path (a single integral's double-exponential rule, an iterated one's nested quadrature).
 */
public final class VectorIntegrals {

  private VectorIntegrals() {}

  /** The rule set of the steps. */
  public static final String RULE_SET = "vector-integrals";

  /** The steps, {@code <command>.<step>}. */
  public static final List<String> STEPS =
      List.of("lineintegral.parametrise", "surfaceintegral.normal", "surfaceintegral.inner");

  /** The names of a field's variables, by dimension. */
  public static final List<Symbol> COORDINATES =
      List.of(Symbol.of("x"), Symbol.of("y"), Symbol.of("z"));

  /** The commands whose first argument is a field in {@link #COORDINATES}. */
  public static final Set<String> COMMANDS = Set.of("lineintegral", "surfaceintegral");

  /**
   * The line integral of a field along a path.
   *
   * @param f the field: a scalar, or a list or vector of as many components as the path
   * @param path the path, a list or vector of 2 or 3 expressions in {@code t}
   * @param t the parameter
   * @param a the parameter's lower bound
   * @param b its upper bound
   * @param context the assumptions (without facts about {@code t} and the coordinates), precision,
   *     trace, cap, providers and numerics
   * @return the value, or the integral it reduces to when that stays, and the trace
   * @throws IllegalArgumentException with the language's message for a wrong argument
   */
  public static Rewriter.Result line(
      Expr f, Expr path, Symbol t, Expr a, Expr b, Expr call, Context context) {
    List<Expr> r = components(path);
    if (r == null || r.size() < 2 || r.size() > 3) {
      throw new IllegalArgumentException(
          "lineintegral needs a path [x(t), y(t)] or [x(t), y(t), z(t)]");
    }
    if (r.size() == 2 && !Free.of(f, COORDINATES.get(2))) {
      throw new IllegalArgumentException("the field uses z on a plane path");
    }
    List<Expr> field = components(f);
    if (field == null && order(a, b, context) == 1) {
      // ∫ f ds does not depend on the orientation: a scalar field integrates over [min, max]
      Expr swap = a;
      a = b;
      b = swap;
    }
    Context quiet = ranged(context, t, a, b).withTrace(false);
    List<Expr> dr = new ArrayList<>();
    for (Expr c : r) {
      dr.add(Calculus.differentiate(c, t, 1, quiet).expr());
    }
    Expr integrand;
    if (field != null) {
      if (field.size() != r.size()) {
        throw new IllegalArgumentException(
            "lineintegral needs a field of as many components as the path");
      }
      List<Expr> terms = new ArrayList<>();
      for (int k = 0; k < r.size(); k++) {
        terms.add(Product.of(at(field.get(k), r, quiet), dr.get(k)));
      }
      integrand = Sum.of(terms);
    } else {
      scalar("lineintegral", f);
      List<Expr> squares = new ArrayList<>();
      for (Expr d : dr) {
        squares.add(Power.of(d, Num.of(2)));
      }
      integrand = Product.of(at(f, r, quiet), Exprs.sqrt(factored(Sum.of(squares), quiet)));
    }
    Expr g = Simplifier.simplify(Canonical.of(integrand, context.precision()), quiet).expr();
    Expr reduced = Canonical.of(Integral.definite(g, t, a, b), context.precision());
    Rewriter.Result integral = Calculus.integrate(g, t, a, b, context);
    if (!context.traceEnabled()) {
      return new Rewriter.Result(integral.expr(), Trace.NONE);
    }
    TreeMap<String, Expr> values = new TreeMap<>();
    values.put("derivative", Canonical.of(Vector.of(dr), context.precision()));
    Expr start = Canonical.of(call, context.precision());
    Rewrite step =
        new Rewrite(
            start,
            reduced,
            RULE_SET,
            "lineintegral.parametrise",
            Path.ROOT,
            Bindings.EMPTY,
            values);
    return new Rewriter.Result(
        integral.expr(),
        join(new Trace(start, reduced, List.of(step), List.of()), integral.trace()));
  }

  /**
   * The integral of a field over a parametrised surface.
   *
   * @param f the field: a scalar, or a list or vector of three components
   * @param surface the surface, a list or vector of three expressions in {@code u} and {@code v}
   * @param u the inner parameter
   * @param a its lower bound, which may hold {@code v}
   * @param b its upper bound, which may hold {@code v}
   * @param v the outer parameter
   * @param c its lower bound
   * @param d its upper bound
   * @param call the call, for the trace
   * @param context the assumptions (without facts about the parameters and the coordinates),
   *     precision, trace, cap, providers and numerics
   * @return the value, or the iterated integral it reduces to when that stays, and the trace
   * @throws IllegalArgumentException with the language's message for a wrong argument
   */
  public static Rewriter.Result surface(
      Expr f,
      Expr surface,
      Symbol u,
      Expr a,
      Expr b,
      Symbol v,
      Expr c,
      Expr d,
      Expr call,
      Context context) {
    List<Expr> r = components(surface);
    if (r == null || r.size() != 3) {
      throw new IllegalArgumentException(
          "surfaceintegral needs a surface [x(u, v), y(u, v), z(u, v)]");
    }
    List<Expr> field = components(f);
    if (field == null) {
      // ∫∫ f dS does not depend on the orientation: each bracket over [min, max]
      if (order(a, b, context) == 1) {
        Expr swap = a;
        a = b;
        b = swap;
      }
      if (order(c, d, context) == 1) {
        Expr swap = c;
        c = d;
        d = swap;
      }
    }
    Context quiet = ranged(ranged(context, v, c, d), u, a, b).withTrace(false);
    List<Expr> ru = new ArrayList<>();
    List<Expr> rv = new ArrayList<>();
    for (Expr comp : r) {
      ru.add(Calculus.differentiate(comp, u, 1, quiet).expr());
      rv.add(Calculus.differentiate(comp, v, 1, quiet).expr());
    }
    List<Expr> n = new ArrayList<>();
    for (int k = 0; k < 3; k++) {
      int i = (k + 1) % 3;
      int j = (k + 2) % 3;
      n.add(
          Simplifier.simplify(
                  Canonical.of(
                      Sum.of(
                          Product.of(ru.get(i), rv.get(j)),
                          Product.of(Num.MINUS_ONE, ru.get(j), rv.get(i))),
                      context.precision()),
                  quiet)
              .expr());
    }
    Expr integrand;
    if (field != null) {
      if (field.size() != 3) {
        throw new IllegalArgumentException("surfaceintegral needs a field of three components");
      }
      List<Expr> terms = new ArrayList<>();
      for (int k = 0; k < 3; k++) {
        terms.add(Product.of(at(field.get(k), r, quiet), n.get(k)));
      }
      integrand = Sum.of(terms);
    } else {
      scalar("surfaceintegral", f);
      List<Expr> squares = new ArrayList<>();
      for (Expr nk : n) {
        squares.add(Power.of(nk, Num.of(2)));
      }
      integrand = Product.of(at(f, r, quiet), Exprs.sqrt(factored(Sum.of(squares), quiet)));
    }
    var mc = context.precision();
    Expr g = Simplifier.simplify(Canonical.of(integrand, mc), quiet).expr();
    Expr reduced = Canonical.of(Integral.definite(Integral.definite(g, u, a, b), v, c, d), mc);
    Rewriter.Result inner = Calculus.integrate(g, u, a, b, quiet);
    // an inner integral that stays leaves the iterated integral whole, for the numeric path
    Rewriter.Result outer =
        inner.expr() instanceof Integral
            ? new Rewriter.Result(reduced, Trace.NONE)
            : Calculus.integrate(inner.expr(), v, c, d, context);
    if (!context.traceEnabled()) {
      return new Rewriter.Result(outer.expr(), Trace.NONE);
    }
    Expr start = Canonical.of(call, mc);
    List<Rewrite> steps = new ArrayList<>();
    TreeMap<String, Expr> normal = new TreeMap<>();
    normal.put("normal", Canonical.of(Vector.of(n), mc));
    steps.add(
        new Rewrite(
            start, reduced, RULE_SET, "surfaceintegral.normal", Path.ROOT, Bindings.EMPTY, normal));
    Expr afterInner = Canonical.of(Integral.definite(inner.expr(), v, c, d), mc);
    if (!(inner.expr() instanceof Integral) && !afterInner.equals(reduced)) {
      TreeMap<String, Expr> values = new TreeMap<>();
      values.put("integral", Canonical.of(Integral.definite(g, u, a, b), mc));
      values.put("result", inner.expr());
      steps.add(
          new Rewrite(
              reduced,
              afterInner,
              RULE_SET,
              "surfaceintegral.inner",
              Path.ROOT,
              Bindings.EMPTY,
              values));
    }
    Trace own = new Trace(start, steps.getLast().after(), steps, List.of());
    return new Rewriter.Result(outer.expr(), join(own, outer.trace()));
  }

  /**
   * A sum of squares (the squared length of a tangent or a normal) factored when the algebra's
   * {@code factor} finds a factorization, so that its root simplifies: {@code cos²u sin²u + sin⁴u}
   * is {@code sin²u}.
   */
  private static Expr factored(Expr e, Context quiet) {
    Expr c = Canonical.of(e, quiet.precision());
    return Algebra.factor(c, quiet).map(Rewriter.Result::expr).orElse(c);
  }

  /**
   * The sign of {@code a − b} for constant bounds: 1 when {@code a > b} is decided, otherwise 0,
   * −1, or 2 (undecided, or a bound with a symbol).
   */
  private static int order(Expr a, Expr b, Context ctx) {
    if (!constant(a) || !constant(b)) {
      return 2;
    }
    return Definite.sign(Sum.of(a, Product.of(Num.MINUS_ONE, b)), ctx);
  }

  /**
   * The context with the facts {@code min ≤ t ≤ max} of a parameter whose bounds are constants, so
   * that the integrand simplifies on its interval ({@code √(r²)} is {@code r} for {@code r} in
   * {@code [0, 1]}); the context as it is when a bound holds a symbol or the facts are not of an
   * accepted form.
   */
  private static Context ranged(Context ctx, Symbol t, Expr a, Expr b) {
    if (!constant(a) || !constant(b)) {
      return ctx;
    }
    int order = order(a, b, ctx);
    if (order == 2) {
      return ctx;
    }
    Expr lo = order > 0 ? b : a;
    Expr hi = order > 0 ? a : b;
    try {
      return ctx.withAssumptions(
          ctx.assumptions()
              .without(t)
              .with(Relation.of(Relation.Op.GE, t, lo))
              .with(Relation.of(Relation.Op.LE, t, hi)));
    } catch (IllegalArgumentException notAccepted) {
      return ctx;
    }
  }

  private static boolean constant(Expr e) {
    if (e instanceof Symbol || e.equals(mathcanvas.engine.expr.Constant.INFINITY)) {
      return false;
    }
    for (Expr o : mathcanvas.engine.match.Operands.of(e)) {
      if (!constant(o)) {
        return false;
      }
    }
    return true;
  }

  /** The two traces joined when the second starts where the first ends; the first otherwise. */
  private static Trace join(Trace first, Trace second) {
    return second != Trace.NONE
            && second.initial() != null
            && first.result().equals(second.initial())
        ? Trace.concat(first, second)
        : first;
  }

  /** {@code e} with the coordinates replaced by the path's or the surface's components. */
  private static Expr at(Expr e, List<Expr> r, Context quiet) {
    List<Symbol> placeholders = new ArrayList<>();
    Expr out = e;
    for (int k = 0; k < r.size(); k++) {
      Symbol p = Symbol.of("\u0000coordinate" + k);
      placeholders.add(p);
      out = IntegrateRules.replace(out, COORDINATES.get(k), p, quiet);
    }
    for (int k = 0; k < r.size(); k++) {
      out = IntegrateRules.replace(out, placeholders.get(k), r.get(k), quiet);
    }
    return out;
  }

  private static List<Expr> components(Expr e) {
    return e instanceof ListExpr l ? l.items() : e instanceof Vector v ? v.items() : null;
  }

  private static void scalar(String name, Expr f) {
    if (!Calculus.isScalar(f)) {
      throw new IllegalArgumentException(name + " needs a scalar field or a vector field");
    }
  }
}
