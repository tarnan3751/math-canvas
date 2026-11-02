package mathcanvas.engine.solve;

import static mathcanvas.engine.solve.Chain.bind;
import static mathcanvas.engine.solve.Support.canon;
import static mathcanvas.engine.solve.Support.contains;
import static mathcanvas.engine.solve.Support.equation;
import static mathcanvas.engine.solve.Support.isConstant;
import static mathcanvas.engine.solve.Support.simplify;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.Factoring;
import mathcanvas.engine.algebra.Factorization;
import mathcanvas.engine.algebra.Monomial;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.algebra.RationalFunction;
import mathcanvas.engine.assume.Facts;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprOrder;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.match.Pattern;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Numerics;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Rule;
import mathcanvas.engine.rule.RuleSet;
import mathcanvas.engine.solve.Support.Declined;

/**
 * Trigonometric equations, solved for their general solution ({@code docs/language.md} §14.7):
 * every solution written as {@code o + P·k} for the integer parameter {@code k}, in the session's
 * angle unit.
 *
 * <p>The path applies when every kernel of the equation that holds the unknown {@code x} is a
 * circular function of a linear argument {@code a·x + b}, {@code a} a non-zero rational. The
 * arguments are integer multiples {@code n·u + β} of one base argument {@code u = g·x + b*}, with
 * {@code |n| ≤} {@link #MAX_MULTIPLE} and {@code β} a multiple of a twelfth of a half turn, so that
 * the expansions have exact coefficients. The steps, each the whole equation:
 *
 * <ol>
 *   <li>one function of {@code u} alone: the equation is a polynomial in it; otherwise the rule set
 *       {@link #RULE_SET} writes every function through {@code sin(u)} and {@code cos(u)} by the
 *       angle-addition and multiple-angle formulas ({@code trig-to-sin-cos});
 *   <li>the numerator is factored over ℚ ({@code trig-factor}), and each factor is a polynomial in
 *       one function, or {@code a·sin(u) + b·cos(u) + d} rewritten as one sine of a shifted angle
 *       ({@code trig-auxiliary-angle}), or even in one of the two and written in the other by
 *       {@code sin² + cos² = 1} ({@code trig-pythagorean}), or of total degree at most {@link
 *       #MAX_HALF_ANGLE_DEGREE}, written through {@code t = tan(u/2)} with {@code u} a half turn
 *       checked on its own ({@code trig-half-angle});
 *   <li>each polynomial is solved by the polynomial path, giving basic equations {@code f(u) = c}
 *       ({@code trig-basic}); a non-real {@code c} is dropped, since the solutions are the real
 *       ones;
 *   <li>each basic equation by its inverse function and the period, the families merged (two of
 *       period {@code P} half a period apart are one of period {@code P/2}) and those another holds
 *       dropped, mapped back to {@code x} and their offsets reduced into {@code [0, P)} ({@code
 *       trig-general});
 *   <li>a family on which a denominator of the equation vanishes is removed, a residue at a time
 *       ({@code trig-exclude}).
 * </ol>
 *
 * <p>Nothing here calls itself: the steps run once each, over finitely many factors, roots and
 * residues, so the path terminates. When it does not apply, or reaches a case it cannot decide, it
 * gives nothing and the caller's numeric scan runs as before.
 */
final class TrigEquations {

  /** The largest multiple of the base argument expanded. */
  static final int MAX_MULTIPLE = 4;

  /** The largest total degree in {@code sin} and {@code cos} the half-angle substitution takes. */
  static final int MAX_HALF_ANGLE_DEGREE = 4;

  /** The rule set that writes the circular functions through {@code sin(u)} and {@code cos(u)}. */
  static final String RULE_SET = "solve-trig";

  private static final Set<String> CIRCULAR = Set.of("sin", "cos", "tan", "cot", "sec", "csc");

  private static final Symbol SIN = Symbol.of("sin");
  private static final Symbol COS = Symbol.of("cos");
  private static final Symbol TAN = Symbol.of("tan");
  private static final Symbol ASIN = Symbol.of("asin");
  private static final Symbol ACOS = Symbol.of("acos");
  private static final Symbol ATAN = Symbol.of("atan");

  /** The variable of the polynomial in one function. */
  private static final Symbol T = Symbol.of("t_");

  private static final Num TWO = Num.of(2);
  private static final RealNumeric HALF = Rational.of(1, 2);

  /** The trigonometric path does not apply, or cannot decide: the scan runs instead. */
  private static final class NotApplicable extends RuntimeException {
    private static final long serialVersionUID = 1L;

    NotApplicable() {
      super(null, null, false, false);
    }
  }

  /** A step recorded once the path has succeeded. */
  private record Pending(String rule, Expr after, Bindings bindings) {}

  /** The basic equation {@code f(m·u + shift) = value}. */
  private record Basic(String f, RealNumeric m, Expr shift, Expr value) {}

  /** The solutions {@code offset + q·H·k} of the base angle, {@code H} the half turn. */
  private record Family(Expr offset, RealNumeric q) {}

  /** A kernel's argument as {@code n·u + β}, {@code β = q·H}. */
  private record Multiple(int n, RealNumeric q) {}

  private final Symbol x;
  private final Symbol k;
  private final Context ctx;
  private final MathContext work;
  private final List<Pending> pending = new ArrayList<>();

  /** The half turn in the unit the arguments are read in: {@code π}, or 180 or 200. */
  private Expr half;

  /** The base argument, {@code g·x + b*}. */
  private Expr u;

  private RealNumeric g;
  private Expr bStar;

  private TrigEquations(Symbol x, Symbol k, Context ctx) {
    this.x = x;
    this.k = k;
    this.ctx = ctx;
    this.work = new MathContext(ctx.precision().getPrecision() + 10);
  }

  /**
   * The general solution of {@code e = 0}, its steps recorded on {@code chain}.
   *
   * @param e the equation's two sides subtracted, canonical
   * @param x the unknown
   * @param k the integer parameter
   * @param ctx the context
   * @param chain the steps
   * @return the solutions, each a family in {@code k}, or empty when the path does not apply
   */
  static Optional<List<Expr>> solve(Expr e, Symbol x, Symbol k, Context ctx, Chain chain) {
    if (ctx.numerics() == Numerics.NONE) {
      return Optional.empty();
    }
    Context c =
        ctx.assumptions().facts(k).equals(Facts.NONE)
            ? ctx.withAssumptions(
                ctx.assumptions().with(new Relation(Relation.Op.IN, k, Constant.INTEGERS)))
            : ctx;
    TrigEquations t = new TrigEquations(x, k, c);
    List<Expr> values;
    try {
      values = t.run(e);
    } catch (NotApplicable | Declined | IllegalArgumentException | ArithmeticException no) {
      return Optional.empty();
    }
    for (Pending p : t.pending) {
      chain.step(p.rule(), p.after(), p.bindings());
    }
    return Optional.of(values);
  }

  private List<Expr> run(Expr e) {
    Set<Apply> kernels = new TreeSet<>(ExprOrder.INSTANCE);
    collect(e, kernels);
    if (kernels.isEmpty()) {
      throw new NotApplicable();
    }
    Map<Expr, RealNumeric> slopes = new TreeMap<>(ExprOrder.INSTANCE);
    Map<Expr, Expr> offsets = new TreeMap<>(ExprOrder.INSTANCE);
    for (Apply a : kernels) {
      Expr arg = a.args().getFirst();
      Polynomial p = Polynomial.of(arg, ctx.cancellation());
      if (p.degree(x) != 1) {
        throw new NotApplicable();
      }
      Polynomial slope = p.coefficient(x, 1);
      Expr b = p.coefficient(x, 0).toExpr();
      if (!slope.isConstant() || !exact(slope.constantTerm()) || contains(b, x)) {
        throw new NotApplicable();
      }
      slopes.put(arg, slope.constantTerm());
      offsets.put(arg, b);
    }
    half = unit(offsets.values());
    // the base argument: the gcd of the slopes, with the offset of an argument of that slope
    g = null;
    for (RealNumeric a : slopes.values()) {
      g = g == null ? a.abs() : gcd(g, a.abs());
    }
    bStar = Num.ZERO;
    for (Map.Entry<Expr, RealNumeric> s : slopes.entrySet()) {
      if (s.getValue().abs().compareTo(g) == 0) {
        // the base is that argument itself, its slope's sign kept
        g = s.getValue();
        bStar = offsets.get(s.getKey());
        break;
      }
    }
    u = canon(Sum.of(Product.of(Num.of(g), x), bStar), ctx);
    Map<Expr, Multiple> multiples = new TreeMap<>(ExprOrder.INSTANCE);
    boolean single = true;
    String head = null;
    for (Apply a : kernels) {
      Expr arg = a.args().getFirst();
      RealNumeric n = Support.div(slopes.get(arg), g);
      if (!(n instanceof Int ni) || ni.value().abs().intValue() > MAX_MULTIPLE) {
        throw new NotApplicable();
      }
      int nn = ni.value().intValue();
      Expr beta = canon(Exprs.sub(offsets.get(arg), Product.of(Num.of(nn), bStar)), ctx);
      RealNumeric q = rational(canon(Exprs.div(beta, half), ctx));
      if (q == null || !(Support.mul(q, Int.of(12)) instanceof Int)) {
        throw new NotApplicable();
      }
      multiples.put(arg, new Multiple(nn, q));
      single &= nn == 1 && q.signum() == 0 && (head == null || head.equals(a.head().name()));
      head = a.head().name();
    }
    Expr workExpr = e;
    List<Expr> kernelSet;
    // the equation's own denominators, taken before any rewriting, since the canonical form
    // cancels a factor the rewriting makes common (sin(2x)/sin(x) is 2cos(x) after it)
    List<Expr> dens = new ArrayList<>();
    denominators(e, dens);
    Expr sinU = canon(Apply.of(SIN, u), ctx);
    Expr cosU = canon(Apply.of(COS, u), ctx);
    if (single) {
      // a value of the one function is finite, so its own poles are never solutions
      kernelSet = List.of(canon(Apply.of(Symbol.of(head), u), ctx));
    } else {
      kernelSet = List.of(sinU, cosU);
      for (Apply a : kernels) {
        Multiple m = multiples.get(a.args().getFirst());
        String f = a.head().name();
        if (Set.of("tan", "sec").contains(f)) {
          dens.add(expansion("cos", m));
        } else if (Set.of("cot", "csc").contains(f)) {
          dens.add(expansion("sin", m));
        }
      }
      RuleSet rules = toSinCos(multiples);
      workExpr = Rewriter.rewrite(e, rules, ctx.withTrace(false)).expr();
      dens.replaceAll(d -> Rewriter.rewrite(d, rules, ctx.withTrace(false)).expr());
    }
    RationalFunction rf = RationalFunction.of(canon(workExpr, ctx), ctx.cancellation());
    Polynomial n = rf.numerator();
    if (!rf.denominator().isConstant()) {
      dens.add(rf.denominator().toExpr());
    }
    boolean any = false;
    for (List<Expr> vs : List.of(n.variables(), rf.denominator().variables())) {
      for (Expr v : vs) {
        if (kernelSet.contains(v)) {
          any = true;
        } else if (contains(v, x)) {
          throw new NotApplicable();
        }
      }
    }
    if (!any || !n.variables().stream().anyMatch(kernelSet::contains)) {
      throw new NotApplicable();
    }
    if (!single && !canon(workExpr, ctx).equals(canon(e, ctx))) {
      pending.add(
          new Pending("trig-to-sin-cos", equation(n.toExpr(), Num.ZERO, ctx), bind("u_", u)));
    }
    Factorization f = Factoring.factor(n, Steps.NONE, ctx.cancellation());
    List<Polynomial> bases = new ArrayList<>();
    for (Factorization.Factor fac : f.factors()) {
      if (fac.base().variables().stream().anyMatch(kernelSet::contains)) {
        bases.add(fac.base());
      }
    }
    if (bases.size() > 1) {
      pending.add(new Pending("trig-factor", equation(f.toExpr(), Num.ZERO, ctx), bind()));
    }
    List<Basic> basics = new ArrayList<>();
    for (Polynomial b : bases) {
      basics.addAll(single ? onePolynomial(b, head, kernelSet.getFirst()) : sinCos(b, sinU, cosU));
    }
    // a non-real value of a function has no real angle: dropped, the solutions being real
    basics.removeIf(b -> isConstant(b.value()) && !real(b.value()));
    List<Expr> shown = new ArrayList<>();
    for (Basic b : basics) {
      Expr arg = canon(Sum.of(Product.of(Num.of(b.m()), u), b.shift()), ctx);
      shown.add(equation(Apply.of(Symbol.of(b.f()), arg), b.value(), ctx));
    }
    pending.add(
        new Pending(
            "trig-basic",
            shown.size() == 1 ? shown.getFirst() : canon(new ListExpr(shown), ctx),
            bind()));
    List<Family> families = new ArrayList<>();
    for (Basic b : basics) {
      families.addAll(inverse(b));
    }
    families = merge(families);
    pending.add(new Pending("trig-general", solved(families), bind("k_", k)));
    List<Family> kept = exclude(families, dens);
    if (!kept.equals(families)) {
      pending.add(new Pending("trig-exclude", solved(kept), bind()));
    }
    List<Expr> out = new ArrayList<>();
    for (Family fam : kept) {
      out.add(general(fam));
    }
    return out;
  }

  // Recognition.

  /** The circular kernels of {@code e} that hold {@code x}. */
  private void collect(Expr e, Set<Apply> out) {
    Deque<Expr> todo = new ArrayDeque<>();
    todo.push(e);
    while (!todo.isEmpty()) {
      Expr n = todo.pop();
      if (n instanceof Apply a
          && CIRCULAR.contains(a.head().name())
          && a.args().size() == 1
          && contains(a, x)) {
        out.add(a);
        continue;
      }
      for (Expr op : Operands.of(n)) {
        todo.push(op);
      }
    }
  }

  /**
   * The half turn in the arguments' unit: {@code π} in radians; in degrees or gradians, the
   * session's half turn when every offset is plain-typed (numbers and symbols, so a plain number
   * put in is read in the unit), {@code π} when every offset is closed and holds {@code π} (never a
   * plain number, so read in radians), and otherwise the path does not apply.
   */
  private Expr unit(Iterable<Expr> offsets) {
    if (ctx.numerics().radians()) {
      return Constant.PI;
    }
    boolean plain = true;
    boolean radians = true;
    for (Expr b : offsets) {
      plain &= plainTyped(b);
      radians &= isConstant(b) && holdsPi(b);
    }
    if (plain) {
      return Num.of(ctx.numerics().halfTurn());
    }
    if (radians) {
      return Constant.PI;
    }
    throw new NotApplicable();
  }

  private static boolean plainTyped(Expr e) {
    return switch (e) {
      case Num n -> n.value() instanceof RealNumeric;
      case Symbol _ -> true;
      case Sum s -> s.terms().stream().allMatch(TrigEquations::plainTyped);
      case Product p -> p.factors().stream().allMatch(TrigEquations::plainTyped);
      case Power w ->
          w.exponent() instanceof Num n && n.value() instanceof Int && plainTyped(w.base());
      default -> false;
    };
  }

  private static boolean holdsPi(Expr e) {
    Deque<Expr> todo = new ArrayDeque<>();
    todo.push(e);
    while (!todo.isEmpty()) {
      Expr n = todo.pop();
      if (n.equals(Constant.PI)) {
        return true;
      }
      for (Expr op : Operands.of(n)) {
        todo.push(op);
      }
    }
    return false;
  }

  // Writing through sin(u) and cos(u).

  /** The rule set that writes each circular kernel through {@code sin(u)} and {@code cos(u)}. */
  private RuleSet toSinCos(Map<Expr, Multiple> multiples) {
    Pattern p =
        Pattern.of(Apply.of(Symbol.of("h_"), Symbol.of("v_")))
            .where("h_", h -> h instanceof Symbol s && CIRCULAR.contains(s.name()))
            .where(
                List.of("h_", "v_"),
                (b, a) -> {
                  String f = ((Symbol) b.get("h_")).name();
                  Expr v = b.get("v_");
                  return multiples.containsKey(v)
                      && !(v.equals(u) && (f.equals("sin") || f.equals("cos")));
                });
    Rule rule =
        Rule.computed(
            "to-sin-cos",
            p,
            m -> {
              String f = ((Symbol) m.bindings().get("h_")).name();
              Multiple mul = multiples.get(m.bindings().get("v_"));
              Expr s = expansion("sin", mul);
              Expr c = expansion("cos", mul);
              Expr out =
                  switch (f) {
                    case "sin" -> s;
                    case "cos" -> c;
                    case "tan" -> Exprs.div(s, c);
                    case "cot" -> Exprs.div(c, s);
                    case "sec" -> Exprs.inv(c);
                    default -> Exprs.inv(s);
                  };
              return Optional.of(canon(out, ctx));
            });
    return new RuleSet(RULE_SET, List.of(rule));
  }

  /**
   * {@code sin(n·u + β)} or {@code cos(n·u + β)} through {@code sin(u)} and {@code cos(u)}: the
   * multiple angle from {@code cos(nu) + i·sin(nu) = (cos u + i·sin u)ⁿ}, then the angle addition
   * with the exact values at {@code β}.
   */
  private Expr expansion(String f, Multiple m) {
    Expr s = Apply.of(SIN, u);
    Expr c = Apply.of(COS, u);
    Expr sn = Num.ZERO;
    Expr cn = Num.ONE;
    for (int i = 0; i < Math.abs(m.n()); i++) {
      Expr nextS = Sum.of(Product.of(sn, c), Product.of(cn, s));
      Expr nextC = Sum.of(Product.of(cn, c), Exprs.neg(Product.of(sn, s)));
      sn = nextS;
      cn = nextC;
    }
    if (m.n() < 0) {
      sn = Exprs.neg(sn);
    }
    Expr beta = Product.of(Num.of(m.q()), Constant.PI);
    Expr cb = simplify(Apply.of(COS, beta), ctx);
    Expr sb = simplify(Apply.of(SIN, beta), ctx);
    Expr out =
        f.equals("sin")
            ? Sum.of(Product.of(sn, cb), Product.of(cn, sb))
            : Sum.of(Product.of(cn, cb), Exprs.neg(Product.of(sn, sb)));
    return canon(out, ctx);
  }

  // The factors.

  /** A polynomial in the one kernel {@code f(u)}: its roots, each a basic equation. */
  private List<Basic> onePolynomial(Polynomial b, String f, Expr kernel) {
    List<Basic> out = new ArrayList<>();
    for (Expr c : roots(substitute(b, Map.of(kernel, Polynomial.variable(T))))) {
      out.add(new Basic(f, Int.ONE, Num.ZERO, c));
    }
    return out;
  }

  /** A factor in {@code sin(u)} and {@code cos(u)}. */
  private List<Basic> sinCos(Polynomial b, Expr s, Expr c) {
    int ds = b.degree(s);
    int dc = b.degree(c);
    if (dc == 0) {
      return onePolynomial(b, "sin", s);
    }
    if (ds == 0) {
      return onePolynomial(b, "cos", c);
    }
    int total = totalDegree(b, s, c);
    if (total == 1) {
      return auxiliary(b, s, c);
    }
    Polynomial tp = Polynomial.variable(T);
    Polynomial oneMinusT2 = Polynomial.ONE.subtract(tp.pow(2));
    if (even(b, c)) {
      Polynomial p = substitute(b, Map.of(s, tp, c, Polynomial.ONE), c, oneMinusT2);
      pending.add(new Pending("trig-pythagorean", equation(rename(p, s), Num.ZERO, ctx), bind()));
      return basics(p, "sin");
    }
    if (even(b, s)) {
      Polynomial p = substitute(b, Map.of(c, tp, s, Polynomial.ONE), s, oneMinusT2);
      pending.add(new Pending("trig-pythagorean", equation(rename(p, c), Num.ZERO, ctx), bind()));
      return basics(p, "cos");
    }
    if (total > MAX_HALF_ANGLE_DEGREE) {
      throw new NotApplicable();
    }
    return halfAngle(b, s, c, total);
  }

  private List<Basic> basics(Polynomial p, String f) {
    List<Basic> out = new ArrayList<>();
    for (Expr r : roots(p)) {
      out.add(new Basic(f, Int.ONE, Num.ZERO, r));
    }
    return out;
  }

  /** {@code a·sin(u) + b·cos(u) + d = 0} as {@code R·sin(u + φ) = −d}. */
  private List<Basic> auxiliary(Polynomial p, Expr s, Expr c) {
    Expr a = p.coefficient(s, 1).toExpr();
    Expr b = p.coefficient(c, 1).toExpr();
    Expr d = p.coefficient(s, 0).coefficient(c, 0).toExpr();
    if (!isConstant(a) || !isConstant(b) || !isConstant(d)) {
      throw new NotApplicable();
    }
    Expr r = simplify(Exprs.sqrt(Sum.of(Power.of(a, TWO), Power.of(b, TWO))), ctx);
    int sign = sign(a);
    Expr phi = Apply.of(ASIN, Exprs.div(b, r));
    if (sign < 0) {
      phi = Exprs.sub(Constant.PI, phi);
    }
    Expr shift = simplify(Exprs.div(Product.of(phi, half), Constant.PI), ctx);
    Expr arg = canon(Sum.of(u, shift), ctx);
    pending.add(
        new Pending(
            "trig-auxiliary-angle",
            equation(Product.of(r, Apply.of(SIN, arg)), Exprs.neg(d), ctx),
            bind("r_", r)));
    return List.of(new Basic("sin", Int.ONE, shift, simplify(Exprs.div(Exprs.neg(d), r), ctx)));
  }

  /**
   * A factor written through {@code t = tan(u/2)}: {@code sin u = 2t/(1 + t²)}, {@code cos u = (1 −
   * t²)/(1 + t²)}, the numerator multiplied by {@code (1 + t²)^deg}; the half turn, where {@code t}
   * has no value, is checked on its own.
   */
  private List<Basic> halfAngle(Polynomial b, Expr s, Expr c, int degree) {
    Polynomial tp = Polynomial.variable(T);
    Polynomial onePlus = Polynomial.ONE.add(tp.pow(2));
    Polynomial oneMinus = Polynomial.ONE.subtract(tp.pow(2));
    Polynomial out = Polynomial.ZERO;
    int is = b.variables().indexOf(s);
    int ic = b.variables().indexOf(c);
    for (Polynomial.Term term : b.terms()) {
      Monomial mono = term.monomial();
      int es = mono.exponent(is);
      int ec = mono.exponent(ic);
      Polynomial part = Polynomial.constant(term.coefficient());
      for (int v = 0; v < b.variables().size(); v++) {
        if (v != is && v != ic && mono.exponent(v) > 0) {
          part = part.multiply(Polynomial.variable(b.variables().get(v)).pow(mono.exponent(v)));
        }
      }
      part =
          part.multiply(tp.multiply(Int.TWO).pow(es))
              .multiply(oneMinus.pow(ec))
              .multiply(onePlus.pow(degree - es - ec));
      out = out.add(part);
    }
    Expr tanHalf = Apply.of(TAN, canon(Product.of(Num.of(HALF), u), ctx));
    pending.add(
        new Pending(
            "trig-half-angle",
            equation(canonReplace(out.toExpr(), T, tanHalf), Num.ZERO, ctx),
            bind("u_", u)));
    List<Basic> basics = new ArrayList<>();
    for (Expr r : roots(out)) {
      basics.add(new Basic("tan", HALF, Num.ZERO, r));
    }
    Polynomial atHalfTurn =
        substitute(b, Map.of(s, Polynomial.ZERO, c, Polynomial.constant(Int.MINUS_ONE)));
    if (simplify(atHalfTurn.toExpr(), ctx).equals(Num.ZERO)) {
      basics.add(new Basic("cos", Int.ONE, Num.ZERO, Num.MINUS_ONE));
    }
    return basics;
  }

  private List<Expr> roots(Polynomial p) {
    if (p.degree(T) <= 0) {
      if (p.isZero()) {
        throw new NotApplicable();
      }
      return List.of();
    }
    return Univariate.allRoots(p, T, ctx);
  }

  // The basic equations.

  /** The solutions of a basic equation for the base angle, as families. */
  private List<Family> inverse(Basic b) {
    Expr c = simplify(b.value(), ctx);
    String f = b.f();
    if (f.equals("sec") || f.equals("csc")) {
      if (isZero(c)) {
        return List.of();
      }
      c = simplify(Exprs.inv(c), ctx);
      f = f.equals("sec") ? "cos" : "sin";
    }
    if (f.equals("cot")) {
      if (isZero(c)) {
        return shifted(b, List.of(new Family(quarter(), Int.ONE)));
      }
      c = simplify(Exprs.inv(c), ctx);
      f = "tan";
    }
    if (f.equals("tan")) {
      if (contains(c, x) || !isConstant(c) && !realSymbolic(c)) {
        throw new NotApplicable();
      }
      if (isConstant(c) && !real(c)) {
        return List.of();
      }
      return shifted(b, List.of(new Family(inUnit(ATAN, c), Int.ONE)));
    }
    if (!isConstant(c)) {
      throw new NotApplicable();
    }
    if (!real(c) || !withinOne(c)) {
      return List.of();
    }
    Expr alpha = inUnit(f.equals("sin") ? ASIN : ACOS, c);
    if (isZero(Exprs.sub(c, Num.ONE)) || isZero(Sum.of(c, Num.ONE))) {
      // at an end of the range the two families are one, decimal or not
      return shifted(b, List.of(new Family(alpha, Int.TWO)));
    }
    Expr other = f.equals("sin") ? combine(Exprs.sub(half, alpha)) : combine(Exprs.neg(alpha));
    return shifted(b, List.of(new Family(alpha, Int.TWO), new Family(other, Int.TWO)));
  }

  /** The families of {@code m·u + shift} as families of {@code u}. */
  private List<Family> shifted(Basic b, List<Family> fs) {
    List<Family> out = new ArrayList<>();
    for (Family f : fs) {
      Expr o = combine(Exprs.div(Exprs.sub(f.offset(), b.shift()), Num.of(b.m())));
      out.add(new Family(o, Support.div(f.q(), b.m())));
    }
    return out;
  }

  /** {@code h(c)} in the arguments' unit: {@code h(c)·H/π}. */
  private Expr inUnit(Symbol h, Expr c) {
    // a decimal value's inverse is evaluated, as evaluation would (asin(0.5) is 0.5235…)
    Expr value = ctx.numerics().exact(canon(Apply.of(h, c), ctx), ctx.precision());
    return simplify(Exprs.div(Product.of(value, half), Constant.PI), ctx);
  }

  private Expr quarter() {
    return canon(Product.of(Num.of(HALF), half), ctx);
  }

  /** A symbol is real unless assumed otherwise, and so is an expression of real symbols. */
  private boolean realSymbolic(Expr c) {
    return ctx.assumptions().isReal(c) == mathcanvas.engine.assume.Tri.TRUE;
  }

  /** Whether a closed {@code c} is real: its imaginary part vanishes within the working digits. */
  private boolean real(Expr c) {
    Numeric v = value(c);
    BigDecimal re = decimal(v.re());
    BigDecimal im = decimal(v.im());
    return im.abs().compareTo(tolerance().multiply(BigDecimal.ONE.max(re.abs()))) <= 0;
  }

  /** Whether a closed real {@code c} lies in {@code [−1, 1]}, decided exactly at the ends. */
  private boolean withinOne(Expr c) {
    if (isZero(Exprs.sub(c, Num.ONE)) || isZero(Sum.of(c, Num.ONE))) {
      return true;
    }
    BigDecimal re = decimal(value(c).re()).abs();
    if (re.compareTo(BigDecimal.ONE.add(tolerance())) > 0) {
      return false;
    }
    if (re.compareTo(BigDecimal.ONE.subtract(tolerance())) < 0) {
      return true;
    }
    throw new NotApplicable();
  }

  // Families.

  /**
   * The families with the same solutions and none held by another: each offset reduced into its
   * period, two of one period half a period apart merged into one of half the period, and a family
   * another holds dropped.
   */
  private List<Family> merge(List<Family> in) {
    List<Family> fs = new ArrayList<>();
    for (Family f : in) {
      fs.add(new Family(reduce(f.offset(), period(f.q())), f.q()));
    }
    boolean changed = true;
    while (changed) {
      changed = false;
      search:
      for (int i = 0; i < fs.size(); i++) {
        for (int j = 0; j < fs.size(); j++) {
          if (i == j) {
            continue;
          }
          Family a = fs.get(i);
          Family b = fs.get(j);
          RealNumeric d = rational(combine(Exprs.div(Exprs.sub(b.offset(), a.offset()), half)));
          if (d == null) {
            continue;
          }
          RealNumeric ratio = Support.div(a.q(), b.q());
          if (ratio instanceof Int && Support.div(d, b.q()) instanceof Int) {
            // b holds a: a's period is a multiple of b's, and a's offset is one of b's values
            fs.remove(i);
            changed = true;
            break search;
          }
          if (a.q().compareTo(b.q()) == 0
              && Support.div(Support.sub(d, Support.mul(HALF, a.q())), a.q()) instanceof Int) {
            RealNumeric q = Support.mul(HALF, a.q());
            fs.set(i, new Family(reduce(a.offset(), period(q)), q));
            fs.remove(j);
            changed = true;
            break search;
          }
        }
      }
    }
    return fs;
  }

  /**
   * The families on which no denominator vanishes: each family is checked at its residues modulo a
   * full turn, where every denominator repeats; a residue where one is zero is removed.
   */
  private List<Family> exclude(List<Family> fs, List<Expr> dens) {
    if (dens.isEmpty()) {
      return fs;
    }
    List<Family> out = new ArrayList<>();
    for (Family f : fs) {
      RealNumeric m = Support.div(Int.TWO, f.q());
      if (!(m instanceof Int mi) || mi.value().compareTo(BigInteger.valueOf(8)) > 0) {
        throw new NotApplicable();
      }
      int count = mi.value().intValue();
      List<Family> keep = new ArrayList<>();
      for (int j = 0; j < count; j++) {
        Expr point = combine(Sum.of(f.offset(), Product.of(Num.of(j), period(f.q()))));
        if (defined(point, dens)) {
          keep.add(new Family(reduce(point, period(Int.TWO)), Int.TWO));
        }
      }
      if (keep.size() == count) {
        out.add(f);
      } else {
        out.addAll(keep);
      }
    }
    return out;
  }

  /** Whether no denominator vanishes at the base angle {@code θ}. */
  private boolean defined(Expr theta, List<Expr> dens) {
    Expr radians = canon(Exprs.div(Product.of(theta, Constant.PI), half), ctx);
    for (Expr d : dens) {
      Expr at = simplify(atAngle(d, radians), ctx);
      if (at.equals(Num.ZERO)) {
        return false;
      }
      if (!isConstant(at)) {
        if (ctx.assumptions().isNonZero(at) == mathcanvas.engine.assume.Tri.TRUE) {
          continue;
        }
        throw new NotApplicable();
      }
      Optional<Numeric> v = ctx.numerics().value(canon(at, ctx), work);
      if (v.isEmpty()) {
        return false;
      }
      BigDecimal size = decimal(v.get().re()).abs().max(decimal(v.get().im()).abs());
      if (size.compareTo(tolerance().sqrt(work)) <= 0) {
        throw new NotApplicable();
      }
    }
    return true;
  }

  /** {@code e} with every function of {@code u} taken at the closed angle {@code radians}. */
  private Expr atAngle(Expr e, Expr radians) {
    if (e instanceof Apply a && a.args().size() == 1 && a.args().getFirst().equals(u)) {
      return Apply.of(a.head(), radians);
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>(ops.size());
    for (Expr op : ops) {
      out.add(atAngle(op, radians));
    }
    return Operands.rebuild(e, out);
  }

  /**
   * A family of the base angle as a solution for {@code x}: {@code (θ − b*)/g}, its period {@code
   * P/|g|}, the sign of {@code k} being immaterial.
   */
  private Expr general(Family f) {
    Expr p = canon(Exprs.div(period(f.q()), Num.of(g.abs())), ctx);
    Expr o = combine(Exprs.div(Exprs.sub(f.offset(), bStar), Num.of(g)));
    o = reduce(o, p);
    if (Support.hasInexact(o) && isConstant(o)) {
      // an offset from a decimal value is a decimal at the precision, as the value's solutions are
      o = Num.of(ctx.numerics().value(o, ctx.precision()).orElseThrow(NotApplicable::new));
    }
    return simplify(Sum.of(o, Product.of(p, k)), ctx);
  }

  /** The list of equations {@code x = family}, a state of the steps. */
  private Expr solved(List<Family> fs) {
    List<Expr> eqs = new ArrayList<>();
    for (Family f : fs) {
      eqs.add(new Relation(Relation.Op.EQ, x, general(f)));
    }
    return canon(new ListExpr(eqs), ctx);
  }

  private Expr period(RealNumeric q) {
    return canon(Product.of(Num.of(q), half), ctx);
  }

  /**
   * {@code o} reduced into {@code [0, p)} by a whole number of periods, where that is decided: the
   * quotient exact, or its value clear of a whole number; otherwise {@code o} itself.
   */
  private Expr reduce(Expr o, Expr p) {
    if (!isConstant(o)) {
      return o;
    }
    Expr ratio = canon(Exprs.div(o, p), ctx);
    RealNumeric exact = rational(ratio);
    RealNumeric whole;
    if (exact != null) {
      whole = exact.floor();
    } else {
      Optional<Numeric> v = ctx.numerics().value(ratio, work);
      if (v.isEmpty() || !(v.get() instanceof RealNumeric r)) {
        return o;
      }
      BigDecimal value = decimal(r);
      BigDecimal floor = value.setScale(0, RoundingMode.FLOOR);
      BigDecimal frac = value.subtract(floor);
      if (frac.compareTo(tolerance()) < 0
          || frac.compareTo(BigDecimal.ONE.subtract(tolerance())) > 0) {
        return o;
      }
      whole = Int.of(floor.toBigIntegerExact());
    }
    if (whole.signum() == 0) {
      return o;
    }
    return combine(Exprs.sub(o, Product.of(Num.of(whole), p)));
  }

  /**
   * A linear combination of angles in its simplest form: expanded, so that a difference of sums
   * collects termwise (the canonical form does not distribute a sign over a sum), then simplified.
   */
  private Expr combine(Expr e) {
    return simplify(Algebra.expand(canon(e, ctx), ctx.withTrace(false)).expr(), ctx);
  }

  // Helpers.

  private Polynomial substitute(Polynomial p, Map<Expr, Polynomial> images) {
    return substitute(p, images, null, null);
  }

  /**
   * {@code p} with each kernel replaced by its image; with {@code squared} given, its even powers
   * {@code squared^(2j)} become {@code square^j} and its own image is ignored.
   */
  private Polynomial substitute(
      Polynomial p, Map<Expr, Polynomial> images, Expr squared, Polynomial square) {
    Polynomial out = Polynomial.ZERO;
    for (Polynomial.Term term : p.terms()) {
      Polynomial part = Polynomial.constant(term.coefficient());
      for (int v = 0; v < p.variables().size(); v++) {
        int e = term.monomial().exponent(v);
        if (e == 0) {
          continue;
        }
        Expr kernel = p.variables().get(v);
        if (kernel.equals(squared)) {
          part = part.multiply(square.pow(e / 2));
        } else {
          part = part.multiply(images.getOrDefault(kernel, Polynomial.variable(kernel)).pow(e));
        }
      }
      out = out.add(part);
    }
    return out;
  }

  /** Whether every power of {@code kernel} in {@code p} is even. */
  private static boolean even(Polynomial p, Expr kernel) {
    int i = p.variables().indexOf(kernel);
    for (Polynomial.Term term : p.terms()) {
      if (term.monomial().exponent(i) % 2 != 0) {
        return false;
      }
    }
    return true;
  }

  private static int totalDegree(Polynomial p, Expr s, Expr c) {
    int is = p.variables().indexOf(s);
    int ic = p.variables().indexOf(c);
    int max = 0;
    for (Polynomial.Term term : p.terms()) {
      max = Math.max(max, term.monomial().exponent(is) + term.monomial().exponent(ic));
    }
    return max;
  }

  /** A polynomial in {@code t} shown as one in {@code kernel}. */
  private Expr rename(Polynomial p, Expr kernel) {
    return canonReplace(p.toExpr(), T, kernel);
  }

  private Expr canonReplace(Expr e, Symbol from, Expr to) {
    return canon(replace(e, from, to), ctx);
  }

  private static Expr replace(Expr e, Symbol from, Expr to) {
    if (e.equals(from)) {
      return to;
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    List<Expr> out = new ArrayList<>(ops.size());
    for (Expr op : ops) {
      out.add(replace(op, from, to));
    }
    return Operands.rebuild(e, out);
  }

  /** Whether {@code e} simplifies to zero, exact or inexact ({@code 1.0 - 1} is {@code 0.0}). */
  private boolean isZero(Expr e) {
    return simplify(e, ctx) instanceof Num n && n.value().isZero();
  }

  /**
   * The bases of the negative integer powers in {@code e} that hold {@code x}, through its sums and
   * products: the equation's denominators.
   */
  private void denominators(Expr e, List<Expr> out) {
    switch (e) {
      case Sum s -> s.terms().forEach(t -> denominators(t, out));
      case Product p -> p.factors().forEach(f -> denominators(f, out));
      case Power w
          when w.exponent() instanceof Num k && k.value() instanceof Int i && contains(w, x) -> {
        if (i.signum() < 0) {
          out.add(w.base());
        } else {
          denominators(w.base(), out);
        }
      }
      default -> {}
    }
  }

  /** The sign of a closed real {@code e}, decided numerically. */
  private int sign(Expr e) {
    if (isZero(e)) {
      return 0;
    }
    BigDecimal v = decimal(value(e).re());
    if (v.abs().compareTo(tolerance()) <= 0) {
      throw new NotApplicable();
    }
    return v.signum();
  }

  private Numeric value(Expr c) {
    return ctx.numerics().value(canon(c, ctx), work).orElseThrow(NotApplicable::new);
  }

  private BigDecimal decimal(RealNumeric r) {
    return r.toReal(work).value();
  }

  /** The working tolerance, {@code 10^-(p + 5)} of the working digits {@code p + 10}. */
  private BigDecimal tolerance() {
    return BigDecimal.ONE.movePointLeft(ctx.precision().getPrecision() + 5);
  }

  private static boolean exact(RealNumeric r) {
    return r instanceof Int || r instanceof Rational;
  }

  private static RealNumeric rational(Expr e) {
    return e instanceof Num n && (n.value() instanceof Int || n.value() instanceof Rational)
        ? (RealNumeric) n.value()
        : null;
  }

  /** The gcd of two positive rationals: of the numerators over the lcm of the denominators. */
  private static RealNumeric gcd(RealNumeric a, RealNumeric b) {
    BigInteger an = numerator(a);
    BigInteger ad = denominator(a);
    BigInteger bn = numerator(b);
    BigInteger bd = denominator(b);
    BigInteger num = an.gcd(bn);
    BigInteger den = ad.divide(ad.gcd(bd)).multiply(bd);
    return Rational.of(num, den);
  }

  private static BigInteger numerator(RealNumeric r) {
    return r instanceof Int i ? i.value() : ((Rational) r).num();
  }

  private static BigInteger denominator(RealNumeric r) {
    return r instanceof Int ? BigInteger.ONE : ((Rational) r).den();
  }
}
