package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Expr;

/**
 * The slope field of {@code y' = f(x, y)}, drawn as short segments of one length at a grid over the
 * visible window ({@link FieldGrid}), each along the direction {@code (1, f)}; a {@link
 * SolutionCurve} is integrated through it.
 *
 * @param id the id
 * @param style the style
 * @param slope the slope {@code f} in {@code x} and {@code y}
 * @param density the grid's columns, {@link FieldGrid#MIN_DENSITY} to {@link FieldGrid#MAX_DENSITY}
 */
public record SlopeField(ObjectId id, Style style, Expr slope, int density)
    implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if the density is out of its range
   */
  public SlopeField {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(slope, "slope");
    FieldGrid.checkDensity(density);
  }

  @Override
  public List<ObjectId> parents() {
    return List.of();
  }

  @Override
  public Set<String> reads() {
    return Reads.of(Set.of(ImplicitCurve.X, ImplicitCurve.Y), slope);
  }

  @Override
  public SlopeField withStyle(Style style) {
    return new SlopeField(id, style, slope, density);
  }

  /**
   * This field on another grid.
   *
   * @param density the grid's columns
   * @return the field
   */
  public SlopeField withDensity(int density) {
    return new SlopeField(id, style, slope, density);
  }
}
