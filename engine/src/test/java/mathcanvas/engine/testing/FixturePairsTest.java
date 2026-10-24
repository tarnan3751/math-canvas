package mathcanvas.engine.testing;

import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.assume.Assumptions;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Relation.Op;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

/** PLAN's acceptance pairs: assumptions change results where they should. */
class FixturePairsTest {

  private static final Symbol X = Symbol.of("x");
  private static final Symbol A = Symbol.of("a");
  private static final Symbol B = Symbol.of("b");
  private static final Symbol C = Symbol.of("c");

  private static Apply log(Expr e) {
    return Apply.of(Symbol.of("log"), e);
  }

  private static Expr rewrite(Expr e, Relation... facts) {
    Assumptions a = Assumptions.EMPTY;
    for (Relation r : facts) {
      a = a.with(r);
    }
    return Rewriter.rewrite(e, FixtureRules.RULES, Context.DEFAULT.withAssumptions(a)).expr();
  }

  @Test
  void sqrtOfASquare() {
    Expr e = Exprs.sqrt(Power.of(X, Num.of(2)));
    assertThat(rewrite(e)).isEqualTo(Apply.of(Symbol.of("abs"), X));
    assertThat(rewrite(e, Relation.of(Op.GE, X, Num.ZERO))).isEqualTo(X);
    assertThat(rewrite(e, Relation.of(Op.IN, X, Constant.COMPLEXES)))
        .isEqualTo(
            Power.of(Power.of(X, Num.of(2)), Num.of(mathcanvas.engine.number.Rational.of(1, 2))));
  }

  /** {@code e} rewritten under {@code facts}, all three written in the language. */
  private static Expr rewrite(String e, String... facts) {
    Assumptions a = Assumptions.EMPTY;
    for (String f : facts) {
      a = a.with((Relation) parse(f));
    }
    return Rewriter.rewrite(parse(e), FixtureRules.RULES, Context.DEFAULT.withAssumptions(a))
        .expr();
  }

  private static Expr parse(String source) {
    return Parser.parseExpression(source, Names.NONE);
  }

  private static Expr canonical(String source) {
    return Canonical.of(parse(source));
  }

  @Test
  void thePairsWrittenInTheLanguage() {
    assertThat(rewrite("sqrt(x^2)")).isEqualTo(canonical("|x|"));
    assertThat(rewrite("sqrt(x^2)", "x >= 0")).isEqualTo(canonical("x"));
    assertThat(rewrite("sqrt(x^2)", "x ∈ ℂ")).isEqualTo(canonical("sqrt(x^2)"));
    assertThat(rewrite("log(a*b)")).isEqualTo(canonical("log(a*b)"));
    assertThat(rewrite("log(a*b)", "a > 0")).isEqualTo(canonical("log(a*b)"));
    assertThat(rewrite("log(a*b)", "a > 0", "b > 0")).isEqualTo(canonical("log(a) + log(b)"));
    assertThat(rewrite("log(2 a b c)", "a > 0", "b > 0", "c > 0"))
        .isEqualTo(canonical("log(2) + log(a) + log(b) + log(c)"));
  }

  @Test
  void logOfAProductSplitsOnlyUnderPositivity() {
    Expr e = log(Product.of(A, B));
    assertThat(rewrite(e)).isEqualTo(e);
    assertThat(rewrite(e, Relation.of(Op.GT, A, Num.ZERO))).isEqualTo(e);
    assertThat(rewrite(e, Relation.of(Op.GT, A, Num.ZERO), Relation.of(Op.GT, B, Num.ZERO)))
        .isEqualTo(Sum.of(log(A), log(B)));
    assertThat(
            rewrite(
                log(Product.of(A, B, C)),
                Relation.of(Op.GT, A, Num.ZERO),
                Relation.of(Op.GT, B, Num.ZERO),
                Relation.of(Op.GT, C, Num.ZERO)))
        .isEqualTo(Sum.of(log(A), log(B), log(C)));
    // A positive constant factor counts: log(2a) splits when a > 0.
    assertThat(rewrite(log(Product.of(Num.of(2), A)), Relation.of(Op.GT, A, Num.ZERO)))
        .isEqualTo(Sum.of(log(Num.of(2)), log(A)));
  }
}
