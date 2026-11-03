package mathcanvas.engine.calculus;

import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Bool;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Derivative;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Interval;
import mathcanvas.engine.expr.Lambda;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Matrix;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Piecewise;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;

/**
 * The growth of an expression as {@code x → ∞}: its leading term {@code c·e^(Σ λ_q
 * x^q)·x^r·log(x)^s} for rational {@code q > 0}, {@code r}, {@code s} and coefficients free of
 * {@code x}. Products multiply, numeric powers raise, a sum keeps its terms of the largest growth
 * and adds their coefficients (a sum whose leading coefficients cancel, or whose order is not
 * decided, has no growth here), {@code e^u} takes the terms of a polynomial-like {@code u}, a
 * logarithm the leading part of its argument's, and another function the limit of it when that is
 * finite and not zero.
 *
 * @param coef the coefficient, free of {@code x}
 * @param exp the exponential part: {@code λ_q} by {@code q}, each not known to be zero
 * @param r the power of {@code x}
 * @param s the power of {@code log(x)}
 */
record Growth(Expr coef, TreeMap<RealNumeric, Expr> exp, RealNumeric r, RealNumeric s) {

  private static final MathContext MC = MathContext.DECIMAL128;

  /**
   * The growth of {@code e} as {@code x → ∞}.
   *
   * @param e the expression, canonical
   * @param x the variable
   * @param ctx the context of the decisions
   * @param limit the limit at {@code ∞} of a function application, finite or not, when one is known
   * @return the growth, or empty when it is not found
   */
  static Optional<Growth> of(Expr e, Symbol x, Context ctx, Function<Expr, Optional<Expr>> limit) {
    return Optional.ofNullable(new Finder(x, ctx, limit).growth(e));
  }

  private static Growth constant(Expr c) {
    return new Growth(c, new TreeMap<>(), Int.ZERO, Int.ZERO);
  }

  /**
   * The sign of the growth's exponent: 1 when it grows without bound, -1 when it tends to 0, 0 when
   * it tends to its coefficient; empty when a leading {@code λ} has no decided sign.
   */
  Optional<Integer> order(Context ctx) {
    for (Map.Entry<RealNumeric, Expr> t : exp.descendingMap().entrySet()) {
      Optional<Integer> s = Signs.sign(t.getValue(), ctx);
      if (s.isEmpty()) {
        return Optional.empty();
      }
      if (s.get() != 0) {
        return s;
      }
    }
    if (r.signum() != 0) {
      return Optional.of(r.signum());
    }
    return Optional.of(s.signum());
  }

  /** The leading term as an expression in {@code x}. */
  Expr term(Symbol x) {
    List<Expr> factors = new ArrayList<>();
    factors.add(coef);
    List<Expr> exponent = new ArrayList<>();
    exp.forEach((q, l) -> exponent.add(Product.of(l, Power.of(x, Num.of(q)))));
    if (!exponent.isEmpty()) {
      factors.add(
          Power.of(Constant.E, exponent.size() == 1 ? exponent.getFirst() : Sum.of(exponent)));
    }
    factors.add(Power.of(x, Num.of(r)));
    factors.add(Power.of(Apply.of(Symbol.of("log"), x), Num.of(s)));
    return Product.of(factors);
  }

  private static final class Finder {

    private final Symbol x;
    private final Context ctx;
    private final Function<Expr, Optional<Expr>> limit;

    Finder(Symbol x, Context ctx, Function<Expr, Optional<Expr>> limit) {
      this.x = x;
      this.ctx = ctx;
      this.limit = limit;
    }

    private Expr canon(Expr e) {
      return Signs.canon(e, ctx);
    }

    Growth growth(Expr e) {
      ctx.cancellation().check();
      if (Free.of(e, x)) {
        return Signs.finite(e) ? constant(e) : null;
      }
      return switch (e) {
        case Symbol _ -> new Growth(Num.ONE, new TreeMap<>(), Int.ONE, Int.ZERO);
        case Product p -> product(p.factors());
        case Sum s -> sum(s.terms());
        case Power p -> power(p.base(), p.exponent());
        case Apply a -> apply(a);
        case Num _,
            mathcanvas.engine.expr.Based _,
            Constant _,
            mathcanvas.engine.expr.Unit _,
            Bool _,
            Relation _,
            ListExpr _,
            Vector _,
            Matrix _,
            SetExpr _,
            Interval _,
            Piecewise _,
            Lambda _,
            Derivative _,
            Integral _,
            Limit _,
            BigSum _,
            BigProduct _ ->
            null;
      };
    }

    private Growth product(List<Expr> factors) {
      Growth out = constant(Num.ONE);
      for (Expr f : factors) {
        Growth g = growth(f);
        if (g == null) {
          return null;
        }
        out = multiply(out, g);
      }
      return out;
    }

    private Growth multiply(Growth a, Growth b) {
      TreeMap<RealNumeric, Expr> exp = new TreeMap<>(a.exp());
      b.exp().forEach((q, l) -> exp.merge(q, l, (u, v) -> canon(Sum.of(u, v))));
      exp.values().removeIf(l -> Signs.zero(l, ctx) == Tri.TRUE);
      return new Growth(
          canon(Product.of(a.coef(), b.coef())),
          exp,
          (RealNumeric) a.r().add(b.r(), MC),
          (RealNumeric) a.s().add(b.s(), MC));
    }

    private Growth sum(List<Expr> terms) {
      List<Growth> best = new ArrayList<>();
      for (Expr t : terms) {
        Growth g = growth(t);
        if (g == null) {
          return null;
        }
        if (best.isEmpty()) {
          best.add(g);
          continue;
        }
        Optional<Integer> c = compare(g, best.getFirst());
        if (c.isEmpty()) {
          return null;
        }
        if (c.get() > 0) {
          best.clear();
          best.add(g);
        } else if (c.get() == 0) {
          best.add(g);
        }
      }
      List<Expr> coefs = new ArrayList<>();
      for (Growth g : best) {
        coefs.add(g.coef());
      }
      Expr c = canon(coefs.size() == 1 ? coefs.getFirst() : Sum.of(coefs));
      if (Signs.zero(c, ctx) != Tri.FALSE) {
        return null;
      }
      Growth lead = best.getFirst();
      return new Growth(c, lead.exp(), lead.r(), lead.s());
    }

    /** The sign of the growth of {@code a} over that of {@code b}. */
    private Optional<Integer> compare(Growth a, Growth b) {
      TreeMap<RealNumeric, Expr> diff = new TreeMap<>(a.exp());
      b.exp()
          .forEach(
              (q, l) ->
                  diff.merge(
                      q, canon(Product.of(Num.MINUS_ONE, l)), (u, v) -> canon(Sum.of(u, v))));
      for (Expr l : diff.descendingMap().values()) {
        Optional<Integer> s = Signs.sign(l, ctx);
        if (s.isEmpty()) {
          return Optional.empty();
        }
        if (s.get() != 0) {
          return s;
        }
      }
      int r = a.r().compareTo(b.r());
      if (r != 0) {
        return Optional.of(r);
      }
      return Optional.of(a.s().compareTo(b.s()));
    }

    private Growth power(Expr b, Expr k) {
      if (Free.of(k, x)) {
        Growth g = growth(b);
        return g == null ? null : power(g, k);
      }
      if (b.equals(Constant.E)) {
        return exponential(k);
      }
      if (Free.of(b, x) && Signs.positive(b, ctx)) {
        return exponential(canon(Product.of(k, Apply.of(Symbol.of("log"), b))));
      }
      return null;
    }

    /** A growth to a rational power. */
    private Growth power(Growth g, Expr k) {
      if (!(k instanceof Num n && n.value() instanceof RealNumeric q && q.isExact())) {
        return null;
      }
      if (!q.isInteger() && !Signs.positive(g.coef(), ctx)) {
        return null;
      }
      TreeMap<RealNumeric, Expr> exp = new TreeMap<>();
      g.exp().forEach((p, l) -> exp.put(p, canon(Product.of(l, k))));
      return new Growth(
          canon(Power.of(g.coef(), k)),
          exp,
          (RealNumeric) g.r().multiply(q, MC),
          (RealNumeric) g.s().multiply(q, MC));
    }

    /** {@code e^u} for a {@code u} whose terms are powers of {@code x} with coefficients. */
    private Growth exponential(Expr u) {
      List<Expr> terms = u instanceof Sum s ? s.terms() : List.of(u);
      TreeMap<RealNumeric, Expr> exp = new TreeMap<>();
      List<Expr> constant = new ArrayList<>();
      for (Expr t : terms) {
        Growth g = growth(t);
        if (g == null || !g.exp().isEmpty() || g.s().signum() != 0) {
          return null;
        }
        if (g.r().signum() > 0) {
          exp.merge(g.r(), g.coef(), (p, q) -> canon(Sum.of(p, q)));
        } else if (g.r().signum() == 0) {
          constant.add(g.coef());
        }
      }
      exp.values().removeIf(l -> Signs.zero(l, ctx) == Tri.TRUE);
      Expr c =
          constant.isEmpty()
              ? Num.ONE
              : canon(
                  Power.of(
                      Constant.E, constant.size() == 1 ? constant.getFirst() : Sum.of(constant)));
      return new Growth(c, exp, Int.ZERO, Int.ZERO);
    }

    private Growth apply(Apply a) {
      String name = a.head().name();
      if (a.args().size() == 1) {
        Expr u = a.args().getFirst();
        switch (name) {
          case "log", "ln" -> {
            return log(u);
          }
          case "sqrt" -> {
            return power(u, Num.of(Rational.of(1, 2)));
          }
          case "exp" -> {
            return exponential(u);
          }
          case "cbrt" -> {
            return cbrt(u);
          }
          case "abs" -> {
            return abs(u);
          }
          default -> {}
        }
      }
      Optional<Expr> l = limit.apply(a);
      if (l.isPresent() && Signs.finite(l.get()) && Signs.zero(l.get(), ctx) == Tri.FALSE) {
        return constant(l.get());
      }
      return null;
    }

    /** The real cube root: the growth to the power 1/3, a negative coefficient's sign kept. */
    private Growth cbrt(Expr u) {
      Growth g = growth(u);
      if (g == null) {
        return null;
      }
      Optional<Integer> s = Signs.sign(g.coef(), ctx);
      if (s.isEmpty() || s.get() == 0) {
        return null;
      }
      Expr magnitude = canon(Product.of(Num.of(s.get()), g.coef()));
      Growth r = power(new Growth(magnitude, g.exp(), g.r(), g.s()), Num.of(Rational.of(1, 3)));
      return r == null
          ? null
          : new Growth(canon(Product.of(Num.of(s.get()), r.coef())), r.exp(), r.r(), r.s());
    }

    /** {@code |u|}: positive at {@code ∞} but for the sign of the coefficient. */
    private Growth abs(Expr u) {
      Growth g = growth(u);
      if (g == null) {
        return null;
      }
      Optional<Integer> s = Signs.sign(g.coef(), ctx);
      if (s.isEmpty() || s.get() == 0) {
        return null;
      }
      return new Growth(canon(Product.of(Num.of(s.get()), g.coef())), g.exp(), g.r(), g.s());
    }

    private Growth log(Expr u) {
      Growth g = growth(u);
      if (g == null || !Signs.positive(g.coef(), ctx)) {
        return null;
      }
      if (!g.exp().isEmpty()) {
        Map.Entry<RealNumeric, Expr> top = g.exp().lastEntry();
        return new Growth(top.getValue(), new TreeMap<>(), top.getKey(), Int.ZERO);
      }
      if (g.r().signum() != 0) {
        return new Growth(Num.of(g.r()), new TreeMap<>(), Int.ZERO, Int.ONE);
      }
      if (g.s().signum() != 0) {
        return null;
      }
      Expr c = canon(Apply.of(Symbol.of("log"), g.coef()));
      return Signs.zero(c, ctx) == Tri.FALSE ? constant(c) : null;
    }
  }
}
