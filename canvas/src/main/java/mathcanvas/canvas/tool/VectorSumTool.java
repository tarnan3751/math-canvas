package mathcanvas.canvas.tool;

import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.model.VectorSum;

/**
 * The vector sum tool: a click on a vector {@code u}, then on another {@code v}, adds {@code u + v}
 * from {@code u}'s tail with its parallelogram ({@link VectorSum}).
 */
public final class VectorSumTool extends TwoVectors {

  /** Makes the vector sum tool, with no vector picked. */
  public VectorSumTool() {}

  @Override
  CanvasObject make(ObjectId id, Style style, ObjectId u, ObjectId v) {
    return new VectorSum(id, style, u, v);
  }
}
