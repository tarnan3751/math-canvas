package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.value;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.match.Operands;
import mathcanvas.engine.number.RealNumeric;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

/**
 * The descriptive statistics against a plain {@code double} computation on random data (relative
 * {@code 1e-12}), and exact on random rational data: no decimal in the value.
 */
class DescriptiveStatisticsProperties {

  private static final double RELATIVE = 1e-12;

  private static double[] data(Random random) {
    int n = 4 + random.nextInt(27);
    double[] x = new double[n];
    for (int i = 0; i < n; i++) {
      // integers with repeats, so the order statistics meet ties
      x[i] = random.nextInt(41) - 20;
    }
    return x;
  }

  private static String list(double[] x) {
    return Arrays.stream(x)
        .mapToObj(v -> Long.toString((long) v))
        .collect(Collectors.joining(", ", "[", "]"));
  }

  private static double numeric(String script) {
    Expr v = value("numeric(" + script + ", 30)");
    assertThat(v).as(script).isInstanceOf(Num.class);
    return ((RealNumeric) ((Num) v).value()).toDouble();
  }

  private static void close(String script, double want) {
    double got = numeric(script);
    assertThat(Math.abs(got - want))
        .as("%s: %s against %s", script, got, want)
        .isLessThanOrEqualTo(RELATIVE * Math.max(1, Math.abs(want)));
  }

  private static double mean(double[] x) {
    return Arrays.stream(x).sum() / x.length;
  }

  private static double central(double[] x, int k) {
    double m = mean(x);
    return Arrays.stream(x).map(v -> Math.pow(v - m, k)).sum();
  }

  private static double medianOf(double[] sorted, int from, int count) {
    int mid = from + count / 2;
    return count % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2;
  }

  @Property(tries = 60)
  void momentsAgreeWithDoubles(@ForAll Random random) {
    double[] x = data(random);
    if (central(x, 2) == 0) {
      return;
    }
    int n = x.length;
    String d = list(x);
    double variance = central(x, 2) / (n - 1);
    close("mean(" + d + ")", mean(x));
    close("variance(" + d + ")", variance);
    close("pvariance(" + d + ")", central(x, 2) / n);
    close("stdev(" + d + ")", Math.sqrt(variance));
    close(
        "skewness(" + d + ")", n / ((n - 1.0) * (n - 2)) * central(x, 3) / Math.pow(variance, 1.5));
    close(
        "kurtosis(" + d + ")",
        n * (n + 1.0) / ((n - 1.0) * (n - 2) * (n - 3)) * central(x, 4) / (variance * variance)
            - 3.0 * (n - 1) * (n - 1) / ((n - 2.0) * (n - 3)));
  }

  @Property(tries = 60)
  void orderStatisticsAgreeWithDoubles(@ForAll Random random) {
    double[] x = data(random);
    double[] s = x.clone();
    Arrays.sort(s);
    int n = s.length;
    String d = list(x);
    close("median(" + d + ")", medianOf(s, 0, n));
    close("nth(quartiles(" + d + "), 1)", medianOf(s, 0, n / 2));
    close("nth(quartiles(" + d + "), 3)", medianOf(s, n - n / 2, n / 2));
    int p = random.nextInt(101);
    double h = (n - 1) * p / 100.0;
    int lo = (int) Math.floor(h);
    double want = lo + 1 < n ? s[lo] + (h - lo) * (s[lo + 1] - s[lo]) : s[lo];
    close("percentile(" + d + ", " + p + ")", want);
    close("range(" + d + ")", s[n - 1] - s[0]);
  }

  @Property(tries = 60)
  void pairedStatisticsAgreeWithDoubles(@ForAll Random random) {
    double[] x = data(random);
    double[] y = new double[x.length];
    for (int i = 0; i < y.length; i++) {
      y[i] = random.nextInt(41) - 20;
    }
    if (central(x, 2) == 0 || central(y, 2) == 0) {
      return;
    }
    double mx = mean(x);
    double my = mean(y);
    double sxy = 0;
    for (int i = 0; i < x.length; i++) {
      sxy += (x[i] - mx) * (y[i] - my);
    }
    String args = list(x) + ", " + list(y);
    close("cov(" + args + ")", sxy / (x.length - 1));
    close("cor(" + args + ")", sxy / Math.sqrt(central(x, 2) * central(y, 2)));
  }

  @Property(tries = 60)
  void rationalDataGivesExactValues(@ForAll Random random) {
    int n = 4 + random.nextInt(10);
    List<String> items = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      items.add((random.nextInt(41) - 20) + "/" + (1 + random.nextInt(9)));
    }
    String d = "[" + String.join(", ", items) + "]";
    for (String f :
        List.of(
            "mean",
            "median",
            "variance",
            "pvariance",
            "stdev",
            "quartiles",
            "iqr",
            "skewness",
            "summary",
            "range",
            "cumsum")) {
      Expr v = value(f + "(" + d + ")");
      assertThat(holdsDecimal(v)).as("%s(%s) = %s", f, d, v).isFalse();
    }
  }

  private static boolean holdsDecimal(Expr e) {
    if (e instanceof Num n) {
      return !n.value().isExact();
    }
    return Operands.of(e).stream().anyMatch(DescriptiveStatisticsProperties::holdsDecimal);
  }
}
