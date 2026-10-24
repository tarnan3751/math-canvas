package mathcanvas.engine.algebra;

import java.math.BigInteger;
import java.util.List;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;

/**
 * A quotient of polynomials in lowest terms: the numerator and denominator have gcd 1, and the
 * denominator is primitive over ℤ with a positive sign ({@link Gcd#sign}), so the numerator carries
 * the rational factor. {@code 0} is {@code 0/1}. The factories return this normal form, so equal
 * rational functions are equal records; the constructor checks the denominator only, since checking
 * the gcd would double the cost of every operation.
 *
 * @param numerator the numerator
 * @param denominator the denominator, not zero
 */
public record RationalFunction(Polynomial numerator, Polynomial denominator) {

  /**
   * Checks the denominator.
   *
   * @throws IllegalArgumentException if the denominator is zero or not primitive with a positive
   *     sign ({@link Gcd#sign})
   */
  public RationalFunction {
    if (denominator.isZero()) {
      throw new IllegalArgumentException("a zero denominator");
    }
    if (!Gcd.primitivePart(denominator).equals(denominator)) {
      throw new IllegalArgumentException("the denominator is not primitive: " + denominator);
    }
  }

  /**
   * Returns {@code num/den} in lowest terms: both are divided by their gcd, and the denominator's
   * content and sign move to the numerator.
   *
   * @param num the numerator
   * @param den the denominator
   * @return the rational function
   * @throws ArithmeticException if {@code den} is zero
   * @throws AlgebraLimitException if a degree exceeds {@link Polynomial#MAX_DEGREE}
   */
  public static RationalFunction of(Polynomial num, Polynomial den) {
    if (den.isZero()) {
      throw new ArithmeticException("division by the zero polynomial");
    }
    if (num.isZero()) {
      return new RationalFunction(Polynomial.ZERO, Polynomial.ONE);
    }
    Polynomial g = Gcd.gcd(num, den);
    Polynomial n = Division.exactQuotient(num, g).orElseThrow();
    Polynomial d = Division.exactQuotient(den, g).orElseThrow();
    Polynomial pd = Gcd.primitivePart(d);
    RealNumeric c = Q.divide(d.leadingCoefficient(), pd.leadingCoefficient());
    return new RationalFunction(n.multiply(Q.divide(Int.ONE, c)), pd);
  }

  /**
   * Converts an expression over a common denominator, collecting kernels as {@link Polynomial#of}
   * does, except that a negative power goes below the line: {@code b^-n} is {@code 1/b^n}, and
   * {@code b^(-p/q)} is {@code 1/(b^(1/q))^p}.
   *
   * @param e the expression
   * @return the rational function in lowest terms
   * @throws IllegalArgumentException if {@code e} contains a factor that does not commute or a node
   *     that is refused as a kernel ({@link Polynomial#of(Expr)})
   * @throws AlgebraLimitException if a polynomial would have more than {@link Polynomial#MAX_TERMS}
   *     terms or a degree above {@link Polynomial#MAX_DEGREE}
   */
  public static RationalFunction of(Expr e) {
    Expr c = Canonical.of(e);
    if (!Canonical.commutes(c)) {
      throw new IllegalArgumentException("a rational function's variables commute: " + c);
    }
    return convert(c);
  }

  private static RationalFunction convert(Expr e) {
    return switch (e) {
      case Sum s -> {
        RationalFunction r = convert(s.terms().getFirst());
        for (Expr t : s.terms().subList(1, s.terms().size())) {
          r = r.add(convert(t));
        }
        yield r;
      }
      case Product f -> {
        RationalFunction r = convert(f.factors().getFirst());
        for (Expr t : f.factors().subList(1, f.factors().size())) {
          r = r.multiply(convert(t));
        }
        yield r;
      }
      // a power of a number that the canonical form left unfolded is a kernel
      case Power w when w.base() instanceof Num -> polynomial(Polynomial.of(e));
      case Power w
          when w.exponent() instanceof Num n
              && n.value() instanceof Int k
              && k.value().bitLength() < 31 -> {
        int m = k.value().intValue();
        RationalFunction b = convert(w.base());
        yield m >= 0 ? b.pow(m) : b.reciprocal().pow(-m);
      }
      case Power w
          when w.exponent() instanceof Num n
              && n.value() instanceof Rational r
              && r.num().bitLength() < 31 -> {
        int p = r.num().intValue();
        Expr kernel =
            Canonical.of(Power.of(w.base(), Num.of(Rational.of(BigInteger.ONE, r.den()))));
        RationalFunction b = polynomial(Polynomial.of(kernel));
        yield p >= 0 ? b.pow(p) : b.reciprocal().pow(-p);
      }
      // every other kind is a kernel, or a polynomial in kernels
      default -> polynomial(Polynomial.of(e));
    };
  }

  private static RationalFunction polynomial(Polynomial p) {
    return of(p, Polynomial.ONE);
  }

  /**
   * Returns the canonical expression {@code numerator · denominator⁻¹}.
   *
   * @return the expression
   */
  public Expr toExpr() {
    return Canonical.of(raw());
  }

  /** The raw quotient, for a caller that canonicalises under its own precision. */
  Expr raw() {
    if (denominator.equals(Polynomial.ONE)) {
      return numerator.raw();
    }
    return Product.of(numerator.raw(), Power.of(denominator.raw(), Num.MINUS_ONE));
  }

  /**
   * Returns {@code this + other}, over the lcm of the denominators.
   *
   * @param other the addend
   * @return the sum in lowest terms
   */
  public RationalFunction add(RationalFunction other) {
    Polynomial l = Gcd.lcm(denominator, other.denominator);
    Polynomial a = Division.exactQuotient(l, denominator).orElseThrow();
    Polynomial b = Division.exactQuotient(l, other.denominator).orElseThrow();
    return of(numerator.multiply(a).add(other.numerator.multiply(b)), l);
  }

  /**
   * Returns {@code this · other}.
   *
   * @param other the multiplier
   * @return the product in lowest terms
   */
  public RationalFunction multiply(RationalFunction other) {
    return of(numerator.multiply(other.numerator), denominator.multiply(other.denominator));
  }

  /**
   * Returns {@code 1/this}.
   *
   * @return the reciprocal
   * @throws ArithmeticException if this is zero
   */
  public RationalFunction reciprocal() {
    return of(denominator, numerator);
  }

  /**
   * Returns {@code this^n}.
   *
   * @param n the exponent, at least 0
   * @return the power; the numerator and denominator stay coprime, so nothing is reduced
   */
  public RationalFunction pow(int n) {
    return new RationalFunction(numerator.pow(n), denominator.pow(n));
  }

  /**
   * Returns the kernels of the numerator and denominator together.
   *
   * @return the variables, sorted
   */
  public List<Expr> variables() {
    return Polynomial.union(numerator.variables(), denominator.variables());
  }
}
