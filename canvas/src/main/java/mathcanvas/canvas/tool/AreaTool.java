package mathcanvas.canvas.tool;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.ShadedArea;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.engine.expr.Expr;

/**
 * The area tool: a click on a function graph picks it; a click on a second graph before the bounds
 * makes an area between the two (the first above); then the bounds {@code a} and {@code b}, each a
 * click (its abscissa) or typed in the entry, add the shaded area. Escape abandons it.
 */
public final class AreaTool extends GraphNumberTool {

  private FunctionGraph lower;
  private Expr from;
  private double fromValue = Double.NaN;

  /** Makes the area tool. */
  public AreaTool() {}

  /** A second graph, before the bounds, is the lower one. */
  @Override
  boolean another(Optional<FunctionGraph> g) {
    if (from == null && lower == null && g.isPresent() && !g.get().id().equals(graph.id())) {
      lower = g.get();
      return true;
    }
    return false;
  }

  @Override
  String next() {
    return from == null ? "a" : "b";
  }

  /** A bound: the first is kept, the second adds the area. */
  @Override
  void take(Expr bound, double value, Consumer<Edit> edits) {
    if (from == null) {
      from = bound;
      fromValue = value;
      return;
    }
    FunctionGraph u = graph;
    Optional<FunctionGraph> l = Optional.ofNullable(lower);
    Expr a = from;
    edits.accept(
        new Edit.Add(
            List.of(
                new Edit.New(
                    ids ->
                        new ShadedArea(
                            ids.getLast(),
                            Style.DEFAULT,
                            u.id(),
                            l.map(FunctionGraph::id),
                            a,
                            bound),
                    false))));
    cancel();
  }

  @Override
  public void cancel() {
    super.cancel();
    lower = null;
    from = null;
    fromValue = Double.NaN;
  }

  @Override
  void paintPicked(Painter painter, CanvasTheme theme) {
    if (lower != null) {
      Preview.curve(painter, theme, scene.canvas(), lower.id());
    } else if (from == null) {
      Picks.graph(scene, snap)
          .filter(g -> !g.id().equals(graph.id()))
          .ifPresent(g -> Preview.curve(painter, theme, scene.canvas(), g.id()));
    }
    Picks.vertical(painter, theme, fromValue, scene.window());
  }
}
