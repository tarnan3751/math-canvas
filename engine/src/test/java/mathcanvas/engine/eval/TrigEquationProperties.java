package mathcanvas.engine.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import mathcanvas.engine.compile.Compiled;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.SetExpr;
import mathcanvas.engine.expr.Symbol;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Random trigonometric equations, in radians and in degrees: every general solution makes the
 * equation hold at {@code k} from −2 to 2, and a numeric scan of a window finds no root that the
 * families miss.
 */
class TrigEquationProperties {

  private static final Symbol X = Symbol.of("x");
  private static final Symbol K = Symbol.of("k");

  private static final String[] FUNCTIONS = {"sin", "cos", "tan", "cot", "sec", "csc"};
  private static final String[] SLOPES = {"1", "2", "3", "-1", "1/2", "-2", "3/2"};
  private static final String[] RADIAN_OFFSETS = {"0", "π/6", "π/4", "-π/3", "1"};
  private static final String[] DEGREE_OFFSETS = {"0", "30", "45", "-60", "1"};
  private static final String[] VALUES = {
    "0", "1/2", "-1/2", "1", "-1", "√2/2", "-√3/2", "1/3", "-2/5", "2", "√3", "-3", "5/4"
  };

  /** An equation and the mode it is solved in. */
  record Case(String equation, boolean degrees) {}

  @Provide
  Arbitrary<Case> cases() {
    return Arbitraries.randomValue(TrigEquationProperties::generate);
  }

  static Case generate(Random random) {
    boolean degrees = random.nextInt(3) == 0;
    String[] offsets = degrees ? DEGREE_OFFSETS : RADIAN_OFFSETS;
    String f = FUNCTIONS[random.nextInt(FUNCTIONS.length)];
    String arg =
        "("
            + SLOPES[random.nextInt(SLOPES.length)]
            + ")x + "
            + offsets[random.nextInt(offsets.length)];
    String kernel = f + "(" + arg + ")";
    String equation =
        switch (random.nextInt(6)) {
          case 0, 1 -> kernel + " = " + VALUES[random.nextInt(VALUES.length)];
          case 4 -> {
            // a quotient: its denominator's zeros are no solutions
            String top = random.nextBoolean() ? "sin" : "cos";
            String bottom = random.nextBoolean() ? "sin" : "cos";
            yield top
                + "("
                + (1 + random.nextInt(4))
                + "x)/"
                + bottom
                + "(x) = "
                + VALUES[random.nextInt(7)];
          }
          case 2 ->
              kernel
                  + "^2 + ("
                  + (random.nextInt(5) - 2)
                  + ")"
                  + kernel
                  + " + ("
                  + (random.nextInt(5) - 2)
                  + ")/2 = 0";
          case 5 -> {
            // functions with poles, of a multiple of the angle
            String g = FUNCTIONS[random.nextInt(FUNCTIONS.length)];
            yield f + "(2x) = " + g + "(x)";
          }
          default -> {
            String g = random.nextBoolean() ? "sin" : "cos";
            yield (random.nextInt(3) + 1)
                + " sin(x) + ("
                + (random.nextInt(5) - 2)
                + ") cos(x) = "
                + (random.nextInt(5) - 2)
                + " + "
                + g
                + "(2x)/2";
          }
        };
    return new Case(equation, degrees);
  }

  @Property(tries = 300)
  void generalSolutionsHoldAndMissNothing(@ForAll("cases") Case c) {
    Settings settings = c.degrees() ? Run.DEGREES : Settings.DEFAULT;
    Expr solved = Run.value("solve(" + c.equation() + ", x)", settings);
    String[] sides = c.equation().split(" = ");
    Expr difference = Run.value("(" + sides[0] + ") - (" + sides[1] + ")", settings);
    Compiled e = Compiled.compile(difference, List.of(X), settings);
    if (!(solved instanceof SetExpr set)) {
      // only the scan's answer or its refusal: the trigonometric path declined
      return;
    }
    List<Double> values = new ArrayList<>();
    for (Expr member : set.items()) {
      Compiled family = Compiled.compile(member, List.of(K), Settings.DEFAULT);
      for (int k = -60; k <= 60; k++) {
        double v = family.eval(k);
        if (k >= -2 && k <= 2) {
          double at = e.eval(v);
          assertThat(Double.isFinite(at)).as("%s defined at %s (k = %s)", c, member, k).isTrue();
          assertThat(Math.abs(at)).as("%s at %s (k = %s)", c, member, k).isLessThan(1e-7);
        }
        values.add(v);
      }
    }
    double unit = c.degrees() ? 180 / Math.PI : 1;
    double window = 9 * unit;
    for (double[] found : roots(e, window, unit)) {
      double root = found[0];
      // a sign change is located to the double precision; a touching root lies somewhere in the
      // run where the value is below the double precision's reach, whose half-width found[2] is,
      // since a flat touch (sec² + 2 sec + 1, of fourth order) cannot be located closer
      double tolerance = found[1] == 0 ? 1e-8 * unit : 1e-4 * unit + found[2];
      assertThat(values.stream().anyMatch(v -> Math.abs(v - root) < tolerance))
          .as("%s misses the root %s: %s", c, root, set)
          .isTrue();
    }
  }

  /**
   * The roots a fine scan of {@code [−w, w]} finds, each with 0 for a sign change and 1 for a
   * touching minimum, and for a touching root the half-width of the run where it is flat to 1e-10.
   */
  private static List<double[]> roots(Compiled e, double w, double unit) {
    int n = 40_000;
    double h = 2 * w / n;
    List<double[]> out = new ArrayList<>();
    double prev = e.eval(-w);
    for (int i = 1; i <= n; i++) {
      double xi = -w + i * h;
      double cur = e.eval(xi);
      if (Double.isFinite(prev) && Double.isFinite(cur) && prev * cur < 0) {
        double lo = xi - h;
        double hi = xi;
        for (int j = 0; j < 80; j++) {
          double mid = (lo + hi) / 2;
          double m = e.eval(mid);
          if (Double.isFinite(m) && m * e.eval(lo) <= 0) {
            hi = mid;
          } else {
            lo = mid;
          }
        }
        double r = (lo + hi) / 2;
        // a true sign change, not rounding noise beside a touching root, which the second pass
        // finds: the values a little way either side have opposite signs
        double d = 1e-6 * unit;
        double left = e.eval(r - d);
        double right = e.eval(r + d);
        if (Math.abs(e.eval(r)) < 1e-6 && left * right < 0 && Math.abs(left) > 1e-12) {
          out.add(new double[] {r, 0, 0});
        }
      }
      prev = cur;
    }
    // touching roots: a local minimum of |e| that reaches 0
    for (int i = 1; i < n; i++) {
      double a = Math.abs(e.eval(-w + (i - 1) * h));
      double b = Math.abs(e.eval(-w + i * h));
      double c = Math.abs(e.eval(-w + (i + 1) * h));
      if (b <= a && b <= c && b < 1e-3) {
        double lo = -w + (i - 1) * h;
        double hi = -w + (i + 1) * h;
        for (int j = 0; j < 100; j++) {
          double m1 = lo + (hi - lo) / 3;
          double m2 = hi - (hi - lo) / 3;
          if (Math.abs(e.eval(m1)) < Math.abs(e.eval(m2))) {
            hi = m2;
          } else {
            lo = m1;
          }
        }
        double r = (lo + hi) / 2;
        if (Math.abs(e.eval(r)) < 1e-10 && Math.abs(r) < w - h) {
          // the half-width of the run around r where the value stays below 1e-10
          double width = 0;
          for (double step = 1e-6 * unit; step < 0.1 * unit; step *= 2) {
            if (Math.abs(e.eval(r - step)) < 1e-10 && Math.abs(e.eval(r + step)) < 1e-10) {
              width = step;
            } else {
              break;
            }
          }
          out.add(new double[] {r, 1, width});
        }
      }
    }
    return out;
  }
}
