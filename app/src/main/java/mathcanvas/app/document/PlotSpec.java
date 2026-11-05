package mathcanvas.app.document;

import java.util.Objects;
import mathcanvas.canvas.model.CanvasObject;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.ImplicitCurve;
import mathcanvas.canvas.model.ObjectId;
import mathcanvas.canvas.model.Style;
import mathcanvas.engine.expr.Relation;

/**
 * A graph a cell draws with {@code plot(e)} ({@code docs/language.md} §12.3): what the document
 * makes of it is a canvas object the cell owns, created or replaced when the cell's output arrives.
 *
 * @param equation the equation as typed, made canonical and never evaluated, so the graph follows
 *     the names it reads: {@code y = e} for a function graph, any equation for an implicit curve
 * @param function whether it is a function graph of the right side in {@code x}
 */
public record PlotSpec(Relation equation, boolean function) {

  /**
   * Checks the fields.
   *
   * @throws NullPointerException if the equation is null
   */
  public PlotSpec {
    Objects.requireNonNull(equation, "equation");
  }

  /**
   * The canvas object of this graph.
   *
   * @param id its id
   * @param style how it is drawn
   * @return a function graph of the right side in {@code x}, or an implicit curve
   */
  public CanvasObject make(ObjectId id, Style style) {
    return function
        ? new FunctionGraph(id, style, equation.right(), ImplicitCurve.X, FunctionGraph.ALL)
        : new ImplicitCurve(id, style, equation);
  }
}
