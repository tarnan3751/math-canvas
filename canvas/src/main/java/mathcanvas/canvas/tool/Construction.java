package mathcanvas.canvas.tool;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.Constructions;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.Line;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Parametrisation;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.Transform;
import mathcanvas.engine.expr.Expr;

/**
 * One construction a {@link ConstructionTool} runs: what it picks, the number it then takes, and
 * what it adds. The constructions differ only in this data, so each tool is a row, made by its
 * factory on {@link ConstructionTool}, and the tool is the one state machine that runs any row.
 *
 * @param picks the picks, in order
 * @param number the name of the number taken once every pick is made, or empty for none
 * @param segmentAsEnds whether a click on a segment, first, picks its two ends
 * @param maker what it adds
 * @param refusalKey the key {@link ConstructionTool#refusal()} gives when a click at the pointer
 *     would add nothing, or empty when the tool does not say why
 * @param previewed whether the object a click would add is previewed: not a locus, which is traced
 *     by up to thousands of recomputes, never on the FX thread for a preview; its picks are
 *     highlighted alone
 */
record Construction(
    List<Pick> picks,
    Optional<String> number,
    boolean segmentAsEnds,
    Maker maker,
    Optional<String> refusalKey,
    boolean previewed) {

  /** What a pick is. */
  enum Pick {
    /** A point, existing or new. */
    POINT,
    /** An existing line, segment or ray. */
    LINEAR,
    /** An existing circle. */
    CIRCLE,
    /** An existing object that can be transformed: a point, a path, a vector. */
    SOURCE,
    /** An existing point, or line, segment or ray: what a reflection is in. */
    MIRROR,
    /** An existing vector. */
    VECTOR,
    /** An existing polygon or circle: what an area is of. */
    MEASURED,
    /** An existing point. */
    EXISTING_POINT,
    /** An existing point on a path: a locus's driver. */
    DRIVER,
    /** An existing line, segment or ray under the pointer, else a point as {@link #POINT}. */
    POINT_OR_LINEAR
  }

  /**
   * One object a construction adds.
   *
   * @param make builds it from the picks' ids and its own id
   * @param named whether it is a point that gets the next free name
   */
  record Made(BiFunction<List<ObjectId>, ObjectId, CanvasObject> make, boolean named) {}

  /** What a construction adds, given its picks' geometry and its number. */
  @FunctionalInterface
  interface Maker {

    /**
     * The objects.
     *
     * @param picked the picks' geometry, in order
     * @param objects the picks' objects, in order, null for a point the construction adds
     * @param number the number, or null when the construction takes none
     * @param canvas the canvas the picks are on
     * @return the objects, none to add nothing
     */
    List<Made> make(
        List<Geometry> picked, List<CanvasObject> objects, Expr number, CanvasState canvas);
  }

  /** The one object most constructions add, from the picks' ids, its own id and the number. */
  @FunctionalInterface
  interface One {

    /**
     * The object.
     *
     * @param picks the picks' ids, in order
     * @param id its own id
     * @param number the number, or null when the construction takes none
     * @return it
     */
    CanvasObject make(List<ObjectId> picks, ObjectId id, Expr number);
  }

  /** The image a transformation makes of its first pick, of that pick's shape. */
  @FunctionalInterface
  interface Image {

    /**
     * The image.
     *
     * @param picks the picks' ids, in order, the object transformed first
     * @param id its own id
     * @param number the number, or null when the transformation takes none
     * @param shape the shape of the object transformed
     * @return it
     */
    CanvasObject make(List<ObjectId> picks, ObjectId id, Expr number, Transform.Shape shape);
  }

  /**
   * Copies the list.
   *
   * @throws NullPointerException if a field or a pick is null
   */
  Construction {
    picks = List.copyOf(picks);
  }

  /** A construction adding one object, unnamed, previewed, saying nothing when it adds nothing. */
  static Construction of(List<Pick> picks, Optional<String> number, One one) {
    return new Construction(
        picks,
        number,
        false,
        (g, o, n, c) -> List.of(new Made((p, id) -> one.make(p, id, n), false)),
        Optional.empty(),
        true);
  }

  /** A construction of one object from points alone. */
  static Construction points(int count, One one) {
    return of(Collections.nCopies(count, Pick.POINT), Optional.empty(), one);
  }

  /** The construction with a click on a segment, first, picking its two ends. */
  Construction withSegmentAsEnds() {
    return new Construction(picks, number, true, maker, refusalKey, previewed);
  }

  /** A transformation: the image of its first pick, named when that is a point. */
  static Construction image(List<Pick> picks, Optional<String> number, Image image) {
    return new Construction(
        picks,
        number,
        false,
        (g, o, n, c) -> {
          Transform.Shape shape = Transform.shapeOf(o.getFirst()).orElseThrow();
          return List.of(
              new Made((p, id) -> image.make(p, id, n, shape), shape == Transform.Shape.POINT));
        },
        Optional.empty(),
        true);
  }

  /** The tangents from the point to the circle: two, one on the circle, none inside. */
  static List<Made> tangentLines(
      List<Geometry> g, List<CanvasObject> o, Expr n, CanvasState canvas) {
    Optional<Parametrisation.Circular> c = Constructions.circle(g.get(0));
    if (c.isEmpty() || !(g.get(1) instanceof Geometry.Point at)) {
      return List.of();
    }
    Optional<Parametrisation.Linear> t0 = Constructions.tangent(c.get(), at, 0);
    if (t0.isEmpty()) {
      return List.of();
    }
    List<Made> out = new ArrayList<>();
    int count = Constructions.onCircle(c.get(), at) ? 1 : 2;
    for (int k = 0; k < count; k++) {
      int index = k;
      out.add(
          new Made(
              (p, id) ->
                  new Line(id, Style.DEFAULT, new Line.CircleTangent(p.get(0), p.get(1), index)),
              false));
    }
    return out;
  }

  /** Whether an object is computed from another, through parents. */
  static boolean follows(CanvasState canvas, ObjectId o, ObjectId driver) {
    Set<ObjectId> seen = new HashSet<>();
    List<ObjectId> todo = new ArrayList<>(List.of(o));
    while (!todo.isEmpty()) {
      ObjectId id = todo.removeLast();
      if (id.equals(driver)) {
        return true;
      }
      if (seen.add(id)) {
        canvas.object(id).ifPresent(x -> todo.addAll(x.parents()));
      }
    }
    return false;
  }
}
