package mathcanvas.canvas.tool;

import java.util.Optional;
import java.util.function.Consumer;
import mathcanvas.canvas.hit.Hit;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;

/**
 * The intersection tool: a click on one path, then a click on another, adds every intersection of
 * the two found now ({@link Edit.Intersect}), the path with the lower id first whichever was
 * clicked first, so a pair is always one family. A click on the first path again keeps it; a click
 * on nothing passes to the view, which pans, and keeps the first path. The preview highlights the
 * first path once chosen and the path under the pointer.
 */
public final class IntersectionTool implements Tool {

  private ObjectId first;
  private Scene scene;
  private ObjectId hovered;

  /** Makes the intersection tool, with no path chosen. */
  public IntersectionTool() {}

  @Override
  public boolean press(Scene scene, Pointer pointer, Consumer<Edit> edits) {
    move(scene, pointer);
    if (hovered == null) {
      return false;
    }
    if (first == null) {
      first = hovered;
    } else if (!first.equals(hovered)) {
      ObjectId a = first.compareTo(hovered) < 0 ? first : hovered;
      ObjectId b = a == first ? hovered : first;
      first = null;
      edits.accept(new Edit.Intersect(a, b));
    }
    return true;
  }

  @Override
  public void drag(Scene scene, Pointer pointer, Consumer<Edit> edits) {}

  @Override
  public void release(Scene scene, Pointer pointer, Consumer<Edit> edits) {}

  @Override
  public void move(Scene scene, Pointer pointer) {
    this.scene = scene;
    if (first != null && scene.canvas().object(first).isEmpty()) {
      first = null; // removed meanwhile, by an undo
    }
    hovered = path(scene, pointer).orElse(null);
  }

  /** The path under the pointer. */
  private static Optional<ObjectId> path(Scene scene, Pointer pointer) {
    return pointer
        .picked()
        .filter(p -> p.hit().kind() == Hit.Kind.POLYLINE)
        .map(Pointer.Picked::id)
        .filter(id -> scene.canvas().object(id).filter(o -> o.isPath()).isPresent());
  }

  @Override
  public void cancel() {
    first = null;
    hovered = null;
    scene = null;
  }

  @Override
  public boolean inProgress() {
    return first != null;
  }

  @Override
  public void paint(Painter painter, CanvasTheme theme) {
    if (scene == null) {
      return;
    }
    if (first != null) {
      Preview.curve(painter, theme, scene.canvas(), first);
    }
    if (hovered != null && !hovered.equals(first)) {
      Preview.curve(painter, theme, scene.canvas(), hovered);
    }
  }
}
