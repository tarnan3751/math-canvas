package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.value;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.print.AsciiPrinter;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.hipparchus.distribution.continuous.ChiSquaredDistribution;
import org.hipparchus.distribution.continuous.TDistribution;

/**
 * The tests' statistics against the hand formulas in {@code double} on random data, and their
 * {@code p}-values against Hipparchus's distributions assembled independently.
 */
class HypothesisTestsProperties {

  private static double[] data(Random random, int n) {
    double[] x = new double[n];
    for (int i = 0; i < n; i++) {
      x[i] = random.nextInt(41) - 20;
    }
    return x;
  }

  private static String list(double[] x) {
    List<String> items = new ArrayList<>();
    for (double v : x) {
      items.add(Long.toString((long) v));
    }
    return "[" + String.join(", ", items) + "]";
  }

  /** The value of each relation of a test's result, by position. */
  private static double at(Expr result, int index) {
    Expr right = ((Relation) ((ListExpr) result).items().get(index)).right();
    Expr v = value("numeric(" + AsciiPrinter.INSTANCE.print(right) + ", 30)");
    return ((RealNumeric) ((Num) v).value()).toDouble();
  }

  private static void close(String what, double got, double want, double tolerance) {
    assertThat(Math.abs(got - want))
        .as("%s: %s against %s", what, got, want)
        .isLessThanOrEqualTo(tolerance * Math.max(1, Math.abs(want)));
  }

  private static double mean(double[] x) {
    double s = 0;
    for (double v : x) {
      s += v;
    }
    return s / x.length;
  }

  private static double variance(double[] x) {
    double m = mean(x);
    double s = 0;
    for (double v : x) {
      s += (v - m) * (v - m);
    }
    return s / (x.length - 1);
  }

  @Property(tries = 40)
  void tTestsAgreeWithTheirFormulas(@ForAll Random random) {
    double[] x = data(random, 3 + random.nextInt(12));
    double[] y = data(random, 3 + random.nextInt(12));
    if (variance(x) == 0 || variance(y) == 0) {
      return;
    }
    int mu0 = random.nextInt(11) - 5;
    Expr one = value("ttest(" + list(x) + ", " + mu0 + ")");
    double t = (mean(x) - mu0) / Math.sqrt(variance(x) / x.length);
    close("t", at(one, 0), t, 1e-12);
    double p = 2 * (1 - new TDistribution(x.length - 1).cumulativeProbability(Math.abs(t)));
    close("p", at(one, 2), p, 1e-9);
    Expr two = value("ttest2(" + list(x) + ", " + list(y) + ")");
    double a1 = variance(x) / x.length;
    double a2 = variance(y) / y.length;
    double t2 = (mean(x) - mean(y)) / Math.sqrt(a1 + a2);
    double df = (a1 + a2) * (a1 + a2) / (a1 * a1 / (x.length - 1) + a2 * a2 / (y.length - 1));
    close("Welch t", at(two, 0), t2, 1e-12);
    close("Welch df", at(two, 1), df, 1e-12);
    close(
        "Welch p",
        at(two, 2),
        2 * (1 - new TDistribution(df).cumulativeProbability(Math.abs(t2))),
        1e-9);
  }

  @Property(tries = 40)
  void chiSquaredAgreesWithItsFormula(@ForAll Random random) {
    int k = 2 + random.nextInt(5);
    double[] o = new double[k];
    double[] e = new double[k];
    // the expected counts sum to the observed total: the last is what the others leave
    do {
      double total = 0;
      for (int i = 0; i < k; i++) {
        o[i] = random.nextInt(50);
        total += o[i];
      }
      for (int i = 0; i < k - 1; i++) {
        e[i] = 1 + random.nextInt(50);
        total -= e[i];
      }
      e[k - 1] = total;
    } while (e[k - 1] < 1);
    Expr r = value("chi2gof(" + list(o) + ", " + list(e) + ")");
    double chi2 = 0;
    for (int i = 0; i < k; i++) {
      chi2 += (o[i] - e[i]) * (o[i] - e[i]) / e[i];
    }
    close("χ²", at(r, 0), chi2, 1e-12);
    close("p", at(r, 2), 1 - new ChiSquaredDistribution(k - 1).cumulativeProbability(chi2), 1e-9);
  }
}
