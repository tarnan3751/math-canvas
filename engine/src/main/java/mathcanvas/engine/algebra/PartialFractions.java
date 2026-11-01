package mathcanvas.engine.algebra;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mathcanvas.engine.Cancellation;
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
    return of(f, x, denominator, Cancellation.NEVER);
  }

  /**
   * Decomposes {@code f} as {@link #of(RationalFunction, Expr, Factorization)} does, checking
   * {@code cancellation} at every factor, every step of its expansion and every step of the
   * extended Euclidean algorithm.
   *
   * @param f the rational function
   * @param x the variable
   * @param denominator the factorization of {@code f}'s denominator, with pairwise coprime bases
   * @param cancellation the request to stop
   * @return the decomposition, or empty when {@code f} has another variable or the bases are not
   *     coprime
   * @throws AlgebraLimitException if a degree exceeds {@link Polynomial#MAX_DEGREE}
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Optional<Decomposition> of(
      RationalFunction f, Expr x, Factorization denominator, Cancellation cancellation) {
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
      cancellation.check();
      Polynomial d = fac.base().pow(fac.multiplicity());
      Polynomial others = Division.exactQuotient(whole, d).orElseThrow();
      Optional<Polynomial> inverse = inverse(others, d, cancellation);
      if (inverse.isEmpty()) {
        return Optional.empty();
      }
      Polynomial a = Division.divide(r.multiply(inverse.get()), d).remainder();
      // the p-adic expansion a = Σ c_j p^j gives Σ c_j / p^(k - j)
      List<Term> part = new ArrayList<>();
      for (int j = 0; j < fac.multiplicity(); j++) {
        cancellation.check();
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

  /**
   * One step of Hermite's reduction (Bronstein, <i>Symbolic Integration I</i>, ch. 2) of {@code
   * a/q^k}, {@code q} squarefree in {@code x} and {@code k ≥ 2}: with {@code a = s·q + t·q′},
   * {@code ∫ a/q^k = −t/((k − 1) q^(k−1)) + ∫ (s + t′/(k − 1))/q^(k−1)}.
   *
   * @param a the numerator
   * @param q the squarefree base
   * @param k its power, at least 2
   * @param x the variable
   * @param cancellation the request to stop
   * @return the numerators over {@code q^(k−1)} of the rational part and of the integral left, or
   *     empty when {@code q} and {@code q′} are not coprime
   */
  public static Optional<Polynomial[]> hermite(
      Polynomial a, Polynomial q, int k, Expr x, Cancellation cancellation) {
    if (k < 2) {
      throw new IllegalArgumentException("Hermite's reduction needs a power of at least 2");
    }
    Polynomial dq = q.derivative(x);
    Optional<Polynomial> inverse = inverse(dq, q, cancellation);
    if (inverse.isEmpty()) {
      return Optional.empty();
    }
    Polynomial t = Division.divide(a.multiply(inverse.get()), q).remainder();
    Polynomial s = Division.exactQuotient(a.subtract(t.multiply(dq)), q).orElseThrow();
    RealNumeric over = Q.divide(Int.ONE, Int.of(k - 1));
    return Optional.of(
        new Polynomial[] {t.multiply(over).negate(), s.add(t.derivative(x).multiply(over))});
  }

  /** The inverse of {@code a} modulo {@code m}, or empty when they are not coprime. */
  private static Optional<Polynomial> inverse(
      Polynomial a, Polynomial m, Cancellation cancellation) {
    Polynomial r0 = m;
    Polynomial r1 = Division.divide(a, m).remainder();
    Polynomial t0 = Polynomial.ZERO;
    Polynomial t1 = Polynomial.ONE;
    while (!r1.isZero()) {
      cancellation.check();
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
