package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.compile.Quadrature;
import mathcanvas.engine.expr.Expr;

/**
 * A Riemann sum of a function graph over {@code [from, to]} in {@code n} equal pieces, drawn as its
 * rectangles (trapezoids for {@link Quadrature.Method#TRAPEZOID}) with its value. The bounds and
 * {@code n} are expressions, which may read sliders; the sum is undefined unless the bounds are
 * numbers with {@code from < to} inside the graph's domain, {@code n} is a whole number from 1 to
 * {@link #MAX_PIECES}, and every height is finite.
 *
 * @param id the id
 * @param style the style
 * @param graph the function graph
 * @param from the lower bound
 * @param to the upper bound
 * @param n the number of pieces
 * @param method where each piece is sampled
 */
public record RiemannSum(
    ObjectId id, Style style, ObjectId graph, Expr from, Expr to, Expr n, Quadrature.Method method)
    implements CanvasObject {

  /** The most pieces a sum is drawn with. */
  public static final int MAX_PIECES = 1000;

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public RiemannSum {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(graph, "graph");
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    Objects.requireNonNull(n, "n");
    Objects.requireNonNull(method, "method");
  }

  @Override
  public List<ObjectId> parents() {
    return List.of(graph);
  }

  @Override
  public Set<String> reads() {
    return Reads.of(Set.of(), from, to, n);
  }

  @Override
  public RiemannSum withStyle(Style style) {
    return new RiemannSum(id, style, graph, from, to, n, method);
  }

  /**
   * This sum by another method.
   *
   * @param method the method
   * @return the sum
   */
  public RiemannSum withMethod(Quadrature.Method method) {
    return new RiemannSum(id, style, graph, from, to, n, method);
  }
}
