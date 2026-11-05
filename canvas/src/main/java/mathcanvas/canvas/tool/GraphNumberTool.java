package mathcanvas.canvas.tool;

import java.util.Optional;
import java.util.function.Consumer;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.engine.expr.Expr;

/**
 * The tools that pick a function graph, then take numbers, each a click (its abscissa, as {@link
 * Picks#number} rounds it) or typed in the entry: the area, the Riemann sum, the Taylor polynomial.
 * The graph picked is the construction in progress; {@link #take} is given each number.
 */
abstract class GraphNumberTool extends ClickTool implements Bounded {

  /** The graph picked, or null while the tool waits for one. */
  FunctionGraph graph;

  @Override
  public boolean press(Scene scene, Pointer pointer, Consumer<Edit> edits) {
    move(scene, pointer);
    Optional<FunctionGraph> g = Picks.graph(scene, snap);
    if (graph == null) {
      if (g.isEmpty()) {
        return false;
      }
      graph = g.get();
      return true;
    }
    if (another(g)) {
      return true;
    }
    double x = Picks.abscissa(snap);
    take(Picks.number(x, scene.window()), x, edits);
    return true;
  }

  /**
   * A click on a graph once one is picked: whether the tool takes it as a graph rather than a
   * number. None does but the area tool's second graph.
   *
   * @param g the graph under the pointer, if any
   * @return whether the click was taken
   */
  boolean another(Optional<FunctionGraph> g) {
    return false;
  }

  @Override
  public Optional<String> wanted() {
    return graph == null ? Optional.empty() : Optional.of(next());
  }

  /** The name of the number wanted next, once a graph is picked. */
  abstract String next();

  @Override
  public void typed(Expr e, double value, Consumer<Edit> edits) {
    if (graph != null) {
      take(e, value, edits);
    }
  }

  /**
   * A number, clicked or typed, once a graph is picked.
   *
   * @param e the number as the object keeps it
   * @param value its value now, for the preview; NaN when it has none
   * @param edits where the edits go
   */
  abstract void take(Expr e, double value, Consumer<Edit> edits);

  @Override
  public void cancel() {
    graph = null;
  }

  @Override
  public boolean inProgress() {
    return graph != null;
  }

  /**
   * Highlights the graph under the pointer until one is picked, then the graph picked, what {@link
   * #paintPicked} adds, and the abscissa a click would take.
   */
  @Override
  public void paint(Painter painter, CanvasTheme theme) {
    if (scene == null || snap == null) {
      return;
    }
    if (graph == null) {
      Picks.graph(scene, snap)
          .ifPresent(g -> Preview.curve(painter, theme, scene.canvas(), g.id()));
      return;
    }
    Preview.curve(painter, theme, scene.canvas(), graph.id());
    paintPicked(painter, theme);
    Picks.vertical(painter, theme, Picks.abscissa(snap), scene.window());
  }

  /**
   * The tool's own preview once a graph is picked, between the graph and the pointer's abscissa.
   *
   * @param painter the overlay's painter
   * @param theme the colours
   */
  void paintPicked(Painter painter, CanvasTheme theme) {}
}
