package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Relation;
import mathcanvas.engine.expr.Symbol;

/**
 * The curve where an equation in {@code x} and {@code y} holds, traced over the window. It has no
 * parameter ({@link ParamKind#NONE}): a point on it is kept by projection.
 *
 * @param id the id
 * @param style the style
 * @param equation the equation, an {@code =} relation in {@code x} and {@code y}
 */
public record ImplicitCurve(ObjectId id, Style style, Relation equation) implements CanvasObject {

  /** The canvas's abscissa, which an implicit curve or a region is written in. */
  public static final Symbol X = Symbol.of("x");

  /** The canvas's ordinate. */
  public static final Symbol Y = Symbol.of("y");

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if the relation is not an equation
   */
  public ImplicitCurve {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(equation, "equation");
    if (equation.op() != Relation.Op.EQ) {
      throw new IllegalArgumentException("an implicit curve is an equation");
    }
  }

  @Override
  public List<ObjectId> parents() {
    return List.of();
  }

  @Override
  public Set<String> reads() {
    return Reads.of(Set.of(X, Y), equation);
  }

  @Override
  public ImplicitCurve withStyle(Style style) {
    return new ImplicitCurve(id, style, equation);
  }
}
