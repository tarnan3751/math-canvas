package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.value;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Vector;
import mathcanvas.engine.number.RealNumeric;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.hipparchus.distribution.continuous.NormalDistribution;
import org.hipparchus.distribution.continuous.TDistribution;

/**
 * The z and t intervals against their formulas in {@code double}, with Hipparchus's quantiles, on
 * random summary statistics and levels.
 *
 * <p>The engine's own quantiles are found to a few units in the last place, so its ends agree with
 * an exact formula to 1e-9 relative. The t oracle is coarser: Hipparchus finds a t quantile by a
 * solver whose absolute accuracy in t is {@link #ORACLE_T_ACCURACY}, so the oracle's end {@code
 * mean ± t·se} may be off by that times {@code se}, which the t ends' tolerance adds (seed
 * 7806498494599799497: t off by 3.6e-10 at se = 4 put the oracle 1.45e-9 from an engine end exact
 * to 2e-14). The normal quantile has a closed form, and the z ends keep the plain tolerance.
 */
class IntervalsProperties {

  /**
   * The absolute accuracy in t of Hipparchus's {@code TDistribution.inverseCumulativeProbability},
   * its {@code AbstractRealDistribution.DEFAULT_SOLVER_ABSOLUTE_ACCURACY}.
   */
  private static final double ORACLE_T_ACCURACY = 1e-9;

  private static double end(String script, int i) {
    Vector v = (Vector) value(script);
    return ((RealNumeric) ((Num) v.items().get(i)).value()).toDouble();
  }

  private static void close(String what, double got, double want) {
    close(what, got, want, 0);
  }

  /** Within 1e-9 relative, and the oracle's own error {@code slack} more. */
  private static void close(String what, double got, double want, double slack) {
    assertThat(Math.abs(got - want))
        .as("%s: %s against %s", what, got, want)
        .isLessThanOrEqualTo(1e-9 * Math.max(1, Math.abs(want)) + slack);
  }

  @Property(tries = 60)
  void intervalsAgreeWithTheirFormulas(@ForAll Random random) {
    int mean = random.nextInt(41) - 20;
    int sd = 1 + random.nextInt(9);
    int n = 2 + random.nextInt(60);
    int level = 50 + random.nextInt(49);
    double c = level / 100.0;
    String args = mean + ", " + sd + ", " + n + ", " + level + "/100";
    double z = new NormalDistribution().inverseCumulativeProbability((1 + c) / 2);
    double se = sd / Math.sqrt(n);
    close("z low", end("zinterval(" + args + ")", 0), mean - z * se);
    close("z high", end("zinterval(" + args + ")", 1), mean + z * se);
    double t = new TDistribution(n - 1).inverseCumulativeProbability((1 + c) / 2);
    close("t low", end("tinterval(" + args + ")", 0), mean - t * se, ORACLE_T_ACCURACY * se);
    close("t high", end("tinterval(" + args + ")", 1), mean + t * se, ORACLE_T_ACCURACY * se);
  }
}
