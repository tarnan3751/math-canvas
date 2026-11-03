package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Num;

/**
 * A box plot of a sample at a height: its box from the first to the third quartile with the median,
 * its whiskers to the furthest values within 1.5 interquartile ranges of the box, and the values
 * beyond as points ({@link Statistics#boxPlot}); the box is a fixed number of pixels thick.
 *
 * @param id the id
 * @param style the style
 * @param data the sample, a list of real numbers
 * @param y the height it is drawn at, an expression
 */
public record BoxPlot(ObjectId id, Style style, Expr data, Expr y) implements CanvasObject {

  /** The height a box plot is drawn at when none is given: under the axis, below a histogram. */
  public static final Expr DEFAULT_Y = Num.MINUS_ONE;

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public BoxPlot {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(data, "data");
    Objects.requireNonNull(y, "y");
  }

  @Override
  public List<ObjectId> parents() {
    return List.of();
  }

  @Override
  public Set<String> reads() {
    return Reads.of(Set.of(), data, y);
  }

  @Override
  public BoxPlot withStyle(Style style) {
    return new BoxPlot(id, style, data, y);
  }
}
