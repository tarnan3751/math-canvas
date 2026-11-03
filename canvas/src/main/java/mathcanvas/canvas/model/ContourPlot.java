package mathcanvas.canvas.model;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import mathcanvas.engine.expr.Expr;

/**
 * The contours of {@code f(x, y)}: at a list of values, or at a count of levels spread between the
 * function's least and greatest values over a grid of the visible window ({@link Contours}), each
 * level traced as an implicit curve is, with a label at one point of it and its colour ramped by
 * the level.
 *
 * @param id the id
 * @param style the style
 * @param function the function in {@code x} and {@code y}
 * @param levels the levels
 */
public record ContourPlot(ObjectId id, Style style, Expr function, Levels levels)
    implements CanvasObject {

  /** The most levels a plot is drawn at. */
  public static final int MAX_LEVELS = 30;

  /** Where the contours are drawn. */
  public sealed interface Levels {}

  /**
   * A number of levels spread over the function's range in the window.
   *
   * @param n the number, 1 to {@link #MAX_LEVELS}
   */
  public record Count(int n) implements Levels {

    /**
     * Checks the number.
     *
     * @throws IllegalArgumentException if it is out of its range
     */
    public Count {
      if (n < 1 || n > MAX_LEVELS) {
        throw new IllegalArgumentException("a contour plot has 1 to " + MAX_LEVELS + " levels");
      }
    }
  }

  /**
   * Levels at given values, expressions that may read sliders.
   *
   * @param values the values, 1 to {@link #MAX_LEVELS} of them
   */
  public record Values(List<Expr> values) implements Levels {

    /**
     * Copies the values.
     *
     * @throws IllegalArgumentException if there are none or too many
     */
    public Values {
      values = List.copyOf(values);
      if (values.isEmpty() || values.size() > MAX_LEVELS) {
        throw new IllegalArgumentException("a contour plot has 1 to " + MAX_LEVELS + " levels");
      }
    }
  }

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public ContourPlot {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(function, "function");
    Objects.requireNonNull(levels, "levels");
  }

  @Override
  public List<ObjectId> parents() {
    return List.of();
  }

  @Override
  public Set<String> reads() {
    Set<String> out = new TreeSet<>(Reads.of(Set.of(ImplicitCurve.X, ImplicitCurve.Y), function));
    if (levels instanceof Values v) {
      out.addAll(Reads.of(Set.of(), v.values().toArray(Expr[]::new)));
    }
    return Collections.unmodifiableSet(out);
  }

  @Override
  public ContourPlot withStyle(Style style) {
    return new ContourPlot(id, style, function, levels);
  }

  /**
   * This plot at other levels.
   *
   * @param levels the levels
   * @return the plot
   */
  public ContourPlot withLevels(Levels levels) {
    return new ContourPlot(id, style, function, levels);
  }
}
