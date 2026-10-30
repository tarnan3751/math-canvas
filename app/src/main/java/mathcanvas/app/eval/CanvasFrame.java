package mathcanvas.app.eval;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleUnaryOperator;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.view.PlotContent;

/**
 * What the canvas side of the window shows, built on the worker: the canvas it was built from, the
 * content drawn for it with its hit index, which object each drawn item is, the algebra panel's
 * rows, and the slope of each function graph: its derivative compiled on the worker, which the
 * trace readout and the tangent tool's preview evaluate on the FX thread with no engine work. Each
 * frame replaces the last whole; the FX thread only sets it on the view and the panel.
 *
 * @param state the canvas the content and the rows were built from
 * @param content the drawn content
 * @param items the object of each item of the content's hit index
 * @param rows the algebra panel's row of each object, in the canvas's drawing order
 * @param slopes the derivative of each function graph, by its record, as a function of its
 *     variable: NaN where undefined
 */
public record CanvasFrame(
    CanvasState state,
    PlotContent content,
    Map<Integer, ObjectId> items,
    Map<ObjectId, AlgebraRow> rows,
    Map<FunctionGraph, DoubleUnaryOperator> slopes) {

  /** An empty canvas, nothing drawn. */
  public static final CanvasFrame EMPTY =
      new CanvasFrame(CanvasState.EMPTY, PlotContent.EMPTY, Map.of(), Map.of(), Map.of());

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
    slopes = Map.copyOf(slopes);
  }

  /**
   * A frame with no slopes known, as the tests show.
   *
   * @param state the canvas
   * @param content the drawn content
   * @param items the object of each item
   * @param rows the algebra panel's rows
   */
  public CanvasFrame(
      CanvasState state,
      PlotContent content,
      Map<Integer, ObjectId> items,
      Map<ObjectId, AlgebraRow> rows) {
    this(state, content, items, rows, Map.of());
  }

  /**
   * The slope of a function graph of this frame's canvas at an abscissa.
   *
   * @param graph the graph's id
   * @param x the abscissa
   * @return the derivative's value, NaN when the object is no graph, its slope is not known, or the
   *     derivative is undefined there
   */
  public double slope(ObjectId graph, double x) {
    if (!(state.object(graph).orElse(null) instanceof FunctionGraph g)) {
      return Double.NaN;
    }
    DoubleUnaryOperator f = slopes.get(g);
    return f == null ? Double.NaN : f.applyAsDouble(x);
  }
}
