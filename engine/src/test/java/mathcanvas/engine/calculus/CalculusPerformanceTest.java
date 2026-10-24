package mathcanvas.engine.calculus;

import static mathcanvas.engine.calculus.DiffCases.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

/** Differentiation of large expressions stays within a generous budget. */
class CalculusPerformanceTest {

  private static final Symbol X = Symbol.of("x");
  private static final Context CONTEXT = Context.DEFAULT.withRewriteCap(100_000).withTrace(true);

  private static long millis(Expr e, int n) {
    long start = System.nanoTime();
    Rewriter.Result r = Calculus.differentiate(e, X, n, CONTEXT);
    long millis = (System.nanoTime() - start) / 1_000_000;
    assertThat(r.trace().rewrites()).hasSizeLessThan(CONTEXT.rewriteCap());
    return millis;
  }

  @Test
  void aPolynomialOfFiveHundredTerms() {
    List<Expr> terms = new ArrayList<>();
    for (int k = 1; k <= 500; k++) {
      terms.add(parse(k + " x^" + k + " sin(" + k + " x)"));
    }
    assertThat(millis(Sum.of(terms), 1)).isLessThan(5_000);
  }

  @Test
  void aProductOfThirtyFactors() {
    List<Expr> factors = new ArrayList<>();
    for (int k = 1; k <= 30; k++) {
      factors.add(parse("sin(" + k + " x + 1)"));
    }
    assertThat(millis(Product.of(factors), 1)).isLessThan(5_000);
  }

  @Test
  void aFourthDerivativeOfXToTheX() {
    assertThat(millis(parse("x^x"), 4)).isLessThan(5_000);
  }
}
