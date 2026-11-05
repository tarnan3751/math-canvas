package mathcanvas.canvas.tool;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.FreePoint;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.SlopeField;
import mathcanvas.canvas.model.SolutionCurve;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;

/**
 * The solution tool: a click adds the solution curve of a slope field through a new free point
 * there, named, as one step, and dragging on moves the point, the curve following it; a click on an
 * existing point adds the solution through that point alone. The field is the slope field under the
 * pointer, else the last one drawn; with no slope field drawn, a press passes to the view, which
 * pans. The preview shows where the point would go.
 */
public final class SolutionTool extends ClickTool {

  /** The point being dragged, or null. */
  private ObjectId dragging;

  /** Makes the solution tool. */
  public SolutionTool() {}

  @Override
  public boolean press(Scene scene, Pointer pointer, Consumer<Edit> edits) {
    move(scene, pointer);
    Optional<ObjectId> field = field(scene, pointer);
    if (field.isEmpty()) {
      return false;
    }
    ObjectId f = field.get();
    if (snap instanceof Snap.Existing e) {
      ObjectId point = e.point();
      edits.accept(
          new Edit.Add(
              List.of(
                  new Edit.New(
                      ids -> new SolutionCurve(ids.getLast(), Style.DEFAULT, f, point), false))));
      return true;
    }
    double x = pointer.x();
    double y = pointer.y();
    dragging = scene.canvas().nextObjectId();
    edits.accept(
        new Edit.Add(
            List.of(
                new Edit.New(ids -> new FreePoint(ids.getFirst(), Style.DEFAULT, x, y), true),
                new Edit.New(
                    ids -> new SolutionCurve(ids.getLast(), Style.DEFAULT, f, ids.getFirst()),
                    false))));
    return true;
  }

  @Override
  public void drag(Scene scene, Pointer pointer, Consumer<Edit> edits) {
    this.scene = scene;
    if (dragging != null && scene.canvas().object(dragging).orElse(null) instanceof FreePoint) {
      edits.accept(new Edit.Move(dragging, pointer.x(), pointer.y(), Double.NaN));
    }
  }

  @Override
  public void release(Scene scene, Pointer pointer, Consumer<Edit> edits) {
    dragging = null;
  }

  @Override
  public void move(Scene scene, Pointer pointer) {
    this.scene = scene;
    // a new point is free wherever it is placed, on a curve or not
    this.snap =
        field(scene, pointer).isEmpty()
            ? null
            : Snap.of(scene, pointer) instanceof Snap.Existing e
                ? e
                : new Snap.Free(pointer.x(), pointer.y());
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
    Preview.snap(painter, theme, snap);
  }

  /** The slope field a click uses: the one under the pointer, else the last one drawn. */
  static Optional<ObjectId> field(Scene scene, Pointer pointer) {
    if (pointer.picked().isPresent()
        && scene.canvas().object(pointer.picked().get().id()).orElse(null) instanceof SlopeField s
        && s.style().visible()) {
      return Optional.of(s.id());
    }
    Optional<ObjectId> last = Optional.empty();
    for (CanvasObject o : scene.canvas().objects()) {
      if (o instanceof SlopeField s && s.style().visible()) {
        last = Optional.of(s.id());
      }
    }
    return last;
  }
}
