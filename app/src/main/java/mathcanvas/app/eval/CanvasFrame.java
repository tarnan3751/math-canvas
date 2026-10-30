package mathcanvas.app.eval;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.view.PlotContent;

/**
 * What the canvas side of the window shows, built on the worker: the canvas it was built from, the
 * content drawn for it with its hit index, which object each drawn item is, and the algebra panel's
 * rows. Each frame replaces the last whole; the FX thread only sets it on the view and the panel.
 *
 * @param state the canvas the content and the rows were built from
 * @param content the drawn content
 * @param items the object of each item of the content's hit index
 * @param rows the algebra panel's row of each object, in the canvas's drawing order
 */
public record CanvasFrame(
    CanvasState state,
    PlotContent content,
    Map<Integer, ObjectId> items,
    Map<ObjectId, AlgebraRow> rows) {

  /** An empty canvas, nothing drawn. */
  public static final CanvasFrame EMPTY =
      new CanvasFrame(CanvasState.EMPTY, PlotContent.EMPTY, Map.of(), Map.of());

  /**
   * Copies the maps, keeping the rows' order.
   *
   * @throws NullPointerException if a field is null
   */
  public CanvasFrame {
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(content, "content");
    items = Map.copyOf(items);
    rows = Collections.unmodifiableMap(new LinkedHashMap<>(rows));
  }
}
