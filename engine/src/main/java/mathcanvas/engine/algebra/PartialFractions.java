package mathcanvas.engine.algebra;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.RealNumeric;

/**
 * Partial fractions over ℚ of a rational function in one variable.
 *
 * <p>The polynomial part is split off by division. The denominator is factored, {@code u·∏ pᵢ^kᵢ}
 * with the {@code pᵢ} pairwise coprime; the numerator over each {@code Dᵢ = pᵢ^kᵢ} is {@code r·u⁻¹·
 * (D/Dᵢ)⁻¹ mod Dᵢ}, the inverse by the extended Euclidean algorithm (Geddes, Czapor and Labahn, ch.
 * 6); and a repeated factor's numerator is expanded {@code pᵢ}-adically, by repeated division, into
 * {@code Σ cⱼ/pᵢ^j} with {@code deg cⱼ < deg pᵢ} (the Hermite form; Bronstein, <i>Symbolic
 * Integration I</i>, ch. 2). An incomplete factorization of the denominator gives a valid, coarser
 * decomposition.
 */
public final class PartialFractions {

  /**
   * One term {@code numerator / base^power}.
   *
   * @param numerator of lower degree than the base
   * @param base an irreducible (or unsplit) factor of the denominator
   * @param power its power, at least 1
   */
  public record Term(Polynomial numerator, Polynomial base, int power) {}

  /**
   * A decomposition {@code polynomial + Σ terms}.
   *
   * @param polynomial the polynomial part
   * @param terms the fractions, in the order of the denominator's factors and then of increasing
   *     power
   */
  public record Decomposition(Polynomial polynomial, List<Term> terms) {

    /** Copies the terms. */
    public Decomposition {
      terms = List.copyOf(terms);
    }
  }

  private PartialFractions() {}

  /**
   * Decomposes {@code f}, a rational function in the single variable {@code x}.
   *
   * @param f the rational function
   * @param x the variable
   * @param denominator the factorization of {@code f}'s denominator, with pairwise coprime bases
   * @return the decomposition, or empty when {@code f} has another variable or the bases are not
   *     coprime
   * @throws AlgebraLimitException if a degree exceeds {@link Polynomial#MAX_DEGREE}
   */
  public static Optional<Decomposition> of(RationalFunction f, Expr x, Factorization denominator) {
    AlgebraLimitException.checkDegree(f.numerator(), f.denominator());
    if (!List.of(x).containsAll(f.variables())) {
      return Optional.empty();
    }
    Division.Result qr = Division.divide(f.numerator(), f.denominator());
    Polynomial r = qr.remainder().multiply(Q.divide(Int.ONE, denominator.unit()));
    List<Term> terms = new ArrayList<>();
    if (r.isZero()) {
      return Optional.of(new Decomposition(qr.quotient(), terms));
    }
    Polynomial whole = f.denominator().multiply(Q.divide(Int.ONE, denominator.unit()));
    for (Factorization.Factor fac : denominator.factors()) {
      Polynomial d = fac.base().pow(fac.multiplicity());
      Polynomial others = Division.exactQuotient(whole, d).orElseThrow();
      Optional<Polynomial> inverse = inverse(others, d);
      if (inverse.isEmpty()) {
        return Optional.empty();
      }
      Polynomial a = Division.divide(r.multiply(inverse.get()), d).remainder();
      // the p-adic expansion a = Σ c_j p^j gives Σ c_j / p^(k - j)
      List<Term> part = new ArrayList<>();
      for (int j = 0; j < fac.multiplicity(); j++) {
        Division.Result step = Division.divide(a, fac.base());
        if (!step.remainder().isZero()) {
          part.add(new Term(step.remainder(), fac.base(), fac.multiplicity() - j));
        }
        a = step.quotient();
      }
      terms.addAll(part.reversed());
    }
    return Optional.of(new Decomposition(qr.quotient(), terms));
  }

  /** The inverse of {@code a} modulo {@code m}, or empty when they are not coprime. */
  private static Optional<Polynomial> inverse(Polynomial a, Polynomial m) {
    Polynomial r0 = m;
    Polynomial r1 = Division.divide(a, m).remainder();
    Polynomial t0 = Polynomial.ZERO;
    Polynomial t1 = Polynomial.ONE;
    while (!r1.isZero()) {
      Division.Result qr = Division.divide(r0, r1);
      Polynomial r2 = qr.remainder();
      Polynomial t2 = t0.subtract(qr.quotient().multiply(t1));
      r0 = r1;
      r1 = r2;
      t0 = t1;
      t1 = t2;
    }
    if (!r0.isConstant()) {
      return Optional.empty();
    }
    RealNumeric c = r0.leadingCoefficient();
    return Optional.of(Division.divide(t0.multiply(Q.divide(Int.ONE, c)), m).remainder());
  }
}
