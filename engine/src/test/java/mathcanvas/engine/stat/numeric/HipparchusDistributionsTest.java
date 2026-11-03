package mathcanvas.engine.stat.numeric;

import static org.assertj.core.api.Assertions.assertThat;

import mathcanvas.engine.provider.Distribute;
import mathcanvas.engine.provider.Distribute.Kind;
import mathcanvas.engine.provider.Distribute.Law;
import mathcanvas.engine.provider.DistributionValue;
import mathcanvas.engine.provider.DistributionValue.Status;
import mathcanvas.engine.provider.Steps;
import mathcanvas.engine.rule.Context;
import org.assertj.core.data.Offset;
import org.assertj.core.data.Percentage;
import org.junit.jupiter.api.Test;

/**
 * The default distribute provider against published tables (NIST/SEMATECH e-Handbook of Statistical
 * Methods, §1.3.6.7) and closed forms.
 */
class HipparchusDistributionsTest {

  private static final double INF = Double.POSITIVE_INFINITY;

  private static double run(Law law, Kind kind, double[] parameters, double... points) {
    DistributionValue v =
        new HipparchusDistributions()
            .apply(new Distribute(law, kind, parameters, points), Context.DEFAULT, Steps.NONE)
            .orElseThrow();
    assertThat(v.status()).isEqualTo(Status.OK);
    return v.value();
  }

  @Test
  void normalProbabilitiesMatchTheTable() {
    double[] standard = {0, 1};
    assertThat(run(Law.NORMAL, Kind.PROBABILITY, standard, -1.96, 1.96))
        .isCloseTo(0.950004209703559, Offset.offset(1e-15));
    assertThat(run(Law.NORMAL, Kind.PROBABILITY, standard, -INF, 1))
        .isCloseTo(0.841344746068543, Offset.offset(1e-15));
    assertThat(run(Law.NORMAL, Kind.PROBABILITY, standard, -INF, INF)).isEqualTo(1.0);
    // an upper tail keeps its digits: P(Z > 6) = 9.8658765e-10
    assertThat(run(Law.NORMAL, Kind.PROBABILITY, standard, 6, INF))
        .isCloseTo(9.865876450376946e-10, Percentage.withPercentage(1e-10));
    assertThat(run(Law.NORMAL, Kind.INVERSE, standard, 0.975))
        .isCloseTo(1.959963984540054, Offset.offset(1e-14));
  }

  @Test
  void quantilesMatchTheTables() {
    assertThat(run(Law.STUDENT_T, Kind.INVERSE, new double[] {10}, 0.975))
        .isCloseTo(2.228138851986, Offset.offset(1e-12));
    assertThat(run(Law.CHI_SQUARED, Kind.INVERSE, new double[] {3}, 0.95))
        .isCloseTo(7.814727903251, Offset.offset(1e-12));
    assertThat(run(Law.F, Kind.INVERSE, new double[] {5, 10}, 0.95))
        .isCloseTo(3.325834530413, Offset.offset(1e-11));
  }

  /**
   * Quantiles in the tails against mpmath at 50 digits (bisection on {@code betainc}, {@code
   * gammainc} and {@code erfc}), to the digits the tail functions hold: a lower tail at the {@code
   * double} probability, an upper one at the exact decimal tail, as the language forms it.
   */
  @Test
  void tailQuantilesMatchMpmath() {
    quantile(Law.NORMAL, new double[] {0, 1}, 1e-10, -6.3613409024040561991);
    quantile(Law.NORMAL, new double[] {0, 1}, 1e-17, -8.4937932241095980661);
    quantile(Law.NORMAL, new double[] {0, 1}, 1e-300, -37.047096299361199237);
    upper(Law.STUDENT_T, new double[] {10}, 0.025, 2.2281388519862747484);
    upper(Law.STUDENT_T, new double[] {100000}, 0.025, 1.9599877075346096386);
    upper(Law.STUDENT_T, new double[] {3}, 1e-20, 4795275.7204689731896);
    upper(Law.NORMAL, new double[] {0, 1}, 1e-300, 37.047096299361199237);
    quantile(Law.STUDENT_T, new double[] {3}, 1e-20, -4795275.7204689731896);
    quantile(Law.STUDENT_T, new double[] {1}, 1e-12, -318309886183.79067794);
    quantile(Law.CHI_SQUARED, new double[] {10}, 1e-15, 0.0052126058791211738686);
    upper(Law.CHI_SQUARED, new double[] {3}, 0.05, 7.8147279032511799553);
    upper(Law.CHI_SQUARED, new double[] {1}, 1e-12, 50.844127911818155546);
    upper(Law.F, new double[] {1, 1}, 1e-9, 405284734569351085.11);
    upper(Law.F, new double[] {3, 7}, 1e-12, 7966.5510382581691689);
    quantile(Law.F, new double[] {3, 7}, 1e-12, 7.5505322301390720102e-9);
  }

  private static void quantile(Law law, double[] parameters, double q, double want) {
    assertThat(run(law, Kind.INVERSE, parameters, q))
        .as("%s quantile at %s", law, q)
        .isCloseTo(want, Percentage.withPercentage(1e-11));
  }

  private static void upper(Law law, double[] parameters, double tail, double want) {
    assertThat(run(law, Kind.UPPER_INVERSE, parameters, tail))
        .as("%s quantile of the upper tail %s", law, tail)
        .isCloseTo(want, Percentage.withPercentage(1e-11));
  }

  /**
   * The χ² density near 0 against mpmath, its values written to 17 digits: Hipparchus's gave 0.0 at
   * 1e-20 for k = 1.
   */
  @Test
  void chiSquaredDensityNearZero() {
    double[][] cases = {
      {1, 1e-20, 3989422804.0143268888},
      {1, 1e-10, 39894.228038148555665},
      {1, 3e-7, 728.36551113988453175},
      {3, 1e-20, 3.98942280401432667e-11},
      {3, 3e-7, 0.00021850965334196534964},
      {7, 5, 0.12204152134938739261}
    };
    for (double[] c : cases) {
      assertThat(run(Law.CHI_SQUARED, Kind.DENSITY, new double[] {c[0]}, c[1]))
          .as("chi2pdf(%s, %s)", c[1], c[0])
          .isCloseTo(c[2], Percentage.withPercentage(1e-11));
    }
  }

  /** A tail below {@code Double.MIN_NORMAL} has lost digits: no convergence, for every law. */
  @Test
  void aSubnormalTailIsNoConvergence() {
    double[][] laws = {{0, 1}, {3}, {3}, {2, 5}, {1}, {0, 1}};
    Law[] which = {Law.NORMAL, Law.STUDENT_T, Law.CHI_SQUARED, Law.F, Law.EXPONENTIAL, Law.UNIFORM};
    for (int i = 0; i < which.length; i++) {
      for (Kind kind : new Kind[] {Kind.INVERSE, Kind.UPPER_INVERSE}) {
        DistributionValue v =
            new HipparchusDistributions()
                .apply(
                    new Distribute(which[i], kind, laws[i], new double[] {1e-320}),
                    Context.DEFAULT,
                    Steps.NONE)
                .orElseThrow();
        assertThat(v.status()).as("%s %s", which[i], kind).isEqualTo(Status.NO_CONVERGENCE);
      }
    }
  }

  /** At large degrees of freedom t's tail keeps its digits; a million still gives a value. */
  @Test
  void studentsTAtLargeDegreesOfFreedom() {
    assertThat(run(Law.STUDENT_T, Kind.UPPER_INVERSE, new double[] {1e6}, 0.025))
        .isCloseTo(1.9599663568141070353, Percentage.withPercentage(1e-9));
    assertThat(run(Law.STUDENT_T, Kind.PROBABILITY, new double[] {1e5}, 1.96, INF))
        .isCloseTo(0.024999281597150819, Percentage.withPercentage(1e-11));
  }

  @Test
  void tailsAgreeWithEachOther() {
    for (double x : new double[] {-3, -0.5, 0.25, 2, 7}) {
      double[] t = {4};
      assertThat(
              run(Law.STUDENT_T, Kind.PROBABILITY, t, -INF, x)
                  + run(Law.STUDENT_T, Kind.PROBABILITY, t, x, INF))
          .isCloseTo(1.0, Offset.offset(1e-15));
    }
    // χ² with 2 degrees of freedom is the exponential with rate 1/2
    assertThat(run(Law.CHI_SQUARED, Kind.PROBABILITY, new double[] {2}, 1, 3))
        .isCloseTo(Math.exp(-0.5) - Math.exp(-1.5), Offset.offset(1e-15));
  }

  @Test
  void discreteLawsCountWhatTheLanguageCounts() {
    assertThat(run(Law.BINOMIAL, Kind.DENSITY, new double[] {10, 0.5}, 3))
        .isCloseTo(15.0 / 128, Offset.offset(1e-15));
    assertThat(run(Law.BINOMIAL, Kind.PROBABILITY, new double[] {10, 0.5}, -INF, 3))
        .isCloseTo(176.0 / 1024, Offset.offset(1e-15));
    // geometric trials: P(K = 1) = p
    assertThat(run(Law.GEOMETRIC, Kind.DENSITY, new double[] {0.25}, 1)).isEqualTo(0.25);
    // p = 1: the first trial succeeds for certain
    assertThat(run(Law.GEOMETRIC, Kind.DENSITY, new double[] {1}, 1)).isEqualTo(1.0);
    assertThat(run(Law.GEOMETRIC, Kind.DENSITY, new double[] {1}, 2)).isEqualTo(0.0);
    assertThat(run(Law.GEOMETRIC, Kind.PROBABILITY, new double[] {1}, -INF, 3)).isEqualTo(1.0);
    assertThat(run(Law.GEOMETRIC, Kind.PROBABILITY, new double[] {1}, 2, 3)).isEqualTo(0.0);
    assertThat(run(Law.GEOMETRIC, Kind.PROBABILITY, new double[] {0.25}, -INF, 2))
        .isCloseTo(1 - 0.75 * 0.75, Offset.offset(1e-15));
    assertThat(run(Law.POISSON, Kind.PROBABILITY, new double[] {2}, -INF, 3))
        .isCloseTo(19 * Math.exp(-2) / 3, Offset.offset(1e-14));
    assertThat(run(Law.HYPERGEOMETRIC, Kind.DENSITY, new double[] {10, 4, 3}, 2))
        .isCloseTo(36.0 / 120, Offset.offset(1e-15));
  }

  @Test
  void specialFunctions() {
    assertThat(run(Law.GAMMA, Kind.VALUE, new double[] {}, 0.5))
        .isCloseTo(Math.sqrt(Math.PI), Offset.offset(1e-15));
    assertThat(run(Law.BETA, Kind.VALUE, new double[] {0.5}, 0.5))
        .isCloseTo(Math.PI, Offset.offset(1e-14));
    assertThat(run(Law.ERF, Kind.VALUE, new double[] {}, 1))
        .isCloseTo(0.8427007929497149, Offset.offset(1e-15));
    DistributionValue pole =
        new HipparchusDistributions()
            .apply(
                new Distribute(Law.GAMMA, Kind.VALUE, new double[] {}, new double[] {-2}),
                Context.DEFAULT,
                Steps.NONE)
            .orElseThrow();
    assertThat(pole.status()).isEqualTo(Status.NOT_FINITE);
    assertThat(DistributionMath.beta(0.5, -0.5)).isEqualTo(0.0);
    // a non-zero value below the double range is beyond it, as one above is; a true zero is not
    for (double[] at : new double[][] {{-200.5}, {30}}) {
      Law law = at[0] < 0 ? Law.GAMMA : Law.ERFC;
      DistributionValue under =
          new HipparchusDistributions()
              .apply(
                  new Distribute(law, Kind.VALUE, new double[] {}, at), Context.DEFAULT, Steps.NONE)
              .orElseThrow();
      assertThat(under.status()).as("%s", law).isEqualTo(Status.NOT_FINITE);
    }
    DistributionValue tiny =
        new HipparchusDistributions()
            .apply(
                new Distribute(Law.BETA, Kind.VALUE, new double[] {6000}, new double[] {6000}),
                Context.DEFAULT,
                Steps.NONE)
            .orElseThrow();
    assertThat(tiny.status()).isEqualTo(Status.NOT_FINITE);
    assertThat(run(Law.BETA, Kind.VALUE, new double[] {-0.5}, 0.5)).isEqualTo(0.0);
  }
}
