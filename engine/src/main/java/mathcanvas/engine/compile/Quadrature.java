package mathcanvas.engine.compile;

import java.util.Comparator;
import java.util.PriorityQueue;
import mathcanvas.engine.Cancellation;

/**
 * Numeric integration in {@code double} over a compiled function of one argument, for the canvas:
 * adaptive Gauss–Kronrod G7K15 and the Riemann sums. {@code docs/reference/engine.md}, section
 * <b>Numeric integration</b>, is the reference.
 *
 * <p>{@link #integrate} is QUADPACK's QAG with the 15-point rule (R. Piessens et al.,
 * <i>QUADPACK</i>, 1983): each interval's value is the Kronrod sum and its error QUADPACK's
 * estimate from the difference of the Kronrod and Gauss sums; the interval of the largest error is
 * bisected until the sum of the errors is below the tolerance times the larger of the value and a
 * thousandth of the integral of the absolute value, or {@link #MAX_INTERVALS} intervals are
 * reached. A non-finite sample makes the value NaN; finite samples whose integral is beyond the
 * {@code double} range give the infinite value with an error that is not finite (infinite or NaN)
 * and {@code converged} false.
 */
public final class Quadrature {

  /** The relative tolerance of {@link #integrate(Compiled, double, double, Cancellation)}. */
  public static final double DEFAULT_TOLERANCE = 1e-10;

  /** The most intervals the bisection keeps. */
  public static final int MAX_INTERVALS = 200;

  // QUADPACK qk15: the Kronrod abscissas (the odd ones the Gauss abscissas) and weights
  private static final double[] XGK = {
    0.991455371120812639206854697526329,
    0.949107912342758524526189684047851,
    0.864864423359769072789712788640926,
    0.741531185599394439863864773280788,
    0.586087235467691130294144845693013,
    0.405845151377397166906606412076961,
    0.207784955007898467600689403773245,
    0.000000000000000000000000000000000
  };
  private static final double[] WGK = {
    0.022935322010529224963732008058970,
    0.063092092629978553290700663189204,
    0.104790010322250183839876322541518,
    0.140653259715525918745189590510238,
    0.169004726639267902826583426598550,
    0.190350578064785409913256402421014,
    0.204432940075298892414161999234649,
    0.209482141084727828012999174891714
  };
  private static final double[] WG = {
    0.129484966168869693270611432679082,
    0.279705391489276667901467771423780,
    0.381830050505118944950369775488975,
    0.417959183673469387755102040816327
  };

  private static final double EPSILON = Math.ulp(1.0);

  private Quadrature() {}

  /**
   * An integral's value and error estimate.
   *
   * @param value the value; NaN where a sample was not finite
   * @param error the estimated absolute error; NaN with the value
   * @param converged whether the error is within the tolerance
   */
  public record Estimate(double value, double error, boolean converged) {}

  /** The Riemann sums. */
  public enum Method {
    /** Each rectangle's height at its left end. */
    LEFT,
    /** Each rectangle's height at its right end. */
    RIGHT,
    /** Each rectangle's height at its middle. */
    MIDPOINT,
    /** Trapezoids between the values at the ends. */
    TRAPEZOID
  }

  /**
   * Integrates {@code f} from {@code a} to {@code b} to {@link #DEFAULT_TOLERANCE}.
   *
   * @param f a compiled function of one argument
   * @param a the lower bound
   * @param b the upper bound
   * @param cancellation the request to stop, checked at every bisection
   * @return the estimate
   * @throws IllegalArgumentException if {@code f} does not take one argument
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Estimate integrate(Compiled f, double a, double b, Cancellation cancellation) {
    return integrate(f, a, b, DEFAULT_TOLERANCE, cancellation);
  }

  /**
   * Integrates {@code f} from {@code a} to {@code b}; {@code a > b} gives the negated integral from
   * {@code b} to {@code a}, and a non-finite bound NaN.
   *
   * @param f a compiled function of one argument
   * @param a the lower bound
   * @param b the upper bound
   * @param tolerance the relative tolerance, positive
   * @param cancellation the request to stop, checked at every bisection
   * @return the estimate
   * @throws IllegalArgumentException if {@code f} does not take one argument, or the tolerance is
   *     not positive
   * @throws mathcanvas.engine.CancelledException if the cancellation is requested
   */
  public static Estimate integrate(
      Compiled f, double a, double b, double tolerance, Cancellation cancellation) {
    checkOne(f);
    if (!(tolerance > 0)) {
      throw new IllegalArgumentException("the tolerance must be positive: " + tolerance);
    }
    cancellation.check();
    if (!Double.isFinite(a) || !Double.isFinite(b)) {
      return new Estimate(Double.NaN, Double.NaN, false);
    }
    if (a == b) {
      return new Estimate(0, 0, true);
    }
    double[] arg = new double[1];
    PriorityQueue<Piece> queue =
        new PriorityQueue<>(
            Comparator.comparingDouble(Piece::error).reversed().thenComparingDouble(Piece::a));
    Piece first = kronrod(f, arg, a, b);
    if (first == null) {
      return new Estimate(Double.NaN, Double.NaN, false);
    }
    queue.add(first);
    double value = first.value();
    double error = first.error();
    double l1 = first.abs();
    while (error > bound(tolerance, value, l1) && queue.size() < MAX_INTERVALS) {
      cancellation.check();
      Piece worst = queue.poll();
      double m = 0.5 * (worst.a() + worst.b());
      Piece left = kronrod(f, arg, worst.a(), m);
      Piece right = kronrod(f, arg, m, worst.b());
      if (left == null || right == null) {
        return new Estimate(Double.NaN, Double.NaN, false);
      }
      queue.add(left);
      queue.add(right);
      value = 0;
      error = 0;
      l1 = 0;
      for (Piece p : queue) {
        value += p.value();
        error += p.error();
        l1 += p.abs();
      }
    }
    // an infinite value is never converged, whatever its error compares to
    return new Estimate(
        value, error, Double.isFinite(value) && error <= bound(tolerance, value, l1));
  }

  private static double bound(double tolerance, double value, double l1) {
    return tolerance * Math.max(Math.abs(value), 1e-3 * l1);
  }

  /** One interval: its Kronrod value, its error estimate and its integral of {@code |f|}. */
  private record Piece(double a, double b, double value, double error, double abs) {}

  /** QUADPACK's qk15 on {@code [a, b]}; null when a sample is not finite. */
  private static Piece kronrod(Compiled f, double[] arg, double a, double b) {
    double centre = 0.5 * (a + b);
    double halfLength = 0.5 * (b - a);
    double fc = at(f, arg, centre);
    if (!Double.isFinite(fc)) {
      return null;
    }
    double resg = fc * WG[3];
    double resk = fc * WGK[7];
    double resabs = Math.abs(resk);
    double[] fv1 = new double[7];
    double[] fv2 = new double[7];
    for (int j = 0; j < 7; j++) {
      double abscissa = halfLength * XGK[j];
      double f1 = at(f, arg, centre - abscissa);
      double f2 = at(f, arg, centre + abscissa);
      if (!Double.isFinite(f1) || !Double.isFinite(f2)) {
        return null;
      }
      fv1[j] = f1;
      fv2[j] = f2;
      resk += WGK[j] * (f1 + f2);
      resabs += WGK[j] * (Math.abs(f1) + Math.abs(f2));
      if (j % 2 == 1) {
        resg += WG[j / 2] * (f1 + f2);
      }
    }
    double reskh = 0.5 * resk;
    double resasc = WGK[7] * Math.abs(fc - reskh);
    for (int j = 0; j < 7; j++) {
      resasc += WGK[j] * (Math.abs(fv1[j] - reskh) + Math.abs(fv2[j] - reskh));
    }
    double h = Math.abs(halfLength);
    double value = resk * halfLength;
    resabs *= h;
    resasc *= h;
    double error = Math.abs((resk - resg) * halfLength);
    if (resasc != 0 && error != 0) {
      error = resasc * Math.min(1, Math.pow(200 * error / resasc, 1.5));
    }
    if (resabs > Double.MIN_NORMAL / (50 * EPSILON)) {
      error = Math.max(50 * EPSILON * resabs, error);
    }
    return new Piece(a, b, value, error, resabs);
  }

  /**
   * A Riemann sum of {@code f} over {@code n} equal pieces of {@code [a, b]}.
   *
   * @param f a compiled function of one argument
   * @param a the lower bound
   * @param b the upper bound
   * @param n the pieces, at least 1
   * @param method where each piece is sampled
   * @return the sum; NaN where a sample or a bound is not finite
   * @throws IllegalArgumentException if {@code f} does not take one argument, or {@code n} is below
   *     1
   */
  public static double riemann(Compiled f, double a, double b, int n, Method method) {
    double[] heights = riemannHeights(f, a, b, n, method);
    double dx = (b - a) / n;
    double sum = 0;
    if (method == Method.TRAPEZOID) {
      sum = 0.5 * (heights[0] + heights[n]);
      for (int i = 1; i < n; i++) {
        sum += heights[i];
      }
    } else {
      for (double y : heights) {
        sum += y;
      }
    }
    return Double.isFinite(sum) ? sum * dx : Double.NaN;
  }

  /**
   * The heights a Riemann sum samples: one per piece, at its left end, right end or middle; for
   * {@link Method#TRAPEZOID}, the {@code n + 1} values at the ends of the pieces. Piece {@code i}
   * spans {@code a + i(b − a)/n} to {@code a + (i + 1)(b − a)/n}.
   *
   * @param f a compiled function of one argument
   * @param a the lower bound
   * @param b the upper bound
   * @param n the pieces, at least 1
   * @param method where each piece is sampled
   * @return the heights; NaN where a sample is not finite
   * @throws IllegalArgumentException if {@code f} does not take one argument, or {@code n} is below
   *     1
   */
  public static double[] riemannHeights(Compiled f, double a, double b, int n, Method method) {
    checkOne(f);
    if (n < 1) {
      throw new IllegalArgumentException("a Riemann sum needs at least one piece: " + n);
    }
    double[] arg = new double[1];
    double width = b - a;
    double offset =
        switch (method) {
          case LEFT, TRAPEZOID -> 0;
          case RIGHT -> 1;
          case MIDPOINT -> 0.5;
        };
    int count = method == Method.TRAPEZOID ? n + 1 : n;
    double[] heights = new double[count];
    for (int i = 0; i < count; i++) {
      double x = a + (i + offset) * width / n;
      double y = at(f, arg, x);
      heights[i] = Double.isFinite(y) ? y : Double.NaN;
    }
    return heights;
  }

  private static double at(Compiled f, double[] arg, double x) {
    arg[0] = x;
    return f.eval(arg);
  }

  private static void checkOne(Compiled f) {
    if (f.arguments().size() != 1) {
      throw new IllegalArgumentException(
          "a quadrature integrates a function of one argument, not " + f.arguments().size());
    }
  }
}
