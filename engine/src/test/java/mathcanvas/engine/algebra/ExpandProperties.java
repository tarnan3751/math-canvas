package mathcanvas.engine.algebra;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.testing.Soundness;
import mathcanvas.engine.testing.TerminationProbe;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/**
 * {@code expand} is sound, idempotent, terminates, and leaves nothing a conversion would expand.
 */
class ExpandProperties {

  /** The exact expressions of {@link PolynomialProperties#exact()}. */
  @Provide
  Arbitrary<Expr> exact() {
    return ExprArbitraries.exactExprs()
        .filter(PolynomialProperties::numericExponents)
        .map(PolynomialProperties::constantsAsSymbols);
  }

  @Provide
  Arbitrary<Expr> any() {
    return ExprArbitraries.exprs();
  }

  @Property(tries = 2000)
  void sound(@ForAll("exact") Expr e, @ForAll long seed) {
    Expr expanded = Algebra.expand(e, Context.DEFAULT).expr();
    List<Map<Symbol, Numeric>> points =
        Soundness.points(PolynomialProperties.SYMBOLS, Assumptions.EMPTY, 3, new Random(seed));
    int compared;
    try {
      compared = Soundness.check(Canonical.of(e), expanded, points);
    } catch (ArithmeticException outOfRange) {
      // Soundness.check's modulus underflows on values past BigDecimal's exponent range
      compared = 0;
    }
    Statistics.label("compared").collect(compared > 0);
    Statistics.label("compared")
        .coverage(coverage -> coverage.check(true).percentage((Double q) -> q > 30));
  }

  @Property(tries = 2000)
  void idempotent(@ForAll("any") Expr e) {
    Expr expanded = Algebra.expand(e, Context.DEFAULT).expr();
    assertThat(Algebra.expand(expanded, Context.DEFAULT).expr()).as("%s", e).isEqualTo(expanded);
  }

  /** The result of expanding a commuting expression is its own polynomial's expression. */
  @Property(tries = 2000)
  void fullyExpanded(@ForAll("exact") Expr e) {
    Expr expanded = Algebra.expand(e, Context.DEFAULT).expr();
    Polynomial p;
    try {
      p = Polynomial.of(expanded);
    } catch (AlgebraLimitException | IllegalArgumentException tooLargeOrUndefined) {
      return;
    }
    assertThat(p.toExpr()).as("%s", e).isEqualTo(expanded);
  }

  @Property(tries = 20)
  void terminates(@ForAll List<@From("any") Expr> exprs) {
    TerminationProbe.run(ExpandRules.RULES, exprs, Context.DEFAULT);
  }
}
