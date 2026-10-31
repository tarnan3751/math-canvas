package mathcanvas.engine.calculus;

import static mathcanvas.engine.calculus.LimitCases.X;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Limit;
import mathcanvas.engine.expr.Limit.Direction;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Power;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.parse.Names;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.testing.NumericEvaluator;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.statistics.Statistics;

/**
 * Limits of generated functions: a continuous function's limit is its value; a rational function's
 * at a pole and at infinity follow the sign and growth analysis and the values near the point; and
 * a {@code 0/0} quotient {@code g₁·h / (g₂·k)} of two different functions vanishing at the point
 * has the limit {@code h(a)/k(a)}, which the values near the point approach.
 */
class LimitProperties {

  private static final MathContext MC = new MathContext(40);
  private static final Context CTX = TestNumerics.CONTEXT;

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

  private static Expr parse(String s) {
    return Parser.parseExpression(s, Names.NONE);
  }

  /** A sum, product or composition of functions continuous on the real line. */
  static Expr smooth(Random r, int more) {
    Expr e = parse(PARTS.get(r.nextInt(PARTS.size())));
    for (int i = 0; i < more; i++) {
      Expr p = parse(PARTS.get(r.nextInt(PARTS.size())));
      e =
          switch (r.nextInt(3)) {
            case 0 -> Sum.of(e, Product.of(Num.of(r.nextInt(7) - 3), p));
            case 1 -> Product.of(e, p);
            default -> Subst.of(e, X, Product.of(Num.of(Rational.of(1, 2)), p), MC).orElseThrow();
          };
    }
    return Canonical.of(e);
  }

  private static Optional<BigDecimal> at(Expr e, Numeric x) {
    return NumericEvaluator.eval(e, Map.of(X, x), MC)
        .filter(v -> v.im().isZero())
        .map(v -> v.re().toReal(MC).value());
  }

  private static BigDecimal value(Expr e) {
    return NumericEvaluator.eval(e, Map.of(), MC).orElseThrow().re().toReal(MC).value();
  }

  private static Expr limit(Expr f, Expr a, Direction d) {
    return Limits.limit(f, X, a, d, CTX).expr();
  }

  private static RealNumeric point(Random r) {
    return Rational.of(r.nextInt(41) - 20, 1 + r.nextInt(10));
  }

  @Property(tries = 40)
  void aContinuousFunctionsLimitIsItsValue(@ForAll Random r) {
    Expr f = smooth(r, r.nextInt(3));
    RealNumeric a = point(r);
    Direction d = Direction.values()[r.nextInt(3)];
    Expr l = limit(f, Num.of(a), d);
    assertThat(l).as("lim %s at %s", f, a).isNotInstanceOf(Limit.class);
    // the test evaluator has no value beyond its bounds (e^(x e^x) far out)
    Optional<BigDecimal> oracle = at(f, a);
    Statistics.label("compared").collect(oracle.isPresent());
    Assume.that(oracle.isPresent());
    BigDecimal expected = oracle.get();
    BigDecimal tolerance = expected.abs().add(BigDecimal.ONE).multiply(new BigDecimal("1e-30"));
    assertThat(value(l).subtract(expected).abs()).as("lim %s at %s", f, a).isLessThan(tolerance);
  }

  /** A polynomial with integer roots and leading coefficient, as its factors. */
  private static List<Expr> factors(Random r, int degree, int avoid) {
    List<Expr> out = new ArrayList<>();
    for (int i = 0; i < degree; i++) {
      int root;
      do {
        root = r.nextInt(11) - 5;
      } while (root == avoid);
      out.add(Sum.of(X, Num.of(-root)));
    }
    return out;
  }

  private static Expr product(List<Expr> fs, int c) {
    List<Expr> all = new ArrayList<>(fs);
    all.add(Num.of(c));
    return Canonical.of(all.size() == 1 ? all.getFirst() : Product.of(all));
  }

  @Property(tries = 60)
  void aRationalFunctionAtAPoleFollowsTheSignOfItsFactors(@ForAll Random r) {
    int a = r.nextInt(11) - 5;
    int m = 1 + r.nextInt(3);
    int c = (r.nextBoolean() ? 1 : -1) * (1 + r.nextInt(4));
    Expr p = product(factors(r, r.nextInt(3), a), c);
    Expr q1 = product(factors(r, r.nextInt(2), a), 1);
    Expr q = Canonical.of(Product.of(Power.of(Sum.of(X, Num.of(-a)), Num.of(m)), q1));
    // expanded, so that the pole is not visible as a factor
    Expr f =
        Canonical.of(
            Product.of(
                mathcanvas.engine.algebra.Algebra.expand(p, CTX).expr(),
                Power.of(mathcanvas.engine.algebra.Algebra.expand(q, CTX).expr(), Num.MINUS_ONE)));
    int s =
        value(Subst.of(Product.of(p, Power.of(q1, Num.MINUS_ONE)), X, Num.of(a), MC).orElseThrow())
            .signum();
    Direction d = Direction.values()[r.nextInt(3)];
    Expr expected =
        switch (d) {
          case RIGHT -> LimitMethods.inf(s);
          case LEFT -> LimitMethods.inf(m % 2 == 0 ? s : -s);
          case BOTH -> m % 2 == 0 ? LimitMethods.inf(s) : Constant.UNDEFINED;
        };
    Expr l = limit(f, Num.of(a), d);
    assertThat(l).as("lim %s at %d %s", f, a, d).isEqualTo(Canonical.of(expected));
    // and the values near the point have that sign
    if (d != Direction.BOTH) {
      int side = d == Direction.RIGHT ? 1 : -1;
      Numeric near = Rational.of(a, 1).add(Rational.of(side, 100_000_000), MC);
      int sign = at(f, near).orElseThrow().signum();
      assertThat(sign).as("sign of %s near %d", f, a).isEqualTo(LimitMethods.infSign(l));
    }
  }

  @Property(tries = 60)
  void aRationalFunctionAtInfinityFollowsItsDegrees(@ForAll Random r) {
    int dp = r.nextInt(4);
    int dq = r.nextInt(4);
    int cp = (r.nextBoolean() ? 1 : -1) * (1 + r.nextInt(4));
    int cq = (r.nextBoolean() ? 1 : -1) * (1 + r.nextInt(4));
    Expr p = mathcanvas.engine.algebra.Algebra.expand(product(factors(r, dp, 99), cp), CTX).expr();
    Expr q = mathcanvas.engine.algebra.Algebra.expand(product(factors(r, dq, 99), cq), CTX).expr();
    Expr f = Canonical.of(Product.of(p, Power.of(q, Num.MINUS_ONE)));
    boolean plus = r.nextBoolean();
    Expr point = plus ? LimitMethods.INF : LimitMethods.NEG_INF;
    Expr expected;
    if (dp < dq) {
      expected = Num.ZERO;
    } else if (dp == dq) {
      expected = Canonical.of(Num.of(Rational.of(cp, cq)));
    } else {
      int s = Integer.signum(cp) * Integer.signum(cq) * (plus || (dp - dq) % 2 == 0 ? 1 : -1);
      expected = Canonical.of(LimitMethods.inf(s));
    }
    Expr l = limit(f, point, Direction.BOTH);
    assertThat(l).as("lim %s at %s", f, point).isEqualTo(expected);
    // and the values far out agree
    Numeric far = Rational.of(plus ? 1_000_000_000L : -1_000_000_000L, 1);
    BigDecimal v = at(f, far).orElseThrow();
    if (LimitMethods.infinite(l)) {
      assertThat(v.signum()).isEqualTo(LimitMethods.infSign(l));
    } else {
      assertThat(v.subtract(value(l)).abs()).isLessThan(new BigDecimal("1e-6"));
    }
  }

  private static final List<String> VANISHING =
      List.of("u", "sin(u)", "e^u - 1", "log(1 + u)", "tan(u)", "u + u^2", "sinh(2u)/2");

  @Property(tries = 40)
  void aZeroOverZeroQuotientHasTheLimitOfItsOtherFactors(@ForAll Random r) {
    RealNumeric a = Rational.of(r.nextInt(9) - 4, 1 + r.nextInt(3));
    Expr u = Sum.of(X, Num.of(a.negate()));
    int i = r.nextInt(VANISHING.size());
    int j = (i + 1 + r.nextInt(VANISHING.size() - 1)) % VANISHING.size();
    Expr g1 =
        Subst.of(parse(VANISHING.get(i)), mathcanvas.engine.expr.Symbol.of("u"), u, MC)
            .orElseThrow();
    Expr g2 =
        Subst.of(parse(VANISHING.get(j)), mathcanvas.engine.expr.Symbol.of("u"), u, MC)
            .orElseThrow();
    Expr h = smooth(r, r.nextInt(2));
    Expr k =
        Canonical.of(Sum.of(parse("x^2 + 1"), Product.of(Num.of(r.nextInt(3)), parse("cos(x)^2"))));
    Expr f = Canonical.of(Product.of(g1, h, Power.of(Product.of(g2, k), Num.MINUS_ONE)));
    // facts about x hold at the point, never in the expression: the limit is the same under any
    Context facts =
        CTX.withAssumptions(
            switch (r.nextInt(3)) {
              case 0 -> DiffCases.facts("x > 0");
              case 1 -> DiffCases.facts("x < 0");
              default -> mathcanvas.engine.assume.Assumptions.EMPTY;
            });
    Expr l = Limits.limit(f, X, Num.of(a), Direction.BOTH, facts).expr();
    Statistics.label("decided").collect(!(l instanceof Limit));
    assertThat(l).as("lim %s at %s", f, a).isNotInstanceOf(Limit.class);
    Optional<BigDecimal> ha = at(h, a);
    Assume.that(ha.isPresent());
    BigDecimal expected = ha.get().divide(at(k, a).orElseThrow(), MC);
    BigDecimal tolerance = expected.abs().add(BigDecimal.ONE).multiply(new BigDecimal("1e-30"));
    assertThat(value(l).subtract(expected).abs()).as("lim %s at %s", f, a).isLessThan(tolerance);
    // the values near the point approach it
    Numeric near = a.add(Rational.of(1, 10_000_000), MC);
    BigDecimal v = at(f, near).orElseThrow();
    assertThat(v.subtract(expected).abs())
        .as("%s near %s", f, a)
        .isLessThan(expected.abs().add(BigDecimal.ONE).multiply(new BigDecimal("1e-4")));
  }
}
