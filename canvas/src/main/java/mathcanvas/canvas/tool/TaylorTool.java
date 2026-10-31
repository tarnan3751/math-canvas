package mathcanvas.canvas.tool;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.engine.expr.Apply;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;

/**
 * The Taylor tool: a click on a function graph picks it, then its centre {@code a}, a click (its
 * abscissa) or typed, adds the graph of {@code taylor(body, x, a, n)} in the next series colour,
 * with its order the slider {@code n}, which the same step adds (0 to 10, step 1, at 2) when the
 * canvas has none; the graph recomputes when {@code n} moves, since its body reads it.
 */
public final class TaylorTool implements Bounded {

  /** The variable of the order. */
  public static final String ORDER = "n";

  private Scene scene;
  private Snap snap;
  private FunctionGraph graph;

  /** Makes the Taylor tool. */
  public TaylorTool() {}

  @Override
  public boolean press(Scene scene, Pointer pointer, Consumer<Edit> edits) {
    move(scene, pointer);
    if (graph == null) {
      Optional<FunctionGraph> g = Picks.graph(scene, snap);
      if (g.isEmpty()) {
        return false;
      }
      graph = g.get();
      return true;
    }
    double x = Picks.abscissa(snap);
    take(Picks.number(x, scene.window()), edits);
    return true;
  }

  @Override
  public Optional<String> wanted() {
    return graph == null ? Optional.empty() : Optional.of("a");
  }

  @Override
  public void typed(Expr e, double value, Consumer<Edit> edits) {
    if (graph != null) {
      take(e, edits);
    }
  }

  private void take(Expr centre, Consumer<Edit> edits) {
    List<Edit.New> news = new ArrayList<>(2);
    if (Picks.slider(scene.canvas(), ORDER).isEmpty()) {
      news.add(
          new Edit.New(
              ids ->
                  new Slider(
                      ids.getLast(),
                      Style.DEFAULT,
                      ORDER,
                      BigDecimal.ZERO,
                      BigDecimal.TEN,
                      BigDecimal.ONE,
                      BigDecimal.TWO),
              false));
    }
    FunctionGraph g = graph;
    Expr body = Apply.of(Symbol.of("taylor"), g.body(), g.variable(), centre, Symbol.of(ORDER));
    news.add(
        new Edit.New(
            ids -> new FunctionGraph(ids.getLast(), Style.DEFAULT, body, g.variable(), g.domain()),
            false));
    edits.accept(new Edit.Add(news));
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
    graph = null;
  }

  @Override
  public boolean inProgress() {
    return graph != null;
  }

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
    Picks.vertical(painter, theme, Picks.abscissa(snap), scene.window());
  }
}
