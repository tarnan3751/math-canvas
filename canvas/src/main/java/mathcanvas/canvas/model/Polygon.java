package mathcanvas.canvas.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Expr;

/**
 * A closed polygon, through its vertices in order or regular on a side. Its sides are its own
 * geometry, not separate segments: its boundary is one path whose parameter runs round the
 * perimeter ({@link ParamKind#PERIMETER}). Undefined with fewer than three distinct vertices.
 *
 * @param id the id
 * @param style the style
 * @param definition how the polygon is given
 */
public record Polygon(ObjectId id, Style style, Definition definition) implements CanvasObject {

  /** The most sides a regular polygon has. */
  public static final int MAX_SIDES = 100;

  /** How a polygon is given. */
  public sealed interface Definition {}

  /**
   * The polygon through points, in order, the last joined to the first.
   *
   * @param points the vertices' ids, at least three, distinct
   */
  public record Vertices(List<ObjectId> points) implements Definition {

    /**
     * Copies the list.
     *
     * @throws NullPointerException if the list or an id is null
     * @throws IllegalArgumentException if it has fewer than three ids or repeats one
     */
    public Vertices {
      points = List.copyOf(points);
      if (points.size() < 3 || new HashSet<>(points).size() != points.size()) {
        throw new IllegalArgumentException("a polygon has three or more different vertices");
      }
    }
  }

  /**
   * The regular polygon with {@code n} sides on the side from {@code a} to {@code b}, lying to its
   * left (counterclockwise), the other vertices derived. {@code n} is a number or an expression
   * that reads sliders, a whole number from 3 to {@link #MAX_SIDES} where it is defined.
   *
   * @param a the first vertex's id
   * @param b the second vertex's id
   * @param n the number of sides
   */
  public record Regular(ObjectId a, ObjectId b, Expr n) implements Definition {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     * @throws IllegalArgumentException if the points are one
     */
    public Regular {
      Objects.requireNonNull(a, "a");
      Objects.requireNonNull(b, "b");
      Objects.requireNonNull(n, "n");
      if (a.equals(b)) {
        throw new IllegalArgumentException("a regular polygon's side joins two different points");
      }
    }
  }

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public Polygon {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(definition, "definition");
  }

  @Override
  public List<ObjectId> parents() {
    return switch (definition) {
      case Vertices v -> v.points();
      case Regular r -> List.of(r.a(), r.b());
    };
  }

  @Override
  public Set<String> reads() {
    return definition instanceof Regular r ? Reads.of(Set.of(), r.n()) : Set.of();
  }

  @Override
  public Polygon withStyle(Style style) {
    return new Polygon(id, style, definition);
  }
}
