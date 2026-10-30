package mathcanvas.canvas.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import mathcanvas.engine.Cancellation;

/**
 * Computes a group's geometry from its definitions, its parents' geometry and a sampler. A group is
 * one object, or a family: every intersection of one ordered pair of paths, recomputed together so
 * the family's members can be matched to the candidates jointly. A recompute may also update an
 * object's tracking state (a point on a path's last position, an intersection's anchor and index),
 * never its definition otherwise. {@link CanvasState#recompute} calls it once per affected group,
 * after the group's parents, and only when every parent is defined.
 */
@FunctionalInterface
public interface Recompute {

  /**
   * Recomputes a group.
   *
   * @param group the objects: one, or the members of an intersection family in drawing order
   * @param parents the geometry of each of the group's parents, all defined
   * @param sampler the sampler
   * @param window the window the window-sampled objects are sampled over
   * @param cancellation checked as it goes
   * @return one result per object, in the group's order
   */
  List<Result> apply(
      List<CanvasObject> group,
      Map<ObjectId, Geometry> parents,
      Sampler sampler,
      Window window,
      Cancellation cancellation);

  /**
   * A recomputed object.
   *
   * @param object the object, with its tracking state updated
   * @param geometry its geometry
   */
  record Result(CanvasObject object, Geometry geometry) {

    /**
     * Checks the fields.
     *
     * @throws NullPointerException if a field is null
     */
    public Result {
      Objects.requireNonNull(object, "object");
      Objects.requireNonNull(geometry, "geometry");
    }
  }
}
