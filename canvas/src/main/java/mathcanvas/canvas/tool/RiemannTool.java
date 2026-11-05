package mathcanvas.canvas.tool;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import mathcanvas.canvas.model.FunctionGraph;
import mathcanvas.canvas.model.RiemannSum;
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
public final class RiemannTool extends GraphNumberTool {

  /** The variable of the number of pieces. */
  public static final String PIECES = "n";

  private Expr from;
  private double fromValue = Double.NaN;

  /** Makes the Riemann sum tool. */
  public RiemannTool() {}

  @Override
  String next() {
    return from == null ? "a" : "b";
  }

  @Override
  void take(Expr bound, double value, Consumer<Edit> edits) {
    if (from == null) {
      from = bound;
      fromValue = value;
      return;
    }
    List<Edit.New> news = new ArrayList<>(2);
    Picks.sliderIfAbsent(
        news,
        scene.canvas(),
        PIECES,
        BigDecimal.ONE,
        BigDecimal.valueOf(100),
        BigDecimal.ONE,
        BigDecimal.valueOf(4));
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
  public void cancel() {
    super.cancel();
    from = null;
    fromValue = Double.NaN;
  }

  @Override
  void paintPicked(Painter painter, CanvasTheme theme) {
    Picks.vertical(painter, theme, fromValue, scene.window());
  }
}
