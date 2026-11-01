package mathcanvas.engine.calculus;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.algebra.AlgebraLimitException;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.assume.Tri;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Complex;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Replacement;
import mathcanvas.engine.solve.Solver;

/**
 * The rule {@code definite}: {@code ∫[a, b] f dx = F(b) − F(a)} for an antiderivative {@code F}
 * found whole, provided {@code F} is continuous on the interval between the bounds. The bounds must
 * be real and finite. The check reads the kernels of {@code f} and {@code F}: the base of a
 * negative power must not vanish on the interval, the base of a fractional power must be positive
 * there (non-negative for a positive exponent, a zero allowed at an end), the argument of a
 * logarithm positive (of {@code log|g|}, {@code g} not zero), that of {@code coth}, {@code csch}
 * and {@code acot} not zero, {@code 1 − g²} non-negative under {@code asin} and {@code acos}, and a
 * polynomial argument of {@code tan}, {@code sec}, {@code cot} and {@code csc} must not reach a
 * pole over its range between the bounds (a sine or cosine that must not vanish is checked against
 * its zeros alike); {@code p + √q} with {@code q − p²} a constant has the sign the constant gives
 * it, and {@code sec v + tan v} and {@code csc v + cot v} do not vanish where their terms are
 * defined; {@code sin}, {@code cos}, {@code atan}, the exponential, {@code sinh}, {@code cosh},
 * {@code tanh}, {@code sech}, {@code asinh} and {@code abs} are continuous. Each condition holds by
 * the assumptions (with the interval as facts about {@code x} when its ends are numbers), or for a
 * polynomial in {@code x} by its real roots, which the solver finds, lying outside the interval and
 * its value at the midpoint having the sign; anything else, or any doubt, leaves the integral
 * unevaluated. In degrees or gradians an integral with a circular function in {@code f} or {@code
 * F} stays too, since its value would be read in the session's unit.
 */
final class Definite {

  private Definite() {}

  /** The values {@code definite} names. */
  static final Set<String> VALUES = Set.of("antiderivative");

  private static final Set<String> CONTINUOUS =
      Set.of("sin", "cos", "atan", "exp", "sinh", "cosh", "tanh", "sech", "asinh", "abs");

  private static final Set<String> CIRCULAR =
      Set.of(
          "sin", "cos", "tan", "cot", "sec", "csc", "asin", "acos", "atan", "acot", "asec", "acsc",
          "atan2");

  private static final MathContext DIGITS = new MathContext(40);

  /** Below this a numeric difference is a tie, which no sign decides. */
  private static final BigDecimal TIE = BigDecimal.ONE.movePointLeft(25);

  static Optional<Replacement.Valued.Result> evaluate(
      Match m, int depth, IntegrateRules.Search search) {
    Expr f = m.bindings().get("f_");
    Symbol x = (Symbol) m.bindings().get("x_");
    Expr a = m.bindings().get("a_");
    Expr b = m.bindings().get("b_");
    Context ctx = m.context().withTrace(false);
    if (!bound(a, ctx) || !bound(b, ctx)) {
      return Optional.empty();
    }
    Optional<Expr> antiderivative = search.attempt(f, x, depth, ctx);
    if (antiderivative.isEmpty()) {
      return Optional.empty();
    }
    Expr F = antiderivative.get();
    if (!ctx.numerics().radians() && (circular(f) || circular(F))) {
      return Optional.empty();
    }
    Context inner = ctx.withAssumptions(ctx.assumptions().without(x));
    if (!continuous(f, x, a, b, inner) || !continuous(F, x, a, b, inner)) {
      return Optional.empty();
    }
    Expr value =
        Sum.of(
            IntegrateRules.replace(F, x, b, ctx),
            Product.of(Num.MINUS_ONE, IntegrateRules.replace(F, x, a, ctx)));
    TreeMap<String, Expr> values = new TreeMap<>();
    values.put("antiderivative", F);
    return Optional.of(new Replacement.Valued.Result(value, values));
  }

  /** The values {@code improper} names. */
  static final Set<String> IMPROPER_VALUES =
      Set.of("antiderivative", "from", "lower", "to", "upper");

  /**
   * The rules {@code limits} and {@code diverges}: an integral that {@code definite} does not
   * evaluate because a bound is infinite or the integrand or antiderivative has no value at one,
   * {@code F} found whole and continuous strictly between the bounds, is {@code lim_{x→b⁻} F −
   * lim_{x→a⁺} F}, each limit through the limit provider (both sides at an infinite end). When both
   * limits are finite, {@code limits} gives their difference; when one is infinite or undefined the
   * integral diverges, and {@code diverges} gives {@code undefined}; a limit the provider does not
   * decide leaves the integral to the note {@code improper}.
   *
   * @param diverging which of the two rules
   */
  static Optional<Replacement.Valued.Result> improper(
      Match m, int depth, IntegrateRules.Search search, boolean diverging) {
    Expr f = m.bindings().get("f_");
    Symbol x = (Symbol) m.bindings().get("x_");
    Expr a = m.bindings().get("a_");
    Expr b = m.bindings().get("b_");
    Context ctx = m.context().withTrace(false);
    if (!end(a, ctx) || !end(b, ctx) || a.equals(b)) {
      return Optional.empty();
    }
    // the ends in order: a finite pair by its sign, an infinite end by its side
    if (a.equals(LimitMethods.INF) || b.equals(LimitMethods.NEG_INF)) {
      return Optional.empty();
    }
    if (!LimitMethods.infinite(a)
        && !LimitMethods.infinite(b)
        && sign(Sum.of(b, Product.of(Num.MINUS_ONE, a)), ctx) != 1) {
      return Optional.empty();
    }
    Optional<Expr> antiderivative = search.attempt(f, x, depth, ctx);
    if (antiderivative.isEmpty()) {
      return Optional.empty();
    }
    Expr F = antiderivative.get();
    if (!ctx.numerics().radians() && (circular(f) || circular(F))) {
      return Optional.empty();
    }
    Context inner = ctx.withAssumptions(ctx.assumptions().without(x));
    if (!continuousInside(f, x, a, b, inner) || !continuousInside(F, x, a, b, inner)) {
      return Optional.empty();
    }
    Expr lower = limit(F, x, a, Limit.Direction.RIGHT, ctx);
    Expr upper = limit(F, x, b, Limit.Direction.LEFT, ctx);
    if (lower instanceof Limit || upper instanceof Limit) {
      return Optional.empty();
    }
    boolean divergent = divergent(lower) || divergent(upper);
    if (divergent != diverging) {
      return Optional.empty();
    }
    TreeMap<String, Expr> values = new TreeMap<>();
    values.put("antiderivative", F);
    values.put("from", a);
    values.put("lower", lower);
    values.put("to", b);
    values.put("upper", upper);
    Expr value = divergent ? Constant.UNDEFINED : Sum.of(upper, Product.of(Num.MINUS_ONE, lower));
    return Optional.of(new Replacement.Valued.Result(value, values));
  }

  /** A real bound, finite or infinite. */
  private static boolean end(Expr e, Context ctx) {
    return LimitMethods.infinite(e) || bound(e, ctx);
  }

  /**
   * The one-sided limit of {@code F} at a finite end, or the limit at an infinite one ({@link
   * Limits#limit}, through the limit provider).
   */
  private static Expr limit(Expr F, Symbol x, Expr end, Limit.Direction side, Context ctx) {
    Limit.Direction direction = LimitMethods.infinite(end) ? Limit.Direction.BOTH : side;
    // through the limit provider, the limit node when none decides
    return Limits.limit(F, x, end, direction, ctx.withTrace(false)).expr();
  }

  /** Whether a limit is infinite, undefined or not a finite real value. */
  private static boolean divergent(Expr v) {
    return has(v, Constant.INFINITY) || has(v, Constant.UNDEFINED);
  }

  /**
   * A real, finite bound.
   *
   * @param e the bound
   * @param ctx the context of its facts
   * @return true if {@code e} has no {@code ∞} or {@code undefined} and is known real
   */
  static boolean bound(Expr e, Context ctx) {
    return !has(e, Constant.INFINITY)
        && !has(e, Constant.UNDEFINED)
        && ctx.assumptions().isReal(e) == Tri.TRUE;
  }

  static boolean has(Expr e, Expr part) {
    if (e.equals(part)) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (has(o, part)) {
        return true;
      }
    }
    return false;
  }

  /** Whether a circular or inverse circular function occurs in {@code e}. */
  static boolean circular(Expr e) {
    if (e instanceof Apply a && CIRCULAR.contains(a.head().name())) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (circular(o)) {
        return true;
      }
    }
    return false;
  }

  /** What a kernel needs of its argument on the interval. */
  private enum Need {
    NON_ZERO,
    POSITIVE,
    NON_NEGATIVE,
    NO_COS_ZERO,
    NO_SIN_ZERO
  }

  /** Whether every kernel of {@code e} in {@code x} has what it needs between the bounds. */
  static boolean continuous(Expr e, Symbol x, Expr a, Expr b, Context ctx) {
    return continuous(e, x, a, b, ctx, false);
  }

  /**
   * Whether every kernel of {@code e} in {@code x} has what it needs strictly between the bounds,
   * which may be infinite: a zero of a kernel's argument at an end is allowed.
   */
  static boolean continuousInside(Expr e, Symbol x, Expr a, Expr b, Context ctx) {
    return continuous(e, x, a, b, ctx, true);
  }

  private static boolean continuous(Expr e, Symbol x, Expr a, Expr b, Context ctx, boolean open) {
    if (Free.of(e, x)) {
      return true;
    }
    boolean own =
        switch (e) {
          case Symbol _, Sum _, Product _ -> true;
          case Power p -> {
            if (Free.of(p.base(), x)) {
              yield true;
            }
            if (!(p.exponent() instanceof Num n)) {
              yield holds(Need.POSITIVE, p.base(), x, a, b, ctx, open);
            }
            boolean integer = n.value() instanceof Int;
            boolean negative = n.value() instanceof RealNumeric r ? r.signum() < 0 : true;
            if (integer) {
              yield !negative || holds(Need.NON_ZERO, p.base(), x, a, b, ctx, open);
            }
            yield holds(negative ? Need.POSITIVE : Need.NON_NEGATIVE, p.base(), x, a, b, ctx, open);
          }
          case Apply f when f.args().size() == 1 -> {
            Expr g = f.args().getFirst();
            String name = f.head().name();
            if (CONTINUOUS.contains(name)) {
              yield true;
            }
            yield switch (name) {
              case "log" ->
                  g instanceof Apply abs && abs.head().name().equals("abs")
                      ? holds(Need.NON_ZERO, abs.args().getFirst(), x, a, b, ctx, open)
                      : holds(Need.POSITIVE, g, x, a, b, ctx, open);
              case "tan", "sec" -> holds(Need.NO_COS_ZERO, g, x, a, b, ctx, open);
              case "cot", "csc" -> holds(Need.NO_SIN_ZERO, g, x, a, b, ctx, open);
              case "coth", "csch", "acot" -> holds(Need.NON_ZERO, g, x, a, b, ctx, open);
              case "asin", "acos" ->
                  holds(
                      Need.NON_NEGATIVE,
                      Canonical.of(
                          Sum.of(Num.ONE, Product.of(Num.MINUS_ONE, Power.of(g, Num.of(2))))),
                      x,
                      a,
                      b,
                      ctx,
                      open);
              default -> false;
            };
          }
          default -> false;
        };
    if (!own) {
      return false;
    }
    for (Expr o : Operands.of(e)) {
      if (!continuous(o, x, a, b, ctx, open)) {
        return false;
      }
    }
    return true;
  }

  private static boolean holds(
      Need need, Expr g, Symbol x, Expr a, Expr b, Context ctx, boolean open) {
    ctx.cancellation().check();
    Assumptions facts = interval(x, a, b, ctx);
    Tri known =
        switch (need) {
          case NON_ZERO -> facts.isNonZero(g);
          case POSITIVE -> facts.isPositive(g);
          case NON_NEGATIVE -> facts.isNonNegative(g);
          case NO_COS_ZERO, NO_SIN_ZERO -> Tri.UNKNOWN;
        };
    if (known == Tri.TRUE) {
      return true;
    }
    if (need == Need.NO_COS_ZERO || need == Need.NO_SIN_ZERO) {
      return noPole(need == Need.NO_COS_ZERO, g, x, a, b, ctx);
    }
    if (need != Need.NON_NEGATIVE && conjugate(g, need, x, ctx)) {
      return true;
    }
    if (need == Need.NON_ZERO && secantSum(g)) {
      return true;
    }
    if (g instanceof Apply f
        && f.args().size() == 1
        && (f.head().name().equals("sin") || f.head().name().equals("cos"))) {
      // a sine or cosine of a linear argument: no zero between the bounds, the sign at the middle
      if (!noPole(f.head().name().equals("cos"), f.args().getFirst(), x, a, b, ctx)) {
        return false;
      }
      return need == Need.NON_ZERO
          || sign(IntegrateRules.replace(g, x, inside(a, b), ctx), ctx) == 1;
    }
    Optional<List<Expr>> roots = roots(g, x, ctx);
    if (roots.isEmpty()) {
      return false;
    }
    for (Expr r : roots.get()) {
      Optional<Boolean> real = real(r, ctx);
      if (real.isEmpty()) {
        return false;
      }
      if (!real.get()) {
        continue;
      }
      int sa = LimitMethods.infinite(a) ? 1 : sign(Sum.of(r, Product.of(Num.MINUS_ONE, a)), ctx);
      int sb = LimitMethods.infinite(b) ? -1 : sign(Sum.of(r, Product.of(Num.MINUS_ONE, b)), ctx);
      if (sa == 2 || sb == 2) {
        return false;
      }
      boolean outside = sa * sb > 0;
      boolean atEnd = sa == 0 || sb == 0;
      if (!(outside || ((open || need == Need.NON_NEGATIVE) && atEnd && sa != sb))) {
        return false;
      }
    }
    if (need == Need.NON_ZERO) {
      return true;
    }
    return sign(IntegrateRules.replace(g, x, inside(a, b), ctx), ctx) == 1;
  }

  /**
   * Whether {@code g = p + √q} with {@code q − p²} a constant, positive for {@code POSITIVE} and
   * not zero for {@code NON_ZERO}: then {@code √q > |p|}, or {@code √q ≠ |p|}, wherever {@code q ≥
   * 0}, which the power's own kernel check asks.
   */
  private static boolean conjugate(Expr g, Need need, Symbol x, Context ctx) {
    if (!(g instanceof Sum s) || s.terms().size() != 2) {
      return false;
    }
    for (int k = 0; k < 2; k++) {
      if (s.terms().get(k) instanceof Power r && r.exponent().equals(HALF)) {
        Expr p = s.terms().get(1 - k);
        Expr difference;
        try {
          difference =
              mathcanvas.engine.algebra.Algebra.expand(
                      Sum.of(r.base(), Product.of(Num.MINUS_ONE, Power.of(p, Num.of(2)))), ctx)
                  .expr();
        } catch (AlgebraLimitException tooLarge) {
          return false;
        }
        if (!Free.of(difference, x)) {
          return false;
        }
        Tri known =
            need == Need.POSITIVE
                ? ctx.assumptions().isPositive(difference)
                : ctx.assumptions().isNonZero(difference);
        return known == Tri.TRUE;
      }
    }
    return false;
  }

  private static final Expr HALF = Canonical.of(Power.of(Num.of(2), Num.MINUS_ONE));

  /**
   * Whether {@code g} is {@code sec v + tan v} or {@code csc v + cot v}, which is {@code (1 + sin
   * v)/cos v} or {@code (1 + cos v)/sin v}: not zero wherever its terms are defined, which their
   * own kernel checks ask.
   */
  private static boolean secantSum(Expr g) {
    if (!(g instanceof Sum s) || s.terms().size() != 2) {
      return false;
    }
    if (!(s.terms().get(0) instanceof Apply f) || !(s.terms().get(1) instanceof Apply h)) {
      return false;
    }
    if (f.args().size() != 1 || !f.args().equals(h.args())) {
      return false;
    }
    Set<String> names = Set.of(f.head().name(), h.head().name());
    return names.equals(Set.of("sec", "tan")) || names.equals(Set.of("csc", "cot"));
  }

  /** A point strictly between the bounds: the midpoint, or one away from a finite end. */
  private static Expr inside(Expr a, Expr b) {
    if (LimitMethods.infinite(a) && LimitMethods.infinite(b)) {
      return Num.ZERO;
    }
    if (LimitMethods.infinite(a)) {
      return Sum.of(b, Num.MINUS_ONE);
    }
    if (LimitMethods.infinite(b)) {
      return Sum.of(a, Num.ONE);
    }
    return middle(a, b);
  }

  /** The midpoint of the bounds. */
  private static Expr middle(Expr a, Expr b) {
    return Product.of(Sum.of(a, b), Power.of(Num.of(2), Num.MINUS_ONE));
  }

  /** The facts with {@code x} between the bounds, when both are numbers and in order. */
  private static Assumptions interval(Symbol x, Expr a, Expr b, Context ctx) {
    if (!(a instanceof Num na && na.value() instanceof RealNumeric ra)
        || !(b instanceof Num nb && nb.value() instanceof RealNumeric rb)) {
      return ctx.assumptions();
    }
    boolean ordered = ra.compareValue(rb) <= 0;
    Expr lo = ordered ? a : b;
    Expr hi = ordered ? b : a;
    return ctx.assumptions()
        .with(Relation.of(Relation.Op.GE, x, lo))
        .with(Relation.of(Relation.Op.LE, x, hi));
  }

  /** The roots of a polynomial in {@code x} alone, by the solver; empty when it cannot say. */
  private static Optional<List<Expr>> roots(Expr g, Symbol x, Context ctx) {
    try {
      Optional<Polynomial> p = Polynomial.of(g, List.of(x));
      if (p.isEmpty() || p.get().degree(x) < 1) {
        return Optional.empty();
      }
      Expr r = Solver.solve(Relation.of(Relation.Op.EQ, g, Num.ZERO), x, ctx).expr();
      return r instanceof SetExpr s ? Optional.of(s.items()) : Optional.empty();
    } catch (IllegalArgumentException | ArithmeticException | AlgebraLimitException refused) {
      return Optional.empty();
    }
  }

  /** Whether a root is real; empty when its value cannot be told. */
  static Optional<Boolean> real(Expr r, Context ctx) {
    Tri known = ctx.assumptions().isReal(r);
    if (known != Tri.UNKNOWN) {
      return Optional.of(known == Tri.TRUE);
    }
    Optional<Numeric> v = ctx.numerics().value(r, DIGITS);
    if (v.isEmpty()) {
      return Optional.empty();
    }
    if (v.get() instanceof Complex c) {
      return Optional.of(decimal(c.im()).abs().compareTo(TIE) < 0);
    }
    return Optional.of(true);
  }

  /**
   * The sign of {@code e}: −1, 0 or 1, by its exact value, the assumptions or its numeric value
   * clear of a tie; 2 when it cannot be told.
   */
  static int sign(Expr e, Context ctx) {
    Expr c = Canonical.of(e, ctx.precision());
    if (c instanceof Num n && n.value() instanceof RealNumeric r && r.isExact()) {
      return r.signum();
    }
    Assumptions facts = ctx.assumptions();
    if (facts.isZero(c) == Tri.TRUE) {
      return 0;
    }
    if (facts.isPositive(c) == Tri.TRUE) {
      return 1;
    }
    if (facts.isNegative(c) == Tri.TRUE) {
      return -1;
    }
    Optional<Numeric> v = ctx.numerics().value(c, DIGITS);
    if (v.isEmpty() || !(v.get() instanceof RealNumeric r)) {
      return 2;
    }
    return decimal(r).abs().compareTo(TIE) < 0 ? 2 : r.signum();
  }

  /**
   * Whether the linear {@code g} stays between two zeros of {@code cos} (or {@code sin}) over the
   * interval: {@code (g − π/2)/π} (or {@code g/π}) takes no integer value between the bounds.
   */
  private static boolean noPole(boolean cosine, Expr g, Symbol x, Expr a, Expr b, Context ctx) {
    // the values of g at the bounds and at its critical points between them: its range there
    List<Expr> at = new ArrayList<>(List.of(a, b));
    if (IntegrateRules.slope(g, x, ctx.assumptions()).isEmpty()) {
      Expr derivative = Calculus.differentiate(g, x, 1, ctx).expr();
      Optional<List<Expr>> critical = roots(derivative, x, ctx);
      if (critical.isEmpty()) {
        return false;
      }
      for (Expr r : critical.get()) {
        Optional<Boolean> real = real(r, ctx);
        if (real.isEmpty()) {
          return false;
        }
        int sa = sign(Sum.of(r, Product.of(Num.MINUS_ONE, a)), ctx);
        int sb = sign(Sum.of(r, Product.of(Num.MINUS_ONE, b)), ctx);
        if (sa == 2 || sb == 2) {
          return false;
        }
        if (real.get() && sa * sb < 0) {
          at.add(r);
        }
      }
    }
    BigDecimal lo = null;
    BigDecimal hi = null;
    for (Expr point : at) {
      Optional<BigDecimal> t = turns(cosine, IntegrateRules.replace(g, x, point, ctx), ctx);
      if (t.isEmpty()) {
        return false;
      }
      lo = lo == null ? t.get() : lo.min(t.get());
      hi = hi == null ? t.get() : hi.max(t.get());
    }
    BigDecimal below = lo.setScale(0, RoundingMode.FLOOR);
    // no integer in [lo, hi], each end clear of the integers around it
    return below.add(BigDecimal.ONE).compareTo(hi) > 0
        && lo.subtract(below).compareTo(TIE) > 0
        && below.add(BigDecimal.ONE).subtract(hi).compareTo(TIE) > 0;
  }

  private static Optional<BigDecimal> turns(boolean cosine, Expr g, Context ctx) {
    Expr shifted =
        cosine
            ? Sum.of(g, Product.of(Num.MINUS_ONE, Constant.PI, Power.of(Num.of(2), Num.MINUS_ONE)))
            : g;
    Expr t = Canonical.of(Product.of(shifted, Power.of(Constant.PI, Num.MINUS_ONE)));
    Optional<Numeric> v = ctx.numerics().value(t, DIGITS);
    return v.isPresent() && v.get() instanceof RealNumeric r
        ? Optional.of(decimal(r))
        : Optional.empty();
  }

  /** A real number as a decimal of the check's precision. */
  private static BigDecimal decimal(RealNumeric r) {
    return switch (r) {
      case Int i -> new BigDecimal(i.value());
      case Rational q -> new BigDecimal(q.num()).divide(new BigDecimal(q.den()), DIGITS);
      case Real d -> d.value();
    };
  }
}
