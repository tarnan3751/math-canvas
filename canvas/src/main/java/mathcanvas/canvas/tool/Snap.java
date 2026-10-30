package mathcanvas.canvas.tool;

import java.util.Optional;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;

/**
 * What a click would use: an existing point within the hit tolerance, else a point on the curve
 * within it, else a free point at the pointer (PLAN §5.3, Tools). The preview shows which.
 */
public sealed interface Snap {

  /**
   * An existing point.
   *
   * @param point the point
   * @param x its abscissa
   * @param y its ordinate
   */
  record Existing(ObjectId point, double x, double y) implements Snap {}

  /**
   * A point on a curve, not yet an object.
   *
   * @param path the curve
   * @param at where on it, with its parameter
   */
  record OnPath(ObjectId path, PathProjection.Projection at) implements Snap {}

  /**
   * A free point at the pointer.
   *
   * @param x the abscissa
   * @param y the ordinate
   */
  record Free(double x, double y) implements Snap {}

  /**
   * The snap of a pointer.
   *
   * @param scene the document
   * @param pointer the pointer
   * @return the snap
   */
  static Snap of(Scene scene, Pointer pointer) {
    if (pointer.picked().isPresent()) {
      Pointer.Picked p = pointer.picked().get();
      Optional<CanvasObject> o = scene.canvas().object(p.id());
      Geometry g = scene.canvas().geometryOf(p.id());
      if (o.isPresent() && o.get().isPoint() && g instanceof Geometry.Point at) {
        return new Existing(p.id(), at.x(), at.y());
      }
      if (o.isPresent()
          && o.get().isPath()
          && p.hit().kind() == Hit.Kind.POLYLINE
          && g instanceof Geometry.Curve c) {
        Optional<PathProjection.Projection> on = PathProjection.onto(c, p.hit().x(), p.hit().y());
        if (on.isPresent()) {
          return new OnPath(p.id(), on.get());
        }
      }
    }
    return new Free(pointer.x(), pointer.y());
  }
}
