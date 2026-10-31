package mathcanvas.engine.calculus;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import java.util.Random;
import mathcanvas.engine.expr.BigProduct;
import mathcanvas.engine.expr.BigSum;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.testing.NumericEvaluator;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.statistics.Statistics;

/**
 * Closed forms of sums and products in a symbolic upper bound equal the term-by-term value at
 * random integer bounds, for summands of every family.
 */
class SumProperties {

  private static final Symbol K = Symbol.of("k");
  private static final Symbol N = Symbol.of("n");
  private static final MathContext MC = new MathContext(40);
  private static final Context CTX = TestNumerics.CONTEXT;

  private static Expr parse(String s) {
    return Canonical.of(Parser.parseExpression(s, Names.NONE));
  }

  private static int small(Random r) {
    return r.nextInt(7) - 3;
  }

  private static String ratio(Random r) {
    int p;
    int q;
    do {
      p = r.nextInt(9) - 4;
      q = 1 + r.nextInt(4);
    } while (p == 0 || p == q);
    return "(" + p + "/" + q + ")";
  }

  /** A summand of a family, as text, and the least lower bound that keeps its terms defined. */
  private static String[] summand(Random r) {
    return switch (r.nextInt(7)) {
      case 0 ->
          new String[] {
            small(r) + "k^3 + " + small(r) + "k^2 + " + small(r) + "k + " + small(r), "0"
          };
      case 1 -> new String[] {small(r) + "*" + ratio(r) + "^k", "0"};
      case 2 -> new String[] {"(" + small(r) + "k + " + small(r) + ")*" + ratio(r) + "^k", "0"};
      case 3 -> {
        int p = 1 + r.nextInt(3);
        int q = p + 1 + r.nextInt(3);
        yield new String[] {"1/((k + " + p + ")(k + " + q + "))", "0"};
      }
      case 4 -> new String[] {"log((k + 2)/(k + 1))", "0"};
      case 5 -> {
        // partial fractions written term by term, their coefficients cancelling in each group
        StringBuilder t = new StringBuilder("0");
        int total = 0;
        for (int i = 2 + r.nextInt(4); i > 0; i--) {
          int c = small(r);
          total += c;
          t.append(" + (").append(c).append(")/(k + ").append(1 + r.nextInt(6)).append(")");
        }
        t.append(" + (").append(-total).append(")/(k + ").append(1 + r.nextInt(6)).append(")");
        int c = 1 + r.nextInt(3);
        t.append(" + ").append(c).append("/(2k + 1)^2 - ").append(c).append("/(2k + 3)^2");
        yield new String[] {t.toString(), "0"};
      }
      default -> new String[] {"binomial(n, k)*" + ratio(r) + "^k", "binomial"};
    };
  }

  private static Expr termByTerm(Expr node) {
    return mathcanvas.engine.eval.Evaluator.evaluate(
            node, mathcanvas.engine.eval.Environment.EMPTY, false)
        .value();
  }

  private static BigDecimal value(Expr e) {
    return NumericEvaluator.eval(e, Map.of(), MC).orElseThrow().re().toReal(MC).value();
  }

  @Property(tries = 70)
  void aClosedFormEqualsTheTermByTermSum(@ForAll Random r) {
    String[] s = summand(r);
    Expr body = parse(s[0]);
    boolean binomial = s[1].equals("binomial");
    int a = binomial ? 0 : r.nextInt(4);
    Expr closed = Calculus.sum(BigSum.of(body, K, Num.of(a), N), CTX).expr();
    Statistics.label("closed").collect(!(closed instanceof BigSum));
    assertThat(closed).as("Σ %s from %d to n", s[0], a).isNotInstanceOf(BigSum.class);
    // the binomial identities hold for a non-negative upper bound
    int b = binomial ? r.nextInt(25) : a - 1 + r.nextInt(25);
    Expr bodyAt = Subst.of(body, N, Num.of(b), MC).orElseThrow();
    // term by term, through the evaluator
    Expr direct = termByTerm(BigSum.of(bodyAt, K, Num.of(a), Num.of(b)));
    Expr closedAt = Subst.of(closed, N, Num.of(b), MC).orElseThrow();
    BigDecimal u = value(closedAt);
    BigDecimal v = value(direct);
    assertThat(u.subtract(v).abs())
        .as("Σ %s from %d to %d: %s", s[0], a, b, closed)
        .isLessThan(v.abs().add(BigDecimal.ONE).multiply(new BigDecimal("1e-30")));
  }

  @Property(tries = 40)
  void aClosedProductEqualsTheFactorByFactorProduct(@ForAll Random r) {
    String body =
        switch (r.nextInt(4)) {
          case 0 -> String.valueOf(1 + r.nextInt(5));
          case 1 -> (1 + r.nextInt(3)) + "(k + " + r.nextInt(3) + ")";
          case 2 -> "(k + " + r.nextInt(3) + ")^2";
          default -> ratio(r) + "^(" + small(r) + "k + " + small(r) + ")";
        };
    Expr f = parse(body);
    int a = 1 + r.nextInt(3);
    Expr closed = Calculus.sum(BigProduct.of(f, K, Num.of(a), N), CTX).expr();
    assertThat(closed).as("Π %s from %d to n", body, a).isNotInstanceOf(BigProduct.class);
    int b = a - 1 + r.nextInt(15);
    Expr direct = termByTerm(BigProduct.of(f, K, Num.of(a), Num.of(b)));
    Expr closedAt = CTX.numerics().exact(Subst.of(closed, N, Num.of(b), MC).orElseThrow(), MC);
    BigDecimal u = value(closedAt);
    BigDecimal v = value(direct);
    assertThat(u.subtract(v).abs())
        .as("Π %s from %d to %d: %s", body, a, b, closed)
        .isLessThan(v.abs().add(BigDecimal.ONE).multiply(new BigDecimal("1e-30")));
  }
}
