package mathcanvas.engine.calculus;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import mathcanvas.engine.algebra.Algebra;
import mathcanvas.engine.assume.Facts;
import mathcanvas.engine.compile.Compiled;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Canonical;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Integral;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Product;
import mathcanvas.engine.expr.Sum;
import mathcanvas.engine.number.Numeric;
import mathcanvas.engine.number.Rational;
import mathcanvas.engine.number.Real;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.rule.Context;
import mathcanvas.engine.rule.RewriteLimitException;
import mathcanvas.engine.simplify.Simplifier;
import mathcanvas.engine.testing.AdaptiveSimpson;
import mathcanvas.engine.testing.NumericEvaluator;
import mathcanvas.engine.testing.TerminationProbe;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/**
 * Integration soundness over generated integrable functions ({@link IntegrateArbitraries}): each is
 * integrated whole, its antiderivative's difference quotient agrees with it at points of its
 * interval ({@link DifferenceQuotient}), and on the polynomial, exponential, polynomial times sine
 * or cosine, substitution and by-parts families {@code d/dx ∫ f dx − f} expands and simplifies to 0
 * (on the others the share that does is recorded); a definite integral over random bounds inside
 * the interval agrees with adaptive Simpson quadrature within {@code 10⁻¹⁰·max(|I|, ∫|f|)}; an
 * inner function shifted off the real line is integrated as complex; and the rule set terminates on
 * them and on integrands with no elementary antiderivative.
 */
class IntegrationProperties {

  private static final Context CONTEXT =
      Context.DEFAULT.withNumerics((e, mc) -> NumericEvaluator.eval(e, Map.of(), mc));

  private static final MathContext DIGITS = new MathContext(34);

  /** The relative tolerance of the comparison with quadrature. */
  private static final double TOLERANCE = 1e-10;

  @Provide
  Arbitrary<IntegrateArbitraries.Case> cases() {
    return IntegrateArbitraries.cases();
  }

  private static RealNumeric point(IntegrateArbitraries.Case c, Random random) {
    // a rational strictly inside (lo, hi), at 1/64 steps
    long lo = (long) Math.ceil(c.lo() * 64) + 1;
    long hi = (long) Math.floor(c.hi() * 64) - 1;
    return Rational.of(lo + (long) random.nextInt((int) (hi - lo + 1)), 64);
  }

  @Property(tries = 400, seed = "4101")
  void theDerivativeOfTheAntiderivativeIsTheIntegrand(
      @ForAll("cases") IntegrateArbitraries.Case c, @ForAll Random random) {
    Expr F = Calculus.integrate(c.f(), IntegrateArbitraries.X, null, null, CONTEXT).expr();
    assertThat(IntegrateRules.complete(F, IntegrateArbitraries.X)).as("∫ %s", c).isTrue();
    boolean compared = false;
    for (int k = 0; k < 3; k++) {
      Map<mathcanvas.engine.expr.Symbol, Numeric> point =
          Map.of(IntegrateArbitraries.X, point(c, random));
      if (!DifferenceQuotient.exponentsBounded(F, point)
          || !DifferenceQuotient.defined(c.f(), point)) {
        continue;
      }
      DifferenceQuotient.Outcome o =
          DifferenceQuotient.check(F, c.f(), IntegrateArbitraries.X, point, Facts.NONE);
      compared |=
          o == DifferenceQuotient.Outcome.COMPARED || o == DifferenceQuotient.Outcome.RECHECKED;
    }
    Statistics.label("compared").collect(compared);
    Statistics.label("compared").coverage(k -> k.check(true).percentage(p -> p >= 95));
    Expr d = Calculus.differentiate(F, IntegrateArbitraries.X, 1, CONTEXT).expr();
    Expr difference = Canonical.of(Sum.of(d, Product.of(Num.MINUS_ONE, c.f())));
    boolean zero =
        Simplifier.simplify(Algebra.expand(difference, CONTEXT).expr(), CONTEXT)
            .expr()
            .equals(Num.ZERO);
    if (c.family().symbolic()) {
      assertThat(zero).as("d/dx ∫ %s − f, with ∫ = %s", c, F).isTrue();
    } else {
      Statistics.label("symbolic zero, " + c.family()).collect(zero);
    }
  }

  @Property(tries = 300, seed = "4102")
  void aDefiniteIntegralAgreesWithQuadrature(
      @ForAll("cases") IntegrateArbitraries.Case c, @ForAll Random random) {
    RealNumeric a = point(c, random);
    RealNumeric b = point(c, random);
    if (a.equals(b)) {
      return;
    }
    Expr value =
        Calculus.integrate(c.f(), IntegrateArbitraries.X, Num.of(a), Num.of(b), CONTEXT).expr();
    boolean evaluated = !(value instanceof Integral);
    Statistics.label("evaluated").collect(evaluated);
    Statistics.label("evaluated").coverage(k -> k.check(true).percentage(p -> p >= 90));
    if (!evaluated) {
      return;
    }
    Optional<Numeric> exact = NumericEvaluator.eval(value, Map.of(), DIGITS);
    assertThat(exact).as("the value %s of ∫ %s over [%s, %s]", value, c, a, b).isPresent();
    assertThat(exact.get()).isInstanceOf(RealNumeric.class);
    Compiled f = Compiled.compile(c.f(), List.of(IntegrateArbitraries.X), Settings.DEFAULT);
    double lo = a.toDouble();
    double hi = b.toDouble();
    AdaptiveSimpson.Estimate quadrature = AdaptiveSimpson.integrate(f::eval, lo, hi, 1e-12);
    double v = decimal((RealNumeric) exact.get()).doubleValue();
    assertThat(Math.abs(v - quadrature.value()))
        .as("∫ %s over [%s, %s] = %s against %s", c, a, b, value, quadrature)
        .isLessThanOrEqualTo(TOLERANCE * Math.max(Math.abs(v), quadrature.magnitude()));
  }

  private static BigDecimal decimal(RealNumeric r) {
    return switch (r) {
      case mathcanvas.engine.number.Int i -> new BigDecimal(i.value());
      case Rational q -> new BigDecimal(q.num()).divide(new BigDecimal(q.den()), DIGITS);
      case Real d -> d.value();
    };
  }

  /** The inner functions of the complex-shift property, each with an interval where it is real. */
  private static final List<String[]> SHIFTED =
      List.of(
          new String[] {"x^2", "-3", "3"},
          new String[] {"x^3 + 1", "-2", "2"},
          new String[] {"sin(x)", "-3", "3"},
          new String[] {"e^x", "-2", "2"},
          new String[] {"x", "-3", "3"},
          new String[] {"x^2 + x", "-3", "3"});

  /**
   * {@code g′/(g + c i)} and {@code g′·(g + c i)^-2}, {@code c} a non-zero rational: the substitute
   * of a complex inner function is complex, so the antiderivative differentiates back to the
   * integrand as a complex value at real points ({@code log(g + c i)}, not {@code log|·|}).
   */
  @Property(tries = 200, seed = "4104")
  void aComplexShiftIsIntegratedAsComplex(@ForAll Random random) {
    String[] g = SHIFTED.get(random.nextInt(SHIFTED.size()));
    int num = (random.nextBoolean() ? 1 : -1) * (1 + random.nextInt(5));
    int den = 1 + random.nextInt(3);
    String shifted = "(" + g[0] + " + (" + num + "/" + den + ") i)";
    Expr inner = IntegrateCases.parse(g[0]);
    Expr derivative = Calculus.differentiate(inner, IntegrateArbitraries.X, 1, CONTEXT).expr();
    Expr outer = IntegrateCases.parse(random.nextBoolean() ? "1/" + shifted : shifted + "^(-2)");
    Expr f = Canonical.of(Product.of(derivative, outer));
    Expr F = Calculus.integrate(f, IntegrateArbitraries.X, null, null, CONTEXT).expr();
    assertThat(IntegrateRules.complete(F, IntegrateArbitraries.X)).as("∫ %s", f).isTrue();
    IntegrateArbitraries.Case c =
        new IntegrateArbitraries.Case(
            f,
            IntegrateArbitraries.Family.SUBSTITUTION,
            Double.parseDouble(g[1]),
            Double.parseDouble(g[2]));
    boolean compared = false;
    for (int k = 0; k < 3; k++) {
      Map<mathcanvas.engine.expr.Symbol, Numeric> at =
          Map.of(IntegrateArbitraries.X, point(c, random));
      if (!DifferenceQuotient.defined(f, at)) {
        continue;
      }
      DifferenceQuotient.Outcome o =
          DifferenceQuotient.check(F, f, IntegrateArbitraries.X, at, Facts.NONE);
      compared |=
          o == DifferenceQuotient.Outcome.COMPARED || o == DifferenceQuotient.Outcome.RECHECKED;
    }
    Statistics.label("compared").collect(compared);
    Statistics.label("compared").coverage(k -> k.check(true).percentage(p -> p >= 90));
  }

  /** Integrands with no elementary antiderivative, which must stay within the cap. */
  private static final List<String> NON_ELEMENTARY =
      List.of(
          "e^(x^2)",
          "sin(x)/x",
          "factorial(x)",
          "x^x",
          "log(log(x))",
          "sqrt(sin(x))",
          "e^(x^2) sin(x) log(x)",
          "sin(x^2) cos(x^3) e^(x + sin(x)) tan(log(x))",
          "1/(x^5 + x + 1)",
          "x tan(x)");

  @Property(tries = 100, seed = "4103")
  void theRuleSetTerminates(@ForAll Random random) {
    List<Expr> sample = new ArrayList<>();
    Arbitrary<IntegrateArbitraries.Case> cases = cases();
    for (int i = 0; i < 10; i++) {
      sample.add(
          Integral.indefinite(
              cases.generator(1000).next(random).value().f(), IntegrateArbitraries.X));
    }
    String hard = NON_ELEMENTARY.get(random.nextInt(NON_ELEMENTARY.size()));
    sample.add(Integral.indefinite(IntegrateCases.parse(hard), IntegrateArbitraries.X));
    TerminationProbe.Report report;
    try {
      report = TerminationProbe.run(RuleSets.INTEGRATE, sample, CONTEXT);
    } catch (RewriteLimitException e) {
      throw new AssertionError("the cap was reached on " + sample, e);
    }
    Statistics.label("most rewrites").collect(report.maxRewrites());
  }
}
