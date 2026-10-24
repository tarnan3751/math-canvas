package mathcanvas.engine.algebra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.testing.Soundness;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** Cancellation keeps the value and lowest terms; partial fractions sum back to the input. */
class RationalFunctionProperties {

  @Provide
  Arbitrary<Polynomial> products() {
    return PolynomialArbitraries.products(2, 2);
  }

  @Provide
  Arbitrary<Polynomial> univariate() {
    return PolynomialArbitraries.products(1, 3);
  }

  private static Expr quotient(Polynomial a, Polynomial b) {
    return Product.of(a.toExpr(), Power.of(b.toExpr(), Num.MINUS_ONE));
  }

  @Property(tries = 300)
  void cancelKeepsTheValueInLowestTerms(
      @ForAll("products") Polynomial a,
      @ForAll("products") Polynomial b,
      @ForAll("products") Polynomial c,
      @ForAll long seed) {
    if (b.isZero() || c.isZero()) {
      return;
    }
    Expr e = quotient(a.multiply(c), b.multiply(c));
    RationalFunction f = RationalFunction.of(e);
    assertThat(f.numerator().multiply(b.multiply(c)))
        .isEqualTo(a.multiply(c).multiply(f.denominator()));
    assertThat(Gcd.gcd(f.numerator(), f.denominator()).isConstant()).isTrue();
    assertThat(Gcd.primitivePart(f.denominator())).isEqualTo(f.denominator());
    List<Map<Symbol, Numeric>> points =
        Soundness.points(PolynomialArbitraries.VARIABLES, Assumptions.EMPTY, 3, new Random(seed));
    Soundness.check(e, Algebra.cancel(e, Context.DEFAULT), points);
  }

  @Property(tries = 300)
  void partialFractionsSumBack(
      @ForAll("univariate") Polynomial a, @ForAll("univariate") Polynomial b) {
    if (b.isZero()) {
      return;
    }
    RationalFunction f = RationalFunction.of(a, b);
    Factorization den = Factoring.factor(f.denominator(), Steps.NONE);
    PartialFractions.Decomposition d =
        PartialFractions.of(f, PolynomialArbitraries.X, den).orElseThrow();
    RationalFunction sum = RationalFunction.of(d.polynomial(), Polynomial.ONE);
    for (PartialFractions.Term t : d.terms()) {
      assertThat(t.numerator().totalDegree()).isLessThan(t.base().totalDegree());
      sum = sum.add(RationalFunction.of(t.numerator(), t.base().pow(t.power())));
    }
    assertThat(sum).isEqualTo(f);
    Expr e = quotient(a, b);
    assertThat(
            RationalFunction.of(
                Algebra.apart(e, PolynomialArbitraries.X, Context.DEFAULT).orElseThrow()))
        .isEqualTo(f);
  }
}
