package mathcanvas.engine.eval;

import static mathcanvas.engine.eval.Run.shown;
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
import org.hipparchus.linear.MatrixUtils;
import org.hipparchus.linear.QRDecomposition;
import org.hipparchus.linear.RealMatrix;

/**
 * Regression against least squares computed independently: the line's coefficients and {@code r²}
 * against the closed form in {@code double}, a polynomial's against Hipparchus's QR solve, and the
 * normal equations met exactly: {@code Σ rᵢ xᵢʲ = 0} for every power of the fit, so the line's
 * residuals sum to 0.
 */
class RegressionProperties {

  private static double[] ints(Random random, int n, int spread) {
    double[] x = new double[n];
    for (int i = 0; i < n; i++) {
      x[i] = random.nextInt(2 * spread + 1) - spread;
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

  private static double numeric(String script) {
    Expr v = value("numeric(" + script + ", 30)");
    assertThat(v).as(script).isInstanceOf(Num.class);
    return ((RealNumeric) ((Num) v).value()).toDouble();
  }

  private static void close(String what, double got, double want) {
    assertThat(Math.abs(got - want))
        .as("%s: %s against %s", what, got, want)
        .isLessThanOrEqualTo(1e-9 * Math.max(1, Math.abs(want)));
  }

  @Property(tries = 40)
  void theLineIsTheClosedForm(@ForAll Random random) {
    int n = 3 + random.nextInt(15);
    double[] x = ints(random, n, 20);
    double[] y = ints(random, n, 50);
    double mx = 0;
    double my = 0;
    for (int i = 0; i < n; i++) {
      mx += x[i] / n;
      my += y[i] / n;
    }
    double sxy = 0;
    double sxx = 0;
    double syy = 0;
    for (int i = 0; i < n; i++) {
      sxy += (x[i] - mx) * (y[i] - my);
      sxx += (x[i] - mx) * (x[i] - mx);
      syy += (y[i] - my) * (y[i] - my);
    }
    if (sxx == 0 || syy == 0) {
      return;
    }
    String fit = "linreg(" + list(x) + ", " + list(y) + ")";
    double a = sxy / sxx;
    close("slope", numeric("predict(" + fit + ", 1) - predict(" + fit + ", 0)"), a);
    close("intercept", numeric("predict(" + fit + ", 0)"), my - a * mx);
    Expr r2 = ((Relation) ((ListExpr) value(fit)).items().get(1)).right();
    close("r²", numeric(AsciiPrinter.INSTANCE.print(r2)), sxy * sxy / (sxx * syy));
  }

  @Property(tries = 40)
  void polynomialsAgreeWithHipparchusAndMeetTheNormalEquations(@ForAll Random random) {
    int degree = 1 + random.nextInt(3);
    int n = degree + 2 + random.nextInt(8);
    double[] x = new double[n];
    for (int i = 0; i < n; i++) {
      x[i] = i - n / 2;
    }
    double[] y = ints(random, n, 30);
    String xs = list(x);
    String ys = list(y);
    String fit = "polyreg(" + xs + ", " + ys + ", " + degree + ")";
    RealMatrix design = MatrixUtils.createRealMatrix(n, degree + 1);
    for (int i = 0; i < n; i++) {
      for (int j = 0; j <= degree; j++) {
        design.setEntry(i, j, Math.pow(x[i], j));
      }
    }
    double[] beta =
        new QRDecomposition(design).getSolver().solve(MatrixUtils.createRealVector(y)).toArray();
    for (double at : new double[] {-1.5, 0.25, 2}) {
      double want = 0;
      for (int j = 0; j <= degree; j++) {
        want += beta[j] * Math.pow(at, j);
      }
      close(fit + " at " + at, numeric("predict(" + fit + ", " + at + ")"), want);
    }
    // the normal equations hold exactly: Σ rᵢ xᵢʲ = 0
    String r = "residuals(" + xs + ", " + ys + ", " + fit + ")";
    for (int j = 0; j <= degree; j++) {
      List<String> powers = new ArrayList<>();
      for (double v : x) {
        powers.add(Long.toString((long) Math.pow(v, j)));
      }
      assertThat(shown("dot(" + r + ", [" + String.join(", ", powers) + "])"))
          .as("%s, power %d", fit, j)
          .isEqualTo("0");
    }
  }
}
