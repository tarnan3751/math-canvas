package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Relation;

/**
 * The region where an inequality in {@code x} and {@code y} holds, shaded over the window.
 *
 * @param id the id
 * @param style the style
 * @param inequality the inequality: a {@code <}, {@code ≤}, {@code >} or {@code ≥} relation in
 *     {@code x} and {@code y}
 */
public record InequalityRegion(ObjectId id, Style style, Relation inequality)
    implements CanvasObject {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if the relation is not an inequality
   */
  public InequalityRegion {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(inequality, "inequality");
    switch (inequality.op()) {
      case LT, LE, GT, GE -> {}
      case EQ, NE, IN -> throw new IllegalArgumentException("a region is an inequality");
    }
  }

  @Override
  public List<ObjectId> parents() {
    return List.of();
  }

  @Override
  public Set<String> reads() {
    return Reads.of(Set.of(ImplicitCurve.X, ImplicitCurve.Y), inequality);
  }

  @Override
  public InequalityRegion withStyle(Style style) {
    return new InequalityRegion(id, style, inequality);
  }
}
