package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Expr;

/**
 * A vector field {@code F = ⟨P, Q⟩} in {@code x} and {@code y}, drawn as arrows at a grid over the
 * visible window ({@link FieldGrid}): {@code density} columns, each arrow's length scaled to the
 * grid's cell with a cap on the magnitude, and its colour ramped by its magnitude.
 *
 * @param id the id
 * @param style the style
 * @param p the abscissa's component
 * @param q the ordinate's component
 * @param density the grid's columns, {@link FieldGrid#MIN_DENSITY} to {@link FieldGrid#MAX_DENSITY}
 */
public record VectorField(ObjectId id, Style style, Expr p, Expr q, int density)
    implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if the density is out of its range
   */
  public VectorField {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(p, "p");
    Objects.requireNonNull(q, "q");
    FieldGrid.checkDensity(density);
  }

  @Override
  public List<ObjectId> parents() {
    return List.of();
  }

  @Override
  public Set<String> reads() {
    return Reads.of(Set.of(ImplicitCurve.X, ImplicitCurve.Y), p, q);
  }

  @Override
  public VectorField withStyle(Style style) {
    return new VectorField(id, style, p, q, density);
  }

  /**
   * This field on another grid.
   *
   * @param density the grid's columns
   * @return the field
   */
  public VectorField withDensity(int density) {
    return new VectorField(id, style, p, q, density);
  }
}
