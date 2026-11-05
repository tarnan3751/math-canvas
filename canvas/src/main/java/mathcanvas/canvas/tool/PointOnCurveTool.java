package mathcanvas.canvas.tool;

import java.util.List;
import java.util.function.Consumer;
import mathcanvas.canvas.model.Geometry;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.ParamKind;
import mathcanvas.canvas.model.PointOnPath;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;

/**
 * The point on curve tool: a click on a curve adds a point on it, named, at the parameter of the
 * nearest point of the curve's samples ({@link PathProjection}), and dragging on moves it along the
 * curve; a press on an existing point on a curve drags it along its curve. A press anywhere else
 * passes to the view, which pans. The preview shows where the point would go, or rings the point a
 * press would drag.
 */
public final class PointOnCurveTool extends ClickTool {

  /** The point being dragged, or null. */
  private ObjectId dragging;

  /** Makes the point on curve tool. */
  public PointOnCurveTool() {}

  @Override
  public boolean press(Scene scene, Pointer pointer, Consumer<Edit> edits) {
    move(scene, pointer);
    switch (snap) {
      case Snap.Existing e -> {
        // a point placed at an expression follows it, not the pointer
        if (!(scene.canvas().object(e.point()).orElse(null) instanceof PointOnPath p)
            || p.at().isPresent()) {
          return false;
        }
        dragging = e.point();
      }
      case Snap.OnPath o -> {
        PathProjection.Projection r = o.at();
        ObjectId path = o.path();
        dragging = scene.canvas().nextObjectId();
        edits.accept(
            new Edit.Add(
                List.of(
                    new Edit.New(
                        ids ->
                            new PointOnPath(
                                ids.getLast(), Style.DEFAULT, path, r.kind(), r.t(), r.x(), r.y()),
                        true))));
      }
      case Snap.Free f -> {
        return false;
      }
    }
    return true;
  }

  @Override
  public void drag(Scene scene, Pointer pointer, Consumer<Edit> edits) {
    this.scene = scene;
    if (dragging == null
        || !(scene.canvas().object(dragging).orElse(null) instanceof PointOnPath p)
        || !(scene.canvas().geometryOf(p.path()) instanceof Geometry.Curve c)) {
      return;
    }
    PathProjection.onto(c, pointer.x(), pointer.y())
        .filter(r -> (r.kind() == ParamKind.NONE) == (p.kind() == ParamKind.NONE))
        .ifPresent(r -> edits.accept(new Edit.Move(p.id(), r.x(), r.y(), r.t())));
  }

  @Override
  public void release(Scene scene, Pointer pointer, Consumer<Edit> edits) {
    dragging = null;
  }

  @Override
  public void cancel() {
    scene = null;
    snap = null;
    dragging = null;
  }

  @Override
  public boolean inProgress() {
    return dragging != null;
  }

  @Override
  public void paint(Painter painter, CanvasTheme theme) {
    if (snap == null || dragging != null) {
      return;
    }
    switch (snap) {
      case Snap.Existing e -> {
        if (scene.canvas().object(e.point()).orElse(null) instanceof PointOnPath) {
          Preview.snap(painter, theme, snap);
        }
      }
      case Snap.OnPath o -> {
        Preview.curve(painter, theme, scene.canvas(), o.path());
        Preview.snap(painter, theme, snap);
      }
      case Snap.Free f -> {}
    }
  }
}
