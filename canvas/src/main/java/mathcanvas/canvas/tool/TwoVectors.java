package mathcanvas.canvas.tool;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;

/**
 * A tool that picks two vectors (arrows, sums or projections), {@code u} then {@code v}, and adds
 * the object made of them as one step: the vector sum tool and the projection tool. A click on
 * {@code u} again keeps it; a click on nothing passes to the view, which pans, and keeps {@code u}.
 * The preview highlights {@code u} once picked and the vector under the pointer.
 */
abstract class TwoVectors implements Tool {

  private ObjectId first;
  private Scene scene;
  private ObjectId hovered;

  /**
   * The object made of two vectors.
   *
   * @param id its id
   * @param style its style
   * @param u the first vector picked
   * @param v the second
   * @return the object
   */
  abstract CanvasObject make(ObjectId id, Style style, ObjectId u, ObjectId v);

  @Override
  public boolean press(Scene scene, Pointer pointer, Consumer<Edit> edits) {
    move(scene, pointer);
    if (hovered == null) {
      return false;
    }
    if (first == null) {
      first = hovered;
    } else if (!first.equals(hovered)) {
      ObjectId u = first;
      ObjectId v = hovered;
      first = null;
      edits.accept(
          new Edit.Add(
              List.of(new Edit.New(ids -> make(ids.getLast(), Style.DEFAULT, u, v), false))));
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
    hovered = vector(scene, pointer).orElse(null);
  }

  /** The vector under the pointer. */
  private static Optional<ObjectId> vector(Scene scene, Pointer pointer) {
    return pointer
        .picked()
        .map(Pointer.Picked::id)
        .filter(id -> scene.canvas().object(id).filter(CanvasObject::isVector).isPresent());
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
      Preview.vector(painter, theme, scene.canvas(), first);
    }
    if (hovered != null && !hovered.equals(first)) {
      Preview.vector(painter, theme, scene.canvas(), hovered);
    }
  }
}
