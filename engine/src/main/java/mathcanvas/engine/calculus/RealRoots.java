package mathcanvas.engine.calculus;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.algebra.AlgebraLimitException;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Bindings;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.solve.Solver;

/**
 * The real zeros and poles of a rational function, when the solver's sets are provably complete,
 * for the commands that split an interval at them or look between them (the applications, and
 * definite integrals of rational integrands).
 *
 * @param zeros the real roots of its numerator in lowest terms
 * @param poles the real roots of its denominator in lowest terms
 */
record RealRoots(List<Expr> zeros, List<Expr> poles) {
  /** The zeros and the poles together. */
  List<Expr> both() {
    List<Expr> out = new ArrayList<>(zeros);
    out.addAll(poles);
    return out;
  }

  /**
   * The real zeros and poles of {@code g} in {@code x} when the solver's sets are provably
   * complete: {@code g} a rational function of {@code x} alone with rational coefficients, whose
   * numerator and denominator in lowest terms are polynomials the solver solves exactly or isolates
   * by Sturm's theorem. A constant has neither. With {@code exact}, every zero and pole must also
   * be exact (no decimal). Empty otherwise.
   */
  static Optional<RealRoots> of(Expr g, Symbol x, Context ctx, boolean exact) {
    Expr c = Canonical.of(g, ctx.precision());
    if (!rationalIn(c, x)) {
      return Optional.empty();
    }
    if (Free.of(c, x)) {
      return Optional.of(new RealRoots(List.of(), List.of()));
    }
    Expr cancelled;
    try {
      cancelled = Algebra.cancel(c, ctx.withTrace(false));
    } catch (AlgebraLimitException tooLarge) {
      return Optional.empty();
    }
    Optional<List<Expr>> zeros = realRoots(part(cancelled, false), x, ctx, exact);
    Optional<List<Expr>> poles = realRoots(part(cancelled, true), x, ctx, exact);
    if (zeros.isEmpty() || poles.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new RealRoots(zeros.get(), poles.get()));
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
}
