package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.AlgebraLimitException;
import mathcanvas.engine.algebra.Factorization;
import mathcanvas.engine.algebra.PartialFractions;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.algebra.RationalFunction;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.provider.Factor;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Match;
import mathcanvas.engine.rule.Replacement;

/**
 * The rules of the {@code integrate} rule set for rational integrands over ℚ, tried after every
 * other technique: {@code partial-fractions} rewrites {@code ∫ R dx} to the integral of {@code R}'s
 * partial fractions ({@link Algebra#apart}, in Hermite's form {@code Σ cⱼ/pᵢʲ} with {@code deg cⱼ <
 * deg pᵢ}), which linearity splits; {@code hermite} lowers the power of an irreducible base of
 * degree at least 2 by one ({@link PartialFractions#hermite}); and {@code log-part} splits {@code
 * (αq′ + β)/q}, {@code q} an irreducible quadratic, into {@code α log|q|} and {@code β ∫ 1/q},
 * which completing the square finishes. A base of degree 3 or more is finished only when its
 * numerator is a multiple of its derivative, a logarithm the substitution finds; no algebraic
 * extension is taken, so otherwise the integral stays.
 */
final class RationalIntegrals {

  private RationalIntegrals() {}

  /**
   * {@code f} as a rational function of {@code x} alone with exact rational coefficients, and the
   * factorization of its denominator; empty otherwise.
   */
  private record Parts(RationalFunction f, Factorization den) {}

  private static Optional<Parts> parts(Expr f, Symbol x, Context ctx) {
    RationalFunction r;
    try {
      r = RationalFunction.of(Canonical.of(f, ctx.precision()), ctx.cancellation());
    } catch (IllegalArgumentException | AlgebraLimitException notRational) {
      return Optional.empty();
    }
    if (!List.of(x).containsAll(r.variables())
        || !exact(r.numerator())
        || !exact(r.denominator())
        || r.denominator().isConstant()) {
      return Optional.empty();
    }
    Factorization den =
        ctx.providers().run(new Factor(r.denominator()), ctx.withTrace(false), Steps.NONE).value();
    return Optional.of(new Parts(r, den));
  }

  private static boolean exact(Polynomial p) {
    return p.coefficients().values().stream()
        .allMatch(c -> c instanceof Int || c instanceof Rational);
  }

  private static Expr expr(Polynomial p) {
    return p.toExpr();
  }

  /** {@code partial-fractions}: {@code ∫ R dx = ∫ apart(R) dx}, when that splits {@code R}. */
  static Optional<Expr> partialFractions(Match m) {
    Expr f = m.bindings().get("f_");
    Symbol x = (Symbol) m.bindings().get("x_");
    Context ctx = m.context().withTrace(false);
    if (parts(f, x, ctx).isEmpty()) {
      return Optional.empty();
    }
    Optional<Expr> split;
    try {
      split = Algebra.apart(f, x, ctx);
    } catch (AlgebraLimitException tooLarge) {
      return Optional.empty();
    }
    if (split.isEmpty()
        || !(Canonical.of(split.get(), ctx.precision()) instanceof Sum s)
        || s.equals(Canonical.of(f, ctx.precision()))) {
      return Optional.empty();
    }
    return Optional.of(Integral.indefinite(s, x));
  }

  /** The values {@code pole} names. */
  static final java.util.Set<String> POLE_VALUES = java.util.Set.of("poles");

  /**
   * {@code pole}: {@code ∫[a, b] R dx}, {@code R} a rational function of {@code x} with rational
   * coefficients, is {@code undefined} when a real pole of {@code R} in lowest terms lies in the
   * closed interval, since such a pole is never integrable; the poles are the solver's, complete
   * and exact ({@link RealRoots}), each placed by the sign decision. Empty when no pole is in the
   * interval, or a pole cannot be placed.
   */
  static Optional<Replacement.Valued.Result> pole(Match m) {
    Expr f = m.bindings().get("f_");
    Symbol x = (Symbol) m.bindings().get("x_");
    Expr a = m.bindings().get("a_");
    Expr b = m.bindings().get("b_");
    Context ctx = m.context().withTrace(false);
    if (parts(f, x, ctx).isEmpty()
        || !(LimitMethods.infinite(a) || Definite.bound(a, ctx))
        || !(LimitMethods.infinite(b) || Definite.bound(b, ctx))) {
      return Optional.empty();
    }
    Optional<RealRoots> roots = RealRoots.of(f, x, ctx, true);
    if (roots.isEmpty()) {
      return Optional.empty();
    }
    List<Expr> in = new ArrayList<>();
    for (Expr p : roots.get().poles()) {
      int sa = place(p, a, ctx);
      int sb = place(p, b, ctx);
      if (sa == 2 || sb == 2) {
        return Optional.empty();
      }
      // between the bounds, in either order, or at one
      if (sa * sb <= 0) {
        in.add(p);
      }
    }
    if (in.isEmpty()) {
      return Optional.empty();
    }
    TreeMap<String, Expr> named = new TreeMap<>();
    named.put("poles", new SetExpr(in));
    return Optional.of(new Replacement.Valued.Result(Constant.UNDEFINED, named));
  }

  /** The sign of {@code p − end}: an infinite end is beyond every pole; 2 when undecided. */
  private static int place(Expr p, Expr end, Context ctx) {
    if (LimitMethods.infinite(end)) {
      return end.equals(LimitMethods.INF) ? -1 : 1;
    }
    return Definite.sign(minus(p, end), ctx);
  }

  private static Expr minus(Expr a, Expr b) {
    return Sum.of(a, Product.of(Num.MINUS_ONE, b));
  }

  /**
   * The one base of a proper {@code A/q^k}, {@code q} of degree at least 2 and proven irreducible,
   * with {@code A} over the unit; empty otherwise.
   */
  private record Single(Polynomial numerator, Polynomial base, int power) {}

  private static Optional<Single> single(Expr f, Symbol x, Context ctx) {
    Optional<Parts> p = parts(f, x, ctx);
    if (p.isEmpty()) {
      return Optional.empty();
    }
    Factorization den = p.get().den();
    if (!den.complete() || den.factors().size() != 1) {
      return Optional.empty();
    }
    Factorization.Factor only = den.factors().getFirst();
    Polynomial num = p.get().f().numerator();
    if (only.base().degree() < 2 || num.degree() >= p.get().f().denominator().degree()) {
      return Optional.empty();
    }
    RealNumeric unit = den.unit();
    Polynomial a = num.multiply(divide(Int.ONE, unit));
    return Optional.of(new Single(a, only.base(), only.multiplicity()));
  }

  private static RealNumeric divide(RealNumeric a, RealNumeric b) {
    return (RealNumeric)
        ((Num) Canonical.of(Product.of(Num.of(a), Power.of(Num.of(b), Num.MINUS_ONE)))).value();
  }

  private static Expr over(Polynomial num, Polynomial base, int k) {
    return Product.of(expr(num), Power.of(expr(base), Num.of(-k)));
  }

  /** {@code hermite}: {@code ∫ A/q^k dx}, {@code k ≥ 2}, lowered to {@code q^(k−1)}. */
  static Optional<Expr> hermite(Match m) {
    Symbol x = (Symbol) m.bindings().get("x_");
    Context ctx = m.context().withTrace(false);
    Optional<Single> s = single(m.bindings().get("f_"), x, ctx);
    if (s.isEmpty() || s.get().power() < 2) {
      return Optional.empty();
    }
    Optional<Polynomial[]> r;
    try {
      r =
          PartialFractions.hermite(
              s.get().numerator(), s.get().base(), s.get().power(), x, ctx.cancellation());
    } catch (AlgebraLimitException tooLarge) {
      return Optional.empty();
    }
    if (r.isEmpty()) {
      return Optional.empty();
    }
    int k = s.get().power() - 1;
    Expr rational = over(r.get()[0], s.get().base(), k);
    if (r.get()[1].isZero()) {
      return Optional.of(rational);
    }
    return Optional.of(
        Sum.of(rational, Integral.indefinite(over(r.get()[1], s.get().base(), k), x)));
  }

  /**
   * {@code log-part}: {@code ∫ (αq′ + β)/q dx = α log|q| + β ∫ 1/q dx} for an irreducible quadratic
   * {@code q}, {@code α} and {@code β} not zero; {@code log(q)} when {@code q} has no real root and
   * a positive leading coefficient.
   */
  static Optional<Expr> logPart(Match m) {
    Symbol x = (Symbol) m.bindings().get("x_");
    Context ctx = m.context().withTrace(false);
    Optional<Single> s = single(m.bindings().get("f_"), x, ctx);
    if (s.isEmpty() || s.get().power() != 1 || s.get().base().degree() != 2) {
      return Optional.empty();
    }
    Polynomial q = s.get().base();
    Polynomial a = s.get().numerator();
    Polynomial dq = q.derivative(x);
    if (a.degree() != 1) {
      return Optional.empty();
    }
    RealNumeric alpha = divide(a.coefficient(1), dq.coefficient(1));
    Polynomial beta = a.subtract(dq.multiply(alpha));
    if (beta.isZero()) {
      return Optional.empty();
    }
    // b² − 4ac < 0 with a > 0: q is positive everywhere
    RealNumeric qa = q.coefficient(2);
    Expr discriminant =
        Canonical.of(
            Sum.of(
                Power.of(Num.of(q.coefficient(1)), Num.of(2)),
                Product.of(Num.of(-4), Num.of(qa), Num.of(q.coefficient(0)))));
    boolean positive =
        discriminant instanceof Num d && ((RealNumeric) d.value()).signum() < 0 && qa.signum() > 0;
    Expr log = Apply.of(Symbol.of("log"), positive ? expr(q) : Apply.of(Symbol.of("abs"), expr(q)));
    return Optional.of(
        Sum.of(
            Product.of(Num.of(alpha), log),
            Product.of(expr(beta), Integral.indefinite(Power.of(expr(q), Num.MINUS_ONE), x))));
  }
}
