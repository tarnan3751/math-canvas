package mathcanvas.canvas.model;

import java.util.List;
import java.util.Map;
import mathcanvas.engine.Cancellation;
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
}
