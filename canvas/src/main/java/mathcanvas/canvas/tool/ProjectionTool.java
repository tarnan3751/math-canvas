package mathcanvas.canvas.tool;

import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Projection;
import mathcanvas.canvas.model.Style;

/**
 * The projection tool: a click on the vector {@code u} to project, then on the vector {@code v} to
 * project onto, adds {@code proj_v u} with its perpendicular ({@link Projection}).
 */
public final class ProjectionTool extends TwoVectors {

  /** Makes the projection tool, with no vector picked. */
  public ProjectionTool() {}

  @Override
  CanvasObject make(ObjectId id, Style style, ObjectId u, ObjectId v) {
    return new Projection(id, style, u, v);
  }
}
