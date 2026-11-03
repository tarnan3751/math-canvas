package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Expr;

/**
 * A residual plot of paired data against a fit: at each abscissa, the ordinate less the fit's value
 * there, drawn as a stem from the axis with a marker ({@link Statistics#residuals}).
 *
 * @param id the id
 * @param style the style
 * @param points the pairs: a matrix of them, or two lists
 * @param fit the fit, which may read cells: a relation {@code y = …} in {@code x}, or a list whose
 *     first item is one, as {@code linreg} gives
 */
public record ResidualPlot(ObjectId id, Style style, ScatterPlot.Points points, Expr fit)
    implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public ResidualPlot {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(points, "points");
    Objects.requireNonNull(fit, "fit");
  }

  @Override
  public List<ObjectId> parents() {
    return List.of();
  }

  @Override
  public Set<String> reads() {
    return switch (points) {
      case ScatterPlot.Pairs p -> Reads.of(Set.of(), p.matrix(), fit);
      case ScatterPlot.Columns c -> Reads.of(Set.of(), c.xs(), c.ys(), fit);
    };
  }

  @Override
  public ResidualPlot withStyle(Style style) {
    return new ResidualPlot(id, style, points, fit);
  }
}
