package mathcanvas.engine.calculus;

import java.util.List;
import java.util.Optional;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.AlgebraLimitException;
import mathcanvas.engine.algebra.Factorization;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.algebra.RationalFunction;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.provider.Factor;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Match;

/**
 * The rule {@code partial-fractions} of the {@code integrate} rule set: {@code ∫ R dx}, {@code R} a
 * rational function of the variable alone with rational coefficients, is rewritten to the integral
 * of its partial fractions ({@link Algebra#apart}, in Hermite's form {@code Σ cⱼ/pᵢʲ} with {@code
 * deg cⱼ < deg pᵢ}), which linearity splits and the table finishes. No algebraic extension is
 * taken: a factor that does not split over ℚ stays a factor.
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
}
