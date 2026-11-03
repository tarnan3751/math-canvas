package mathcanvas.canvas.model;

import java.util.List;
import java.util.Map;
import mathcanvas.engine.Cancellation;
import mathcanvas.engine.CancelledException;
import mathcanvas.engine.compile.Quadrature;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;

/**
 * Turns expressions into numbers and samples: the model's only way to evaluate an expression. An
 * implementation is bound by its maker to an environment (the worksheet's definitions and the
 * sliders' values), compiles expressions to {@code double} evaluators and caches them; it is
 * immutable and safe to call from any thread. A value that is undefined or not real is NaN.
 */
public interface Sampler {

  /**
   * Evaluates an expression: a domain's end, a tangent's slope.
   *
   * @param e the expression
   * @param locals values for symbols of the object's own, over the environment's
   * @return the value, NaN when undefined; an infinite end may be ±∞
   */
  double value(Expr e, Map<Symbol, Double> locals);

  /**
   * One point of a path, to land a point on it or refine an intersection.
   *
   * @param p the path
   * @param t the parameter
   * @return the point, {@link Sample#UNDEFINED} where the path is
   */
  Sample point(Parametrisation.Expressed p, double t);

  /**
   * Samples a path over a range of its parameter, split into polylines at every gap, each vertex
   * carrying its parameter.
   *
   * @param p the path
   * @param lo the least parameter, finite
   * @param hi the greatest parameter, finite, not below the least
   * @param window the window the path is drawn in: flatness is judged on its scale, and samples far
   *     outside it are cut
   * @param cancellation checked between samples
   * @return the polylines, possibly none
   */
  List<Polyline> sample(
      Parametrisation.Expressed p, double lo, double hi, Window window, Cancellation cancellation);

  /**
   * Traces where an equation in {@code x} and {@code y} holds over a window.
   *
   * @param equation the equation
   * @param window the window
   * @param cancellation checked as it goes
   * @return the polylines, with NaN parameters
   */
  List<Polyline> contour(Relation equation, Window window, Cancellation cancellation);

  /**
   * The region where an inequality in {@code x} and {@code y} holds over a window.
   *
   * @param inequality the inequality
   * @param window the window
   * @param cancellation checked as it goes
   * @return the closed rings bounding it, inside by the even-odd rule
   */
  List<Polyline> region(Relation inequality, Window window, Cancellation cancellation);

  /**
   * The integral of a function graph's body over {@code [a, b]}. The real sampler integrates by
   * Gauss–Kronrod over the compiled evaluator ({@link
   * Quadrature#integrate(mathcanvas.engine.compile.Compiled, double, double, Cancellation)}), NaN
   * and not converged where the body does not compile; this default, for samplers with no compiled
   * evaluators (the tests'), is Simpson's rule on 2,000 pieces of {@link #point}, converged when
   * finite.
   *
   * @param graph the graph's body in its variable
   * @param a the lower bound
   * @param b the upper bound
   * @param cancellation the request to stop
   * @return the estimate
   */
  default Quadrature.Estimate integral(
      Parametrisation.Graph graph, double a, double b, Cancellation cancellation) {
    int n = 2000;
    double h = (b - a) / n;
    double sum = 0;
    for (int k = 0; k <= n; k++) {
      double y = point(graph, a + k * h).y();
      sum += (k == 0 || k == n ? 1 : k % 2 == 1 ? 4 : 2) * y;
    }
    double value = sum * h / 3;
    return new Quadrature.Estimate(value, Double.NaN, Double.isFinite(value));
  }

  /**
   * The heights a Riemann sum of a function graph samples, as {@link Quadrature#riemannHeights}
   * places them: one per piece, or {@code n + 1} for the trapezoids; NaN where the body has no
   * value. The real sampler runs the compiled evaluator; this default asks {@link #point}.
   *
   * @param graph the graph's body in its variable
   * @param a the lower bound
   * @param b the upper bound
   * @param n the pieces, at least 1
   * @param method where each piece is sampled
   * @return the heights
   */
  default double[] riemannHeights(
      Parametrisation.Graph graph, double a, double b, int n, Quadrature.Method method) {
    double offset =
        switch (method) {
          case LEFT, TRAPEZOID -> 0;
          case RIGHT -> 1;
          case MIDPOINT -> 0.5;
        };
    double[] out = new double[method == Quadrature.Method.TRAPEZOID ? n + 1 : n];
    for (int i = 0; i < out.length; i++) {
      out[i] = point(graph, a + (i + offset) * (b - a) / n).y();
    }
    return out;
  }

  /**
   * The values of an expression in {@code x} and {@code y} at points: a field's components at its
   * grid, a contour plot's function over the window. The real sampler runs the compiled evaluator;
   * this default asks {@link #value} at each point.
   *
   * @param e the expression
   * @param xs the abscissas
   * @param ys the ordinates, as many
   * @param cancellation checked as it goes
   * @return the values, NaN where there is none
   */
  default double[] plane(Expr e, double[] xs, double[] ys, Cancellation cancellation) {
    double[] out = new double[xs.length];
    for (int k = 0; k < out.length; k++) {
      if (k % 1024 == 0) {
        cancellation.check();
      }
      out[k] = value(e, Map.of(ImplicitCurve.X, xs[k], ImplicitCurve.Y, ys[k]));
    }
    return out;
  }

  /**
   * Traces where a function of {@code x} and {@code y} takes a value over a window, as {@link
   * #contour} traces an equation: one level of a contour plot. This default, for samplers with no
   * compiled evaluators (the tests' stubs), traces nothing.
   *
   * @param f the function
   * @param level the value
   * @param window the window
   * @param cancellation checked as it goes
   * @return the polylines, with NaN parameters
   */
  default List<Polyline> level(Expr f, double level, Window window, Cancellation cancellation) {
    return List.of();
  }

  /**
   * The solution of {@code y' = f(x, y)} through a point, integrated both ways to the window's
   * edge, a blow-up or a step budget: a solution curve. The real sampler integrates the compiled
   * slope by a Dormand–Prince 5(4) stepper; this default, for samplers with no compiled evaluators
   * (the tests' stubs), traces nothing.
   *
   * @param slope {@code f} in {@code x} and {@code y}
   * @param x0 the point's abscissa
   * @param y0 the point's ordinate
   * @param window the window it is traced in
   * @param cancellation checked as it goes
   * @return one polyline through the point, or none where the slope has no value there
   */
  default List<Polyline> solution(
      Expr slope, double x0, double y0, Window window, Cancellation cancellation) {
    return List.of();
  }

  // Phase 7d: the 3D view's grids.

  /**
   * The values of an expression in two of its own variables over a grid: {@code (n + 1)²} values,
   * row by row ({@code v} outer, {@code u} inner), the ends included, NaN where undefined. The real
   * sampler runs the compiled evaluator; this default asks {@link #value} at every vertex.
   *
   * @param e the expression
   * @param u the first variable
   * @param u0 its least value
   * @param u1 its greatest value
   * @param v the second variable
   * @param v0 its least value
   * @param v1 its greatest value
   * @param n the cells along each side, at least 1
   * @param cancellation checked as it goes
   * @return the values
   * @throws CancelledException if the cancellation is requested
   */
  default double[] grid(
      Expr e,
      Symbol u,
      double u0,
      double u1,
      Symbol v,
      double v0,
      double v1,
      int n,
      Cancellation cancellation) {
    double[] out = new double[(n + 1) * (n + 1)];
    for (int j = 0; j <= n; j++) {
      cancellation.check();
      for (int i = 0; i <= n; i++) {
        out[j * (n + 1) + i] =
            value(e, Map.of(u, u0 + (u1 - u0) * i / n, v, v0 + (v1 - v0) * j / n));
      }
    }
    return out;
  }

  /**
   * The values of an expression in one of its own variables at {@code n + 1} evenly spaced values,
   * the ends included, NaN where undefined. The real sampler runs the compiled evaluator; this
   * default asks {@link #value} at each.
   *
   * @param e the expression
   * @param t the variable
   * @param lo its least value
   * @param hi its greatest value
   * @param n the steps, at least 1
   * @param cancellation checked as it goes
   * @return the values
   * @throws CancelledException if the cancellation is requested
   */
  default double[] line(Expr e, Symbol t, double lo, double hi, int n, Cancellation cancellation) {
    double[] out = new double[n + 1];
    for (int i = 0; i <= n; i++) {
      if (i % 1024 == 0) {
        cancellation.check();
      }
      out[i] = value(e, Map.of(t, lo + (hi - lo) * i / n));
    }
    return out;
  }
}
