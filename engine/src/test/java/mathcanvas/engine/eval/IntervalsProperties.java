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
 */
class IntervalsProperties {

  private static double end(String script, int i) {
    Vector v = (Vector) value(script);
    return ((RealNumeric) ((Num) v.items().get(i)).value()).toDouble();
  }

  private static void close(String what, double got, double want) {
    assertThat(Math.abs(got - want))
        .as("%s: %s against %s", what, got, want)
        .isLessThanOrEqualTo(1e-9 * Math.max(1, Math.abs(want)));
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
    close("t low", end("tinterval(" + args + ")", 0), mean - t * se);
    close("t high", end("tinterval(" + args + ")", 1), mean + t * se);
  }
}
