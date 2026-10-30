package mathcanvas.canvas.model;

import java.util.Objects;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;

/**
 * How a parametrised path maps its parameter to the plane. The expressed forms are evaluated by a
 * {@link Sampler}; the linear form is plain arithmetic on its parents' points, which the model does
 * itself.
 */
public sealed interface Parametrisation {

  /** The forms given by expressions, which only a {@link Sampler} evaluates. */
  sealed interface Expressed extends Parametrisation {}

  /**
   * {@code t ↦ (t, body(t))}.
   *
   * @param body the expression
   * @param variable its variable
   */
  record Graph(Expr body, Symbol variable) implements Expressed {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public Graph {
      Objects.requireNonNull(body, "body");
      Objects.requireNonNull(variable, "variable");
    }
  }

  /**
   * {@code t ↦ (x(t), y(t))}.
   *
   * @param x the abscissa
   * @param y the ordinate
   * @param parameter the parameter
   */
  record Parametric(Expr x, Expr y, Symbol parameter) implements Expressed {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public Parametric {
      Objects.requireNonNull(x, "x");
      Objects.requireNonNull(y, "y");
      Objects.requireNonNull(parameter, "parameter");
    }
  }

  /**
   * {@code θ ↦ (r(θ) cos θ, r(θ) sin θ)}.
   *
   * @param radius the radius
   * @param angle the angle variable
   */
  record Polar(Expr radius, Symbol angle) implements Expressed {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public Polar {
      Objects.requireNonNull(radius, "radius");
      Objects.requireNonNull(angle, "angle");
    }
  }

  /**
   * {@code t ↦ (px + t·dx, py + t·dy)}: a line or a segment.
   *
   * @param px the abscissa at 0
   * @param py the ordinate at 0
   * @param dx the abscissa's change per unit of the parameter
   * @param dy the ordinate's change per unit of the parameter
   */
  record Linear(double px, double py, double dx, double dy) implements Parametrisation {

    /**
     * The point at a parameter.
     *
     * @param t the parameter
     * @return the point
     */
    public Sample at(double t) {
      return new Sample(px + t * dx, py + t * dy);
    }
  }
}
