package mathcanvas.engine.numbertheory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import mathcanvas.engine.algebra.AlgebraLimitException;
import mathcanvas.engine.algebra.Polynomial;
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
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;

/**
 * {@code diophantine(a x + b y = c, [x, y])} ({@code docs/language.md} §14.23): the integer
 * solutions of a linear equation in two unknowns with rational coefficients, cleared to integers.
 * With {@code g = gcd(a, b)} dividing {@code c}, the solutions are {@code x = x₀ + (|b|/g)k},
 * {@code y = y₀ − sign(b)(a/g)k} for every integer {@code k}, {@code x₀} the least non-negative
 * one, from the inverse of {@code a/g} modulo {@code |b|/g}; with {@code g ∤ c} there are none,
 * {@code {}}. Steps in the rule set {@code nt}: the particular solution, then the general one.
 */
public final class Diophantine {

  /** The steps, each narrated by the key {@code nt.<step>}. */
  public static final List<String> STEPS =
      List.of(
          "diophantine-particular", "diophantine-general", "diophantine-one", "diophantine-none");

  private static final String SHAPE = "diophantine takes a linear equation a x + b y = c";

  private Diophantine() {}

  /**
   * Solves.
   *
   * @param call the call, which is the value when the equation is not one this solves
   * @param equation the equation, evaluated
   * @param x the first unknown
   * @param y the second unknown
   * @param k the integer parameter
   * @param ctx the trace switch and the cancellation
   * @return the set of solutions, {@code {}}, or the call, with the steps
   * @throws IllegalArgumentException if the equation is not an equation, or not linear
   */
  public static Rewriter.Result solve(
      Expr call, Expr equation, Symbol x, Symbol y, Symbol k, Context ctx) {
    if (equation instanceof mathcanvas.engine.expr.Bool b) {
      // an equation between numbers: every pair, or none
      return new Rewriter.Result(b.value() ? call : SetExpr.of(), Trace.NONE);
    }
    if (!(equation instanceof Relation r && r.op() == Relation.Op.EQ)) {
      throw new IllegalArgumentException(SHAPE);
    }
    Expr difference = Canonical.of(Sum.of(r.left(), Exprs.neg(r.right())), ctx.precision());
    Polynomial p;
    try {
      p = Polynomial.of(difference, ctx.cancellation());
    } catch (IllegalArgumentException | AlgebraLimitException notPolynomial) {
      return new Rewriter.Result(call, Trace.NONE);
    }
    if (p.totalDegree() > 1) {
      // nonlinear in the unknowns alone is refused; a symbol times an unknown leaves the call
      if (Polynomial.of(difference, List.of(x, y)).isPresent()) {
        throw new IllegalArgumentException(SHAPE);
      }
      return new Rewriter.Result(call, Trace.NONE);
    }
    Polynomial pa = p.coefficient(x, 1);
    Polynomial pb = p.coefficient(y, 1);
    Polynomial pc = p.coefficient(x, 0).coefficient(y, 0);
    if (!pa.isConstant() || !pb.isConstant() || !pc.isConstant()) {
      return new Rewriter.Result(call, Trace.NONE);
    }
    List<RealNumeric> coefficients =
        List.of(pa.constantTerm(), pb.constantTerm(), pc.constantTerm());
    BigInteger lcm = BigInteger.ONE;
    for (RealNumeric q : coefficients) {
      if (!(q instanceof Int || q instanceof Rational)) {
        return new Rewriter.Result(call, Trace.NONE);
      }
      BigInteger den = q instanceof Rational rq ? rq.den() : BigInteger.ONE;
      lcm = lcm.divide(lcm.gcd(den)).multiply(den);
    }
    BigInteger[] ints = new BigInteger[3];
    for (int i = 0; i < 3; i++) {
      RealNumeric q = coefficients.get(i);
      BigInteger num = q instanceof Rational rq ? rq.num() : ((Int) q).value();
      BigInteger den = q instanceof Rational rq ? rq.den() : BigInteger.ONE;
      ints[i] = num.multiply(lcm.divide(den));
    }
    BigInteger a = ints[0];
    BigInteger b = ints[1];
    BigInteger c = ints[2].negate();
    Steps steps = new Steps(call, ctx);
    if (a.signum() == 0 && b.signum() == 0) {
      return c.signum() == 0
          ? new Rewriter.Result(call, Trace.NONE)
          : steps.none(a, b, c, BigInteger.ZERO);
    }
    BigInteger g = a.gcd(b);
    if (c.mod(g).signum() != 0) {
      return steps.none(a, b, c, g);
    }
    if (a.signum() == 0 || b.signum() == 0) {
      boolean onlyX = b.signum() == 0;
      BigInteger value = c.divide(onlyX ? a : b);
      Expr v = Num.of(Int.of(value));
      Expr solution = onlyX ? solutions(x, v, y, k) : solutions(x, k, y, v);
      return steps.finish("diophantine-one", solution, values("v", v));
    }
    BigInteger a1 = a.divide(g);
    BigInteger b1 = b.divide(g);
    BigInteger c1 = c.divide(g);
    BigInteger period = b1.abs();
    BigInteger x0 =
        period.equals(BigInteger.ONE)
            ? BigInteger.ZERO
            : c1.multiply(a1.mod(period).modInverse(period)).mod(period);
    BigInteger y0 = c1.subtract(a1.multiply(x0)).divide(b1);
    Num nx0 = Num.of(Int.of(x0));
    Num ny0 = Num.of(Int.of(y0));
    steps.step(
        "diophantine-particular",
        solutions(x, nx0, y, ny0),
        values("a", n(a), "b", n(b), "c", n(c), "g", n(g)));
    BigInteger dy = a1.multiply(BigInteger.valueOf(b1.signum())).negate();
    Expr general =
        solutions(
            x,
            Sum.of(nx0, Product.of(Num.of(Int.of(period)), k)),
            y,
            Sum.of(ny0, Product.of(Num.of(Int.of(dy)), k)));
    return steps.finish("diophantine-general", general, values("p", n(period), "q", n(dy)));
  }

  private static Num n(BigInteger v) {
    return Num.of(Int.of(v));
  }

  private static TreeMap<String, Expr> values(Object... pairs) {
    TreeMap<String, Expr> out = new TreeMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      out.put((String) pairs[i], (Expr) pairs[i + 1]);
    }
    return out;
  }

  /** {@code {[x = u, y = v]}}. */
  private static Expr solutions(Symbol x, Expr u, Symbol y, Expr v) {
    return SetExpr.of(
        ListExpr.of(Relation.of(Relation.Op.EQ, x, u), Relation.of(Relation.Op.EQ, y, v)));
  }

  /** The steps from the call. */
  private static final class Steps {
    private final Expr call;
    private final Context ctx;
    private final List<Rewrite> rewrites = new ArrayList<>();
    private Expr current;

    Steps(Expr call, Context ctx) {
      this.call = call;
      this.ctx = ctx;
      this.current = call;
    }

    void step(String rule, Expr after, TreeMap<String, Expr> values) {
      Expr next = Canonical.of(after, ctx.precision());
      if (ctx.traceEnabled()) {
        rewrites.add(
            new Rewrite(current, next, Modular.RULE_SET, rule, Path.ROOT, Bindings.EMPTY, values));
      }
      current = next;
    }

    Rewriter.Result finish(String rule, Expr after, TreeMap<String, Expr> values) {
      step(rule, after, values);
      Trace trace = ctx.traceEnabled() ? new Trace(call, current, rewrites, List.of()) : Trace.NONE;
      return new Rewriter.Result(current, trace);
    }

    Rewriter.Result none(BigInteger a, BigInteger b, BigInteger c, BigInteger g) {
      return finish(
          "diophantine-none", SetExpr.of(), values("a", n(a), "b", n(b), "c", n(c), "g", n(g)));
    }
  }
}
