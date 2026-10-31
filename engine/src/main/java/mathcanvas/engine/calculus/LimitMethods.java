package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.AlgebraLimitException;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.algebra.RationalFunction;
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
import mathcanvas.engine.expr.Limit.Direction;
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
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Matcher;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.provider.Gcd;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.simplify.Simplifier;

/**
 * The methods of the rule set {@code limit} ({@link LimitRules}), and the decision of the limits a
 * method needs on the way: of the parts of an expression, or of the expression a method turned the
 * limit into. One {@link LimitMethods} is one rule's application, with its own memo; a limit it
 * needs is decided by running the methods of the rule set in order, as the rewriter would.
 *
 * <p>Every method is terminal: it decides the limit, a value free of the variable, {@code ∞},
 * {@code -∞} or {@code undefined}, or it does not apply. A method that decides by the limit of
 * another expression (a cancelled quotient, L'Hôpital's quotient, a side, the expression in {@code
 * t = 1/x}) decides that one at the next <b>level</b>, and the methods that do so are absent at the
 * last level, so every chain of such steps is bounded; the limits of the parts of an expression are
 * of strictly smaller expressions at the same level. So the decision terminates.
 *
 * <p>A decision carries its derivation, the {@link Line}s a textbook shows, in order: the inner
 * decisions first, the method's own conclusion last.
 */
final class LimitMethods {

  /** The levels: 0 for the limit asked, one more for each limit a transformation produces. */
  static final int MAX_LEVEL = 4;

  /** L'Hôpital's rule applies at most this many times along one chain. */
  static final int MAX_LHOPITAL = 3;

  /** The highest order of a series expansion. */
  static final int MAX_ORDER = 6;

  /** One line of a derivation: its kind (a rule's name or a narrator key) and its values. */
  record Line(String kind, List<Expr> args) {

    Line {
      args = List.copyOf(args);
    }

    Expr expr() {
      return Apply.of(Symbol.of(kind), args);
    }
  }

  /** The approach of a value: unknown, from above, from below, or exactly (a constant). */
  static final int UNKNOWN = 0;

  static final int ABOVE = 1;
  static final int BELOW = -1;
  static final int EXACT = 2;

  /**
   * A decided limit.
   *
   * @param value the limit: a canonical value free of the variable, {@code ∞}, {@code -∞} or {@code
   *     undefined}
   * @param approach for a finite value, the side the expression takes it from when known
   * @param lines the derivation
   */
  record Found(Expr value, int approach, List<Line> lines) {

    Found {
      lines = List.copyOf(lines);
    }

    /** The derivation as one value, a list of applications headed by the kinds. */
    Expr derivation() {
      return new ListExpr(lines.stream().map(Line::expr).toList());
    }
  }

  /** A method: a rule of the set. */
  interface Method {
    Optional<Found> apply(LimitMethods m, Limit l, int level);
  }

  private final Context ctx;
  private final Map<Limit, Map<Integer, Optional<Found>>> memo = new HashMap<>();
  private final Set<Limit> active = new HashSet<>();

  LimitMethods(Context ctx) {
    this.ctx = ctx.withTrace(false);
  }

  static final Expr INF = Constant.INFINITY;
  static final Expr NEG_INF =
      mathcanvas.engine.expr.Canonical.of(Product.of(Num.MINUS_ONE, Constant.INFINITY));

  /** The sum of {@code terms}: 0 for none, the term itself for one. */
  static Expr sumOf(List<Expr> terms) {
    return terms.isEmpty() ? Num.ZERO : terms.size() == 1 ? terms.getFirst() : Sum.of(terms);
  }

  /** The product of {@code factors}: 1 for none, the factor itself for one. */
  static Expr productOf(List<Expr> factors) {
    return factors.isEmpty()
        ? Num.ONE
        : factors.size() == 1 ? factors.getFirst() : Product.of(factors);
  }

  // The decision of a limit a method needs.

  /** The limit of {@code f} with the variable, point and side of {@code like}. */
  Optional<Found> of(Expr f, Limit like, int level) {
    return decide(Limit.of(canon(f), like.var(), like.point(), like.direction()), level);
  }

  /** Decides {@code l} by the methods of its level in order. */
  Optional<Found> decide(Limit l, int level) {
    ctx.cancellation().check();
    Expr f = l.expr();
    Symbol x = l.var();
    if (Free.of(f, x)) {
      return Signs.finite(f) ? Optional.of(new Found(f, EXACT, List.of())) : Optional.empty();
    }
    if (f.equals(x)) {
      return Optional.of(new Found(l.point(), side(l), List.of()));
    }
    if (level > MAX_LEVEL) {
      return Optional.empty();
    }
    Map<Integer, Optional<Found>> byLevel = memo.computeIfAbsent(l, k -> new HashMap<>());
    Optional<Found> known = byLevel.get(level);
    if (known != null) {
      return known;
    }
    if (!active.add(l)) {
      return Optional.empty();
    }
    Optional<Found> out = Optional.empty();
    try {
      for (LimitRules.Entry e : LimitRules.methods(level)) {
        out = e.method().apply(this, l, level);
        if (out.isPresent()) {
          break;
        }
      }
    } catch (AlgebraLimitException | ArithmeticException | IllegalArgumentException refused) {
      out = Optional.empty();
    } finally {
      active.remove(l);
    }
    byLevel.put(level, out);
    return out;
  }

  // Helpers.

  Expr canon(Expr e) {
    return Signs.canon(e, ctx);
  }

  /** Simplifies a value free of the variable, under the facts that hold at the point. */
  Expr simplify(Expr e) {
    return Simplifier.simplify(e, ctx).expr();
  }

  /**
   * The context of an expression in the variable of {@code l}: without the facts about it, which
   * hold at the point but not in the expression.
   */
  Context in(Limit l) {
    return ctx.withAssumptions(ctx.assumptions().without(l.var()));
  }

  Context context() {
    return ctx;
  }

  static boolean infinite(Expr v) {
    return v.equals(INF) || v.equals(NEG_INF);
  }

  static int infSign(Expr v) {
    return v.equals(INF) ? 1 : -1;
  }

  static Expr inf(int sign) {
    return sign > 0 ? INF : NEG_INF;
  }

  static boolean finite(Expr v) {
    return Signs.finite(v);
  }

  /** The side of the variable's approach: above, below, or unknown for a two-sided limit. */
  static int side(Limit l) {
    if (infinite(l.point())) {
      return UNKNOWN;
    }
    return switch (l.direction()) {
      case RIGHT -> ABOVE;
      case LEFT -> BELOW;
      case BOTH -> UNKNOWN;
    };
  }

  static boolean atInfinity(Limit l) {
    return infinite(l.point());
  }

  private Optional<Integer> sign(Expr c) {
    return Signs.sign(c, ctx);
  }

  private boolean isZero(Expr v) {
    return finite(v) && Signs.zero(v, ctx) == Tri.TRUE;
  }

  private boolean nonZero(Expr v) {
    return infinite(v) || finite(v) && Signs.zero(v, ctx) == Tri.FALSE;
  }

  /** The sign of a value: of a finite real one by {@link Signs}, of an infinite one its own. */
  private Optional<Integer> valueSign(Expr v) {
    if (infinite(v)) {
      return Optional.of(infSign(v));
    }
    return finite(v) ? sign(v) : Optional.empty();
  }

  /**
   * The side from which {@code f} takes its finite limit {@code found}: the one recorded, else from
   * the first derivative of {@code f} that is not zero at a finite point.
   */
  private int approach(Expr f, Limit like, Found found) {
    if (found.approach() != UNKNOWN) {
      return found.approach();
    }
    if (atInfinity(like) || Free.of(f, like.var())) {
      return UNKNOWN;
    }
    TaylorRules.Derivatives d = new TaylorRules.Derivatives(f, like.var(), like.point(), ctx);
    Optional<Expr> v0 = d.next();
    if (v0.isEmpty() || !canon(v0.get()).equals(found.value())) {
      return UNKNOWN;
    }
    int dir = side(like);
    for (int k = 1; k <= MAX_ORDER; k++) {
      Optional<Expr> v = d.next();
      if (v.isEmpty()) {
        return UNKNOWN;
      }
      Tri z = Signs.zero(v.get(), ctx);
      if (z == Tri.UNKNOWN) {
        return UNKNOWN;
      }
      if (z == Tri.FALSE) {
        Optional<Integer> s = sign(v.get());
        if (s.isEmpty()) {
          return UNKNOWN;
        }
        if (k % 2 == 0) {
          return s.get();
        }
        return dir == UNKNOWN ? UNKNOWN : s.get() * dir;
      }
    }
    return UNKNOWN;
  }

  /** The kinds of line that say nothing new about a part: what the laws show anyway. */
  private static final Set<String> ROUTINE = Set.of("substitution", "limit-laws", "pole");

  /** The lines of a part's decision, without the routine ones. */
  private static List<Line> partLines(Found f) {
    return f.lines().stream().filter(l -> !ROUTINE.contains(l.kind())).toList();
  }

  private static Found found(Expr value, int approach, List<Line> before, Line conclusion) {
    List<Line> lines = new ArrayList<>(before);
    lines.add(conclusion);
    return new Found(value, approach, lines);
  }

  private static Line line(String kind, Expr... args) {
    return new Line(kind, List.of(args));
  }

  private Expr finish(Expr v) {
    return infinite(v) || v.equals(Constant.UNDEFINED) ? v : simplify(v);
  }

  // 1. Substitution.

  static Optional<Found> substitution(LimitMethods m, Limit l, int level) {
    if (atInfinity(l)) {
      return Optional.empty();
    }
    Context c = m.ctx;
    Optional<List<Expr>> conditions = Continuity.at(l.expr(), l.var(), l.point(), c, false);
    if (conditions.isEmpty()) {
      return Optional.empty();
    }
    Optional<Expr> v = Subst.value(l.expr(), l.var(), l.point(), c);
    if (v.isEmpty()) {
      return Optional.empty();
    }
    Expr value = v.get();
    if (!finite(value)) {
      return Optional.empty();
    }
    Line line =
        conditions.get().isEmpty()
            ? line("substitution", l.expr(), value)
            : line("substitution-where", l.expr(), value, m.canon(new SetExpr(conditions.get())));
    return Optional.of(new Found(value, UNKNOWN, List.of(line)));
  }

  // 2. Poles and one-sided limits.

  /** A rational function in {@code x} alone at a rational zero of its denominator. */
  static Optional<Found> pole(LimitMethods m, Limit l, int level) {
    if (atInfinity(l)
        || !(l.point() instanceof Num n && n.value() instanceof RealNumeric a && a.isExact())) {
      return Optional.empty();
    }
    Symbol x = l.var();
    RationalFunction q = RationalFunction.of(l.expr(), m.ctx.cancellation());
    if (!List.of(x).containsAll(q.variables()) || q.denominator().isConstant()) {
      return Optional.empty();
    }
    Polynomial den = q.denominator();
    if (!m.isZero(m.at(den, x, l.point()))) {
      return Optional.empty();
    }
    int order = 0;
    Polynomial d = den;
    Expr lead;
    while (true) {
      d = d.derivative(x);
      order++;
      lead = m.at(d, x, l.point());
      if (!m.isZero(lead)) {
        break;
      }
    }
    Expr top = m.at(q.numerator(), x, l.point());
    int s = Integer.signum(((RealNumeric) ((Num) m.canon(Product.of(top, lead))).value()).signum());
    Expr mult = Num.of(order);
    boolean odd = order % 2 == 1;
    Expr right = inf(s);
    Expr left = inf(odd ? -s : s);
    return Optional.of(
        switch (l.direction()) {
          case RIGHT -> new Found(right, UNKNOWN, List.of(line("pole", l.expr(), mult, right)));
          case LEFT -> new Found(left, UNKNOWN, List.of(line("pole", l.expr(), mult, left)));
          case BOTH ->
              odd
                  ? new Found(
                      Constant.UNDEFINED,
                      UNKNOWN,
                      List.of(line("pole-sides", l.expr(), mult, left, right)))
                  : new Found(right, UNKNOWN, List.of(line("pole", l.expr(), mult, right)));
        });
  }

  private Expr at(Polynomial p, Symbol x, Expr a) {
    return Subst.of(p.toExpr(), x, a, ctx.precision()).orElseThrow();
  }

  /** On one side of a jump of {@code |u|}, {@code sign}, {@code floor} and the others. */
  static Optional<Found> oneSided(LimitMethods m, Limit l, int level) {
    if (atInfinity(l) || l.direction() == Direction.BOTH) {
      return Optional.empty();
    }
    Expr g = m.sideForm(l.expr(), l, level);
    if (g == null || g.equals(l.expr())) {
      return Optional.empty();
    }
    Optional<Found> r = m.of(g, l, level + 1);
    Expr side = Symbol.of(l.direction() == Direction.RIGHT ? "right" : "left");
    return r.map(f -> m.foundAfter(f, line("one-sided", l.expr(), g, side)));
  }

  private Found foundAfter(Found f, Line first) {
    List<Line> lines = new ArrayList<>();
    lines.add(first);
    lines.addAll(f.lines());
    return new Found(f.value(), f.approach(), lines);
  }

  /** {@code f} with each step function at a jump replaced by its form on the limit's side. */
  private Expr sideForm(Expr f, Limit l, int level) {
    if (Free.of(f, l.var())) {
      return f;
    }
    List<Expr> ops = Operands.of(f);
    List<Expr> out = new ArrayList<>(ops.size());
    for (Expr o : ops) {
      Expr r = sideForm(o, l, level);
      if (r == null) {
        return null;
      }
      out.add(r);
    }
    Expr e = Operands.rebuild(f, out);
    if (!(e instanceof Apply a) || a.args().size() != 1) {
      return e;
    }
    Expr u = a.args().getFirst();
    String name = a.head().name();
    if (!Set.of("abs", "sign", "floor", "ceil", "round", "trunc").contains(name)) {
      return e;
    }
    Optional<Found> lu = of(u, l, level);
    if (lu.isEmpty() || !finite(lu.get().value())) {
      return e;
    }
    Expr v = lu.get().value();
    if (!(v instanceof Num n && n.value() instanceof RealNumeric r)) {
      return e;
    }
    boolean jump =
        switch (name) {
          case "abs", "sign" -> r.signum() == 0;
          case "round" -> !r.isInteger() && ((RealNumeric) r.multiply(Int.TWO, mc())).isInteger();
          default -> r.isInteger();
        };
    if (!jump) {
      return e;
    }
    int s = approach(u, l, lu.get());
    if (s != ABOVE && s != BELOW) {
      return null;
    }
    return switch (name) {
      case "abs" -> canon(Product.of(Num.of(s), u));
      case "sign" -> Num.of(s);
      case "floor" -> s > 0 ? v : canon(Sum.of(v, Num.MINUS_ONE));
      case "ceil" -> s > 0 ? canon(Sum.of(v, Num.ONE)) : v;
      case "round" -> canon(Sum.of(v, Num.of(Rational.of(s, 2))));
      default -> {
        // trunc: toward zero
        int sv = r.signum();
        if (s > 0) {
          yield sv >= 0 ? v : canon(Sum.of(v, Num.ONE));
        }
        yield sv > 0 ? canon(Sum.of(v, Num.MINUS_ONE)) : v;
      }
    };
  }

  private java.math.MathContext mc() {
    return ctx.precision();
  }

  /** Both sides of a two-sided limit at a finite point, compared. */
  static Optional<Found> twoSided(LimitMethods m, Limit l, int level) {
    if (atInfinity(l) || l.direction() != Direction.BOTH) {
      return Optional.empty();
    }
    Optional<Found> left =
        m.decide(Limit.of(l.expr(), l.var(), l.point(), Direction.LEFT), level + 1);
    if (left.isEmpty()) {
      return Optional.empty();
    }
    if (left.get().value().equals(Constant.UNDEFINED)) {
      // no limit from one side: none from both
      return left;
    }
    Optional<Found> right =
        m.decide(Limit.of(l.expr(), l.var(), l.point(), Direction.RIGHT), level + 1);
    if (right.isEmpty()) {
      return Optional.empty();
    }
    if (right.get().value().equals(Constant.UNDEFINED)) {
      return right;
    }
    Expr a = left.get().value();
    Expr b = right.get().value();
    List<Line> lines = new ArrayList<>(left.get().lines());
    lines.addAll(right.get().lines());
    boolean same =
        a.equals(b)
            || finite(a) && finite(b) && m.isZero(m.canon(Sum.of(a, Product.of(Num.MINUS_ONE, b))));
    if (same && !a.equals(Constant.UNDEFINED)) {
      lines.add(line("two-sided", a, b, a));
      return Optional.of(new Found(a, UNKNOWN, lines));
    }
    lines.add(line("sides-differ", a, b));
    return Optional.of(new Found(Constant.UNDEFINED, UNKNOWN, lines));
  }

  // The limit laws.

  /** The limits of the operands combined, when the combination is determinate. */
  static Optional<Found> laws(LimitMethods m, Limit l, int level) {
    Expr f = l.expr();
    Symbol x = l.var();
    List<Line> lines = new ArrayList<>();
    Optional<Found> r =
        switch (f) {
          case Sum s -> m.sumLaw(s.terms(), l, level, lines);
          case Product p -> m.productLaw(p.factors(), l, level, lines);
          case Power p -> m.powerLaw(p.base(), p.exponent(), l, level, lines);
          case Apply a when a.args().size() == 1 -> m.functionLaw(a, l, level, lines);
          case Apply _,
              Symbol _,
              Num _,
              Constant _,
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
              Optional.empty();
        };
    if (r.isEmpty() || Free.of(f, x)) {
      return Optional.empty();
    }
    Expr v = m.finish(r.get().value());
    lines.add(line("limit-laws", f, v));
    return Optional.of(new Found(v, r.get().approach(), lines));
  }

  private Optional<Found> part(Expr e, Limit l, int level, List<Line> lines) {
    Optional<Found> r = of(e, l, level);
    r.ifPresent(f -> lines.addAll(partLines(f)));
    return r;
  }

  private Optional<Found> sumLaw(List<Expr> terms, Limit l, int level, List<Line> lines) {
    List<Expr> finite = new ArrayList<>();
    int infinity = 0;
    int approach = EXACT;
    for (Expr t : terms) {
      Optional<Found> r = part(t, l, level, lines);
      if (r.isEmpty() || r.get().value().equals(Constant.UNDEFINED)) {
        return Optional.empty();
      }
      Expr v = r.get().value();
      if (infinite(v)) {
        if (infinity != 0 && infinity != infSign(v)) {
          return Optional.empty();
        }
        infinity = infSign(v);
      } else {
        finite.add(v);
        approach = combine(approach, r.get().approach());
      }
    }
    if (infinity != 0) {
      return Optional.of(new Found(inf(infinity), UNKNOWN, List.of()));
    }
    Expr v = canon(sumOf(finite));
    return Optional.of(new Found(v, approach == EXACT ? UNKNOWN : approach, List.of()));
  }

  private static int combine(int a, int b) {
    if (a == EXACT) {
      return b;
    }
    if (b == EXACT) {
      return a;
    }
    return a == b ? a : UNKNOWN;
  }

  private Optional<Found> productLaw(List<Expr> factors, Limit l, int level, List<Line> lines) {
    List<Expr> values = new ArrayList<>();
    boolean anyInfinite = false;
    boolean anyZero = false;
    int sign = 1;
    boolean signKnown = true;
    int zeroSide = 1;
    boolean zeroSideKnown = true;
    for (Expr f : factors) {
      Optional<Found> r = part(f, l, level, lines);
      if (r.isEmpty() || r.get().value().equals(Constant.UNDEFINED)) {
        return Optional.empty();
      }
      Expr v = r.get().value();
      values.add(v);
      if (infinite(v)) {
        anyInfinite = true;
        sign *= infSign(v);
        continue;
      }
      if (isZero(v)) {
        anyZero = true;
        int a = approach(f, l, r.get());
        if (a == ABOVE || a == BELOW) {
          zeroSide *= a;
        } else {
          zeroSideKnown = false;
        }
        continue;
      }
      Optional<Integer> s = sign(v);
      if (s.isPresent() && s.get() != 0) {
        sign *= s.get();
        zeroSide *= s.get();
      } else {
        signKnown = false;
        zeroSideKnown = false;
        if (!nonZero(v)) {
          return Optional.empty();
        }
      }
    }
    if (anyInfinite) {
      if (anyZero || !signKnown) {
        return Optional.empty();
      }
      return Optional.of(new Found(inf(sign), UNKNOWN, List.of()));
    }
    Expr v = canon(productOf(values));
    return Optional.of(new Found(v, anyZero && zeroSideKnown ? zeroSide : UNKNOWN, List.of()));
  }

  private Optional<Found> powerLaw(Expr b, Expr e, Limit l, int level, List<Line> lines) {
    Symbol x = l.var();
    if (Free.of(e, x)) {
      if (!(e instanceof Num n && n.value() instanceof RealNumeric k)) {
        return symbolicPower(b, e, l, level, lines);
      }
      Optional<Found> rb = part(b, l, level, lines);
      if (rb.isEmpty()) {
        return Optional.empty();
      }
      Expr v = rb.get().value();
      boolean integer = k.isInteger();
      boolean even = integer && ((RealNumeric) k.divide(Int.TWO, mc())).isInteger();
      if (infinite(v)) {
        if (k.signum() > 0) {
          if (v.equals(INF)) {
            return Optional.of(new Found(INF, UNKNOWN, List.of()));
          }
          return integer
              ? Optional.of(new Found(even ? INF : NEG_INF, UNKNOWN, List.of()))
              : Optional.empty();
        }
        int a = v.equals(INF) ? ABOVE : integer ? (even ? ABOVE : BELOW) : UNKNOWN;
        return integer || v.equals(INF)
            ? Optional.of(new Found(Num.ZERO, a, List.of()))
            : Optional.empty();
      }
      if (!finite(v)) {
        return Optional.empty();
      }
      if (isZero(v)) {
        int a = approach(b, l, rb.get());
        if (k.signum() > 0) {
          int side =
              even
                  ? (a == UNKNOWN ? UNKNOWN : ABOVE)
                  : integer ? a : (a == ABOVE ? ABOVE : UNKNOWN);
          if (!integer && a == BELOW) {
            return Optional.empty();
          }
          return Optional.of(new Found(Num.ZERO, side, List.of()));
        }
        if (even && (a == ABOVE || a == BELOW)) {
          return Optional.of(new Found(INF, UNKNOWN, List.of()));
        }
        if (a == ABOVE) {
          return Optional.of(new Found(INF, UNKNOWN, List.of()));
        }
        if (integer && a == BELOW) {
          return Optional.of(new Found(NEG_INF, UNKNOWN, List.of()));
        }
        return Optional.empty();
      }
      if (!integer && !Signs.positive(v, ctx) || k.signum() < 0 && !nonZero(v)) {
        return Optional.empty();
      }
      int a = UNKNOWN;
      if (Signs.positive(v, ctx)) {
        int ab = rb.get().approach();
        a = ab == ABOVE || ab == BELOW ? ab * k.signum() : UNKNOWN;
      }
      return Optional.of(new Found(canon(Power.of(v, e)), a, List.of()));
    }
    Optional<Found> re = part(e, l, level, lines);
    if (re.isEmpty() || re.get().value().equals(Constant.UNDEFINED)) {
      return Optional.empty();
    }
    Expr w = re.get().value();
    Expr v;
    if (Free.of(b, x)) {
      v = b;
    } else {
      Optional<Found> rb = part(b, l, level, lines);
      if (rb.isEmpty()) {
        return Optional.empty();
      }
      v = rb.get().value();
      if (isZero(v)) {
        if (approach(b, l, rb.get()) != ABOVE) {
          return Optional.empty();
        }
        Optional<Integer> sw = valueSign(w);
        if (sw.isEmpty() || sw.get() == 0) {
          return Optional.empty();
        }
        return Optional.of(
            sw.get() > 0
                ? new Found(Num.ZERO, ABOVE, List.of())
                : new Found(INF, UNKNOWN, List.of()));
      }
    }
    if (v.equals(INF)) {
      Optional<Integer> sw = valueSign(w);
      if (sw.isEmpty() || sw.get() == 0) {
        return Optional.empty();
      }
      return Optional.of(
          sw.get() > 0
              ? new Found(INF, UNKNOWN, List.of())
              : new Found(Num.ZERO, ABOVE, List.of()));
    }
    if (!finite(v) || !(v.equals(Constant.E) || Signs.positive(v, ctx))) {
      return Optional.empty();
    }
    if (finite(w)) {
      return Optional.of(new Found(canon(Power.of(v, w)), UNKNOWN, List.of()));
    }
    Optional<Integer> above = sign(canon(Sum.of(v, Num.MINUS_ONE)));
    if (above.isEmpty() || above.get() == 0) {
      return Optional.empty();
    }
    boolean grows = above.get() * infSign(w) > 0;
    return Optional.of(
        grows ? new Found(INF, UNKNOWN, List.of()) : new Found(Num.ZERO, ABOVE, List.of()));
  }

  /** {@code b^e} for an exponent free of {@code x} that is not a number, by its sign. */
  private Optional<Found> symbolicPower(Expr b, Expr e, Limit l, int level, List<Line> lines) {
    Optional<Integer> se = sign(e);
    if (se.isEmpty() || se.get() == 0 || !Signs.finite(e)) {
      return Optional.empty();
    }
    Optional<Found> rb = part(b, l, level, lines);
    if (rb.isEmpty()) {
      return Optional.empty();
    }
    Expr v = rb.get().value();
    if (v.equals(INF)) {
      return Optional.of(
          se.get() > 0
              ? new Found(INF, UNKNOWN, List.of())
              : new Found(Num.ZERO, ABOVE, List.of()));
    }
    if (isZero(v) && approach(b, l, rb.get()) == ABOVE) {
      return Optional.of(
          se.get() > 0
              ? new Found(Num.ZERO, ABOVE, List.of())
              : new Found(INF, UNKNOWN, List.of()));
    }
    if (finite(v) && Signs.positive(v, ctx)) {
      return Optional.of(new Found(canon(Power.of(v, e)), UNKNOWN, List.of()));
    }
    return Optional.empty();
  }

  /** The limits at {@code ±∞} of the functions that have one, by the sign of the infinity. */
  private static Expr atInfinity(String name, int s) {
    Expr halfPi = Product.of(Num.of(Rational.of(s, 2)), Constant.PI);
    return switch (name) {
      case "exp" -> s > 0 ? INF : Num.ZERO;
      case "log", "ln", "sqrt", "acosh" -> s > 0 ? INF : null;
      case "atan" -> halfPi;
      case "acot", "sech", "csch" -> Num.ZERO;
      case "tanh", "coth", "sign" -> Num.of(s);
      case "sinh", "cbrt", "asinh", "floor", "ceil", "round", "trunc" -> inf(s);
      case "cosh", "abs" -> INF;
      default -> null;
    };
  }

  private Optional<Found> functionLaw(Apply a, Limit l, int level, List<Line> lines) {
    String name = a.head().name();
    Expr u = a.args().getFirst();
    Optional<Found> ru = part(u, l, level, lines);
    if (ru.isEmpty()) {
      return Optional.empty();
    }
    Expr v = ru.get().value();
    if (infinite(v)) {
      Expr r = atInfinity(name, infSign(v));
      if (r == null) {
        return Optional.empty();
      }
      int approach =
          r.equals(Num.ZERO)
              ? (name.equals("exp") || name.equals("sech")
                  ? ABOVE
                  : name.equals("acot") || name.equals("csch") ? infSign(v) : UNKNOWN)
              : UNKNOWN;
      return Optional.of(new Found(canon(r), approach, List.of()));
    }
    if (!finite(v)) {
      return Optional.empty();
    }
    Symbol y = fresh(a, l.var());
    Expr fy = Apply.of(a.head(), y);
    if (Continuity.at(fy, y, v, ctx, false).filter(List::isEmpty).isPresent()) {
      Expr value = Subst.value(fy, y, v, ctx).orElseThrow();
      if (!finite(value)) {
        return Optional.empty();
      }
      return Optional.of(new Found(value, slope(fy, y, v, approach(u, l, ru.get())), List.of()));
    }
    int s = approach(u, l, ru.get());
    if (isZero(v) && (s == ABOVE || s == BELOW)) {
      switch (name) {
        case "log", "ln" -> {
          return s == ABOVE
              ? Optional.of(new Found(NEG_INF, UNKNOWN, List.of()))
              : Optional.empty();
        }
        case "sign" -> {
          return Optional.of(new Found(Num.of(s), EXACT, List.of()));
        }
        case "csch", "coth" -> {
          return Optional.of(new Found(inf(s), UNKNOWN, List.of()));
        }
        default -> {}
      }
    }
    if ((s == ABOVE || s == BELOW) && v instanceof Num n && n.value() instanceof RealNumeric r) {
      Expr g =
          sideForm(
              a, Limit.of(a, l.var(), l.point(), s > 0 ? Direction.RIGHT : Direction.LEFT), level);
      if (g != null && !g.equals(a) && Free.of(g, l.var())) {
        return Optional.of(new Found(g, EXACT, List.of()));
      }
    }
    if (level < MAX_LEVEL) {
      Expr quotient =
          switch (name) {
            case "tan" -> ratio("sin", "cos", u);
            case "cot" -> ratio("cos", "sin", u);
            case "sec" -> ratio(null, "cos", u);
            case "csc" -> ratio(null, "sin", u);
            default -> null;
          };
      if (quotient != null) {
        Optional<Found> q = of(quotient, l, level + 1);
        q.ifPresent(f -> lines.addAll(f.lines()));
        return q;
      }
    }
    return Optional.empty();
  }

  private static Expr ratio(String top, String bottom, Expr u) {
    Expr below = Power.of(Apply.of(Symbol.of(bottom), u), Num.MINUS_ONE);
    return top == null ? below : Product.of(Apply.of(Symbol.of(top), u), below);
  }

  /**
   * The side from which {@code f(y)} takes its value at {@code y = v} when {@code y} comes from
   * {@code side}: the sign of the derivative there, or of the second where the first is zero.
   */
  private int slope(Expr fy, Symbol y, Expr v, int side) {
    TaylorRules.Derivatives d = new TaylorRules.Derivatives(fy, y, v, ctx);
    if (d.next().isEmpty()) {
      return UNKNOWN;
    }
    for (int k = 1; k <= 2; k++) {
      Optional<Expr> dk = d.next();
      if (dk.isEmpty()) {
        return UNKNOWN;
      }
      Optional<Integer> s = sign(dk.get());
      if (s.isEmpty()) {
        return UNKNOWN;
      }
      if (s.get() != 0) {
        if (k == 2) {
          return side == UNKNOWN ? s.get() : s.get();
        }
        return side == ABOVE || side == BELOW ? s.get() * side : UNKNOWN;
      }
    }
    return UNKNOWN;
  }

  /**
   * A symbol occurring in neither {@code e} nor equal to {@code x}: {@code t}, then {@code t_1}…
   */
  static Symbol fresh(Expr e, Symbol x) {
    Set<String> names = new HashSet<>();
    names(e, names);
    names.add(x.name());
    String name = "t";
    for (int k = 1; names.contains(name); k++) {
      name = "t_" + k;
    }
    return Symbol.of(name);
  }

  private static void names(Expr e, Set<String> out) {
    if (e instanceof Symbol s) {
      out.add(s.name());
    }
    for (Expr o : Operands.of(e)) {
      names(o, out);
    }
  }

  // The exponential form.

  /** {@code u^v} with both in {@code x} as {@code e^(v log u)}. */
  static Optional<Found> exponentialForm(LimitMethods m, Limit l, int level) {
    if (!(l.expr() instanceof Power p)
        || Free.of(p.base(), l.var())
        || Free.of(p.exponent(), l.var())) {
      return Optional.empty();
    }
    Optional<Found> base = m.of(p.base(), l, level);
    if (base.isEmpty()) {
      return Optional.empty();
    }
    Expr b = base.get().value();
    boolean positive =
        b.equals(INF)
            || finite(b) && Signs.positive(b, m.ctx)
            || m.isZero(b) && m.approach(p.base(), l, base.get()) == ABOVE;
    if (!positive) {
      return Optional.empty();
    }
    Expr g = m.canon(Product.of(p.exponent(), Apply.of(Symbol.of("log"), p.base())));
    Optional<Found> r = m.of(g, l, level + 1);
    if (r.isEmpty()) {
      return Optional.empty();
    }
    Expr w = r.get().value();
    Expr v;
    if (w.equals(INF)) {
      v = INF;
    } else if (w.equals(NEG_INF)) {
      v = Num.ZERO;
    } else if (finite(w)) {
      v = m.simplify(Power.of(Constant.E, w));
    } else {
      return Optional.empty();
    }
    return Optional.of(
        m.foundAfter(
            found(v, UNKNOWN, r.get().lines(), line("exponential-value", l.expr(), v)),
            line("exponential-form", l.expr(), g)));
  }

  // 3. Algebraic forms.

  /**
   * The reciprocal functions as quotients: the numerator's function (or none) and the
   * denominator's.
   */
  private static final Map<String, String[]> RECIPROCALS =
      Map.of(
          "csc", new String[] {null, "sin"},
          "sec", new String[] {null, "cos"},
          "cot", new String[] {"cos", "sin"},
          "csch", new String[] {null, "sinh"},
          "sech", new String[] {null, "cosh"},
          "coth", new String[] {"cosh", "sinh"});

  /**
   * The numerator and denominator of a product, by the signs of its integer exponents and with the
   * reciprocal functions written as quotients.
   */
  private static Expr[] fraction(Expr f, Symbol x) {
    List<Expr> num = new ArrayList<>();
    List<Expr> den = new ArrayList<>();
    for (Expr factor : f instanceof Product p ? p.factors() : List.of(f)) {
      // a reciprocal function is a quotient: csc(u) = 1/sin(u), cot(u) = cos(u)/sin(u), …
      Expr base = factor;
      RealNumeric e = Int.ONE;
      if (factor instanceof Power w
          && w.exponent() instanceof Num n
          && n.value() instanceof RealNumeric k) {
        base = w.base();
        e = k;
      }
      String[] parts =
          base instanceof Apply a && a.args().size() == 1 && !Free.of(a, x)
              ? RECIPROCALS.get(a.head().name())
              : null;
      if (parts != null && e.isInteger() && e.signum() > 0) {
        Expr u = ((Apply) base).args().getFirst();
        if (parts[0] != null) {
          num.add(Power.of(Apply.of(Symbol.of(parts[0]), u), Num.of(e)));
        }
        den.add(Power.of(Apply.of(Symbol.of(parts[1]), u), Num.of(e)));
        continue;
      }
      if (factor instanceof Power w
          && w.exponent() instanceof Num n
          && n.value() instanceof RealNumeric k
          && k.signum() < 0
          && !Free.of(w.base(), x)) {
        den.add(Power.of(w.base(), Num.of(k.negate())));
      } else {
        num.add(factor);
      }
    }
    if (den.isEmpty()) {
      return null;
    }
    return new Expr[] {
      num.isEmpty() ? Num.ONE : num.size() == 1 ? num.getFirst() : Product.of(num),
      den.size() == 1 ? den.getFirst() : Product.of(den)
    };
  }

  /**
   * {@code e} with every reciprocal function, and {@code tan} and {@code tanh}, written as a
   * quotient of {@code sin} and {@code cos} or their hyperbolic forms.
   */
  private static Expr quotients(Expr e) {
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>(ops.size());
    for (Expr o : ops) {
      out.add(quotients(o));
    }
    Expr r = Operands.rebuild(e, out);
    String name = r instanceof Apply a ? a.head().name() : "";
    String[] parts =
        name.equals("tan")
            ? new String[] {"sin", "cos"}
            : name.equals("tanh") ? new String[] {"sinh", "cosh"} : RECIPROCALS.get(name);
    if (parts != null && ((Apply) r).args().size() == 1) {
      Apply a = (Apply) r;
      Expr u = a.args().getFirst();
      Expr below = Power.of(Apply.of(Symbol.of(parts[1]), u), Num.MINUS_ONE);
      return parts[0] == null ? below : Product.of(Apply.of(Symbol.of(parts[0]), u), below);
    }
    return r;
  }

  /** A common factor of the numerator and denominator cancelled, or fractions combined. */
  static Optional<Found> cancel(LimitMethods m, Limit l, int level) {
    Expr f = l.expr();
    Context in = m.in(l);
    // the reciprocal functions as quotients of sin, cos, sinh and cosh, so that fractions of them
    // combine (−csc(x) + 1/x is (sin(x) − x)/(x sin(x)))
    Expr c = Algebra.cancel(m.canon(quotients(f)), in);
    if (c.equals(f)) {
      return Optional.empty();
    }
    Line line;
    Expr[] q = fraction(f, l.var());
    Expr common = null;
    if (q != null) {
      Polynomial n = Polynomial.of(q[0], m.ctx.cancellation());
      Polynomial d = Polynomial.of(q[1], m.ctx.cancellation());
      Polynomial g = in.providers().run(new Gcd(n, d), in, Steps.NONE).value();
      if (!g.isConstant()) {
        common = g.toExpr();
      }
    }
    if (common != null) {
      // the cancelled form as the simplifier writes it, for the next limit and the line
      c = Simplifier.simplify(c, in).expr();
      line = line("cancel", f, common, c);
    } else if (f instanceof Sum) {
      line = line("combine", f, c);
    } else {
      return Optional.empty();
    }
    return m.of(c, l, level + 1).map(r -> m.foundAfter(r, line));
  }

  /** A sum of two terms with a square root multiplied above and below by its conjugate. */
  static Optional<Found> conjugate(LimitMethods m, Limit l, int level) {
    Expr f = l.expr();
    Symbol x = l.var();
    List<Expr> factors = f instanceof Product p ? p.factors() : List.of(f);
    for (int i = 0; i < factors.size(); i++) {
      Expr factor = factors.get(i);
      int k = 1;
      Expr s = factor;
      if (factor instanceof Power w && w.exponent().equals(Num.MINUS_ONE)) {
        k = -1;
        s = w.base();
      }
      if (!(s instanceof Sum sum) || sum.terms().size() != 2 || Free.of(s, x) || !hasRoot(s, x)) {
        continue;
      }
      // the root term keeps a positive sign in the conjugate: √u − v → √u + v, v − √u → v + √u
      Expr r = hasRoot(sum.terms().get(0), x) ? sum.terms().get(0) : sum.terms().get(1);
      Expr o = r == sum.terms().get(0) ? sum.terms().get(1) : sum.terms().get(0);
      boolean negative =
          r instanceof Product pr
              && pr.factors().getFirst() instanceof Num c
              && c.value() instanceof RealNumeric cr
              && cr.signum() < 0;
      Expr conj =
          m.canon(
              negative
                  ? Sum.of(o, Product.of(Num.MINUS_ONE, r))
                  : Sum.of(r, Product.of(Num.MINUS_ONE, o)));
      Expr square;
      if (cubeRoot(r, x)) {
        // a − b = (a³ − b³)/(a² + ab + b²): the conjugate is the second factor
        Expr a = negative ? m.canon(Product.of(Num.MINUS_ONE, r)) : r;
        Expr b = negative ? o : m.canon(Product.of(Num.MINUS_ONE, o));
        conj = m.canon(Sum.of(Power.of(a, Num.of(2)), Product.of(a, b), Power.of(b, Num.of(2))));
        Expr cubes = Sum.of(cube(a), Product.of(Num.MINUS_ONE, Power.of(b, Num.of(3))));
        square =
            Algebra.expand(negative ? Product.of(Num.MINUS_ONE, cubes) : cubes, m.in(l)).expr();
      } else {
        square = Algebra.expand(Product.of(s, conj), m.in(l)).expr();
      }
      List<Expr> rest = new ArrayList<>(factors);
      rest.remove(i);
      if (k == 1) {
        rest.add(square);
        rest.add(Power.of(conj, Num.MINUS_ONE));
      } else {
        rest.add(conj);
        rest.add(Power.of(square, Num.MINUS_ONE));
      }
      Expr g = m.canon(productOf(rest));
      Line line = line("conjugate", f, conj, g);
      Optional<Found> decided = m.of(g, l, level + 1);
      if (decided.isPresent()) {
        return Optional.of(m.foundAfter(decided.get(), line));
      }
    }
    return Optional.empty();
  }

  /** Whether the root term {@code r} is a cube root of an expression in {@code x}. */
  private static boolean cubeRoot(Expr r, Symbol x) {
    for (Expr f : r instanceof Product p ? p.factors() : List.of(r)) {
      if (f instanceof Apply a && a.head().name().equals("cbrt") && !Free.of(a, x)) {
        return true;
      }
      if (f instanceof Power w && w.exponent().equals(Num.of(Rational.of(1, 3)))) {
        return !Free.of(w.base(), x);
      }
    }
    return false;
  }

  /** The cube of a term with a cube root among its factors, the root's radicand taken out. */
  private static Expr cube(Expr r) {
    List<Expr> out = new ArrayList<>();
    for (Expr f : r instanceof Product p ? p.factors() : List.of(r)) {
      if (f instanceof Apply a && a.head().name().equals("cbrt")) {
        out.add(a.args().getFirst());
      } else if (f instanceof Power w && w.exponent().equals(Num.of(Rational.of(1, 3)))) {
        out.add(w.base());
      } else {
        out.add(Power.of(f, Num.of(3)));
      }
    }
    return productOf(out);
  }

  private static boolean hasRoot(Expr e, Symbol x) {
    if (e instanceof Power w
        && w.exponent() instanceof Num n
        && n.value() instanceof RealNumeric r
        && !r.isInteger()
        && !Free.of(w.base(), x)) {
      return true;
    }
    if (e instanceof Apply a
        && (a.head().name().equals("sqrt") || a.head().name().equals("cbrt"))
        && !Free.of(a, x)) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (hasRoot(o, x)) {
        return true;
      }
    }
    return false;
  }

  /** A standard limit: {@code shape} with {@code u_ → target} and the body's constant factor. */
  static Method table(String name, String shape, Expr target, Expr value) {
    Pattern pattern = Pattern.parse(shape);
    return (m, l, level) -> {
      Symbol x = l.var();
      List<Expr> constant = new ArrayList<>();
      List<Expr> rest = new ArrayList<>();
      for (Expr factor : l.expr() instanceof Product p ? p.factors() : List.of(l.expr())) {
        (Free.of(factor, x) ? constant : rest).add(factor);
      }
      if (rest.isEmpty()) {
        return Optional.empty();
      }
      Expr body = m.canon(rest.size() == 1 ? rest.getFirst() : Product.of(rest));
      Optional<Bindings> b = Matcher.match(pattern, body, m.in(l).assumptions());
      if (b.isEmpty() || Free.of(b.get().get("u_"), x)) {
        return Optional.empty();
      }
      Optional<Found> lu = m.of(b.get().get("u_"), l, level);
      if (lu.isEmpty() || !lu.get().value().equals(target)) {
        return Optional.empty();
      }
      constant.add(value);
      Expr v = m.simplify(productOf(constant));
      return Optional.of(new Found(v, UNKNOWN, List.of(line(name, l.expr(), v))));
    };
  }

  // 4. At infinity.

  /** The leading term of the growth at {@code ±∞}. */
  static Optional<Found> leadingTerm(LimitMethods m, Limit l, int level) {
    if (!atInfinity(l)) {
      return Optional.empty();
    }
    Symbol x = l.var();
    Expr f = l.expr();
    Expr g =
        l.point().equals(INF)
            ? f
            : Subst.of(f, x, Product.of(Num.MINUS_ONE, x), m.ctx.precision()).orElse(null);
    if (g == null) {
      return Optional.empty();
    }
    Limit atPlus = Limit.of(g, x, INF, Direction.BOTH);
    // far out an absolute value is its argument or its negation, by the argument's sign there, so
    // |x| − x cancels before the growth is read
    g = m.canon(m.signedAbs(g, atPlus, level));
    Optional<Growth> growth = Growth.of(g, x, m.ctx, a -> m.of(a, atPlus, level).map(Found::value));
    if (growth.isEmpty()) {
      return Optional.empty();
    }
    Growth lead = growth.get();
    Optional<Integer> order = lead.order(m.ctx);
    if (order.isEmpty()) {
      return Optional.empty();
    }
    Expr v;
    int approach = UNKNOWN;
    if (order.get() > 0) {
      Optional<Integer> s = m.sign(lead.coef());
      if (s.isEmpty() || s.get() == 0) {
        return Optional.empty();
      }
      v = inf(s.get());
    } else if (order.get() < 0) {
      v = Num.ZERO;
      approach = m.sign(lead.coef()).orElse(UNKNOWN);
    } else {
      v = m.simplify(lead.coef());
    }
    Expr term = m.canon(lead.term(x));
    if (!l.point().equals(INF)) {
      term = Subst.of(term, x, Product.of(Num.MINUS_ONE, x), m.ctx.precision()).orElse(term);
    }
    return Optional.of(
        new Found(v, approach, List.of(line("leading-term", f, term, v, x, l.point()))));
  }

  /**
   * {@code e} with each {@code |u|} replaced by {@code ±u}, the sign of {@code u}'s limit at the
   * point.
   */
  private Expr signedAbs(Expr e, Limit at, int level) {
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty() || Free.of(e, at.var())) {
      return e;
    }
    List<Expr> out = new ArrayList<>(ops.size());
    for (Expr o : ops) {
      out.add(signedAbs(o, at, level));
    }
    Expr r = Operands.rebuild(e, out);
    if (r instanceof Apply a && a.head().name().equals("abs") && a.args().size() == 1) {
      Optional<Found> lu = of(a.args().getFirst(), at, level);
      Optional<Integer> s = lu.flatMap(f -> valueSign(f.value()));
      if (s.isPresent() && s.get() != 0) {
        return Product.of(Num.of(s.get()), a.args().getFirst());
      }
    }
    return r;
  }

  /** {@code x → ±∞} as {@code t → 0⁺} with {@code x = ±1/t}. */
  static Optional<Found> reciprocalPoint(LimitMethods m, Limit l, int level) {
    if (!atInfinity(l)) {
      return Optional.empty();
    }
    Symbol x = l.var();
    Symbol t = fresh(l.expr(), x);
    Expr put = Product.of(Num.of(infSign(l.point())), Power.of(t, Num.MINUS_ONE));
    Optional<Expr> g = Subst.of(l.expr(), x, put, m.ctx.precision());
    if (g.isEmpty()) {
      return Optional.empty();
    }
    Optional<Found> r = m.decide(Limit.of(g.get(), t, Num.ZERO, Direction.RIGHT), level + 1);
    return r.map(
        f ->
            m.foundAfter(
                f,
                line(
                    "reciprocal-point", Relation.of(Relation.Op.EQ, x, m.canon(put)), t, g.get())));
  }

  // Bounded and oscillating factors.

  private static final Set<String> BOUNDED = Set.of("sin", "cos", "atan", "tanh", "sign", "acot");

  private boolean bounded(Expr e, Symbol x) {
    if (e instanceof Apply a
        && a.args().size() == 1
        && BOUNDED.contains(a.head().name())
        && ctx.assumptions().without(x).isReal(a.args().getFirst()) == Tri.TRUE) {
      return true;
    }
    return e instanceof Power w
        && w.exponent() instanceof Num n
        && n.value() instanceof Int k
        && k.signum() > 0
        && bounded(w.base(), x);
  }

  /** A bounded factor times a part that tends to 0. */
  static Optional<Found> squeeze(LimitMethods m, Limit l, int level) {
    if (!(l.expr() instanceof Product p)) {
      return Optional.empty();
    }
    Symbol x = l.var();
    List<Expr> bounded = new ArrayList<>();
    List<Expr> rest = new ArrayList<>();
    for (Expr f : p.factors()) {
      (!Free.of(f, x) && m.bounded(f, x) ? bounded : rest).add(f);
    }
    if (bounded.isEmpty() || rest.isEmpty()) {
      return m.betweenSteps(p, l, level);
    }
    Expr r = m.canon(rest.size() == 1 ? rest.getFirst() : Product.of(rest));
    Optional<Found> lr = m.of(r, l, level);
    if (lr.isEmpty() || !m.isZero(lr.get().value())) {
      return Optional.empty();
    }
    Expr b = m.canon(bounded.size() == 1 ? bounded.getFirst() : Product.of(bounded));
    return Optional.of(
        found(Num.ZERO, UNKNOWN, partLines(lr.get()), line("squeeze", b, r, l.expr())));
  }

  /**
   * A product with one step function {@code s(u)} and a rest of constant sign near the point lies
   * between the products with the step's bounds ({@code u − 1 < floor(u) ≤ u}, …); when those two
   * have one limit, so has the product.
   */
  private Optional<Found> betweenSteps(Product p, Limit l, int level) {
    if (level >= MAX_LEVEL) {
      return Optional.empty();
    }
    Symbol x = l.var();
    Apply step = null;
    List<Expr> rest = new ArrayList<>();
    for (Expr f : p.factors()) {
      if (f instanceof Apply a
          && a.args().size() == 1
          && Set.of("floor", "ceil", "round", "trunc").contains(a.head().name())
          && !Free.of(a, x)
          && step == null) {
        step = a;
      } else {
        rest.add(f);
      }
    }
    if (step == null) {
      return Optional.empty();
    }
    Expr r = canon(productOf(rest));
    Optional<Found> lr = of(r, l, level);
    if (lr.isEmpty() || lr.get().value().equals(Constant.UNDEFINED)) {
      return Optional.empty();
    }
    boolean constantSign =
        infinite(lr.get().value())
            || nonZero(lr.get().value()) && sign(lr.get().value()).isPresent()
            || isZero(lr.get().value()) && Math.abs(approach(r, l, lr.get())) == 1;
    if (!constantSign) {
      return Optional.empty();
    }
    Expr u = step.args().getFirst();
    Expr[] ends =
        switch (step.head().name()) {
          case "floor" -> new Expr[] {Sum.of(u, Num.MINUS_ONE), u};
          case "ceil" -> new Expr[] {u, Sum.of(u, Num.ONE)};
          case "round" ->
              new Expr[] {
                Sum.of(u, Num.of(Rational.of(-1, 2))), Sum.of(u, Num.of(Rational.of(1, 2)))
              };
          default -> new Expr[] {Sum.of(u, Num.MINUS_ONE), Sum.of(u, Num.ONE)};
        };
    Expr low = canon(Product.of(ends[0], r));
    Expr high = canon(Product.of(ends[1], r));
    Optional<Found> a = of(low, l, level + 1);
    Optional<Found> b = of(high, l, level + 1);
    if (a.isEmpty() || b.isEmpty() || a.get().value().equals(Constant.UNDEFINED)) {
      return Optional.empty();
    }
    Expr va = a.get().value();
    Expr vb = b.get().value();
    boolean same =
        va.equals(vb)
            || finite(va) && finite(vb) && isZero(canon(Sum.of(va, Product.of(Num.MINUS_ONE, vb))));
    if (!same) {
      return Optional.empty();
    }
    List<Line> lines = new ArrayList<>(a.get().lines());
    lines.addAll(b.get().lines());
    lines.add(line("squeeze-between", l.expr(), low, high, va));
    return Optional.of(new Found(va, UNKNOWN, lines));
  }

  private static final Set<String> STEPS = Set.of("abs", "sign", "floor", "ceil", "round", "trunc");

  /** {@code c·sin(u)} or {@code c·cos(u)} with {@code u → ±∞}: no limit. */
  static Optional<Found> oscillation(LimitMethods m, Limit l, int level) {
    Symbol x = l.var();
    Expr trig = null;
    for (Expr f : l.expr() instanceof Product p ? p.factors() : List.of(l.expr())) {
      if (Free.of(f, x)) {
        if (!m.nonZero(f)) {
          return Optional.empty();
        }
        continue;
      }
      if (trig != null) {
        return Optional.empty();
      }
      trig = f;
    }
    if (!(trig instanceof Apply a)
        || a.args().size() != 1
        || !Set.of("sin", "cos").contains(a.head().name())
        || m.ctx.assumptions().without(x).isReal(a.args().getFirst()) != Tri.TRUE) {
      return Optional.empty();
    }
    Expr u = a.args().getFirst();
    // a step-valued argument may take only the values where the function repeats itself
    // (cos(2π·floor(x)) is 1), so it does not show an oscillation
    if (hasStep(u)) {
      return Optional.empty();
    }
    Optional<Found> lu = m.of(u, l, level);
    if (lu.isEmpty() || !infinite(lu.get().value())) {
      return Optional.empty();
    }
    return Optional.of(
        found(Constant.UNDEFINED, UNKNOWN, partLines(lu.get()), line("oscillation", l.expr(), u)));
  }

  /** Whether a function with jumps or kinks is applied in {@code e}. */
  private static boolean hasStep(Expr e) {
    if (e instanceof Apply a && STEPS.contains(a.head().name())) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (hasStep(o)) {
        return true;
      }
    }
    return false;
  }

  // 5. L'Hôpital's rule.

  static Optional<Found> lhopital(LimitMethods m, Limit l, int level) {
    Symbol x = l.var();
    Expr f = l.expr();
    List<Expr[]> candidates = new ArrayList<>();
    Expr[] q = fraction(f, x);
    if (q != null) {
      candidates.add(q);
    } else if (f instanceof Product p) {
      // 0·∞: the infinite factors over the reciprocal of the others, then the other way round
      List<Expr> zero = new ArrayList<>();
      List<Expr> infinite = new ArrayList<>();
      for (Expr factor : p.factors()) {
        Optional<Found> r = m.of(factor, l, level);
        if (r.isEmpty()) {
          return Optional.empty();
        }
        (infinite(r.get().value()) ? infinite : zero).add(factor);
      }
      if (zero.isEmpty() || infinite.isEmpty()) {
        return Optional.empty();
      }
      Expr z = m.canon(productOf(zero));
      Expr i = m.canon(productOf(infinite));
      candidates.add(new Expr[] {i, m.canon(Power.of(z, Num.MINUS_ONE))});
      candidates.add(new Expr[] {z, m.canon(Power.of(i, Num.MINUS_ONE))});
    }
    for (Expr[] c : candidates) {
      Optional<Found> r = m.lhopital(c[0], c[1], l, level);
      if (r.isPresent()) {
        return r;
      }
    }
    return Optional.empty();
  }

  private Optional<Found> lhopital(Expr n, Expr d, Limit l, int level) {
    Optional<Found> ln = of(n, l, level);
    Optional<Found> ld = of(d, l, level);
    if (ln.isEmpty() || ld.isEmpty()) {
      return Optional.empty();
    }
    Expr a = ln.get().value();
    Expr b = ld.get().value();
    if (!(isZero(a) && isZero(b) || infinite(a) && infinite(b))) {
      return Optional.empty();
    }
    Symbol x = l.var();
    // The derivative rules ignore jumps, which may gather at the point (floor(1/x) at 0): the rule
    // needs both functions differentiable near it.
    if (hasStep(n) || hasStep(d)) {
      return Optional.empty();
    }
    Context in = in(l);
    Expr dn = Calculus.differentiate(n, x, 1, in).expr();
    Expr dd = Calculus.differentiate(d, x, 1, in).expr();
    if (hasDerivative(dn)
        || hasDerivative(dd)
        || Signs.zero(dd, in) == Tri.TRUE
        || oscillates(dd, l, level)) {
      return Optional.empty();
    }
    // the quotient is in x: simplified without the facts about it
    Expr quotient = Simplifier.simplify(Product.of(dn, Power.of(dd, Num.MINUS_ONE)), in).expr();
    Optional<Found> r = of(quotient, l, level + 1);
    if (r.isEmpty()) {
      return Optional.empty();
    }
    List<Line> lines = new ArrayList<>(partLines(ln.get()));
    lines.addAll(partLines(ld.get()));
    lines.add(line("lhopital", n, d, a, b, dn, dd));
    lines.addAll(r.get().lines());
    return Optional.of(new Found(r.get().value(), r.get().approach(), lines));
  }

  private static final Set<String> CIRCULAR = Set.of("sin", "cos", "tan", "cot", "sec", "csc");

  /**
   * Whether {@code e} has a circular function of an argument that grows without bound, so that its
   * zeros may gather at the point: L'Hôpital's rule needs a denominator's derivative that is not
   * zero near it.
   */
  private boolean oscillates(Expr e, Limit l, int level) {
    if (e instanceof Apply a
        && CIRCULAR.contains(a.head().name())
        && a.args().size() == 1
        && !Free.of(a, l.var())) {
      Optional<Found> u = of(a.args().getFirst(), l, level);
      if (u.isEmpty() || !finite(u.get().value())) {
        return true;
      }
    }
    for (Expr o : Operands.of(e)) {
      if (oscillates(o, l, level)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasDerivative(Expr e) {
    if (e instanceof mathcanvas.engine.expr.Derivative) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (hasDerivative(o)) {
        return true;
      }
    }
    return false;
  }

  // 6. Series expansion.

  static Optional<Found> series(LimitMethods m, Limit l, int level) {
    if (atInfinity(l)) {
      return Optional.empty();
    }
    Symbol x = l.var();
    Expr[] q = fraction(l.expr(), x);
    if (q == null) {
      return Optional.empty();
    }
    Optional<Object[]> top = m.leading(q[0], l);
    Optional<Object[]> bottom = m.leading(q[1], l);
    if (top.isEmpty() || bottom.isEmpty()) {
      return Optional.empty();
    }
    int p = (Integer) top.get()[0];
    int r = (Integer) bottom.get()[0];
    Expr c = (Expr) top.get()[1];
    Expr d = (Expr) bottom.get()[1];
    Expr ratio = m.simplify(Product.of(c, Power.of(d, Num.MINUS_ONE)));
    Expr shift = Sum.of(x, Product.of(Num.MINUS_ONE, l.point()));
    Expr lineTop = m.canon(Product.of(c, Power.of(shift, Num.of(p))));
    Expr lineBottom = m.canon(Product.of(d, Power.of(shift, Num.of(r))));
    Expr v;
    int approach = UNKNOWN;
    if (p == r) {
      v = ratio;
    } else if (p > r) {
      v = Num.ZERO;
      Optional<Integer> s = m.sign(ratio);
      int side = side(l);
      if (s.isPresent() && s.get() != 0) {
        int k = p - r;
        approach = k % 2 == 0 ? s.get() : side == UNKNOWN ? UNKNOWN : s.get() * side;
      }
    } else {
      Optional<Integer> s = m.sign(ratio);
      if (s.isEmpty() || s.get() == 0) {
        return Optional.empty();
      }
      int k = r - p;
      Expr right = inf(s.get());
      Expr left = inf(k % 2 == 0 ? s.get() : -s.get());
      v =
          switch (l.direction()) {
            case RIGHT -> right;
            case LEFT -> left;
            case BOTH -> left.equals(right) ? right : Constant.UNDEFINED;
          };
    }
    return Optional.of(
        new Found(v, approach, List.of(line("series", q[0], lineTop, q[1], lineBottom, v))));
  }

  /** The order and the coefficient of the first term of {@code g}'s expansion that is not 0. */
  private Optional<Object[]> leading(Expr g, Limit l) {
    Symbol x = l.var();
    if (Free.of(g, x)) {
      return Signs.zero(g, ctx) == Tri.FALSE ? Optional.of(new Object[] {0, g}) : Optional.empty();
    }
    TaylorRules.Derivatives d = new TaylorRules.Derivatives(g, x, l.point(), ctx);
    java.math.BigInteger factorial = java.math.BigInteger.ONE;
    for (int k = 0; k <= MAX_ORDER; k++) {
      if (k > 0) {
        factorial = factorial.multiply(java.math.BigInteger.valueOf(k));
      }
      Optional<Expr> v = d.next();
      if (v.isEmpty()) {
        return Optional.empty();
      }
      Tri z = Signs.zero(v.get(), ctx);
      if (z == Tri.UNKNOWN) {
        return Optional.empty();
      }
      if (z == Tri.FALSE) {
        Expr c =
            simplify(Product.of(v.get(), Num.of(Rational.of(java.math.BigInteger.ONE, factorial))));
        return Optional.of(new Object[] {k, c});
      }
    }
    return Optional.empty();
  }
}
