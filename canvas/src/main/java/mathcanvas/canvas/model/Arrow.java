package mathcanvas.canvas.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A vector drawn as an arrow from a tail to a head. The tail is a point or the origin; the head is
 * a point or the origin ({@link At}), or the tail moved by the value of a variable that holds a
 * 2-vector ({@link Bound}), as a slider binds a variable by name: a bound arrow follows the
 * variable's value at every recompute, is undefined while the value is not a 2-vector of real
 * numbers, and has no head to drag.
 *
 * @param id the id
 * @param style the style
 * @param tail the tail's point, or empty for the origin
 * @param head how the head is given
 */
public record Arrow(ObjectId id, Style style, Optional<ObjectId> tail, Head head)
    implements CanvasObject {

  /** How an arrow's head is given. */
  public sealed interface Head {}

  /**
   * The head at a point, or at the origin.
   *
   * @param point the point's id, or empty for the origin
   */
  public record At(Optional<ObjectId> point) implements Head {

    /**
     * Checks the field.
     *
     * @throws NullPointerException if it is null
     */
    public At {
      Objects.requireNonNull(point, "point");
    }
  }

  /**
   * The head at the tail moved by a variable's value, a 2-vector.
   *
   * @param variable the variable's name ({@link Slider#isVariableName})
   */
  public record Bound(String variable) implements Head {

    /**
     * Checks the field.
     *
     * @throws NullPointerException if it is null
     * @throws IllegalArgumentException if it is not a variable name
     */
    public Bound {
      Objects.requireNonNull(variable, "variable");
      if (!Slider.isVariableName(variable)) {
        throw new IllegalArgumentException("not a variable name: " + variable);
      }
    }
  }

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if the tail and the head are one point
   */
  public Arrow {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(tail, "tail");
    Objects.requireNonNull(head, "head");
    if (tail.isPresent() && head instanceof At a && tail.equals(a.point())) {
      throw new IllegalArgumentException("an arrow joins two different points");
    }
  }

  @Override
  public List<ObjectId> parents() {
    List<ObjectId> out = new ArrayList<>(2);
    tail.ifPresent(out::add);
    if (head instanceof At a) {
      a.point().ifPresent(out::add);
    }
    return List.copyOf(out);
  }

  @Override
  public Set<String> reads() {
    return head instanceof Bound b ? Set.of(b.variable()) : Set.of();
  }

  @Override
  public Arrow withStyle(Style style) {
    return new Arrow(id, style, tail, head);
  }

  /**
   * This arrow with another tail.
   *
   * @param tail the tail's point, or empty for the origin
   * @return the arrow
   */
  public Arrow withTail(Optional<ObjectId> tail) {
    return new Arrow(id, style, tail, head);
  }
}
