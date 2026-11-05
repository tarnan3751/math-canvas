package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.expr.Constant;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.number.Int;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.testing.Inputs;
import mathcanvas.engine.testing.NumericEvaluator;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.statistics.Statistics;

/**
 * Improper integrals against the numeric rule: convergent families ({@code p(x) e^(−ax)} on {@code
 * [0, ∞)}, the reciprocal of a positive quadratic and of a product of two on {@code ℝ}, {@code
 * x^−p} with {@code p < 1} on {@code (0, 1]} and with {@code p > 1} on {@code [1, ∞)}) are
 * evaluated exactly and agree at 20 digits with the double-exponential rule's value of the same
 * integral; the divergent ones ({@code x^−p} with {@code p ≥ 1} at 0 and with {@code p ≤ 1} at
 * {@code ∞}) are {@code undefined}.
 */
class ImproperIntegralProperties {

  private static final int DIGITS = 20;

  private static String q(int num, int den) {
    return "(" + num + "/" + den + ")";
  }

  /** A convergent improper integral, as text. */
  private static String convergent(Random r) {
    return switch (r.nextInt(5)) {
      case 0 -> {
        StringBuilder p = new StringBuilder();
        int degree = r.nextInt(3);
        for (int k = 0; k <= degree; k++) {
          p.append(k == 0 ? "" : " + ").append(1 + r.nextInt(3)).append(" x^").append(k);
        }
        yield "integrate(("
            + p
            + ") e^(-"
            + q(1 + r.nextInt(3), 1 + r.nextInt(2))
            + " x), x, 0, inf)";
      }
      case 1 -> {
        int b = r.nextInt(5) - 2;
        int c = b * b / 4 + 1 + r.nextInt(4);
        yield "integrate(1/(x^2 + " + b + "x + " + c + "), x, -inf, inf)";
      }
      case 2 -> {
        int a = 1 + r.nextInt(3);
        int b = a + 1 + r.nextInt(3);
        yield "integrate(1/((x^2 + " + a + ")(x^2 + " + b + ")), x, -inf, inf)";
      }
      case 3 -> {
        // p = n/d in (0, 1)
        int d = 2 + r.nextInt(3);
        int n = 1 + r.nextInt(d - 1);
        yield "integrate(x^(-" + q(n, d) + "), x, 0, 1)";
      }
      default -> {
        // p = n/d > 1
        int d = 1 + r.nextInt(3);
        int n = d + 1 + r.nextInt(3);
        yield "integrate(x^(-" + q(n, d) + "), x, 1, inf)";
      }
    };
  }

  private static BigDecimal decimal(Expr e) {
    Optional<Numeric> v = NumericEvaluator.eval(e, Map.of(), new MathContext(40));
    assertThat(v).as("the value of %s", e).isPresent();
    return switch ((RealNumeric) v.get()) {
      case Int i -> new BigDecimal(i.value());
      case Rational q ->
          new BigDecimal(q.num()).divide(new BigDecimal(q.den()), new MathContext(40));
      case Real d -> d.value();
    };
  }

  @Property(tries = 40, seed = "4221")
  void aConvergentImproperIntegralAgreesWithTheNumericRule(@ForAll Random random) {
    String integral = convergent(random);
    Expr value = Run.value(integral);
    assertThat(value).as(integral).isNotInstanceOf(Integral.class).isNotEqualTo(Constant.UNDEFINED);
    // the same integral, unevaluated, through the double-exponential rule
    Expr node = Inputs.canonical(integral);
    assertThat(node).isInstanceOf(Integral.class);
    Expr numeric = NumericIntegral.of(node, DIGITS, true, false, node, Cancellation.NEVER).value();
    Statistics.label("numeric").collect(numeric instanceof Num);
    if (!(numeric instanceof Num)) {
      return;
    }
    BigDecimal exact = decimal(value);
    BigDecimal quadrature = decimal(numeric);
    assertThat(exact.subtract(quadrature).abs())
        .as("%s = %s against %s", integral, value, numeric)
        .isLessThanOrEqualTo(
            BigDecimal.ONE.movePointLeft(DIGITS - 2).max(exact.abs().movePointLeft(DIGITS - 2)));
  }

  @Property(tries = 40, seed = "4222")
  void aDivergentImproperIntegralIsUndefined(@ForAll Random random) {
    int d = 1 + random.nextInt(3);
    String integral =
        random.nextBoolean()
            // p ≥ 1 at 0
            ? "integrate(x^(-" + q(d + random.nextInt(4), d) + "), x, 0, 1)"
            // p ≤ 1 at ∞
            : "integrate(x^(-" + q(1 + random.nextInt(d), d) + "), x, 1, inf)";
    assertThat(Run.value(integral)).as(integral).isEqualTo(Constant.UNDEFINED);
  }
}
