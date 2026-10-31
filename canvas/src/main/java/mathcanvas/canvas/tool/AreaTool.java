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
public final class AreaTool implements Bounded {

  private Scene scene;
  private Snap snap;
  private FunctionGraph upper;
  private FunctionGraph lower;
  private Expr from;
  private double fromValue = Double.NaN;

  /** Makes the area tool. */
  public AreaTool() {}

  @Override
  public boolean press(Scene scene, Pointer pointer, Consumer<Edit> edits) {
    move(scene, pointer);
    Optional<FunctionGraph> g = Picks.graph(scene, snap);
    if (upper == null) {
      if (g.isEmpty()) {
        return false;
      }
      upper = g.get();
      return true;
    }
    if (from == null && lower == null && g.isPresent() && !g.get().id().equals(upper.id())) {
      lower = g.get();
      return true;
    }
    double x = Picks.abscissa(snap);
    take(Picks.number(x, scene.window()), x, edits);
    return true;
  }

  @Override
  public Optional<String> wanted() {
    return upper == null ? Optional.empty() : Optional.of(from == null ? "a" : "b");
  }

  @Override
  public void typed(Expr e, double value, Consumer<Edit> edits) {
    if (upper != null) {
      take(e, value, edits);
    }
  }

  /** A bound: the first is kept, the second adds the area. */
  private void take(Expr bound, double value, Consumer<Edit> edits) {
    if (from == null) {
      from = bound;
      fromValue = value;
      return;
    }
    FunctionGraph u = upper;
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
  public void drag(Scene scene, Pointer pointer, Consumer<Edit> edits) {}

  @Override
  public void release(Scene scene, Pointer pointer, Consumer<Edit> edits) {}

  @Override
  public void move(Scene scene, Pointer pointer) {
    this.scene = scene;
    this.snap = Snap.of(scene, pointer);
  }

  @Override
  public void cancel() {
    upper = null;
    lower = null;
    from = null;
    fromValue = Double.NaN;
  }

  @Override
  public boolean inProgress() {
    return upper != null;
  }

  @Override
  public void paint(Painter painter, CanvasTheme theme) {
    if (scene == null || snap == null) {
      return;
    }
    if (upper == null) {
      Picks.graph(scene, snap)
          .ifPresent(g -> Preview.curve(painter, theme, scene.canvas(), g.id()));
      return;
    }
    Preview.curve(painter, theme, scene.canvas(), upper.id());
    if (lower != null) {
      Preview.curve(painter, theme, scene.canvas(), lower.id());
    } else if (from == null) {
      Picks.graph(scene, snap)
          .filter(g -> !g.id().equals(upper.id()))
          .ifPresent(g -> Preview.curve(painter, theme, scene.canvas(), g.id()));
    }
    Picks.vertical(painter, theme, fromValue, scene.window());
    Picks.vertical(painter, theme, Picks.abscissa(snap), scene.window());
  }
}
