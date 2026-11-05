package mathcanvas.engine.algebra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.testing.Inputs;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;

/** Division identities, the gcd's defining properties and its normal form, and Yun's output. */
class GcdProperties {

  @BeforeProperty
  void begin() {
    BoundsTally.begin();
  }

  /** Caps the tries a run skips past the algebra's bounds ({@link BoundsTally}). */
  @AfterProperty
  void end() {
    BoundsTally.end();
  }

  @Provide
  Arbitrary<Polynomial> univariate() {
    return PolynomialArbitraries.univariate(8);
  }

  @Provide
  Arbitrary<Polynomial> polys() {
    return PolynomialArbitraries.polynomials(3, 4, 5);
  }

  /**
   * Products of factors of degree at most 2 in three variables: the subresultant PRS costs grow
   * quickly with the degree in several variables, so larger products would make the run slow.
   */
  @Provide
  Arbitrary<Polynomial> products() {
    return PolynomialArbitraries.products(3, 2);
  }

  @Provide
  Arbitrary<Polynomial> bivariateProducts() {
    return PolynomialArbitraries.products(2, 3);
  }

  @Provide
  Arbitrary<Polynomial> univariateProducts() {
    return PolynomialArbitraries.products(1, 4);
  }

  @Property(tries = 500)
  void divisionWithRemainder(
      @ForAll("univariate") Polynomial a, @ForAll("univariate") Polynomial b) {
    Division.Result r = Division.divide(a, b);
    assertThat(r.quotient().multiply(b).add(r.remainder())).isEqualTo(a);
    assertThat(r.remainder().totalDegree()).isLessThan(Math.max(1, b.totalDegree()));
  }

  @Property(tries = 300)
  void pseudoDivision(@ForAll("polys") Polynomial a, @ForAll("polys") Polynomial b) {
    if (b.isZero()) {
      return;
    }
    Expr x = PolynomialArbitraries.X;
    Division.Result r = Division.pseudoDivide(a, b, x);
    int e = Math.max(a.degree(x) - b.degree(x) + 1, 0);
    assertThat(r.quotient().multiply(b).add(r.remainder()))
        .isEqualTo(b.leadingCoefficient(x).pow(e).multiply(a));
    assertThat(
            r.remainder().isZero()
                || r.remainder().degree(x) < Math.max(1, b.degree(x))
                || b.degree(x) == 0)
        .isTrue();
  }

  @Property(tries = 300)
  void exactQuotientUndoesAProduct(@ForAll("polys") Polynomial a, @ForAll("polys") Polynomial b) {
    if (!b.isZero()) {
      assertThat(Division.exactQuotient(a.multiply(b), b)).contains(a);
    }
  }

  @Property(tries = 300)
  void gcdDividesBothAndLeavesCoprimeCofactors(
      @ForAll("products") Polynomial a,
      @ForAll("products") Polynomial b,
      @ForAll("products") Polynomial c) {
    checkGcd(a.multiply(c), b.multiply(c), c);
  }

  @Property(tries = 200)
  void bivariateGcdDividesBothAndLeavesCoprimeCofactors(
      @ForAll("bivariateProducts") Polynomial a,
      @ForAll("bivariateProducts") Polynomial b,
      @ForAll("bivariateProducts") Polynomial c) {
    checkGcd(a.multiply(c), b.multiply(c), c);
  }

  /**
   * Checks the gcd of {@code ac} and {@code bc}, a multiple of {@code c}. A pair whose subresultant
   * remainders pass the algebra's bounds ({@link AlgebraLimitException}: their coefficients grow
   * past {@link Polynomial#MAX_TOTAL_BITS}) has no gcd to check, as {@code gcd} refuses it for
   * every caller; it is counted, under {@link BoundsTally}'s cap.
   */
  private static void checkGcd(Polynomial ac, Polynomial bc, Polynomial c) {
    Polynomial g;
    try {
      g = Gcd.gcd(ac, bc);
    } catch (AlgebraLimitException beyond) {
      BoundsTally.beyond();
      return;
    }
    BoundsTally.checked();
    Polynomial u = Division.exactQuotient(ac, g).orElseThrow();
    Polynomial v = Division.exactQuotient(bc, g).orElseThrow();
    assertThat(Gcd.gcd(u, v).isConstant()).isTrue();
    assertThat(Division.exactQuotient(g, c)).isPresent();
    assertThat(Gcd.gcd(bc, ac)).isEqualTo(g);
    assertThat(Gcd.sign(g)).isPositive();
    assertThat(Gcd.primitivePart(g).multiply(Gcd.rationalContent(g))).isEqualTo(g);
  }

  @Property(tries = 300)
  void univariateGcdAgreesWithTheMultivariatePath(
      @ForAll("univariateProducts") Polynomial a,
      @ForAll("univariateProducts") Polynomial b,
      @ForAll("univariateProducts") Polynomial c) {
    // over x alone ZPoly answers; multiplied by a second variable, the subresultant PRS does
    Polynomial y = Polynomial.variable(PolynomialArbitraries.VARIABLES.get(1));
    Polynomial g;
    Polynomial gy;
    try {
      g = Gcd.gcd(a.multiply(c), b.multiply(c));
      gy = Gcd.gcd(a.multiply(c).multiply(y), b.multiply(c).multiply(y));
    } catch (AlgebraLimitException beyond) {
      BoundsTally.beyond();
      return;
    }
    BoundsTally.checked();
    assertThat(gy).isEqualTo(g.multiply(y));
  }

  @Provide
  Arbitrary<Polynomial> mixed() {
    return PolynomialArbitraries.polynomials(
        List.of(
            mathcanvas.engine.expr.Constant.PI,
            Inputs.canonical("sin(x)"),
            PolynomialArbitraries.X,
            PolynomialArbitraries.VARIABLES.get(1)),
        3,
        4);
  }

  /** The symbols-first order is a monomial order, so the sign multiplies. */
  @Property(tries = 1000)
  void theSignIsMultiplicative(@ForAll("mixed") Polynomial p, @ForAll("mixed") Polynomial q) {
    assertThat(Gcd.sign(p.multiply(q))).isEqualTo(Gcd.sign(p) * Gcd.sign(q));
    if (!p.isZero()) {
      assertThat(Gcd.sign(Gcd.primitivePart(p))).isPositive();
    }
  }

  @Property(tries = 300)
  void squareFreeDecomposition(@ForAll("products") Polynomial p) {
    if (p.isZero()) {
      return;
    }
    SquareFree.Decomposition d;
    try {
      d = SquareFree.of(p);
    } catch (AlgebraLimitException beyond) {
      // Yun's gcds past the algebra's bounds: no decomposition to check
      BoundsTally.beyond();
      return;
    }
    BoundsTally.checked();
    Polynomial back = Polynomial.constant(d.unit());
    List<Polynomial> parts = d.parts();
    for (int i = 0; i < parts.size(); i++) {
      back = back.multiply(parts.get(i).pow(i + 1));
      Polynomial part = parts.get(i);
      assertThat(Gcd.primitivePart(part)).isEqualTo(part);
      if (!part.isConstant()) {
        assertThat(SquareFree.of(part).parts()).containsExactly(part);
      }
      for (int j = 0; j < i; j++) {
        assertThat(Gcd.gcd(part, parts.get(j))).isEqualTo(Polynomial.ONE);
      }
    }
    assertThat(back).isEqualTo(p);
    assertThat(d.unit()).isNotEqualTo(Int.ZERO);
  }
}
