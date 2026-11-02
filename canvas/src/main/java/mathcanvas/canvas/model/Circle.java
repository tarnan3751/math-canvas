package mathcanvas.canvas.model;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mathcanvas.engine.expr.Expr;

/**
 * A circle, given by its centre and a point on it, its centre and a radius, or three points on it.
 * Its parameter is the angle from its starting point, from 0 to 2π ({@link ParamKind#ANGLE}).
 * Undefined while its radius is zero or not a positive number, or its three points are collinear
 * (two of them coincident included).
 *
 * @param id the id
 * @param style the style
 * @param definition how the circle is given
 */
public record Circle(ObjectId id, Style style, Definition definition) implements CanvasObject {

  /** How a circle is given. */
  public sealed interface Definition {}

  /**
   * The circle about a centre through a point.
   *
   * @param center the centre's id
   * @param through the id of the point on it
   */
  public record CenterPoint(ObjectId center, ObjectId through) implements Definition {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     * @throws IllegalArgumentException if the points are one
     */
    public CenterPoint {
      Objects.requireNonNull(center, "center");
      Objects.requireNonNull(through, "through");
      if (center.equals(through)) {
        throw new IllegalArgumentException("a circle's centre is not the point it passes through");
      }
    }
  }

  /**
   * The circle about a centre with a radius: a number, or an expression that reads sliders or
   * worksheet definitions, evaluated through the sampler at every recompute.
   *
   * @param center the centre's id
   * @param radius the radius
   */
  public record CenterRadius(ObjectId center, Expr radius) implements Definition {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public CenterRadius {
      Objects.requireNonNull(center, "center");
      Objects.requireNonNull(radius, "radius");
    }
  }

  /**
   * The circle through three points.
   *
   * @param a the first point's id
   * @param b the second point's id
   * @param c the third point's id
   */
  public record ThreePoints(ObjectId a, ObjectId b, ObjectId c) implements Definition {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     * @throws IllegalArgumentException if two of the points are one
     */
    public ThreePoints {
      Objects.requireNonNull(a, "a");
      Objects.requireNonNull(b, "b");
      Objects.requireNonNull(c, "c");
      if (a.equals(b) || b.equals(c) || a.equals(c)) {
        throw new IllegalArgumentException("a circle passes through three different points");
      }
    }
  }

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public Circle {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(style, "style");
    Objects.requireNonNull(definition, "definition");
  }

  @Override
  public List<ObjectId> parents() {
    return switch (definition) {
      case CenterPoint d -> List.of(d.center(), d.through());
      case CenterRadius d -> List.of(d.center());
      case ThreePoints d -> List.of(d.a(), d.b(), d.c());
    };
  }

  @Override
  public Set<String> reads() {
    return definition instanceof CenterRadius d ? Reads.of(Set.of(), d.radius()) : Set.of();
  }

  @Override
  public Circle withStyle(Style style) {
    return new Circle(id, style, definition);
  }
}
