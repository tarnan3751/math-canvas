package mathcanvas.engine.stat.numeric;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import mathcanvas.engine.provider.Distribute.Law;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.statistics.Statistics;
import org.hipparchus.exception.MathIllegalStateException;

/**
 * The quantiles in the tails: for a tail {@code t} in {@code [1e-15, 1e-3]}, lower or upper, the
 * quantile {@code x} of each continuous law with an iterative inverse gives back that tail itself,
 * {@code P(X ≤ x) = t} or {@code P(X > x) = t}, to {@code 1e-12} relative: the upper case is built
 * from {@code t}, never from {@code 1 − q} in {@code double}. A quantile that fails its own check
 * is no convergence, which t past {@code 10⁴} degrees of freedom may honestly declare (its tail
 * holds about {@code 1e-11} there, {@code docs/reference/engine.md} **Statistics**), and nothing
 * else may.
 */
class QuantileTailProperties {

  @Property(tries = 200)
  void quantilesInvertTheTails(
      @ForAll @DoubleRange(min = -15, max = -3) double exponent,
      @ForAll boolean upper,
      @ForAll @IntRange(min = 0, max = 3) int which,
      @ForAll @IntRange(min = 1, max = 200) int df,
      @ForAll @IntRange(min = 1, max = 200) int df2,
      @ForAll boolean large) {
    Law law = Law.values()[which];
    // t with up to 200 000 degrees of freedom as well, where its incomplete beta argument is near 1
    df *= large && law == Law.STUDENT_T ? 1000 : 1;
    double[] p =
        switch (law) {
          case NORMAL -> new double[] {df - 100.0, df2 / 7.0};
          case F -> new double[] {df, df2};
          default -> new double[] {df};
        };
    double tail = Math.pow(10, exponent);
    double x;
    try {
      x = DistributionMath.inverse(law, p, tail, upper);
    } catch (MathIllegalStateException noConvergence) {
      assertThat(law == Law.STUDENT_T && df >= 10_000)
          .as("%s %s, the tail %s: no convergence", law, Arrays.toString(p), tail)
          .isTrue();
      Statistics.collect("no convergence, large t");
      return;
    }
    Statistics.collect("inverted");
    double got = upper ? DistributionMath.upper(law, p, x) : DistributionMath.lower(law, p, x);
    assertThat(Math.abs(got - tail))
        .as(
            "%s %s, the %s tail %s: x = %s",
            law, Arrays.toString(p), upper ? "upper" : "lower", tail, x)
        .isLessThanOrEqualTo(1e-12 * tail);
  }
}
