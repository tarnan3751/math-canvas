package mathcanvas.canvas.tool;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.RiemannSum;
import mathcanvas.canvas.model.Slider;
import mathcanvas.canvas.model.Style;
import mathcanvas.canvas.paint.CanvasTheme;
import mathcanvas.canvas.paint.Painter;
import mathcanvas.engine.compile.Quadrature;
import mathcanvas.engine.expr.Expr;
import mathcanvas.engine.expr.Symbol;

/**
 * The Riemann sum tool: a click on a function graph picks it, then the bounds {@code a} and {@code
 * b}, each a click or typed, add the sum, by the left rule, with its number of pieces the slider
 * {@code n}, which the same step adds (1 to 100, step 1, at 4) when the canvas has none. The method
 * is changed in the algebra row.
 */
public final class RiemannTool implements Bounded {

  /** The variable of the number of pieces. */
  public static final String PIECES = "n";

  private Scene scene;
  private Snap snap;
  private FunctionGraph graph;
  private Expr from;
  private double fromValue = Double.NaN;

  /** Makes the Riemann sum tool. */
  public RiemannTool() {}

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
    take(Picks.number(x, scene.window()), x, edits);
    return true;
  }

  @Override
  public Optional<String> wanted() {
    return graph == null ? Optional.empty() : Optional.of(from == null ? "a" : "b");
  }

  @Override
  public void typed(Expr e, double value, Consumer<Edit> edits) {
    if (graph != null) {
      take(e, value, edits);
    }
  }

  private void take(Expr bound, double value, Consumer<Edit> edits) {
    if (from == null) {
      from = bound;
      fromValue = value;
      return;
    }
    List<Edit.New> news = new ArrayList<>(2);
    if (Picks.slider(scene.canvas(), PIECES).isEmpty()) {
      news.add(
          new Edit.New(
              ids ->
                  new Slider(
                      ids.getLast(),
                      Style.DEFAULT,
                      PIECES,
                      BigDecimal.ONE,
                      BigDecimal.valueOf(100),
                      BigDecimal.ONE,
                      BigDecimal.valueOf(4)),
              false));
    }
    FunctionGraph g = graph;
    Expr a = from;
    news.add(
        new Edit.New(
            ids ->
                new RiemannSum(
                    ids.getLast(),
                    Style.DEFAULT,
                    g.id(),
                    a,
                    bound,
                    Symbol.of(PIECES),
                    Quadrature.Method.LEFT),
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
    from = null;
    fromValue = Double.NaN;
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
    Picks.vertical(painter, theme, fromValue, scene.window());
    Picks.vertical(painter, theme, Picks.abscissa(snap), scene.window());
  }
}
