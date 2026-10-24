package mathcanvas.engine.solve;

import static mathcanvas.engine.solve.SolveCases.X;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Random;
import mathcanvas.engine.algebra.Factoring;
import mathcanvas.engine.algebra.Factorization;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.algebra.TraceChecks;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Rewriter;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/**
 * Random integer polynomials of degree 5 to 9: the numeric solutions make the equation hold within
 * the tolerance of their precision, there are as many as distinct roots, and at 34 digits they are
 * the roots found at 60 digits, rounded.
 */
class NumericSolveProperties {

  @Provide
  Arbitrary<Polynomial> polynomials() {
    return Arbitraries.randomValue(NumericSolveProperties::polynomial);
  }

  static Polynomial polynomial(Random random) {
    int n = 5 + random.nextInt(5);
    Polynomial x = Polynomial.variable(X);
    Polynomial p = Polynomial.ZERO;
    for (int k = 0; k <= n; k++) {
      int c = k == n ? 1 + random.nextInt(5) : random.nextInt(19) - 9;
      if (random.nextInt(4) == 0 && k < n && k > 0) {
        c = 0;
      }
      p = p.add(x.pow(k).multiply(Int.of(c)));
    }
    return p;
  }

  @Property(tries = 500)
  void numericSolutionsHoldAndAreComplete(@ForAll("polynomials") Polynomial p) {
    Rewriter.Result r =
        Solver.solve(
            new Relation(Relation.Op.EQ, p.toExpr(), Num.ZERO),
            X,
            NumericSolveTest.digits(34).withTrace(true));
    TraceChecks.assertChain(r);
    assertThat(r.expr()).as("%s", p).isInstanceOf(SetExpr.class);
    SetExpr s = (SetExpr) r.expr();
    Factorization f = Factoring.factor(p, Steps.NONE);
    int distinct = 0;
    for (Factorization.Factor fac : f.factors()) {
      distinct += fac.base().degree(X);
    }
    if (f.complete()) {
      assertThat(s.items()).as("%s", p).hasSize(distinct);
    }
    SolveCases.assertSolutions(p, s, new Random(1));
    SetExpr fine =
        (SetExpr)
            Solver.solve(
                    new Relation(Relation.Op.EQ, p.toExpr(), Num.ZERO),
                    X,
                    NumericSolveTest.digits(60))
                .expr();
    List<Expr> numeric = s.items().stream().filter(e -> e instanceof Num).toList();
    List<Expr> fineNumeric = fine.items().stream().filter(e -> e instanceof Num).toList();
    assertThat(numeric).hasSameSizeAs(fineNumeric);
    MathContext mc = new MathContext(34);
    for (Expr each : numeric) {
      Numeric a = ((Num) each).value();
      Numeric b =
          fineNumeric.stream()
              .map(e -> ((Num) e).value())
              .min(
                  (u, v) ->
                      u.subtract(a, mc)
                          .modulus(mc)
                          .toReal(mc)
                          .value()
                          .compareTo(v.subtract(a, mc).modulus(mc).toReal(mc).value()))
              .orElseThrow();
      BigDecimal scale = b.modulus(mc).toReal(mc).value().max(new BigDecimal("1e-30"));
      assertThat(a.subtract(b, mc).modulus(mc).toReal(mc).value())
          .as("%s: %s vs %s", p, a, b)
          .isLessThanOrEqualTo(scale.movePointLeft(32));
    }
    Statistics.label("numeric solutions")
        .collect(numeric.size() == s.items().size() ? "all" : "some");
  }
}
