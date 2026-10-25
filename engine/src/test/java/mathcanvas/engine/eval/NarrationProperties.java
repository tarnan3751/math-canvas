package mathcanvas.engine.eval;

import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.algebra.PolynomialArbitraries;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ExprArbitraries;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.rule.Context;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * The narration of every operation it curates, over the rule sets' generators: it never throws, its
 * steps stand for every rewrite in order, and they replay to the result ({@link
 * NarrationTest#replays}).
 */
class NarrationProperties {

  private static final Symbol X = Symbol.of("x");

  @Provide
  Arbitrary<Expr> exprs() {
    return ExprArbitraries.elementaryExprs();
  }

  @Provide
  Arbitrary<Polynomial> polynomials() {
    return PolynomialArbitraries.products(2, 3);
  }

  @Provide
  Arbitrary<Polynomial> univariate() {
    return PolynomialArbitraries.univariate(4);
  }

  private static void narrates(Expr call) {
    Evaluator.Result r;
    try {
      r = Evaluator.evaluate(Apply.of(Symbol.of("steps"), call), Environment.EMPTY, true);
    } catch (EvaluationException e) {
      return;
    }
    NarrationTest.replays(r.trace(), Narration.steps(r, Context.DEFAULT));
  }

  @Property(tries = 1_000)
  void simplify(@ForAll("exprs") Expr e) {
    narrates(e);
  }

  @Property(tries = 1_000)
  void differentiate(@ForAll("exprs") Expr e) {
    narrates(Apply.of(Symbol.of("diff"), e, X));
  }

  @Property(tries = 300)
  void expand(@ForAll("polynomials") Polynomial p) {
    narrates(Apply.of(Symbol.of("expand"), p.toExpr()));
  }

  @Property(tries = 300)
  void factor(@ForAll("polynomials") Polynomial p) {
    narrates(Apply.of(Symbol.of("factor"), p.toExpr()));
  }

  @Property(tries = 300)
  void solve(@ForAll("univariate") Polynomial p) {
    narrates(Apply.of(Symbol.of("solve"), Relation.of(Relation.Op.EQ, p.toExpr(), Num.ZERO), X));
  }
}
