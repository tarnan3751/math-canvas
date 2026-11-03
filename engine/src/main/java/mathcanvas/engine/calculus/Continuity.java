package mathcanvas.engine.calculus;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;

/**
 * A certificate that an expression is continuous at a point of the real line, read operator by
 * operator: sums, products and non-negative integer powers always; a negative power where its base
 * is not zero; a power with another exponent, a root or a logarithm where a real argument is not
 * zero (an argument that may be complex must be positive, off the branch cut); the elementary
 * functions on the interior of their real domains; the step functions away from their jumps. A
 * value at the point is decided by {@link Signs}; one that depends on a parameter and is not
 * decided becomes a condition ({@code a ≠ 0}, {@code a > 0}), so the answer is the generic one.
 *
 * <p>The <b>strict</b> certificate, for Taylor polynomials, asks for differentiability as well:
 * {@code |u|} where {@code u} is not zero, and a root or fractional power of zero refused. The lax
 * one, for limits, accepts {@code |u|} everywhere and {@code u^r} at {@code u = 0} for a positive
 * {@code r}, whose value there is 0 from either side.
 */
final class Continuity {

  private Continuity() {}

  /** The functions continuous on the whole real line, of a real argument. */
  private static final int FAILS = 0;

  private static final int CONTINUOUS = 1;
  private static final int REAL = 2;

  private static final Num HALF = Num.of(Rational.of(1, 2));

  private static final Set<String> ENTIRE =
      Set.of("sin", "cos", "exp", "sinh", "cosh", "tanh", "sech", "atan", "acot", "asinh");

  /**
   * Whether {@code f} is continuous at {@code x = a}, and on what conditions.
   *
   * @param f the expression
   * @param x the variable, real
   * @param a the point, finite and free of {@code x}
   * @param ctx the context, with the facts that hold at the point
   * @param strict whether differentiability is asked for too
   * @return the conditions on the parameters, possibly none; empty when the certificate fails
   */
  static Optional<List<Expr>> at(Expr f, Symbol x, Expr a, Context ctx, boolean strict) {
    Check c = new Check(x, a, ctx, strict);
    return c.ok(f) ? Optional.of(List.copyOf(c.conditions)) : Optional.empty();
  }

  private static final class Check {

    private final Symbol x;
    private final Expr a;
    private final Context ctx;
    private final Context inner;
    private final boolean strict;
    private final List<Expr> conditions = new ArrayList<>();

    Check(Symbol x, Expr a, Context ctx, boolean strict) {
      this.x = x;
      this.a = a;
      this.ctx = ctx;
      this.inner = ctx.withAssumptions(ctx.assumptions().without(x));
      this.strict = strict;
    }

    boolean ok(Expr f) {
      return kind(f) != FAILS;
    }

    /**
     * Whether {@code f} is continuous at the point: {@link #FAILS}, {@link #CONTINUOUS}, or {@link
     * #REAL} when it is also real-valued near the point.
     */
    private int kind(Expr f) {
      ctx.cancellation().check();
      if (Free.of(f, x)) {
        if (!Signs.finite(f)) {
          return FAILS;
        }
        return ctx.assumptions().isReal(f) == Tri.TRUE ? REAL : CONTINUOUS;
      }
      return switch (f) {
        case Symbol _ -> inner.assumptions().isReal(f) == Tri.TRUE ? REAL : CONTINUOUS;
        case Sum s -> all(s.terms());
        case Product p -> all(p.factors());
        case Power p -> power(p);
        case Apply ap ->
            ap.args().size() == 1 ? function(ap.head().name(), ap.args().getFirst()) : FAILS;
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
            FAILS;
      };
    }

    private int all(List<Expr> operands) {
      int out = REAL;
      for (Expr o : operands) {
        out = Math.min(out, kind(o));
        if (out == FAILS) {
          return FAILS;
        }
      }
      return out;
    }

    private int power(Power p) {
      Expr b = p.base();
      Expr e = p.exponent();
      if (!Free.of(e, x)) {
        int ke = kind(e);
        if (ke == FAILS) {
          return FAILS;
        }
        if (Free.of(b, x)) {
          return b.equals(Constant.E) || positive(b) ? Math.min(ke, REAL) : FAILS;
        }
        int kb = kind(b);
        return kb == REAL && positive(b) ? ke : FAILS;
      }
      int kb = kind(b);
      if (kb == FAILS) {
        return FAILS;
      }
      if (e instanceof Num n && n.value() instanceof Int k) {
        return k.signum() >= 0 || nonZero(b) ? kb : FAILS;
      }
      return root(b, kb, e);
    }

    /** {@code b^e} for an exponent that is not an integer, {@code b} of kind {@code kb}. */
    private int root(Expr b, int kb, Expr e) {
      boolean positiveExponent =
          e instanceof Num n && n.value() instanceof RealNumeric r && r.signum() > 0;
      if (!strict && positiveExponent && Signs.zero(value(b), ctx) == Tri.TRUE) {
        return CONTINUOUS;
      }
      if (kb == REAL) {
        if (positive(b)) {
          return realExponent(e) ? REAL : CONTINUOUS;
        }
        return nonZero(b) ? CONTINUOUS : FAILS;
      }
      return positive(b) ? CONTINUOUS : FAILS;
    }

    private boolean realExponent(Expr e) {
      return ctx.assumptions().isReal(e) == Tri.TRUE;
    }

    private int function(String name, Expr u) {
      int ku = kind(u);
      if (ku == FAILS) {
        return FAILS;
      }
      boolean real = ku == REAL;
      if (ENTIRE.contains(name)) {
        return real ? REAL : FAILS;
      }
      boolean ok =
          switch (name) {
            case "tan", "sec" -> real && nonZeroValue(Apply.of(Symbol.of("cos"), value(u)));
            case "cot", "csc" -> real && nonZeroValue(Apply.of(Symbol.of("sin"), value(u)));
            case "coth", "csch", "acsch" -> real && nonZero(u);
            case "sign" -> real && nonZero(u);
            case "log", "ln", "log10", "log2", "sqrt" -> false;
            case "cbrt" -> real && (!strict || nonZero(u));
            case "abs" -> real && (!strict || nonZero(u));
            case "asin", "acos", "atanh" -> real && inside(u, -1, 1);
            case "acosh" -> real && greater(u, 1);
            case "asec", "acsc", "acoth" -> real && (greater(u, 1) || greater(neg(u), 1));
            case "asech" -> real && positive(u) && inside(u, -1, 1);
            case "floor", "ceil", "trunc" -> real && offJump(u, false);
            case "round" -> real && offJump(u, true);
            default -> false;
          };
      if (ok) {
        return REAL;
      }
      return switch (name) {
        case "log", "ln", "log10", "log2" ->
            real
                ? (positive(u) ? REAL : nonZero(u) ? CONTINUOUS : FAILS)
                : positive(u) ? CONTINUOUS : FAILS;
        case "sqrt" -> root(u, ku, HALF);
        default -> FAILS;
      };
    }

    private Expr value(Expr u) {
      return Subst.value(u, x, a, ctx).orElse(Constant.UNDEFINED);
    }

    private boolean nonZero(Expr u) {
      return nonZeroValue(value(u));
    }

    private boolean nonZeroValue(Expr raw) {
      Expr v = Signs.canon(raw, ctx);
      if (!Signs.finite(v)) {
        return false;
      }
      Tri z = Signs.zero(v, ctx);
      if (z == Tri.FALSE) {
        return true;
      }
      if (z == Tri.UNKNOWN && Signs.hasSymbol(v)) {
        conditions.add(
            Relation.of(Relation.Op.NE, Signs.canon(withoutNumber(v, false), ctx), Num.ZERO));
        return true;
      }
      return false;
    }

    private boolean positive(Expr u) {
      return greaterValue(value(u), Num.ZERO);
    }

    private boolean greater(Expr u, long bound) {
      return greaterValue(value(u), Num.of(bound));
    }

    private boolean inside(Expr u, long low, long high) {
      Expr v = value(u);
      return greaterValue(v, Num.of(low)) && greaterValue(Num.of(high), v);
    }

    /** Whether {@code v > w}, decided or recorded as a condition on parameters. */
    private boolean greaterValue(Expr v, Expr w) {
      Expr d = Signs.canon(Sum.of(v, Product.of(Num.MINUS_ONE, w)), ctx);
      if (!Signs.finite(d)) {
        return false;
      }
      Optional<Integer> s = Signs.sign(d, ctx);
      if (s.isPresent()) {
        return s.get() > 0;
      }
      if (Signs.hasSymbol(d)) {
        Expr c = Signs.canon(v, ctx);
        if (w.equals(Num.ZERO)) {
          // a positive numeric factor says nothing of the sign: 2a > 0 is a > 0
          c = Signs.canon(withoutNumber(c, true), ctx);
        }
        conditions.add(Relation.of(Relation.Op.GT, c, w));
        return true;
      }
      return false;
    }

    /**
     * {@code v} without its numeric coefficient, which does not change whether it is zero; for a
     * sign, only a positive coefficient is dropped.
     */
    private static Expr withoutNumber(Expr v, boolean positiveOnly) {
      if (v instanceof Product p
          && p.factors().getFirst() instanceof Num n
          && n.value() instanceof RealNumeric r
          && (!positiveOnly || r.signum() > 0)) {
        List<Expr> rest = p.factors().subList(1, p.factors().size());
        return rest.size() == 1 ? rest.getFirst() : Product.of(rest);
      }
      return v;
    }

    private Expr neg(Expr u) {
      return Product.of(Num.MINUS_ONE, u);
    }

    /** A step function's argument at the point is a number away from the jumps. */
    private boolean offJump(Expr u, boolean halves) {
      Expr v = Signs.canon(value(u), ctx);
      if (!Signs.finite(v) || Signs.hasSymbol(v)) {
        return false;
      }
      Expr shifted = halves ? Sum.of(v, HALF) : v;
      return fractional(Signs.canon(shifted, ctx), ctx);
    }
  }

  /** Whether a real closed value is decided not to be an integer. */
  static boolean fractional(Expr v, Context ctx) {
    if (v instanceof Num n && n.value() instanceof RealNumeric r) {
      return !r.isInteger();
    }
    MathContext mc = new MathContext(60);
    Optional<Numeric> n = ctx.numerics().value(v, mc);
    if (n.isEmpty() || !(n.get() instanceof RealNumeric r)) {
      return false;
    }
    BigDecimal f = ((RealNumeric) r.subtract(r.floor(), mc)).toReal(mc).value();
    return f.compareTo(CLEAR) > 0 && f.compareTo(BigDecimal.ONE.subtract(CLEAR)) < 0;
  }

  private static final BigDecimal CLEAR = new BigDecimal("1e-25");
}
