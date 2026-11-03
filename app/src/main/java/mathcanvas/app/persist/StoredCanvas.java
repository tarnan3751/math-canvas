package mathcanvas.app.persist;

import java.util.Map;
import java.util.Objects;
import mathcanvas.app.document.DocumentState;
import mathcanvas.canvas.geom.Viewport;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.space.Camera;

/**
 * What the session file keeps of the canvas: the objects with their ids and {@code nextId}, the
 * names the document gives points, the viewport and the 3D view's camera. Geometry is not kept; a
 * decoded state has none, so every object is undefined until it is recomputed.
 *
 * @param state the objects and the id counter
 * @param names the names of the named points (a slider's name is its variable, not here)
 * @param viewport the viewport, its pixel size included, which a view of another size resizes
 *     keeping its centre and scale
 * @param camera the 3D view's camera
 */
public record StoredCanvas(
    CanvasState state, Map<ObjectId, String> names, Viewport viewport, Camera camera) {

  /** No objects at a new document's viewport: what a file of version 1 gets. */
  public static final StoredCanvas DEFAULT =
      new StoredCanvas(CanvasState.EMPTY, Map.of(), DocumentState.DEFAULT_VIEWPORT);

  /**
   * Copies the names and checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public StoredCanvas {
    Objects.requireNonNull(state, "state");
    names = Map.copyOf(names);
    Objects.requireNonNull(viewport, "viewport");
    Objects.requireNonNull(camera, "camera");
  }

  /**
   * A canvas seen through the default camera.
   *
   * @param state the objects and the id counter
   * @param names the names of the named points
   * @param viewport the viewport
   */
  public StoredCanvas(CanvasState state, Map<ObjectId, String> names, Viewport viewport) {
    this(state, names, viewport, Camera.DEFAULT);
  }
}
