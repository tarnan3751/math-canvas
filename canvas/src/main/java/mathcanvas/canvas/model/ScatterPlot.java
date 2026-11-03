package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Expr;

/**
 * A scatter plot of paired data: a marker at each pair, no line ({@link Statistics#scatter}).
 *
 * @param id the id
 * @param style the style
 * @param points the pairs: a matrix of them, or two lists
 */
public record ScatterPlot(ObjectId id, Style style, Points points) implements CanvasObject {

  /** Where the pairs come from. */
  public sealed interface Points permits Pairs, Columns {}

  /**
   * A matrix of two columns, a pair per row.
   *
   * @param matrix the matrix, which may read cells
   */
  public record Pairs(Expr matrix) implements Points {

    /**
     * Checks the field.
     *
     * @throws NullPointerException if it is null
     */
    public Pairs {
      Objects.requireNonNull(matrix, "matrix");
    }
  }

  /**
   * Two lists of one length, the abscissae and the ordinates.
   *
   * @param xs the abscissae
   * @param ys the ordinates
   */
  public record Columns(Expr xs, Expr ys) implements Points {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public Columns {
      Objects.requireNonNull(xs, "xs");
      Objects.requireNonNull(ys, "ys");
    }
  }

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public ScatterPlot {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(points, "points");
  }

  @Override
  public List<ObjectId> parents() {
    return List.of();
  }

  @Override
  public Set<String> reads() {
    return switch (points) {
      case Pairs p -> Reads.of(Set.of(), p.matrix());
      case Columns c -> Reads.of(Set.of(), c.xs(), c.ys());
    };
  }

  @Override
  public ScatterPlot withStyle(Style style) {
    return new ScatterPlot(id, style, points);
  }
}
