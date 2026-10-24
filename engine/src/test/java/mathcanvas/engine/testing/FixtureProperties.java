package mathcanvas.engine.testing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Relation.Op;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/**
 * The fixture rule set through the tooling every rule set's tests use: soundness at points within
 * random assumptions, and termination.
 */
class FixtureProperties {

  /** Random facts about each generated symbol: none, a sign, integer, or complex. */
  @Provide
  Arbitrary<Assumptions> assumptions() {
    return Arbitraries.randomValue(FixtureProperties::assumptions);
  }

  static Assumptions assumptions(Random random) {
    Assumptions a = Assumptions.EMPTY;
    for (Symbol s : ExprArbitraries.SYMBOLS) {
      Relation r =
          switch (random.nextInt(7)) {
            case 0 -> Relation.of(Op.GT, s, Num.ZERO);
            case 1 -> Relation.of(Op.GE, s, Num.ZERO);
            case 2 -> Relation.of(Op.LT, s, Num.ZERO);
            case 3 -> Relation.of(Op.IN, s, Constant.INTEGERS);
            case 4 -> Relation.of(Op.IN, s, Constant.COMPLEXES);
            default -> null;
          };
      if (r != null) {
        a = a.with(r);
      }
    }
    return a;
  }

  /** Expressions shaped for the fixture's rules, around random exact subexpressions. */
  @Provide
  Arbitrary<Expr> shaped() {
    Arbitrary<Expr> u = ExprArbitraries.exactExprs();
    Arbitrary<Expr> factor =
        Arbitraries.oneOf(Arbitraries.of(ExprArbitraries.SYMBOLS).map(Expr.class::cast), u);
    Arbitrary<Expr> sqrtSquare = u.map(x -> Exprs.sqrt(Power.of(x, Num.of(2))));
    Arbitrary<Expr> logProduct =
        factor.list().ofMinSize(2).ofMaxSize(4).map(f -> Apply.of(Symbol.of("log"), Product.of(f)));
    Arbitrary<Expr> one = Arbitraries.oneOf(sqrtSquare, logProduct);
    return Arbitraries.oneOf(one, one.list().ofMinSize(2).ofMaxSize(3).map(Sum::of));
  }

  @Property(tries = 2000)
  void theFixtureIsSound(
      @ForAll("shaped") Expr e, @ForAll("assumptions") Assumptions a, @ForAll Random random) {
    Expr before = Canonical.of(e);
    Expr after =
        Rewriter.rewrite(before, FixtureRules.RULES, Context.DEFAULT.withAssumptions(a)).expr();
    List<Map<Symbol, Numeric>> points = Soundness.points(ExprArbitraries.SYMBOLS, a, 6, random);
    int compared = Soundness.check(before, after, points);
    Statistics.label("rewritten").collect(!after.equals(before));
    Statistics.label("compared").collect(compared > 0);
  }

  @Property(tries = 200)
  void theFixtureTerminates(@ForAll("assumptions") Assumptions a, @ForAll Random random) {
    List<Expr> exprs = new ArrayList<>();
    Arbitrary<Expr> shaped = shaped();
    for (int i = 0; i < 20; i++) {
      exprs.add(shaped.generator(1000).next(random).value());
    }
    TerminationProbe.Report report =
        TerminationProbe.run(FixtureRules.RULES, exprs, Context.DEFAULT.withAssumptions(a));
    assertThat(report.expressions()).isEqualTo(20);
    assertThat(report.maxRewrites()).isLessThan(100);
  }
}
