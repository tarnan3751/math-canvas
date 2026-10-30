package mathcanvas.canvas.tool;

import java.util.Objects;
import mathcanvas.canvas.model.CanvasState;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Window;

/**
 * What a tool sees of the document at an event: the canvas (its objects and their geometry as last
 * computed), the visible window, and the slopes of the function graphs.
 *
 * @param canvas the canvas
 * @param window the visible window
 * @param slopes the slope of each function graph at an abscissa
 */
public record Scene(CanvasState canvas, Window window, Slopes slopes) {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if a field is null
   */
  public Scene {
    Objects.requireNonNull(canvas, "canvas");
    Objects.requireNonNull(window, "window");
    Objects.requireNonNull(slopes, "slopes");
  }

  /** The slope of a function graph, from evaluators compiled on the worker. */
  @FunctionalInterface
  public interface Slopes {

    /**
     * The slope of a graph at an abscissa.
     *
     * @param graph the graph's id
     * @param x the abscissa
     * @return the derivative's value there, NaN where it is undefined or not known
     */
    double at(ObjectId graph, double x);

    /** No slope known. */
    Slopes NONE = (graph, x) -> Double.NaN;
  }
}
