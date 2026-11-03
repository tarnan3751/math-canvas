package mathcanvas.engine.compile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.eval.Environment;
import mathcanvas.engine.eval.Evaluator;
import mathcanvas.engine.eval.Settings;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.parse.Parser;
import mathcanvas.engine.provider.Distribute.Law;
import mathcanvas.engine.stat.numeric.DistributionMath;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

/**
 * The densities integrate to their probabilities: each density as evaluation gives it (the closed
 * form, or the call the compiler evaluates), compiled and integrated by {@link Quadrature} over a
 * random interval, against the provider's probability of the interval (relative {@code 1e-8}); and
 * the whole normal line, through a wide interval, has probability 1.
 */
class DensityQuadratureProperties {

  private static final Symbol X = Symbol.of("x");

  private static double integral(String density, double a, double b) {
    Environment env = Environment.EMPTY;
    Compiled f =
        Compiled.compile(
            Evaluator.evaluate(Parser.parseExpression(density, env.names()), env, false).value(),
            List.of(X),
            Settings.DEFAULT);
    Quadrature.Estimate e = Quadrature.integrate(f, a, b, 1e-12, Cancellation.NEVER);
    assertThat(e.converged()).as("%s on [%s, %s]", density, a, b).isTrue();
    return e.value();
  }

  @Property(tries = 100)
  void densitiesIntegrateToTheirProbabilities(
      @ForAll @IntRange(min = 0, max = 4) int law,
      @ForAll @IntRange(min = 1, max = 20) int k,
      @ForAll @IntRange(min = 1, max = 20) int k2,
      @ForAll @IntRange(min = 1, max = 400) int from,
      @ForAll @IntRange(min = 1, max = 400) int width) {
    double a = from / 100.0;
    double b = a + width / 100.0;
    String density;
    Law l;
    double[] p;
    switch (law) {
      case 0 -> {
        density = "normalpdf(x, 1/2, " + k + "/4)";
        l = Law.NORMAL;
        p = new double[] {0.5, k / 4.0};
        a -= 2;
      }
      case 1 -> {
        density = "tpdf(x, " + k + ")";
        l = Law.STUDENT_T;
        p = new double[] {k};
        a -= 3;
      }
      case 2 -> {
        density = "chi2pdf(x, " + k + ")";
        l = Law.CHI_SQUARED;
        p = new double[] {k};
      }
      case 3 -> {
        density = "fpdf(x, " + k + ", " + k2 + ")";
        l = Law.F;
        p = new double[] {k, k2};
      }
      default -> {
        density = "exppdf(x, " + k + "/5)";
        l = Law.EXPONENTIAL;
        p = new double[] {k / 5.0};
      }
    }
    double want = DistributionMath.probability(l, p, a, b);
    double got = integral(density, a, b);
    assertThat(Math.abs(got - want))
        .as("%s on [%s, %s]", density, a, b)
        .isLessThanOrEqualTo(1e-8 * Math.max(want, 1e-3));
  }

  @Property(tries = 10)
  void theNormalDensityIntegratesToOne(@ForAll @IntRange(min = 1, max = 8) int sigma) {
    assertThat(integral("normalpdf(x, 1, " + sigma + ")", 1 - 40.0 * sigma, 1 + 40.0 * sigma))
        .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-12));
  }
}
