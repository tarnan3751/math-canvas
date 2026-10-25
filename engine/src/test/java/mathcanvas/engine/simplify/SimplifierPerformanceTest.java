package mathcanvas.engine.simplify;

import static mathcanvas.engine.simplify.RuleCases.facts;
import static mathcanvas.engine.simplify.RuleCases.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import mathcanvas.engine.algebra.Polynomial;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Exprs;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.Rewriter;
import org.junit.jupiter.api.Test;

/** The standard list on a large expression stays within a generous budget. */
class SimplifierPerformanceTest {

  @Test
  void aThousandTermsOfRadicalsLogsAndAbsoluteValues() {
    List<Expr> terms = new ArrayList<>();
    for (int k = 1; k <= 1000; k++) {
      terms.add(parse("sqrt(" + k + "^3) + abs(-" + k + " y) + log(" + (2 * k) + " x) + exp(2)"));
    }
    Expr e = Sum.of(terms);
    Context ctx = Context.DEFAULT.withAssumptions(facts("x > 0"));
    long start = System.nanoTime();
    Rewriter.Result r = Simplifier.simplify(e, ctx.withTrace(true));
    long millis = (System.nanoTime() - start) / 1_000_000;
    assertThat(r.trace().rewrites()).hasSizeLessThan(ctx.rewriteCap());
    // typically 0.1 to 0.3 s locally (docs/reference/engine.md, Timing budgets)
    assertThat(millis).isLessThan(30_000);
  }

  @Test
  void aThousandTermsOfTrigonometry() {
    List<Expr> terms = new ArrayList<>();
    for (int k = 1; k <= 1000; k++) {
      terms.add(
          parse(
              k
                  + " sin("
                  + k
                  + " x)^2 + "
                  + k
                  + " cos("
                  + k
                  + " x)^2 + sin("
                  + k
                  + " pi/12) + tan(y + "
                  + k
                  + " pi) + sin("
                  + k
                  + " y)/cos("
                  + k
                  + " y)"));
    }
    Expr e = Sum.of(terms);
    long start = System.nanoTime();
    Rewriter.Result r = Simplifier.simplify(e, Context.DEFAULT.withTrace(true));
    long millis = (System.nanoTime() - start) / 1_000_000;
    assertThat(r.trace().rewrites()).hasSizeLessThan(Context.DEFAULT.rewriteCap());
    // typically 0.1 to 0.3 s locally (docs/reference/engine.md, Timing budgets)
    assertThat(millis).isLessThan(30_000);
  }

  /**
   * Twenty fractions over distinct linear denominators: the lcm of degree 20 is built (nineteen
   * gcds through the provider) and the combination declines on size; and a quotient of two
   * polynomials of degree 50 expanded, with a common factor of degree 25, cancelled in one gcd.
   */
  @Test
  void fractionsOfHighDegree() {
    List<Expr> terms = new ArrayList<>();
    for (int k = 1; k <= 20; k++) {
      terms.add(parse("1/(x - " + k + ")"));
    }
    Expr sum = Sum.of(terms);
    StringBuilder common = new StringBuilder("1");
    StringBuilder num = new StringBuilder("1");
    StringBuilder den = new StringBuilder("1");
    for (int k = 1; k <= 25; k++) {
      common.append(" + ").append(k).append(" x^").append(k);
      num.append(" + ").append(k + 1).append(" x^").append(k);
      den.append(" + ").append(2 * k - 1).append(" x^").append(k);
    }
    Expr c = parse(common.toString());
    Expr n = parse(num.toString());
    Expr d = parse(den.toString());
    Expr quotient =
        Product.of(
            Polynomial.of(Product.of(c, n)).toExpr(),
            Exprs.inv(Polynomial.of(Product.of(c, d)).toExpr()));
    long start = System.nanoTime();
    Expr combined = Simplifier.simplify(sum, Context.DEFAULT).expr();
    Expr cancelled = Simplifier.simplify(quotient, Context.DEFAULT).expr();
    long millis = (System.nanoTime() - start) / 1_000_000;
    assertThat(combined).isEqualTo(Canonical.of(sum));
    assertThat(cancelled)
        .isEqualTo(
            Simplifier.simplify(
                    Product.of(Polynomial.of(n).toExpr(), Exprs.inv(Polynomial.of(d).toExpr())),
                    Context.DEFAULT)
                .expr());
    // typically 0.05 s locally (docs/reference/engine.md, Timing budgets)
    assertThat(millis).isLessThan(30_000);
  }
}
