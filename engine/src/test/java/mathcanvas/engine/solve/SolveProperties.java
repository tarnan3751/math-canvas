package mathcanvas.engine.solve;

import static mathcanvas.engine.solve.SolveCases.X;
import static org.assertj.core.api.Assertions.assertThat;

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
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/**
 * Every solution of a polynomial equation makes it hold, and a complete factorization gives as many
 * solutions as the polynomial has distinct roots.
 */
class SolveProperties {

  private static final Polynomial XP = Polynomial.variable(X);
  private static final int[] RADICANDS = {2, 3, 5, 6, 7, 10};

  /** Products of linear and quadratic factors with rational, radical and complex roots. */
  @Provide
  Arbitrary<Polynomial> polynomials() {
    return Arbitraries.randomValue(SolveProperties::polynomial);
  }

  static Polynomial polynomial(Random random) {
    Polynomial p = Polynomial.constant(rational(random, 6, true));
    int degree = 0;
    int target = 1 + random.nextInt(6);
    while (degree < target) {
      int kind = random.nextInt(10);
      Polynomial f;
      if (kind < 4 || target - degree < 2) {
        f =
            XP.multiply(Int.of(1 + random.nextInt(4)))
                .subtract(Polynomial.constant(rational(random, 9, false)));
      } else if (kind < 7) {
        // (x − u)² − v²m: roots u ± v√m
        RealNumeric u = rational(random, 5, false);
        RealNumeric v = rational(random, 3, true);
        int m = RADICANDS[random.nextInt(RADICANDS.length)];
        Polynomial shifted = XP.subtract(Polynomial.constant(u));
        f =
            shifted
                .multiply(shifted)
                .subtract(Polynomial.constant(Support.mul(Support.mul(v, v), Int.of(m))));
      } else if (kind < 9) {
        // x² + bx + c with b² < 4c: complex roots
        int b = random.nextInt(9) - 4;
        int c = b * b / 4 + 1 + random.nextInt(6);
        f = XP.multiply(XP).add(XP.multiply(Int.of(b))).add(Polynomial.constant(Int.of(c)));
      } else if (target - degree >= 3) {
        // x³ + ax + b, most often irreducible
        f =
            XP.pow(3)
                .add(XP.multiply(Int.of(random.nextInt(9) - 4)))
                .add(Polynomial.constant(Int.of(random.nextInt(9) - 4)));
      } else {
        continue;
      }
      p = p.multiply(f);
      degree += f.degree();
    }
    return p;
  }

  private static RealNumeric rational(Random random, int range, boolean nonZero) {
    int n;
    do {
      n = random.nextInt(2 * range + 1) - range;
    } while (nonZero && n == 0);
    return Rational.of(n, 1 + random.nextInt(3));
  }

  @Property(tries = 5000)
  void solutionsSubstituteBackAndAreComplete(@ForAll("polynomials") Polynomial p) {
    Random random = new Random(p.hashCode());
    Factorization f = Factoring.factor(p, Steps.NONE);
    Rewriter.Result r =
        Solver.solve(
            new Relation(Relation.Op.EQ, p.toExpr(), Num.ZERO), X, Context.DEFAULT.withTrace(true));
    TraceChecks.assertChain(r);
    Expr result = r.expr();
    assertThat(result).as("%s", p).isInstanceOf(SetExpr.class);
    SetExpr s = (SetExpr) result;
    if (f.complete()) {
      int distinct = 0;
      for (Factorization.Factor fac : f.factors()) {
        distinct += fac.base().degree(X);
      }
      assertThat(s.items()).as("%s", p).hasSize(distinct);
    }
    int exact = SolveCases.assertSolutions(p, s, random);
    Statistics.label("checked exactly").collect(exact == s.items().size());
    Statistics.label("degree").collect(p.degree(X));
  }
}
