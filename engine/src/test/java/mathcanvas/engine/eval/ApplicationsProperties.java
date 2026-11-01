package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;
import java.util.function.DoubleUnaryOperator;
import mathcanvas.engine.compile.Compiled;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.ListExpr;
import mathcanvas.engine.expr.Num;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import mathcanvas.engine.number.RealNumeric;
import mathcanvas.engine.testing.AdaptiveSimpson;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.statistics.Statistics;

/**
 * The applications against numeric references: arc length and volume of generated polynomials
 * against adaptive Simpson quadrature of their integrands, the area between polynomials with
 * planted crossings against the unsigned pieces' quadrature, the extrema of polynomials with
 * planted critical points against the sign of the derivative either side, and the global extrema
 * against a dense scan.
 */
class ApplicationsProperties {

  private static final Symbol X = Symbol.of("x");

  /** A rational in [−3, 3] at steps of 1/8. */
  private static int eighths(Random r) {
    return r.nextInt(49) - 24;
  }

  private static String q(int eighths) {
    return "(" + eighths + "/8)";
  }

  private static String polynomial(Random r, int degree) {
    StringBuilder s = new StringBuilder();
    for (int k = 0; k <= degree; k++) {
      s.append(k == 0 ? "" : " + ").append(r.nextInt(7) - 3).append("/2 x^").append(k);
    }
    return s.toString();
  }

  private static double number(String statement) {
    Expr v = Run.value(statement);
    assertThat(v).as(statement).isInstanceOf(Num.class);
    return ((RealNumeric) ((Num) v).value()).toDouble();
  }

  private static DoubleUnaryOperator compiled(String f) {
    Compiled c = Compiled.compile(Run.value(f), List.of(X), Settings.DEFAULT);
    return c::eval;
  }

  /** The value of an exact constant, compiled. */
  private static double constant(Expr e) {
    return Compiled.compile(e, List.of(), Settings.DEFAULT).eval();
  }

  private static double simpson(DoubleUnaryOperator f, double a, double b) {
    return AdaptiveSimpson.integrate(f, a, b, 1e-13).value();
  }

  private static void close(double actual, double expected, String what) {
    assertThat(Math.abs(actual - expected))
        .as("%s: %s against %s", what, actual, expected)
        .isLessThanOrEqualTo(1e-10 * Math.max(1, Math.abs(expected)));
  }

  @Property(tries = 60, seed = "4201")
  void theArcLengthOfAPolynomialIsItsQuadrature(@ForAll Random random) {
    String f = polynomial(random, 1 + random.nextInt(3));
    int a = eighths(random);
    int b = a + 1 + random.nextInt(16);
    DoubleUnaryOperator d = compiled("diff(" + f + ", x)");
    double expected =
        simpson(t -> Math.sqrt(1 + d.applyAsDouble(t) * d.applyAsDouble(t)), a / 8.0, b / 8.0);
    double actual = number("numeric(arclength(" + f + ", x, " + q(a) + ", " + q(b) + "), 20)");
    close(actual, expected, "arclength " + f);
  }

  @Property(tries = 60, seed = "4202")
  void aLineAndACircleArcHaveTheirClosedForms(@ForAll Random random) {
    int m = random.nextInt(9) - 4;
    int a = eighths(random);
    int b = a + 1 + random.nextInt(16);
    close(
        number("numeric(arclength(" + m + "x + 1, x, " + q(a) + ", " + q(b) + "), 20)"),
        (b - a) / 8.0 * Math.sqrt(1 + m * m),
        "line");
    int r = 1 + random.nextInt(4);
    close(
        number(
            "numeric(arclength(["
                + r
                + " cos(t), "
                + r
                + " sin(t)], t, "
                + q(a)
                + ", "
                + q(b)
                + "), 20)"),
        r * (b - a) / 8.0,
        "circle arc");
  }

  @Property(tries = 80, seed = "4203")
  void theAreaBetweenPolynomialsIsTheSumOfUnsignedPieces(@ForAll Random random) {
    TreeSet<Integer> roots = new TreeSet<>();
    int n = 1 + random.nextInt(3);
    while (roots.size() < n) {
      roots.add(eighths(random));
    }
    StringBuilder h = new StringBuilder(String.valueOf(1 + random.nextInt(3)));
    for (int r : roots) {
      h.append(" (x - ").append(q(r)).append(")");
    }
    String g = polynomial(random, 2);
    String f = h + " + " + g;
    int a = roots.first() - 1 - random.nextInt(8);
    int b = roots.last() + 1 + random.nextInt(8);
    DoubleUnaryOperator hd = compiled(h.toString());
    List<Double> ends = new ArrayList<>();
    ends.add(a / 8.0);
    roots.forEach(r -> ends.add(r / 8.0));
    ends.add(b / 8.0);
    double expected = 0;
    for (int k = 0; k + 1 < ends.size(); k++) {
      expected += Math.abs(simpson(hd, ends.get(k), ends.get(k + 1)));
    }
    String call = "area(" + f + ", " + g + ", x, " + q(a) + ", " + q(b) + ")";
    Expr exact = Run.value(call);
    Statistics.label("exact").collect(!exact.toString().contains("Integral"));
    close(number("numeric(" + call + ", 20)"), expected, call);
  }

  @Property(tries = 60, seed = "4204")
  void theVolumeOfAPolynomialIsItsQuadrature(@ForAll Random random) {
    String f = polynomial(random, 1 + random.nextInt(3));
    int a = eighths(random);
    int b = a + 1 + random.nextInt(16);
    DoubleUnaryOperator fd = compiled(f);
    double expected =
        simpson(t -> Math.PI * fd.applyAsDouble(t) * fd.applyAsDouble(t), a / 8.0, b / 8.0);
    close(number("numeric(volume(" + f + ", x, " + q(a) + ", " + q(b) + "), 20)"), expected, f);
  }

  /** f′ = c ∏ (x − r) for planted distinct rational roots, and f its antiderivative. */
  private static String planted(Random random, TreeSet<Integer> roots) {
    int n = 1 + random.nextInt(4);
    while (roots.size() < n) {
      roots.add(eighths(random));
    }
    StringBuilder d = new StringBuilder(String.valueOf(random.nextBoolean() ? 1 : -2));
    for (int r : roots) {
      d.append(" (x - ").append(q(r)).append(")");
      if (random.nextInt(4) == 0) {
        // a double root: no change of sign, so neither
        d.append(" (x - ").append(q(r)).append(")");
      }
    }
    return d.toString();
  }

  @Property(tries = 60, seed = "4205")
  void theExtremaAreWhereTheDerivativeChangesSign(@ForAll Random random) {
    TreeSet<Integer> roots = new TreeSet<>();
    String d = planted(random, roots);
    String f = "integrate(" + d + ", x)";
    Expr result = Run.value("extrema(" + f + ", x)");
    assertThat(result).as("extrema of %s", f).isInstanceOf(SetExpr.class);
    DoubleUnaryOperator dd = compiled(d);
    TreeSet<Double> expected = new TreeSet<>();
    for (int r : roots) {
      double left = dd.applyAsDouble(r / 8.0 - 1e-3);
      double right = dd.applyAsDouble(r / 8.0 + 1e-3);
      if (Math.signum(left) != Math.signum(right)) {
        expected.add(r / 8.0);
      }
    }
    TreeSet<Double> found = new TreeSet<>();
    for (Expr p : ((SetExpr) result).items()) {
      Expr x0 = ((ListExpr) p).items().get(0);
      found.add(constant(x0));
    }
    assertThat(found).as("extrema of %s", f).hasSameSizeAs(expected);
    for (double e : expected) {
      assertThat(found.stream().anyMatch(v -> Math.abs(v - e) < 1e-12))
          .as("%s in %s", e, found)
          .isTrue();
    }
  }

  @Property(tries = 60, seed = "4207")
  void theFirstDerivativeTestDoesNotStepAcrossAPole(@ForAll Random random) {
    // f′ = c ∏ (x − r) / (x − p)^k, k odd, a pole within 1 of the roots; f its antiderivative
    // with log(x − p) for log|x − p|, so that f′ is the rational function again
    int pole = eighths(random);
    TreeSet<Integer> roots = new TreeSet<>();
    int n = 1 + random.nextInt(3);
    while (roots.size() < n) {
      int r = pole + random.nextInt(17) - 8;
      if (r != pole) {
        roots.add(r);
      }
    }
    StringBuilder d = new StringBuilder(String.valueOf(random.nextBoolean() ? 1 : -2));
    for (int r : roots) {
      d.append(" (x - ").append(q(r)).append(")");
      if (random.nextInt(4) == 0) {
        d.append(" (x - ").append(q(r)).append(")");
      }
    }
    d.append("/(x - ").append(q(pole)).append(")^").append(random.nextBoolean() ? 1 : 3);
    String antiderivative = Run.shown("integrate(" + d + ", x)");
    String f = antiderivative.replaceAll("log\\(\\|([^|]*)\\|\\)", "log($1)");
    Statistics.label("logarithm").collect(!f.equals(antiderivative));
    Expr result = Run.value("extrema(" + f + ", x)");
    assertThat(result).as("extrema of %s", f).isInstanceOf(SetExpr.class);
    DoubleUnaryOperator dd = compiled(d.toString());
    TreeSet<Double> expected = new TreeSet<>();
    for (int r : roots) {
      if (Math.signum(dd.applyAsDouble(r / 8.0 - 1e-3))
          != Math.signum(dd.applyAsDouble(r / 8.0 + 1e-3))) {
        expected.add(r / 8.0);
      }
    }
    TreeSet<Double> found = new TreeSet<>();
    for (Expr p : ((SetExpr) result).items()) {
      found.add(constant(((ListExpr) p).items().get(0)));
    }
    assertThat(found).as("extrema of %s", f).hasSameSizeAs(expected);
    for (double e : expected) {
      assertThat(found.stream().anyMatch(v -> Math.abs(v - e) < 1e-12))
          .as("%s in %s, extrema of %s", e, found, f)
          .isTrue();
    }
  }

  @Property(tries = 60, seed = "4206")
  void theGlobalExtremaBeatADenseScan(@ForAll Random random) {
    TreeSet<Integer> roots = new TreeSet<>();
    String d = planted(random, roots);
    String f = "integrate(" + d + ", x)";
    int a = eighths(random);
    int b = a + 1 + random.nextInt(24);
    DoubleUnaryOperator fd = compiled(f);
    double max = Double.NEGATIVE_INFINITY;
    double min = Double.POSITIVE_INFINITY;
    for (int k = 0; k <= 20_000; k++) {
      double v = fd.applyAsDouble(a / 8.0 + (b - a) / 8.0 * k / 20_000);
      max = Math.max(max, v);
      min = Math.min(min, v);
    }
    for (String command : List.of("maximize", "minimize")) {
      Expr p = Run.value(command + "(" + f + ", x, " + q(a) + ", " + q(b) + ")");
      assertThat(p).as("%s of %s", command, f).isInstanceOf(ListExpr.class);
      double y = constant(((ListExpr) p).items().get(1));
      double scan = command.equals("maximize") ? max : min;
      // the exact extremum is at least as good as every scanned value, and close to the best
      if (command.equals("maximize")) {
        assertThat(y)
            .as("%s of %s", command, f)
            .isGreaterThanOrEqualTo(scan - 1e-9 * Math.max(1, Math.abs(scan)));
      } else {
        assertThat(y)
            .as("%s of %s", command, f)
            .isLessThanOrEqualTo(scan + 1e-9 * Math.max(1, Math.abs(scan)));
      }
      assertThat(Math.abs(y - scan)).isLessThan(1e-3 * Math.max(1, Math.abs(scan)));
    }
  }
}
