package mathcanvas.engine.calculus;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import mathcanvas.engine.algebra.AlgebraLimitException;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Path;
import mathcanvas.engine.rule.Rewrite;
import mathcanvas.engine.rule.Rewriter;
import mathcanvas.engine.rule.Trace;
import mathcanvas.engine.simplify.Simplifier;
import mathcanvas.engine.solve.Solver;

/**
 * The applications of integration and the optimisation helpers ({@code docs/language.md} §14.15):
 * {@code arclength}, {@code area}, {@code volume}, {@code shellvolume}, {@code extrema}, {@code
 * maximize} and {@code minimize}. Each sets its quantity up as a textbook would and hands every
 * integral to {@link Calculus#integrate} and every root to {@link Solver#solve}; nothing here
 * integrates by its own means. The steps are rewrites of the whole expression in the rule set
 * {@code applications}, named {@code <command>.<step>}, followed for a single integral by that
 * integral's own steps.
 *
 * <p>The unsigned quantities need {@code a ≤ b}: equal bounds give 0, {@code a > b} is an error,
 * and an order the sign decision ({@link Definite#sign}) cannot settle leaves the call as it is.
 * Zeros and poles are used only where the sets the solver gives are provably complete: the
 * expression is a rational function of the variable alone with rational coefficients, whose
 * numerator and denominator the solver solves or isolates by Sturm's theorem; any other root set,
 * or an undecidable sign, leaves the call with the note {@code <command>.none}, or for an area or
 * volume the integral of the absolute value with the note {@code <command>.crossings}, handed to
 * {@link Calculus#integrate} and then to the numeric path.
 */
public final class Applications {

  private Applications() {}

  /** The rule set of the steps. */
  public static final String RULE_SET = "applications";

  /** The steps the commands record, {@code <command>.<step>}, each with its narration text. */
  public static final Set<String> STEPS =
      Set.of(
          "arclength.formula",
          "arclength.curve",
          "arclength.integrand",
          "arclength.empty",
          "arclength.none",
          "area.formula",
          "area.crossings",
          "area.split",
          "area.whole",
          "area.value",
          "area.empty",
          "area.none",
          "volume.formula",
          "volume.washers",
          "volume.integrand",
          "volume.crossings",
          "volume.split",
          "volume.whole",
          "volume.value",
          "volume.empty",
          "volume.none",
          "shellvolume.formula",
          "shellvolume.crossings",
          "shellvolume.split",
          "shellvolume.whole",
          "shellvolume.value",
          "shellvolume.empty",
          "shellvolume.none",
          "extrema.critical",
          "extrema.no-critical",
          "extrema.classify",
          "extrema.none");

  /**
   * Evaluates an application command whose arguments have been evaluated, its variable bound.
   *
   * @param call the call, {@code arclength(f, x, a, b)} and the others
   * @param context the assumptions (without facts about the bound variable), precision, trace, cap,
   *     providers and numerics
   * @return the value and its trace; the call itself, or the integral it set up, when it cannot be
   *     settled
   * @throws IllegalArgumentException with the language's message for a wrong argument
   */
  public static Rewriter.Result of(Apply call, Context context) {
    Steps steps = new Steps(Canonical.of(call, context.precision()), context);
    String name = call.head().name();
    List<Expr> args = call.args();
    return switch (name) {
      case "arclength" ->
          arclength(steps, args.get(0), variable(args.get(1)), args.get(2), args.get(3));
      case "area" ->
          args.size() == 4
              ? area(steps, args.get(0), Num.ZERO, variable(args.get(1)), args.get(2), args.get(3))
              : area(
                  steps, args.get(0), args.get(1), variable(args.get(2)), args.get(3), args.get(4));
      case "volume" ->
          args.size() == 4
              ? discs(steps, args.get(0), variable(args.get(1)), args.get(2), args.get(3))
              : washers(
                  steps, args.get(0), args.get(1), variable(args.get(2)), args.get(3), args.get(4));
      case "shellvolume" ->
          shells(steps, args.get(0), variable(args.get(1)), args.get(2), args.get(3));
      case "extrema" -> extrema(steps, args.get(0), variable(args.get(1)));
      default -> throw new IllegalStateException("not an application command: " + name);
    };
  }

  private static Symbol variable(Expr e) {
    if (!(e instanceof Symbol s)) {
      throw new IllegalArgumentException("the variable must be a name");
    }
    return s;
  }

  /** The steps of one command, each a rewrite of the whole expression, chained. */
  private static final class Steps {
    final Expr call;
    final Context context;
    final List<Rewrite> rewrites = new ArrayList<>();
    Expr current;

    Steps(Expr call, Context context) {
      this.call = call;
      this.context = context;
      this.current = call;
    }

    String command() {
      return ((Apply) call).head().name();
    }

    /** Records the step {@code <command>.<step>} to {@code after}, with named values. */
    void step(String step, Expr after, TreeMap<String, Expr> values) {
      Expr next = Canonical.of(after, context.precision());
      if (context.traceEnabled()) {
        rewrites.add(
            new Rewrite(
                current,
                next,
                RULE_SET,
                command() + "." + step,
                Path.ROOT,
                Bindings.EMPTY,
                values));
      }
      current = next;
    }

    Rewriter.Result result() {
      return new Rewriter.Result(current, trace());
    }

    Trace trace() {
      return context.traceEnabled() ? new Trace(call, current, rewrites, List.of()) : Trace.NONE;
    }

    /** The call as it is, with the note {@code <command>.none}. */
    Rewriter.Result none() {
      step("none", current, new TreeMap<>());
      return result();
    }

    /** These steps, then an integral's own ({@link Calculus#integrate}) from where they ended. */
    Rewriter.Result then(Rewriter.Result integral) {
      if (!context.traceEnabled()) {
        return new Rewriter.Result(integral.expr(), Trace.NONE);
      }
      return new Rewriter.Result(integral.expr(), Trace.concat(trace(), integral.trace()));
    }
  }

  private static TreeMap<String, Expr> values(Object... pairs) {
    TreeMap<String, Expr> out = new TreeMap<>();
    for (int k = 0; k < pairs.length; k += 2) {
      out.put((String) pairs[k], (Expr) pairs[k + 1]);
    }
    return out;
  }

  private static Expr simplify(Expr e, Context ctx) {
    return Simplifier.simplify(e, ctx.withTrace(false)).expr();
  }

  private static Expr derivative(Expr f, Symbol x, Context ctx) {
    return Calculus.differentiate(f, x, 1, ctx.withTrace(false)).expr();
  }

  private static Expr minus(Expr a, Expr b) {
    return Sum.of(a, Product.of(Num.MINUS_ONE, b));
  }

  /** The order of the bounds: −1 for {@code a < b}, 0 for equal, 1 for {@code a > b}, 2 unknown. */
  private static int order(Expr a, Expr b, Context ctx) {
    int s = Definite.sign(minus(b, a), ctx);
    return s == 2 ? 2 : -s;
  }

  /**
   * The bounds of an unsigned quantity: empty to go on, the value 0 for equal bounds, the call for
   * an order that cannot be decided; an error for {@code a > b}.
   */
  private static Optional<Rewriter.Result> bounds(Steps steps, Expr a, Expr b) {
    int o = order(a, b, steps.context);
    if (o == 1) {
      throw new IllegalArgumentException(steps.command() + " needs a ≤ b");
    }
    if (o == 0) {
      steps.step("empty", Num.ZERO, values());
      return Optional.of(steps.result());
    }
    return o == 2 ? Optional.of(steps.none()) : Optional.empty();
  }

  // arclength

  private static Rewriter.Result arclength(Steps steps, Expr f, Symbol x, Expr a, Expr b) {
    Context ctx = steps.context;
    Optional<Rewriter.Result> early = bounds(steps, a, b);
    if (early.isPresent()) {
      return early.get();
    }
    Expr formula;
    String step = "formula";
    List<Expr> parts =
        f instanceof ListExpr l ? l.items() : f instanceof Vector v ? v.items() : List.of();
    if (!parts.isEmpty()) {
      if (parts.size() != 2) {
        throw new IllegalArgumentException("arclength needs a function or a pair [x(t), y(t)]");
      }
      formula =
          Power.of(
              Sum.of(
                  Power.of(derivative(parts.get(0), x, ctx), Num.of(2)),
                  Power.of(derivative(parts.get(1), x, ctx), Num.of(2))),
              half());
      step = "curve";
    } else {
      formula = Power.of(Sum.of(Num.ONE, Power.of(derivative(f, x, ctx), Num.of(2))), half());
    }
    steps.step(step, Integral.definite(formula, x, a, b), values());
    Expr integrand = simplify(formula, ctx);
    if (!integrand.equals(Canonical.of(formula, steps.context.precision()))) {
      steps.step("integrand", Integral.definite(integrand, x, a, b), values());
    }
    return steps.then(Calculus.integrate(integrand, x, a, b, ctx));
  }

  private static Expr half() {
    return Num.of(Rational.of(1, 2));
  }

  // area, washers and shells: unsigned integrals split at the crossings

  /**
   * The unsigned integral of {@code weight·h} between the bounds, {@code h} changing sign only at
   * its zeros and poles: those strictly inside {@code (a, b)} split the interval and each piece is
   * {@code |∫ weight·h|}, improper at a pole. Empty when the zeros and poles are not provably
   * complete and exact, or their places cannot be decided.
   */
  private static Optional<Expr[]> split(
      Steps steps, Expr h, Expr weight, Symbol x, Expr a, Expr b) {
    Context ctx = steps.context;
    Optional<Roots> roots = roots(h, x, ctx, true);
    if (roots.isEmpty()) {
      return Optional.empty();
    }
    List<Expr> inside = new ArrayList<>();
    for (Expr r : roots.get().both()) {
      int left = Definite.sign(minus(r, a), ctx);
      int right = Definite.sign(minus(r, b), ctx);
      if (left == 2 || right == 2) {
        return Optional.empty();
      }
      if (left > 0 && right < 0) {
        inside.add(r);
      }
    }
    Optional<List<Expr>> sorted = sort(inside, ctx);
    if (sorted.isEmpty()) {
      return Optional.empty();
    }
    List<Expr> ends = new ArrayList<>();
    ends.add(a);
    ends.addAll(sorted.get());
    ends.add(b);
    Expr integrand = simplify(Product.of(weight, h), ctx);
    List<Expr> pieces = new ArrayList<>();
    for (int k = 0; k + 1 < ends.size(); k++) {
      Expr piece =
          Calculus.integrate(integrand, x, ends.get(k), ends.get(k + 1), ctx.withTrace(false))
              .expr();
      pieces.add(Apply.of(Symbol.of("abs"), piece));
    }
    return Optional.of(
        new Expr[] {
          new SetExpr(sorted.get()),
          new ListExpr(pieces),
          Bindings.combine(pieces, Bindings.Origin.SUM)
        });
  }

  private static Rewriter.Result unsigned(
      Steps steps, String formula, Expr factor, Expr h, Expr weight, Symbol x, Expr a, Expr b) {
    Context ctx = steps.context;
    Expr integrand =
        Canonical.of(
            Product.of(factor, Apply.of(Symbol.of("abs"), Product.of(weight, h))), ctx.precision());
    Expr absolute = Integral.definite(integrand, x, a, b);
    Optional<Expr[]> split = split(steps, h, weight, x, a, b);
    if (split.isEmpty()) {
      // the crossings are not all known exactly: the integral of the absolute value, which may
      // still finish when the absolute value simplifies away
      steps.step("crossings", absolute, values());
      return steps.then(Calculus.integrate(integrand, x, a, b, ctx));
    }
    steps.step(formula, absolute, values());
    Expr pieces = Product.of(factor, split.get()[2]);
    if (((SetExpr) split.get()[0]).items().isEmpty()) {
      steps.step("whole", pieces, values("pieces", split.get()[1]));
    } else {
      steps.step("split", pieces, values("roots", split.get()[0], "pieces", split.get()[1]));
    }
    if (factor.equals(Num.ONE)) {
      Expr value = simplify(pieces, ctx);
      if (!value.equals(steps.current)) {
        steps.step("value", value, values());
      }
      // the final pass of a definite integral's value: a number distributed over a sum, a step
      return steps.then(IntegrateResult.apply(steps.current, x, true, ctx));
    }
    // π or 2π times the sum: the pass on the sum, within the value step, since it distributes
    // numbers only
    Expr sum =
        IntegrateResult.apply(simplify(split.get()[2], ctx), x, true, ctx.withTrace(false)).expr();
    Expr value = simplify(Product.of(factor, sum), ctx);
    if (!value.equals(steps.current)) {
      steps.step("value", value, values());
    }
    return steps.result();
  }

  private static Rewriter.Result area(Steps steps, Expr f, Expr g, Symbol x, Expr a, Expr b) {
    Optional<Rewriter.Result> early = bounds(steps, a, b);
    if (early.isPresent()) {
      return early.get();
    }
    return unsigned(steps, "formula", Num.ONE, Canonical.of(minus(f, g)), Num.ONE, x, a, b);
  }

  private static Rewriter.Result discs(Steps steps, Expr f, Symbol x, Expr a, Expr b) {
    Context ctx = steps.context;
    Optional<Rewriter.Result> early = bounds(steps, a, b);
    if (early.isPresent()) {
      return early.get();
    }
    Expr formula = Product.of(Constant.PI, Power.of(f, Num.of(2)));
    steps.step("formula", Integral.definite(formula, x, a, b), values());
    Expr integrand = simplify(formula, ctx);
    if (!integrand.equals(Canonical.of(formula, steps.context.precision()))) {
      steps.step("integrand", Integral.definite(integrand, x, a, b), values());
    }
    return steps.then(Calculus.integrate(integrand, x, a, b, ctx));
  }

  private static Rewriter.Result washers(Steps steps, Expr f, Expr g, Symbol x, Expr a, Expr b) {
    Optional<Rewriter.Result> early = bounds(steps, a, b);
    if (early.isPresent()) {
      return early.get();
    }
    Expr h = Canonical.of(minus(Power.of(f, Num.of(2)), Power.of(g, Num.of(2))));
    return unsigned(steps, "washers", Constant.PI, h, Num.ONE, x, a, b);
  }

  private static Rewriter.Result shells(Steps steps, Expr f, Symbol x, Expr a, Expr b) {
    Optional<Rewriter.Result> early = bounds(steps, a, b);
    if (early.isPresent()) {
      return early.get();
    }
    int s = Definite.sign(a, steps.context);
    if (s == 2) {
      return steps.none();
    }
    if (s < 0) {
      throw new IllegalArgumentException("shellvolume needs a ≥ 0");
    }
    // x ≥ 0 on [a, b], so |x f| = x |f| and the pieces split at the roots of f
    return unsigned(steps, "formula", Product.of(Num.of(2), Constant.PI), f, x, x, a, b);
  }

  // extrema, maximize, minimize

  /**
   * The real zeros and poles of a rational function.
   *
   * @param zeros the real roots of its numerator in lowest terms
   * @param poles the real roots of its denominator in lowest terms
   */
  record Roots(List<Expr> zeros, List<Expr> poles) {

    /** The zeros and the poles together. */
    List<Expr> both() {
      List<Expr> out = new ArrayList<>(zeros);
      out.addAll(poles);
      return out;
    }
  }

  /**
   * The real zeros and poles of {@code g} in {@code x} when the solver's sets are provably
   * complete: {@code g} a rational function of {@code x} alone with rational coefficients, whose
   * numerator and denominator in lowest terms are polynomials the solver solves exactly or isolates
   * by Sturm's theorem. A constant has neither. With {@code exact}, every zero and pole must also
   * be exact (no decimal). Empty otherwise.
   */
  static Optional<Roots> roots(Expr g, Symbol x, Context ctx, boolean exact) {
    Expr c = Canonical.of(g, ctx.precision());
    if (!rationalIn(c, x)) {
      return Optional.empty();
    }
    if (Free.of(c, x)) {
      return Optional.of(new Roots(List.of(), List.of()));
    }
    Expr cancelled;
    try {
      cancelled = mathcanvas.engine.algebra.Algebra.cancel(c, ctx.withTrace(false));
    } catch (AlgebraLimitException tooLarge) {
      return Optional.empty();
    }
    Optional<List<Expr>> zeros = realRoots(part(cancelled, false), x, ctx, exact);
    Optional<List<Expr>> poles = realRoots(part(cancelled, true), x, ctx, exact);
    if (zeros.isEmpty() || poles.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new Roots(zeros.get(), poles.get()));
  }

  /**
   * The real roots of a polynomial {@code p} with rational coefficients, a non-zero constant having
   * none; empty when the solver does not give a finite set or a root's reality is undecided.
   */
  private static Optional<List<Expr>> realRoots(Expr p, Symbol x, Context ctx, boolean exact) {
    if (Free.of(p, x)) {
      return Optional.of(List.of());
    }
    Expr r;
    try {
      r =
          Solver.solve(
                  Relation.of(Relation.Op.EQ, p, Num.ZERO),
                  x,
                  ctx.withTrace(false).withAssumptions(ctx.assumptions().without(x)))
              .expr();
    } catch (IllegalArgumentException | ArithmeticException | AlgebraLimitException refused) {
      return Optional.empty();
    }
    if (!(r instanceof SetExpr s)) {
      return Optional.empty();
    }
    List<Expr> real = new ArrayList<>();
    for (Expr root : s.items()) {
      Optional<Boolean> isReal = Definite.real(root, ctx);
      if (isReal.isEmpty()) {
        return Optional.empty();
      }
      if (!isReal.get()) {
        continue;
      }
      if (exact && hasDecimal(root)) {
        return Optional.empty();
      }
      real.add(root);
    }
    return Optional.of(real);
  }

  /**
   * The numerator of a quotient in lowest terms, the factors that are not negative powers; or, with
   * {@code below}, its denominator, the bases of the negative powers to their positive exponents.
   */
  private static Expr part(Expr e, boolean below) {
    List<Expr> factors = e instanceof Product p ? p.factors() : List.of(e);
    List<Expr> out = new ArrayList<>();
    for (Expr f : factors) {
      if (f instanceof Power w
          && w.exponent() instanceof Num n
          && n.value() instanceof RealNumeric r
          && r.signum() < 0) {
        if (below) {
          out.add(Power.of(w.base(), Num.of(r.negate())));
        }
      } else if (!below) {
        out.add(f);
      }
    }
    return out.isEmpty() ? Num.ONE : Bindings.combine(out, Bindings.Origin.PRODUCT);
  }

  /** Whether {@code e} is built of {@code x}, rationals, sums, products and integer powers. */
  private static boolean rationalIn(Expr e, Symbol x) {
    return switch (e) {
      case Symbol s -> s.equals(x);
      case Num n -> n.value() instanceof Int || n.value() instanceof Rational;
      case Sum _, Product _ -> Operands.of(e).stream().allMatch(o -> rationalIn(o, x));
      case Power p ->
          p.exponent() instanceof Num n && n.value() instanceof Int && rationalIn(p.base(), x);
      default -> false;
    };
  }

  private static boolean hasDecimal(Expr e) {
    if (e instanceof Num n && !n.value().isExact()) {
      return true;
    }
    for (Expr o : Operands.of(e)) {
      if (hasDecimal(o)) {
        return true;
      }
    }
    return false;
  }

  /** The values in ascending order, by the sign decision; empty when a comparison is undecided. */
  private static Optional<List<Expr>> sort(List<Expr> values, Context ctx) {
    List<Expr> out = new ArrayList<>(values);
    for (int i = 1; i < out.size(); i++) {
      for (int j = i; j > 0; j--) {
        int s = Definite.sign(minus(out.get(j), out.get(j - 1)), ctx);
        if (s == 2) {
          return Optional.empty();
        }
        if (s >= 0) {
          break;
        }
        Expr t = out.get(j);
        out.set(j, out.get(j - 1));
        out.set(j - 1, t);
      }
    }
    return Optional.of(out);
  }

  /** {@code f} at {@code x = v}, simplified. */
  private static Expr at(Expr f, Symbol x, Expr v, Context ctx) {
    return simplify(IntegrateRules.replace(f, x, v, ctx), ctx);
  }

  /**
   * The kind of the critical point {@code sorted.get(k)}, among the zeros and poles of the
   * derivative in order: −1 a maximum, 1 a minimum, 0 neither, 2 undecided.
   */
  private static int kind(Expr d, Expr d2, Symbol x, List<Expr> sorted, int k, Context ctx) {
    int curvature = Definite.sign(at(d2, x, sorted.get(k), ctx), ctx);
    if (curvature == 1 || curvature == -1) {
      return curvature;
    }
    // the first-derivative test at rational points either side, nearer than the neighbouring
    // zeros and poles of the derivative, so the sign is the one next to the point
    Optional<Expr> delta = step(sorted, k, ctx);
    if (delta.isEmpty()) {
      return 2;
    }
    Expr r = sorted.get(k);
    int left = Definite.sign(at(d, x, minus(r, delta.get()), ctx), ctx);
    int right = Definite.sign(at(d, x, Sum.of(r, delta.get()), ctx), ctx);
    if (left == 2 || right == 2 || left == 0 || right == 0) {
      return 2;
    }
    return left == right ? 0 : left < 0 ? 1 : -1;
  }

  /**
   * A rational {@code δ = 2^−j} below half the distance from the k-th point to its neighbours (1
   * for a lone point), by their numeric values at 40 digits; empty when they have none.
   */
  private static Optional<Expr> step(List<Expr> sorted, int k, Context ctx) {
    BigDecimal gap = null;
    for (int j : new int[] {k - 1, k + 1}) {
      if (j < 0 || j >= sorted.size()) {
        continue;
      }
      Optional<BigDecimal> d = decimal(minus(sorted.get(j), sorted.get(k)), ctx);
      if (d.isEmpty()) {
        return Optional.empty();
      }
      BigDecimal g = d.get().abs();
      gap = gap == null ? g : gap.min(g);
    }
    if (gap == null) {
      return Optional.of(Num.ONE);
    }
    BigDecimal half = gap.divide(BigDecimal.valueOf(2), new MathContext(40));
    for (int j = 0; j < 4_000; j++) {
      BigDecimal candidate = BigDecimal.ONE.divide(BigDecimal.valueOf(2).pow(j));
      if (candidate.compareTo(half) < 0) {
        return Optional.of(Num.of(Rational.of(BigInteger.ONE, BigInteger.TWO.pow(j))));
      }
    }
    return Optional.empty();
  }

  private static Optional<BigDecimal> decimal(Expr e, Context ctx) {
    Optional<Numeric> v =
        ctx.numerics().value(Canonical.of(e, ctx.precision()), new MathContext(40));
    if (v.isEmpty() || !(v.get() instanceof RealNumeric r)) {
      Expr c = Canonical.of(e, ctx.precision());
      if (c instanceof Num n && n.value() instanceof RealNumeric exact) {
        return Optional.of(toDecimal(exact));
      }
      return Optional.empty();
    }
    return Optional.of(toDecimal(r));
  }

  private static BigDecimal toDecimal(RealNumeric r) {
    return switch (r) {
      case Int i -> new BigDecimal(i.value());
      case Rational q ->
          new BigDecimal(q.num()).divide(new BigDecimal(q.den()), new MathContext(40));
      case Real d -> d.value();
    };
  }

  private static Expr point(Expr x0, Expr y0) {
    return new ListExpr(List.of(x0, y0));
  }

  private static Rewriter.Result extrema(Steps steps, Expr f, Symbol x) {
    Context ctx = steps.context;
    Expr d = derivative(f, x, ctx);
    Optional<Roots> roots = roots(d, x, ctx, false);
    // f′ ≡ 0: every point is critical
    if (roots.isEmpty() || d.equals(Num.ZERO)) {
      return steps.none();
    }
    Optional<List<Expr>> sorted = sort(roots.get().zeros(), ctx);
    Optional<List<Expr>> all = sort(roots.get().both(), ctx);
    if (sorted.isEmpty() || all.isEmpty()) {
      return steps.none();
    }
    if (sorted.get().isEmpty()) {
      steps.step("no-critical", new SetExpr(List.of()), values("derivative", d));
      return steps.result();
    }
    steps.step(
        "critical",
        new SetExpr(sorted.get()),
        values("derivative", d, "points", new SetExpr(sorted.get())));
    Expr d2 = derivative(d, x, ctx);
    List<Expr> minima = new ArrayList<>();
    List<Expr> maxima = new ArrayList<>();
    List<Expr> neither = new ArrayList<>();
    List<Expr> points = new ArrayList<>();
    for (Expr r : sorted.get()) {
      int kind = kind(d, d2, x, all.get(), all.get().indexOf(r), ctx);
      if (kind == 2) {
        return steps.none();
      }
      Expr p = point(r, at(f, x, r, ctx));
      switch (kind) {
        case 1 -> minima.add(p);
        case -1 -> maxima.add(p);
        default -> neither.add(r);
      }
      if (kind != 0) {
        points.add(p);
      }
    }
    steps.step(
        "classify",
        new SetExpr(points),
        values(
            "minima", new SetExpr(minima),
            "maxima", new SetExpr(maxima),
            "neither", new SetExpr(neither)));
    return steps.result();
  }
}
