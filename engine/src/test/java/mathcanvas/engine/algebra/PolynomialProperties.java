package mathcanvas.engine.algebra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.testing.Recheck;
import mathcanvas.engine.testing.Soundness;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Reporter;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.api.statistics.Statistics;

/** The polynomial ring's laws, the conversions, and the soundness of conversion. */
class PolynomialProperties {

  @Provide
  Arbitrary<Polynomial> polys() {
    return PolynomialArbitraries.polynomials(3, 5, 6);
  }

  /**
   * Exact expressions whose exponents are numbers, with {@code π} and {@code e} turned into the
   * symbols {@link #CONSTANTS}. A symbolic exponent is a kernel to the algebra, and evaluating it
   * at random points makes the comparison slow without testing anything more; {@code π} and {@code
   * e} are kernels too, and as symbols they take exact values, so the comparison is exact where an
   * expansion's cancellation would otherwise round past the absolute floor.
   */
  @Provide
  Arbitrary<Expr> exact() {
    return ExprArbitraries.exactExprs()
        .filter(PolynomialProperties::numericExponents)
        .map(PolynomialProperties::constantsAsSymbols);
  }

  @Property(tries = 500)
  void ringLaws(
      @ForAll("polys") Polynomial a, @ForAll("polys") Polynomial b, @ForAll("polys") Polynomial c) {
    assertThat(a.add(b)).isEqualTo(b.add(a));
    assertThat(a.multiply(b)).isEqualTo(b.multiply(a));
    assertThat(a.add(b).add(c)).isEqualTo(a.add(b.add(c)));
    assertThat(a.multiply(b).multiply(c)).isEqualTo(a.multiply(b.multiply(c)));
    assertThat(a.multiply(b.add(c))).isEqualTo(a.multiply(b).add(a.multiply(c)));
    assertThat(a.subtract(a)).isEqualTo(Polynomial.ZERO);
  }

  @Property(tries = 500)
  void powIsRepeatedMultiplication(
      @ForAll("polys") Polynomial a, @ForAll @IntRange(max = 5) int n) {
    Polynomial expected = Polynomial.ONE;
    for (int i = 0; i < n; i++) {
      expected = expected.multiply(a);
    }
    assertThat(a.pow(n)).isEqualTo(expected);
  }

  @Property(tries = 500)
  void convertsBackAndForth(@ForAll("polys") Polynomial a) {
    assertThat(Polynomial.of(a.toExpr())).isEqualTo(a);
  }

  @Property(tries = 300)
  void productRule(@ForAll("polys") Polynomial a, @ForAll("polys") Polynomial b) {
    Symbol x = PolynomialArbitraries.X;
    assertThat(a.multiply(b).derivative(x))
        .isEqualTo(a.derivative(x).multiply(b).add(a.multiply(b.derivative(x))));
  }

  @BeforeProperty
  void begin() {
    Recheck.begin();
  }

  /** Caps the ill-conditioned points a property run skips ({@link Recheck#end}). */
  @AfterProperty
  void end(Reporter reporter) {
    Recheck.end(reporter);
  }

  /**
   * A conversion's expression has the value of the canonical form it came from (the raw form is the
   * canonical form's own property; its inexact evaluation can fall on the other side of a cut). A
   * point where they disagree at 34 digits is rechecked as the rule sets' properties recheck it
   * ({@link Recheck}): at a zero of a factor, the expanded terms cancel all but their rounding.
   */
  @Property(tries = 2000)
  void conversionIsSound(@ForAll("exact") Expr e, @ForAll long seed) {
    Polynomial p;
    try {
      p = Polynomial.of(e);
    } catch (AlgebraLimitException | IllegalArgumentException tooLargeOrUndefined) {
      return;
    }
    List<Map<Symbol, Numeric>> points =
        Soundness.points(SYMBOLS, Assumptions.EMPTY, 3, new Random(seed));
    Expr before = Canonical.of(e);
    Expr after = p.toExpr();
    int compared = 0;
    for (Map<Symbol, Numeric> point : points) {
      try {
        compared += Soundness.check(before, after, List.of(point));
      } catch (ArithmeticException outOfRange) {
        // Soundness.check's modulus underflows on values past BigDecimal's exponent range
        Statistics.label("overflowed points").collect(true);
      } catch (AssertionError disagreement) {
        switch (Recheck.of(before, after, point)) {
          case AGREE -> {
            Statistics.label("rechecked at 100 digits").collect(true);
            compared++;
          }
          case ON_A_CUT -> Statistics.label("within rounding of a cut or a zero").collect(true);
          case ILL_CONDITIONED -> {
            Statistics.label("ill-conditioned").collect(true);
            Recheck.illConditioned();
          }
          case OVERFLOW -> Statistics.label("overflowed points").collect(true);
          case DISAGREE -> throw disagreement;
        }
      }
    }
    Recheck.compared(compared);
    Statistics.label("compared").collect(compared > 0);
    Statistics.label("compared")
        .coverage(coverage -> coverage.check(true).percentage((Double q) -> q > 30));
  }

  static boolean numericExponents(Expr e) {
    if (e instanceof Power w && !(w.exponent() instanceof Num)) {
      return false;
    }
    return Operands.of(e).stream().allMatch(PolynomialProperties::numericExponents);
  }

  /** The symbols {@code π} and {@code e} become. */
  static final List<Symbol> CONSTANTS = List.of(Symbol.of("c"), Symbol.of("d"));

  /** The symbols of the generated expressions. */
  static final List<Symbol> SYMBOLS =
      java.util.stream.Stream.concat(ExprArbitraries.SYMBOLS.stream(), CONSTANTS.stream()).toList();

  static Expr constantsAsSymbols(Expr e) {
    if (e.equals(Constant.PI)) {
      return CONSTANTS.get(0);
    }
    if (e.equals(Constant.E)) {
      return CONSTANTS.get(1);
    }
    List<Expr> ops = Operands.of(e);
    if (ops.isEmpty()) {
      return e;
    }
    return Operands.rebuild(e, ops.stream().map(PolynomialProperties::constantsAsSymbols).toList());
  }
}
