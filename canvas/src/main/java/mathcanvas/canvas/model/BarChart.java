package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Expr;

/**
 * A bar chart: one bar per value, drawn at 1, 2, … on the abscissa, labelled with its category
 * under the axis when there are categories ({@link Statistics#barChart}).
 *
 * @param id the id
 * @param style the style
 * @param categories the bars' labels, as many as the values, or none
 * @param values the bars' heights, a list of real numbers, which may read cells and sliders
 */
public record BarChart(ObjectId id, Style style, List<String> categories, Expr values)
    implements CanvasObject {

  /**
   * Copies the categories.
   *
   * @throws NullPointerException if a field is null
   */
  public BarChart {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    categories = List.copyOf(categories);
    Objects.requireNonNull(values, "values");
  }

  @Override
  public List<ObjectId> parents() {
    return List.of();
  }

  @Override
  public Set<String> reads() {
    return Reads.of(Set.of(), values);
  }

  @Override
  public BarChart withStyle(Style style) {
    return new BarChart(id, style, categories, values);
  }
}
