package mathcanvas.canvas.model;

import java.util.Arrays;
import java.util.Objects;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;

/**
 * How a parametrised path maps its parameter to the plane. The expressed forms are evaluated by a
 * {@link Sampler}; the linear, circular and polygonal forms are plain arithmetic on their parents'
 * points, which the model does itself.
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

  /**
   * {@code t ↦ (cx + r cos(phase + dir·t), cy + r sin(phase + dir·t))}, {@code t} from 0 to 2π: a
   * circle, counterclockwise for {@code dir} 1 and clockwise for −1 (the image of a circle under a
   * reflection).
   *
   * @param cx the centre's abscissa
   * @param cy the centre's ordinate
   * @param r the radius, positive
   * @param phase the angle of the point at 0
   * @param dir 1 or −1
   */
  record Circular(double cx, double cy, double r, double phase, double dir)
      implements Parametrisation {

    /**
     * The point at a parameter.
     *
     * @param t the parameter
     * @return the point
     */
    public Sample at(double t) {
      double a = phase + dir * t;
      return new Sample(cx + r * Math.cos(a), cy + r * Math.sin(a));
    }

    /**
     * The parameter of the point of the circle in a direction from the centre, in [0, 2π).
     *
     * @param x the abscissa of a point
     * @param y the ordinate of a point
     * @return the parameter
     */
    public double parameterOf(double x, double y) {
      double t = dir * (Math.atan2(y - cy, x - cx) - phase);
      double turn = 2 * Math.PI;
      t -= turn * Math.floor(t / turn);
      return t < turn ? t : 0;
    }
  }

  /**
   * A closed polygon: {@code t} from 0 to n, side k running affinely from vertex k at k to vertex k
   * + 1 (vertex 0 after the last) at k + 1.
   *
   * @param xs the vertices' abscissas, at least one
   * @param ys the vertices' ordinates, as many
   */
  record Polygonal(double[] xs, double[] ys) implements Parametrisation {

    /**
     * Copies the arrays.
     *
     * @throws IllegalArgumentException if they are empty or of different lengths
     */
    public Polygonal {
      xs = xs.clone();
      ys = ys.clone();
      if (xs.length == 0 || xs.length != ys.length) {
        throw new IllegalArgumentException("a polygon has as many abscissas as ordinates");
      }
    }

    /**
     * The number of vertices, and of sides.
     *
     * @return it
     */
    public int size() {
      return xs.length;
    }

    /**
     * A vertex's abscissa.
     *
     * @param k the vertex, taken modulo the size
     * @return it
     */
    public double x(int k) {
      return xs[Math.floorMod(k, xs.length)];
    }

    /**
     * A vertex's ordinate.
     *
     * @param k the vertex, taken modulo the size
     * @return it
     */
    public double y(int k) {
      return ys[Math.floorMod(k, ys.length)];
    }

    /**
     * The point at a parameter in [0, n].
     *
     * @param t the parameter
     * @return the point
     */
    public Sample at(double t) {
      int k = Math.min((int) Math.floor(t), xs.length - 1);
      double u = t - k;
      return new Sample(x(k) + u * (x(k + 1) - x(k)), y(k) + u * (y(k + 1) - y(k)));
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Polygonal p && Arrays.equals(xs, p.xs) && Arrays.equals(ys, p.ys);
    }

    @Override
    public int hashCode() {
      return 31 * Arrays.hashCode(xs) + Arrays.hashCode(ys);
    }

    @Override
    public String toString() {
      return "Polygonal" + Arrays.toString(xs) + Arrays.toString(ys);
    }
  }
}
