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
import mathcanvas.engine.testing.Recheck;
import mathcanvas.engine.testing.Soundness;
import mathcanvas.engine.testing.TerminationProbe;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.From;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Reporter;
import net.jqwik.api.lifecycle.AfterProperty;
import net.jqwik.api.lifecycle.BeforeProperty;
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
   * An expansion has the value of its input. A point where they disagree at 34 digits is rechecked
   * as the rule sets' properties recheck it ({@link Recheck}): at a zero of a factor, the expanded
   * terms cancel all but their rounding.
   */
  @Property(tries = 2000)
  void sound(@ForAll("exact") Expr e, @ForAll long seed) {
    Expr before = Canonical.of(e);
    Expr expanded = Algebra.expand(e, Context.DEFAULT).expr();
    List<Map<Symbol, Numeric>> points =
        Soundness.points(PolynomialProperties.SYMBOLS, Assumptions.EMPTY, 3, new Random(seed));
    int compared = 0;
    for (Map<Symbol, Numeric> point : points) {
      try {
        compared += Soundness.check(before, expanded, List.of(point));
      } catch (ArithmeticException outOfRange) {
        // Soundness.check's modulus underflows on values past BigDecimal's exponent range
        Statistics.label("overflowed points").collect(true);
      } catch (AssertionError disagreement) {
        switch (Recheck.of(before, expanded, point)) {
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
