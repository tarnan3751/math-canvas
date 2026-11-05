package mathcanvas.engine.calculus;

import static mathcanvas.engine.testing.Inputs.parse;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.testing.NumericEvaluator;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.statistics.Statistics;

/**
 * Taylor polynomials of generated smooth functions at random rational points: the polynomial is
 * within the Lagrange remainder bound of the function near the point, and its {@code k}-th
 * derivative at the point is the function's for every {@code k} up to the degree.
 */
class TaylorProperties {

  private static final Symbol X = Symbol.of("x");
  private static final MathContext MC = new MathContext(40);
  private static final Context CTX = TestNumerics.CONTEXT;

  /** Smooth on the whole real line. */
  private static final List<String> PARTS =
      List.of(
          "x",
          "x^2",
          "x^3 - 2x",
          "sin(x)",
          "cos(2x)",
          "e^x",
          "e^(-x/2)",
          "atan(x)",
          "1/(x^2 + 1)",
          "log(x^2 + 2)",
          "sinh(x/3)",
          "sqrt(x^2 + 1)",
          "x e^x",
          "cos(x)^2");

  private static Expr smooth(Random r) {
    Expr e = parse(PARTS.get(r.nextInt(PARTS.size())));
    int more = r.nextInt(2);
    for (int i = 0; i < more; i++) {
      Expr p = parse(PARTS.get(r.nextInt(PARTS.size())));
      int c = r.nextInt(7) - 3;
      e =
          switch (r.nextInt(3)) {
            case 0 -> Sum.of(e, Product.of(Num.of(c), p));
            case 1 -> Product.of(e, p);
            default -> compose(e, p);
          };
    }
    return e;
  }

  /** {@code a} with {@code b/2} put in for {@code x}: still smooth everywhere. */
  private static Expr compose(Expr a, Expr b) {
    return Subst.of(a, X, Product.of(Num.of(Rational.of(1, 2)), b), MC).orElseThrow();
  }

  private static Optional<BigDecimal> at(Expr e, Numeric x) {
    return NumericEvaluator.eval(e, Map.of(X, x), MC)
        .filter(v -> v.im().isZero())
        .map(v -> v.re().toReal(MC).value());
  }

  @Property(tries = 25)
  void thePolynomialIsWithinTheLagrangeBound(@ForAll Random r) {
    Expr f = smooth(r);
    RealNumeric a = Rational.of(r.nextInt(21) - 10, 10);
    int n = r.nextInt(4);
    Expr t = taylor(f, a, n);
    Expr next = Calculus.differentiate(f, X, n + 1, CTX).expr();
    RealNumeric h = Rational.of(1 + r.nextInt(9), 200);
    RealNumeric x = (RealNumeric) a.add(h, MC);
    BigDecimal max = BigDecimal.ZERO;
    for (int i = 0; i <= 8; i++) {
      Numeric xi = a.add(h.multiply(Rational.of(i, 8), MC), MC);
      max = max.max(at(next, xi).orElseThrow().abs());
    }
    BigDecimal fact = new BigDecimal(java.math.BigInteger.valueOf(factorial(n + 1)));
    BigDecimal hd = h.toReal(MC).value();
    BigDecimal bound =
        max.multiply(new BigDecimal("1.5"))
            .multiply(hd.pow(n + 1))
            .divide(fact, MC)
            .add(new BigDecimal("1e-30"));
    BigDecimal error = at(f, x).orElseThrow().subtract(at(t, x).orElseThrow()).abs();
    assertThat(error).as("%s about %s, degree %d", f, a, n).isLessThanOrEqualTo(bound);
    Statistics.label("degree").collect(n);
  }

  @Property(tries = 25)
  void theDerivativesAtThePointAgree(@ForAll Random r) {
    Expr f = smooth(r);
    RealNumeric a = Rational.of(r.nextInt(21) - 10, 10);
    int n = r.nextInt(4);
    Expr t = taylor(f, a, n);
    for (int k = 0; k <= n; k++) {
      Expr dt = k == 0 ? t : Calculus.differentiate(t, X, k, CTX).expr();
      Expr df = k == 0 ? f : Calculus.differentiate(f, X, k, CTX).expr();
      BigDecimal u = at(dt, a).orElseThrow();
      BigDecimal v = at(df, a).orElseThrow();
      BigDecimal tolerance = v.abs().add(BigDecimal.ONE).multiply(new BigDecimal("1e-30"));
      assertThat(u.subtract(v).abs()).as("order %d of %s about %s", k, f, a).isLessThan(tolerance);
    }
  }

  private static Expr taylor(Expr f, RealNumeric a, int n) {
    try {
      return Calculus.taylor(f, X, Num.of(a), n, CTX).expr();
    } catch (TaylorException e) {
      throw new AssertionError("taylor of " + f + " about " + a + ": " + e.getMessage(), e);
    }
  }

  private static long factorial(int n) {
    long out = 1;
    for (int i = 2; i <= n; i++) {
      out *= i;
    }
    return out;
  }
}
